"""Builds the geothermal map data: two small rasters the backend reads for a point, and
map tiles for the six map layers.

Usage: python3 tool/geo_build.py <SoilTemp SBIO1 .tif> <heat flow table (.csv/.xlsx/.zip)> <out folder> [max zoom]

Sources
  Ground temperature  SoilTemp, Lembrechts et al. 2022, "Global maps of soil temperature":
                      SBIO1 annual mean soil temperature, 5-15 cm, 30 arc seconds (~1 km), CC BY 4.0.
                      The annual mean barely changes over the first metres, so it stands for the
                      ground a ground-source heat pump uses.
  Heat flow           The Global Heat Flow Database, release 2024 (IHFC, GFZ Data Services):
                      measurements (mW/m²) turned into a 0.25° grid with a Gaussian-weighted mean
                      (sigma 80 km; values clipped to 20-150 mW/m² so single hot springs do not
                      paint whole regions). Cells with no measurement within 250 km stay empty.
  Temperature at depth  ground temperature + heat flow / 2.5 W/(m·K) x depth (a conductive
                      estimate, the usual first guess), for 1, 2, 3 and 5 km.

Writes to <out>:
  ground.tif     ground temperature, byte = round((°C + 30) x 4) (-30 to 33.5 °C), 255 = no data
  heatflow.tif   heat flow, byte = round(mW/m²) (0-254), 255 = no data; land only
  tiles/<layer>.tiles + .index  map tiles z0..max zoom (default 7), 256 px palette PNGs, packed
                 like the wind tiles: ground, heatflow, depth-1, depth-2, depth-3, depth-5
  geo.json       sizes, counts and a check value for Cluj-Napoca
The backend decodes with the same rules (GeoData.java).
"""
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
CONDUCTIVITY = 2.5  # W/(m·K)
DEPTHS = [1, 2, 3, 5]  # km
HF_STEP = 0.25
HF_SIGMA_KM = 80.0
HF_REACH_KM = 250.0
COLORS = ["#FCEBDD", "#F3A77A", "#D45F35", "#8C2A1B"]
# Colour scale per layer (value range on the map); the backend reports the same.
SCALES = {
    "ground": (-5, 30), "heatflow": (30, 120),
    "depth-1": (20, 70), "depth-2": (40, 120), "depth-3": (60, 170), "depth-5": (100, 260),
}


def ground_code(t):
    return np.clip(np.rint((t + 30) * 4), 0, 254)


def ground_value(code):
    return code / 4.0 - 30


# ---------------------------------------------------------------- ground temperature

def build_ground(src_path, out_path):
    src = gdal.Open(src_path)
    band = src.GetRasterBand(1)
    w, h = src.RasterXSize, src.RasterYSize
    nodata = band.GetNoDataValue()
    # Some releases store tenths of a degree: look at a sample to decide.
    sample = band.ReadAsArray(0, h // 3, w, min(400, h - h // 3)).astype(np.float64)
    ok = np.isfinite(sample) & (sample != nodata if nodata is not None else True) & (np.abs(sample) < 1e6)
    p95 = float(np.percentile(np.abs(sample[ok]), 95)) if ok.any() else 0
    scale = 0.1 if p95 > 100 else 1.0
    print(f"ground: {w} x {h}, nodata {nodata}, |value| p95 {p95:.1f} -> scale {scale}", flush=True)
    work = out_path + ".work.tif"
    dst = gdal.GetDriverByName("GTiff").Create(work, w, h, 1, gdal.GDT_Byte,
                                               ["TILED=YES", "COMPRESS=ZSTD", "PREDICTOR=2", "BIGTIFF=YES"])
    dst.SetGeoTransform(src.GetGeoTransform())
    dst.SetProjection(src.GetProjection())
    ob = dst.GetRasterBand(1)
    ob.SetNoDataValue(NODATA)
    for y in range(0, h, 1024):
        rows = min(1024, h - y)
        v = band.ReadAsArray(0, y, w, rows).astype(np.float64)
        valid = np.isfinite(v) & (np.abs(v) < 1e6)
        if nodata is not None:
            valid &= v != nodata
        ob.WriteArray(np.where(valid, ground_code(v * scale), NODATA).astype(np.uint8), 0, y)
    dst.FlushCache()
    dst = None
    cog(work, out_path)


def cog(work, out_path):
    gdal.Translate(out_path, work, format="COG", creationOptions=[
        "COMPRESS=ZSTD", "LEVEL=19", "PREDICTOR=2", "BLOCKSIZE=512", "OVERVIEWS=AUTO",
        "RESAMPLING=NEAREST", "BIGTIFF=YES", "NUM_THREADS=ALL_CPUS"])
    os.remove(work)


# ---------------------------------------------------------------- heat flow

def read_table(path):
    import pandas as pd
    if path.lower().endswith(".zip"):
        with zipfile.ZipFile(path) as z:
            names = [n for n in z.namelist() if n.lower().endswith((".csv", ".xlsx", ".txt", ".tsv"))]
            names.sort(key=lambda n: -z.getinfo(n).file_size)
            print("tables in the zip:", names, flush=True)
            name = names[0]
            data = z.read(name)
        path = name
        buf = io.BytesIO(data)
    else:
        buf = path
    if path.lower().endswith(".xlsx"):
        frames = pd.read_excel(buf, sheet_name=None)
        df = max(frames.values(), key=len)
    else:
        df = pd.read_csv(buf, sep=None, engine="python", encoding_errors="replace")
    print("columns:", list(df.columns)[:40], flush=True)
    print("rows:", len(df), flush=True)
    return df


def pick(df, patterns, exclude=()):
    for p in patterns:
        for c in df.columns:
            name = str(c).strip().lower()
            if re.fullmatch(p, name) and not any(re.search(e, name) for e in exclude):
                return c
    return None


def heat_points(path):
    import pandas as pd
    df = read_table(path)
    lat = pick(df, [r"lat_ns", r"lat(itude)?", r"lat.*"])
    lon = pick(df, [r"long_ew", r"lon(g(itude)?)?", r"lon.*", r"long.*"])
    q = pick(df, [r"q", r"q\s*\(mw.*", r"heat.?flow.*", r"hf", r"q_.*"], exclude=[r"unc", r"err", r"std", r"comment"])
    print(f"using columns lat={lat!r} lon={lon!r} q={q!r}", flush=True)
    if lat is None or lon is None or q is None:
        sys.exit("could not find latitude / longitude / heat flow columns")
    pts = pd.DataFrame({
        "lat": pd.to_numeric(df[lat], errors="coerce"),
        "lon": pd.to_numeric(df[lon], errors="coerce"),
        "q": pd.to_numeric(df[q], errors="coerce"),
    }).dropna()
    pts = pts[(pts.lat.abs() <= 90) & (pts.lon.abs() <= 180) & (pts.q > 5) & (pts.q < 1000)]
    print(f"heat flow points: {len(pts)}, median {pts.q.median():.0f} mW/m²", flush=True)
    return pts


def unit_vectors(lat, lon):
    la, lo = np.radians(lat), np.radians(lon)
    return np.column_stack([np.cos(la) * np.cos(lo), np.cos(la) * np.sin(lo), np.sin(la)])


def build_heatflow(points, ground_path, out_path):
    from scipy.spatial import cKDTree
    earth = 6371.0
    tree = cKDTree(unit_vectors(points.lat.values, points.lon.values))
    q = np.clip(points.q.values, 20, 150)
    w, h = int(360 / HF_STEP), int(180 / HF_STEP)
    lats = 90 - (np.arange(h) + 0.5) * HF_STEP
    lons = -180 + (np.arange(w) + 0.5) * HF_STEP
    grid = np.full((h, w), np.nan)
    reach = 2 * math.sin(HF_REACH_KM / earth / 2)
    for r, la in enumerate(lats):
        if la < -60 or la > 84:
            continue
        cells = unit_vectors(np.full(w, la), lons)
        dist, idx = tree.query(cells, k=48, distance_upper_bound=reach)
        found = np.isfinite(dist)
        km = np.where(found, 2 * np.arcsin(np.minimum(dist, 2) / 2) * earth, 0)
        wt = np.where(found, np.exp(-0.5 * (km / HF_SIGMA_KM) ** 2), 0)
        vals = np.where(found, q[np.minimum(idx, len(q) - 1)], 0)
        s = wt.sum(axis=1)
        row = np.where(s > 1e-6, (wt * vals).sum(axis=1) / np.maximum(s, 1e-12), np.nan)
        grid[r] = row
    # Land only: where the ground temperature has data at this cell size.
    land = gdal.Warp("", ground_path, format="MEM", width=w, height=h,
                     outputBounds=[-180, -90, 180, 90], resampleAlg="mode", dstNodata=NODATA)
    land = land.GetRasterBand(1).ReadAsArray() != NODATA
    code = np.where(np.isfinite(grid) & land, np.clip(np.rint(grid), 0, 254), NODATA).astype(np.uint8)
    work = out_path + ".work.tif"
    dst = gdal.GetDriverByName("GTiff").Create(work, w, h, 1, gdal.GDT_Byte, ["TILED=YES", "COMPRESS=ZSTD"])
    dst.SetGeoTransform([-180, HF_STEP, 0, 90, 0, -HF_STEP])
    dst.SetProjection(gdal.Open(ground_path).GetProjection())
    dst.GetRasterBand(1).SetNoDataValue(NODATA)
    dst.GetRasterBand(1).WriteArray(code)
    dst = None
    cog(work, out_path)
    print(f"heat flow grid: {int((code != NODATA).sum())} land cells with data of {int(land.sum())}", flush=True)


# ---------------------------------------------------------------- tiles

def palette(lo, hi, decode):
    rgb = [tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) for c in COLORS]
    out = []
    for code in range(256):
        if code == NODATA:
            out.append((0, 0, 0))
            continue
        t = max(0.0, min(1.0, (decode(code) - lo) / (hi - lo))) * (len(rgb) - 1)
        i = min(len(rgb) - 2, int(math.floor(t)))
        u = t - i
        out.append(tuple(int(math.floor(a + (b - a) * u + 0.5)) for a, b in zip(rgb[i], rgb[i + 1])))
    return [v for c in out for v in c]


class Levels:
    def __init__(self, path):
        self.ds = gdal.Open(path)
        band = self.ds.GetRasterBand(1)
        gt = self.ds.GetGeoTransform()
        self.ox, self.oy = gt[0], gt[3]
        w0, h0 = self.ds.RasterXSize, self.ds.RasterYSize
        self.bands = [band] + [band.GetOverview(i) for i in range(band.GetOverviewCount())]
        self.sizes = [(b.XSize, b.YSize) for b in self.bands]
        self.pixel = [(gt[1] * w0 / w, -gt[5] * h0 / h) for w, h in self.sizes]

    def level_for(self, z):
        dpp = 360.0 / (TILE * (2 ** z))
        best = 0
        for i, (px, _) in enumerate(self.pixel):
            if px <= dpp * 1.01:
                best = i
        return best

    def sample(self, li, lat, lon):
        """Codes at every (lat row) x (lon column), nearest cell."""
        w, h = self.sizes[li]
        sx, sy = self.pixel[li]
        cols = np.floor((lon - self.ox) / sx).astype(np.int64)
        rows = np.floor((self.oy - lat) / sy).astype(np.int64)
        col_ok = (cols >= 0) & (cols < w)
        row_ok = (rows >= 0) & (rows < h)
        out = np.full((len(lat), len(lon)), NODATA, dtype=np.uint8)
        if not row_ok.any() or not col_ok.any():
            return out
        r0, r1 = int(rows[row_ok].min()), int(rows[row_ok].max())
        strip = self.bands[li].ReadAsArray(0, r0, w, r1 - r0 + 1)
        rr = np.clip(rows - r0, 0, r1 - r0)
        cc = np.clip(cols, 0, w - 1)
        out = strip[rr][:, cc]
        out[~row_ok, :] = NODATA
        out[:, ~col_ok] = NODATA
        return out


def render_tiles(ground_path, heat_path, out, max_zoom):
    ground = Levels(ground_path)
    heat = Levels(heat_path)
    layers = ["ground", "heatflow"] + [f"depth-{d}" for d in DEPTHS]
    decoders = {"ground": ground_value, "heatflow": float}
    for d in DEPTHS:
        decoders[f"depth-{d}"] = lambda c: c * 2.0
    pals = {l: palette(*SCALES[l], decoders[l]) for l in layers}
    os.makedirs(out, exist_ok=True)
    packs = {l: open(os.path.join(out, l + ".tiles"), "wb") for l in layers}
    index = {l: [] for l in layers}
    started = time.time()
    for z in range(max_zoom + 1):
        n = 2 ** z
        lon = (np.arange(n * TILE) + 0.5) / (n * TILE) * 360 - 180
        lg, lh = ground.level_for(z), 0
        count = 0
        for ty in range(n):
            py = (ty * TILE + np.arange(TILE) + 0.5) / (n * TILE)
            lat = np.degrees(np.arctan(np.sinh(np.pi * (1 - 2 * py))))
            g = ground.sample(lg, lat, lon)
            if (g == NODATA).all():
                continue
            hf = heat.sample(lh, lat, lon)
            t0 = ground_value(g.astype(np.float64))
            qv = hf.astype(np.float64)
            has_q = (hf != NODATA) & (g != NODATA)
            codes = {"ground": g, "heatflow": np.where(has_q, hf, NODATA).astype(np.uint8)}
            for d in DEPTHS:
                td = t0 + qv / CONDUCTIVITY * d
                codes[f"depth-{d}"] = np.where(has_q, np.clip(np.rint(td / 2), 0, 254), NODATA).astype(np.uint8)
            for tx in range(n):
                sl = slice(tx * TILE, (tx + 1) * TILE)
                if (g[:, sl] == NODATA).all():
                    continue
                key = (z << 48) | (tx << 24) | ty
                for l in layers:
                    tile = codes[l][:, sl]
                    if (tile == NODATA).all():
                        continue
                    img = gdal_free_png(tile, pals[l])
                    index[l].append((key, packs[l].tell(), len(img)))
                    packs[l].write(img)
                count += 1
        print(f"z{z}: {count} tiles per layer (ground level {lg}), {(time.time() - started) / 60:.1f} min", flush=True)
    sizes = {}
    for l in layers:
        packs[l].close()
        index[l].sort()
        with open(os.path.join(out, l + ".index"), "wb") as f:
            f.write(b"WSTI" + struct.pack(">ii", 1, len(index[l])))
            for key, offset, length in index[l]:
                f.write(struct.pack(">qqi", key, offset, length))
        sizes[l] = {"tiles": len(index[l]), "MB": round(os.path.getsize(os.path.join(out, l + ".tiles")) / 1e6, 1)}
    return sizes


def gdal_free_png(codes, pal):
    from PIL import Image
    img = Image.fromarray(np.ascontiguousarray(codes), mode="P")
    img.putpalette(pal)
    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True, transparency=NODATA)
    return buf.getvalue()


def check(path, lat, lon):
    ds = gdal.Open(path)
    gt = ds.GetGeoTransform()
    col, row = int((lon - gt[0]) / gt[1]), int((lat - gt[3]) / gt[5])
    return int(ds.GetRasterBand(1).ReadAsArray(col, row, 1, 1)[0, 0])


def main(soil, heat_table, out, max_zoom=7):
    started = time.time()
    os.makedirs(out, exist_ok=True)
    ground_path = os.path.join(out, "ground.tif")
    heat_path = os.path.join(out, "heatflow.tif")
    build_ground(soil, ground_path)
    print(f"ground done, {(time.time() - started) / 60:.1f} min", flush=True)
    build_heatflow(heat_points(heat_table), ground_path, heat_path)
    print(f"heat flow done, {(time.time() - started) / 60:.1f} min", flush=True)
    tiles = render_tiles(ground_path, heat_path, os.path.join(out, "tiles"), max_zoom)
    g, q = check(ground_path, 46.77, 23.59), check(heat_path, 46.77, 23.59)
    info = {
        "built": time.strftime("%Y-%m-%d"),
        "maxZoom": max_zoom,
        "groundMB": round(os.path.getsize(ground_path) / 1e6, 1),
        "heatflowMB": round(os.path.getsize(heat_path) / 1e6, 2),
        "tiles": tiles,
        "cluj": {"groundC": None if g == NODATA else ground_value(g), "heatFlow": None if q == NODATA else q},
        "minutes": round((time.time() - started) / 60, 1),
    }
    with open(os.path.join(out, "geo.json"), "w") as f:
        json.dump(info, f, indent=1)
    print(json.dumps(info, indent=1))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 7)
