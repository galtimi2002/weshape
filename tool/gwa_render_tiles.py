"""Draws the wind map tiles once, so the backend only hands out finished pictures.

Usage: python3 tool/gwa_render_tiles.py <layer> <folder with ws10/ws50/ws100/ws200/pd100.tif> <out folder> [max zoom]

  layer: speed, power, height-10, height-50, height-100, height-150, height-200

Writes <out>/<layer>/<z>/<x>/<y>.png (256 px, Web Mercator) for zoom 0 to max zoom
(default 8), only where there is data. Colours and sampling match the backend's
WindData / WindLayer exactly: the same overview level per zoom, the cell under each
pixel centre, the same fixed colour scales. Tiles are 8-bit palette PNGs: the
palette maps the stored byte to its colour, 255 (no data) is transparent.
"""
import json
import math
import os
import sys
import time

import numpy as np
from osgeo import gdal
from PIL import Image

gdal.UseExceptions()
gdal.SetConfigOption("GDAL_CACHEMAX", "2048")
TILE = 256
NODATA = 255

SCALES = {
    "speed": (3, 9, False, ["#E3EEF8", "#7FB2E0", "#2F6DB0", "#163E78"]),
    # One blue scale for all wind layers (agreed on the map).
    "power": (50, 800, True, ["#E3EEF8", "#7FB2E0", "#2F6DB0", "#163E78"]),
    "height": (2, 10, False, ["#E3EEF8", "#7FB2E0", "#2F6DB0", "#163E78"]),
}


def palette(kind):
    lo, hi, log, colors = SCALES[kind]
    rgb = [tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) for c in colors]
    out = []
    for code in range(256):
        if code == NODATA:
            out.append((0, 0, 0))
            continue
        value = math.expm1(code / 30.0) if kind == "power" else code / 10.0
        if log:
            t = (math.log(max(value, 1e-6)) - math.log(lo)) / (math.log(hi) - math.log(lo))
        else:
            t = (value - lo) / (hi - lo)
        t = max(0.0, min(1.0, t)) * (len(rgb) - 1)
        i = min(len(rgb) - 2, int(math.floor(t)))
        u = t - i
        out.append(tuple(int(math.floor(a + (b - a) * u + 0.5)) for a, b in zip(rgb[i], rgb[i + 1])))
    return [v for c in out for v in c]


class Levels:
    """The full-resolution band and its overviews, with their cell sizes."""

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


def main(layer, src, out, max_zoom=8):
    if layer in ("speed", "power"):
        kind = layer
        names = ["ws100"] if layer == "speed" else ["pd100"]
    else:
        kind = "height"
        h = int(layer.split("-")[1])
        names = ["ws100", "ws200"] if h == 150 else ["ws%d" % h]
    files = [Levels(os.path.join(src, n + ".tif")) for n in names]
    ref = files[0]
    pal = palette(kind)
    written = 0
    started = time.time()
    for z in range(max_zoom + 1):
        n = 2 ** z
        li = ref.level_for(z)
        w, hgt = ref.sizes[li]
        sx, sy = ref.pixel[li]
        lon = (np.arange(n * TILE) + 0.5) / (n * TILE) * 360 - 180
        cols = np.floor((lon - ref.ox) / sx).astype(np.int64)
        col_ok = (cols >= 0) & (cols < w)
        cols_c = np.clip(cols, 0, w - 1)
        count_z = 0
        for ty in range(n):
            py = (ty * TILE + np.arange(TILE) + 0.5) / (n * TILE)
            lat = np.degrees(np.arctan(np.sinh(np.pi * (1 - 2 * py))))
            rows = np.floor((ref.oy - lat) / sy).astype(np.int64)
            row_ok = (rows >= 0) & (rows < hgt)
            if not row_ok.any():
                continue
            r0, r1 = int(rows[row_ok].min()), int(rows[row_ok].max())
            strips = [f.bands[li].ReadAsArray(0, r0, w, r1 - r0 + 1) for f in files]
            rr = np.clip(rows - r0, 0, r1 - r0)
            grids = [s[rr][:, cols_c] for s in strips]
            code = grids[0].copy()
            if len(grids) == 2:
                v100 = grids[0].astype(np.float64) / 10
                v200 = grids[1].astype(np.float64) / 10
                ok = (grids[0] != NODATA) & (grids[1] != NODATA)
                with np.errstate(divide="ignore", invalid="ignore"):
                    alpha = np.log(v200 / v100) / math.log(2)
                    v150 = np.where((v100 > 0) & (v200 > 0), v100 * 1.5 ** alpha, v100 + (v200 - v100) * 0.5)
                code = np.where(ok, np.clip(np.rint(v150 * 10), 0, 254), NODATA).astype(np.uint8)
            code[~row_ok, :] = NODATA
            code[:, ~col_ok] = NODATA
            for tx in range(n):
                tile = code[:, tx * TILE:(tx + 1) * TILE]
                if (tile == NODATA).all():
                    continue
                img = Image.fromarray(np.ascontiguousarray(tile), mode="P")
                img.putpalette(pal)
                d = os.path.join(out, layer, str(z), str(tx))
                os.makedirs(d, exist_ok=True)
                img.save(os.path.join(d, "%d.png" % ty), optimize=True, transparency=NODATA)
                count_z += 1
        written += count_z
        print(f"{layer} z{z}: {count_z} tiles (level {li}), {(time.time() - started) / 60:.1f} min", flush=True)
    with open(os.path.join(out, layer + ".json"), "w") as f:
        json.dump({"layer": layer, "maxZoom": max_zoom, "tiles": written,
                   "built": time.strftime("%Y-%m-%d")}, f)
    print(f"{layer}: {written} tiles in {(time.time() - started) / 60:.1f} min")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 8)
