"""Turns one whole-world Global Wind Atlas layer into the compact file the app reads.

Usage: python3 tool/gwa_build_wind.py <layer> <source url or downloaded file> <output.tif>

  layer: ws10, ws50, ws100, ws200 (mean wind speed at that height) or pd100
         (mean wind power density at 100 m).

The source is read in strips straight from the Global Wind Atlas server (the
files are 13-15 GB, more than a GitHub runner's disk), encoded in one byte per
cell and written as a cloud-optimised GeoTIFF with overviews, so the backend can
read a single point or a map tile with a few small range requests.

Encoding (the backend decodes with the same rules, see WindEncoding.java):
  wind speed:     byte = round(m/s * 10), 0 to 254 (0 to 25.4 m/s)
  power density:  byte = round(ln(1 + W/m²) * 30), 0 to 254 (0 to about 4,700 W/m²)
  255 = no data (sea beyond the coastal zone, outside the atlas)
"""
import json
import os
import sys
import time

import numpy as np
from osgeo import gdal

gdal.UseExceptions()
for key, value in {
    "GDAL_DISABLE_READDIR_ON_OPEN": "EMPTY_DIR",
    "GDAL_HTTP_MAX_RETRY": "8",
    "GDAL_HTTP_RETRY_DELAY": "5",
    "CPL_VSIL_CURL_CHUNK_SIZE": "8388608",
    "VSI_CACHE": "TRUE",
    "VSI_CACHE_SIZE": "1000000000",
    "GDAL_CACHEMAX": "2048",
}.items():
    gdal.SetConfigOption(key, value)

NODATA = 255
SPEED_SCALE = 10.0
DENSITY_SCALE = 30.0


def encode(layer, values, valid):
    if layer.startswith("ws"):
        coded = np.clip(np.rint(values * SPEED_SCALE), 0, 254)
    else:
        coded = np.clip(np.rint(np.log1p(np.maximum(values, 0)) * DENSITY_SCALE), 0, 254)
    return np.where(valid, coded, NODATA).astype(np.uint8)


def main(layer, url, out_path):
    started = time.time()
    # A downloaded file is read from disk; an address is read in ranges over the network.
    source = gdal.Open(("/vsicurl/" + url) if url.startswith("http") else url)
    band = source.GetRasterBand(1)
    width, height = source.RasterXSize, source.RasterYSize
    nodata = band.GetNoDataValue()
    block_w, block_h = band.GetBlockSize()
    rows = max(block_h, 512)
    print(f"{layer}: {width} x {height} cells, block {block_w} x {block_h}, nodata {nodata}", flush=True)

    work = out_path + ".work.tif"
    target = gdal.GetDriverByName("GTiff").Create(
        work, width, height, 1, gdal.GDT_Byte,
        ["TILED=YES", "BLOCKXSIZE=512", "BLOCKYSIZE=512", "COMPRESS=ZSTD", "ZSTD_LEVEL=1",
         "PREDICTOR=2", "BIGTIFF=YES"])
    target.SetGeoTransform(source.GetGeoTransform())
    target.SetProjection(source.GetProjection())
    out_band = target.GetRasterBand(1)
    out_band.SetNoDataValue(NODATA)

    valid_cells = 0
    total = 0.0
    for y in range(0, height, rows):
        h = min(rows, height - y)
        values = band.ReadAsArray(0, y, width, h).astype(np.float64)
        valid = np.isfinite(values)
        if nodata is not None:
            valid &= values != nodata
        valid &= values >= 0
        out_band.WriteArray(encode(layer, values, valid), 0, y)
        valid_cells += int(valid.sum())
        total += float(values[valid].sum())
        if (y // rows) % 20 == 0:
            done = (y + h) / height
            print(f"  {done:6.1%} after {(time.time() - started) / 60:.1f} min", flush=True)
    target.FlushCache()
    target = None

    print("Writing the cloud-optimised file with overviews...", flush=True)
    gdal.Translate(out_path, work, format="COG", creationOptions=[
        "COMPRESS=ZSTD", "LEVEL=19", "PREDICTOR=2", "BLOCKSIZE=512", "OVERVIEWS=AUTO",
        "RESAMPLING=AVERAGE", "BIGTIFF=YES", "NUM_THREADS=ALL_CPUS"])
    os.remove(work)

    # A known place as a sanity check: Cluj-Napoca.
    check = gdal.Open(out_path)
    gt = check.GetGeoTransform()
    col = int((23.59 - gt[0]) / gt[1])
    row = int((46.77 - gt[3]) / gt[5])
    cluj = int(check.GetRasterBand(1).ReadAsArray(col, row, 1, 1)[0, 0])

    info = {
        "layer": layer,
        "source": os.environ.get("SOURCE_URL", url),
        "width": width,
        "height": height,
        "geoTransform": list(gt),
        "nodata": NODATA,
        "encoding": "speed: m/s * 10" if layer.startswith("ws") else "density: ln(1 + W/m²) * 30",
        "validCells": valid_cells,
        "mean": total / valid_cells if valid_cells else None,
        "clujByte": cluj,
        "bytes": os.path.getsize(out_path),
        "minutes": round((time.time() - started) / 60, 1),
        "sourceMetadata": gdal.Info(source, format="json").get("metadata", {}),
    }
    with open(out_path + ".json", "w") as f:
        json.dump(info, f, indent=2)
    print(json.dumps({k: v for k, v in info.items() if k != "sourceMetadata"}, indent=2))


if __name__ == "__main__":
    main(*sys.argv[1:4])
