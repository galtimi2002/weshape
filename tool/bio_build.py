"""Builds the biomass map data: four small rasters the backend reads for a point, and map tiles
for the three map layers.

Usage: python3 tool/bio_build.py <SPAM production folder or zip> <ESA CCI AGB file> <out folder> [max zoom]

Sources
  Crop residues   MapSPAM 2020 V2r2 (IFPRI, CC BY 4.0): production per crop (t/year) on a
                  5 arc-minute grid (~9 km). Residue = production x residue-to-product ratio x 40 %
                  (the rest stays on the field for the soil). Ratios and heating values per crop: RESIDUES.
  Forest residues ESA CCI Biomass above-ground biomass (Mg/ha): the yearly share that can be taken
                  sustainably as harvest residues (branches, tops) = AGB x FOREST_SHARE.

On the 5 arc-minute grid:
  density   available biomass, t/ha per year (crop + forest residues), averaged over the land
            within DENSITY_KM: SPAM spreads each district's crops over blocks of cells, which
            per cell would show as squares
  crop      crop residues within 25 km, thousand t per year
  forest    forest residues within 25 km, thousand t per year
  energy    their heating value within 25 km, GWh per year

Writes to <out>: density.tif, crop.tif, forest.tif, energy.tif (one byte per cell, log-encoded,
see ENCODING; 255 = no data), tiles/<layer>.tiles + .index for density, resource, energy
(z0..max zoom, default 7, packed like the wind tiles) and bio.json. The backend decodes with
the same rules (BioData.java).
"""
import glob
import io
import json
import math
import os
import re
import struct
import sys
import time
import zipfile

import numpy as np
from osgeo import gdal

gdal.UseExceptions()
gdal.SetConfigOption("GDAL_CACHEMAX", "2048")

NODATA = 255
TILE = 256
CELL = 1 / 12.0  # 5 arc-minutes
W, H = 4320, 2160
RADIUS_KM = 25.0
DENSITY_KM = 20.0
RESIDUE_SHARE = 0.40
FOREST_SHARE = 0.002  # of above-ground biomass per year
WOOD_MWH_PER_T = 5.1
# SPAM crop code: (residue-to-product ratio, MWh per tonne of dry residue)
RESIDUES = {
    "whea": (1.3, 4.1), "barl": (1.2, 4.1), "ocer": (1.3, 4.1), "rice": (1.2, 3.9),
    "maiz": (1.0, 4.7), "pmil": (1.4, 4.1), "smil": (1.4, 4.1), "sorg": (1.25, 4.2),
    "soyb": (1.5, 4.4), "rape": (1.5, 4.5), "sunf": (2.0, 4.0), "bean": (1.2, 4.2),
    "grou": (1.0, 4.4), "cott": (2.5, 4.6), "sugc": (0.15, 4.4), "cnut": (0.6, 4.6),
    "oilp": (0.4, 4.4), "cass": (0.2, 4.4),
}
# value = a x (exp(code / s) - 1); code 0 = nothing, 254 = the largest value
ENCODING = {"density": (0.01, 30.0), "crop": (0.1, 24.0), "forest": (0.1, 24.0), "energy": (0.4, 24.0)}
COLORS = ["#EAF4E0", "#A9D48C", "#5BAA5E", "#2A6B3B"]
# Map layers: (raster(s) summed, colour range)
LAYERS = {"density": (["density"], (0, 3)), "resource": (["crop", "forest"], (0, 500)),
          "energy": (["energy"], (0, 2000))}


def encode(name, v):
    a, s = ENCODING[name]
    return np.where(np.isfinite(v), np.clip(np.rint(s * np.log1p(np.maximum(v, 0) / a)), 0, 254), NODATA).astype(np.uint8)


def decode(name, code):
    a, s = ENCODING[name]
    return a * np.expm1(code / s)


def cell_area_ha():
    lat = 90 - (np.arange(H) + 0.5) * CELL
    side = 6371.0 * math.radians(CELL)
    return (side * side * np.cos(np.radians(lat)) * 100.0)[:, None]  # km² -> ha


# ---------------------------------------------------------------- crops

def crop_rasters(src):
    """Crop residues (t/year per cell) and their energy (MWh/year per cell) on the SPAM grid."""
    files = []
    if src.lower().endswith(".zip"):
        with zipfile.ZipFile(src) as z:
            names = [n for n in z.namelist() if n.lower().endswith(".tif")]
        files = ["/vsizip/" + os.path.abspath(src) + "/" + n for n in names]
    else:
        files = glob.glob(os.path.join(src, "**", "*.tif"), recursive=True)
    print(f"{len(files)} GeoTIFFs in the crop data, e.g.", *files[:4], sep="\n  ", flush=True)
    residue = np.zeros((H, W))
    energy = np.zeros((H, W))
    used = []
    for code, (rpr, mwh) in RESIDUES.items():
        # All technologies together: ..._P_WHEA_A.tif (SPAM naming); fall back to any _A file of the crop.
        match = [f for f in files if re.search(rf"_P_{code}_A\.tif$", f, re.I)] or \
                [f for f in files if re.search(rf"(^|[_/]){code}_a\.tif$", f, re.I)]
        if not match:
            continue
        ds = gdal.Open(match[0])
        if ds.RasterXSize != W or ds.RasterYSize != H:
            ds = gdal.Warp("", ds, format="MEM", width=W, height=H, outputBounds=[-180, -90, 180, 90],
                           resampleAlg="sum")
        band = ds.GetRasterBand(1)
        p = band.ReadAsArray().astype(np.float64)
        nd = band.GetNoDataValue()
        p[~np.isfinite(p) | (p < 0) | ((p == nd) if nd is not None else False)] = 0
        r = p * rpr * RESIDUE_SHARE
        residue += r
        energy += r * mwh
        used.append((code, round(float(p.sum()) / 1e6, 1)))
    print("crops used (million t produced):", used, flush=True)
    if not used:
        sys.exit("no crop production files matched")
    return residue, energy


# ---------------------------------------------------------------- forests

def forest_rasters(agb_path):
    """Forest residues (t/year per cell) on the SPAM grid, from above-ground biomass (Mg/ha)."""
    path = agb_path
    subs = gdal.Info(path, format="json").get("metadata", {}).get("SUBDATASETS", {})
    names = [v for k, v in subs.items() if k.endswith("_NAME")]
    if names:
        # A netCDF file holds several variables: take the biomass, not its uncertainty.
        agb = [n for n in names if re.search(r":agb$", n, re.I)] or names
        path = agb[0]
    print("forest biomass from", path, flush=True)
    ds = gdal.Warp("", path, format="MEM", width=W, height=H, outputBounds=[-180, -90, 180, 90],
                   resampleAlg="average", dstSRS="EPSG:4326")
    band = ds.GetRasterBand(1)
    agb = band.ReadAsArray().astype(np.float64)
    nd = band.GetNoDataValue()
    bad = ~np.isfinite(agb) | (agb < 0) | (agb > 2000) | ((agb == nd) if nd is not None else False)
    land = ~bad  # the biomass map covers land (0 where nothing grows), not the sea
    agb[bad] = 0
    agb[agb < 20] = 0  # not a forest
    print(f"forest biomass: median of forests {np.median(agb[agb > 0]):.0f} Mg/ha, "
          f"land cells {int(land.sum())}", flush=True)
    return agb * FOREST_SHARE * cell_area_ha(), land


# ---------------------------------------------------------------- within 25 km

def within_radius(grid, radius_km=RADIUS_KM):
    """Sum of every cell within radius_km of each cell (the cells' centres)."""
    out = np.zeros_like(grid)
    side = 6371.0 * math.radians(CELL)
    lat = 90 - (np.arange(H) + 0.5) * CELL
    ky = int(radius_km // side)
    for dy in range(-ky, ky + 1):
        shifted = np.roll(grid, dy, axis=0)
        if dy > 0:
            shifted[:dy] = 0
        elif dy < 0:
            shifted[dy:] = 0
        rest = max(0.0, radius_km ** 2 - (dy * side) ** 2)
        width = side * np.maximum(np.cos(np.radians(lat)), 0.05)
        kx = np.floor(np.sqrt(rest) / width).astype(int)  # per row
        for dx in range(0, int(kx.max()) + 1):
            rows = (kx >= dx)[:, None]
            if dx == 0:
                out += np.where(rows, shifted, 0)
            else:
                out += np.where(rows, np.roll(shifted, dx, axis=1) + np.roll(shifted, -dx, axis=1), 0)
    return out


# ---------------------------------------------------------------- output

def write(name, values, land, out):
    code = np.where(land, encode(name, values), NODATA).astype(np.uint8)
    work = os.path.join(out, name + ".work.tif")
    ds = gdal.GetDriverByName("GTiff").Create(work, W, H, 1, gdal.GDT_Byte, ["TILED=YES", "COMPRESS=ZSTD"])
    ds.SetGeoTransform([-180, CELL, 0, 90, 0, -CELL])
    ds.SetProjection('GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]]')
    ds.GetRasterBand(1).SetNoDataValue(NODATA)
    ds.GetRasterBand(1).WriteArray(code)
    ds = None
    gdal.Translate(os.path.join(out, name + ".tif"), work, format="COG", creationOptions=[
        "COMPRESS=ZSTD", "LEVEL=19", "PREDICTOR=2", "BLOCKSIZE=512", "OVERVIEWS=AUTO", "RESAMPLING=NEAREST"])
    os.remove(work)
    return code


def palette(lo, hi):
    rgb = [tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) for c in COLORS]
    out = []
    for v in range(255):
        t = max(0.0, min(1.0, (v / 254.0))) * (len(rgb) - 1)
        i = min(len(rgb) - 2, int(math.floor(t)))
        u = t - i
        out.append(tuple(int(math.floor(a + (b - a) * u + 0.5)) for a, b in zip(rgb[i], rgb[i + 1])))
    out.append((0, 0, 0))
    return [x for c in out for x in c]


def sample(grid, lat, lon):
    """Bilinear values between cell centres (cells without data left out); NaN where empty."""
    fx = (lon + 180) / CELL - 0.5
    fy = (90 - lat) / CELL - 0.5
    x0 = np.floor(fx).astype(np.int64)
    y0 = np.floor(fy).astype(np.int64)
    tx = (fx - x0)[None, :]
    ty = (fy - y0)[:, None]
    total = np.zeros((len(lat), len(lon)))
    weight = np.zeros_like(total)
    for dy, wy in ((0, 1 - ty), (1, ty)):
        rows = np.clip(y0 + dy, 0, H - 1)
        for dx, wx in ((0, 1 - tx), (1, tx)):
            v = grid[rows][:, (x0 + dx) % W]
            ok = np.isfinite(v)
            wgt = np.where(ok, wy * wx, 0)
            total += wgt * np.where(ok, v, 0)
            weight += wgt
    near = grid[np.clip(np.rint(fy).astype(np.int64), 0, H - 1)][:, np.rint(fx).astype(np.int64) % W]
    return np.where((weight > 0) & np.isfinite(near), total / np.maximum(weight, 1e-12), np.nan)


def render_tiles(values, out, max_zoom):
    """values: layer -> grid of the layer's value (NaN = no data)."""
    os.makedirs(out, exist_ok=True)
    from PIL import Image
    packs = {l: open(os.path.join(out, l + ".tiles"), "wb") for l in LAYERS}
    index = {l: [] for l in LAYERS}
    pal = palette(0, 1)
    started = time.time()
    for z in range(max_zoom + 1):
        n = 2 ** z
        lon = (np.arange(n * TILE) + 0.5) / (n * TILE) * 360 - 180
        count = 0
        for ty in range(n):
            py = (ty * TILE + np.arange(TILE) + 0.5) / (n * TILE)
            lat = np.degrees(np.arctan(np.sinh(np.pi * (1 - 2 * py))))
            for layer, (_, (lo, hi)) in LAYERS.items():
                v = sample(values[layer], lat, lon)
                code = np.where(np.isfinite(v), np.clip(np.rint((v - lo) / (hi - lo) * 254), 0, 254), NODATA).astype(np.uint8)
                for tx in range(n):
                    tile = code[:, tx * TILE:(tx + 1) * TILE]
                    if (tile == NODATA).all():
                        continue
                    img = Image.fromarray(np.ascontiguousarray(tile), mode="P")
                    img.putpalette(pal)
                    buf = io.BytesIO()
                    img.save(buf, format="PNG", optimize=True, transparency=NODATA)
                    data = buf.getvalue()
                    index[layer].append(((z << 48) | (tx << 24) | ty, packs[layer].tell(), len(data)))
                    packs[layer].write(data)
                    if layer == "density":
                        count += 1
        print(f"z{z}: {count} tiles per layer, {(time.time() - started) / 60:.1f} min", flush=True)
    sizes = {}
    for l in LAYERS:
        packs[l].close()
        index[l].sort()
        with open(os.path.join(out, l + ".index"), "wb") as f:
            f.write(b"WSTI" + struct.pack(">ii", 1, len(index[l])))
            for key, offset, length in index[l]:
                f.write(struct.pack(">qqi", key, offset, length))
        sizes[l] = {"tiles": len(index[l]), "MB": round(os.path.getsize(os.path.join(out, l + ".tiles")) / 1e6, 1)}
    return sizes


def main(spam, agb, out, max_zoom=7):
    started = time.time()
    os.makedirs(out, exist_ok=True)
    crop_t, crop_mwh = crop_rasters(spam)
    forest_t, agb_land = forest_rasters(agb)
    print(f"inputs read, {(time.time() - started) / 60:.1f} min", flush=True)
    area = cell_area_ha()
    # Land: where the biomass map has a value or crops grow; the sea stays empty.
    land = agb_land | (crop_t > 0)
    land_ha = np.where(land, np.broadcast_to(area, land.shape), 0.0)
    density = within_radius(crop_t + forest_t, DENSITY_KM) / np.maximum(within_radius(land_ha, DENSITY_KM), 1.0)
    crop_25 = within_radius(crop_t) / 1000.0
    forest_25 = within_radius(forest_t) / 1000.0
    energy_25 = within_radius(crop_mwh + forest_t * WOOD_MWH_PER_T) / 1000.0
    print(f"within 25 km done, {(time.time() - started) / 60:.1f} min", flush=True)
    codes = {
        "density": write("density", density, land, out),
        "crop": write("crop", crop_25, land, out),
        "forest": write("forest", forest_25, land, out),
        "energy": write("energy", energy_25, land, out),
    }
    # The map shows what the stored bytes say, so the card and the map agree.
    dec = {k: np.where(c == NODATA, np.nan, decode(k, c.astype(np.float64))) for k, c in codes.items()}
    values = {"density": dec["density"], "resource": dec["crop"] + dec["forest"], "energy": dec["energy"]}
    tiles = render_tiles(values, os.path.join(out, "tiles"), max_zoom)
    r, c = int((90 - 46.77) / CELL), int((23.59 + 180) / CELL)
    info = {
        "built": time.strftime("%Y-%m-%d"),
        "maxZoom": max_zoom,
        "tiles": tiles,
        "cluj": {k: round(float(v[r, c]), 2) for k, v in dec.items()},
        "world": {"cropResiduesMt": round(float(crop_t.sum()) / 1e6), "forestResiduesMt": round(float(forest_t.sum()) / 1e6)},
        "minutes": round((time.time() - started) / 60, 1),
    }
    with open(os.path.join(out, "bio.json"), "w") as f:
        json.dump(info, f, indent=1)
    print(json.dumps(info, indent=1))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 7)
