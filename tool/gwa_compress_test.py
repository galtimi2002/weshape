"""Compression and accuracy test for the Global Wind Atlas wind layers.

Usage: python3 tool/gwa_compress_test.py <folder with the Romania GeoTIFFs>
Prints a Markdown report (it goes into the GitHub Actions run summary).
"""
import os
import subprocess
import sys

import numpy as np
from osgeo import gdal

gdal.UseExceptions()
folder = sys.argv[1]
MB = 1024 * 1024

# Sizes of the whole-world files, from the size check run (bytes, rounded to the GB shown there).
GLOBAL_GB = {"wind-speed_10": 15, "wind-speed_50": 14, "wind-speed_100": 14,
             "wind-speed_150": 13, "wind-speed_200": 13, "power-density_100": 14}


def read(name):
    ds = gdal.Open(os.path.join(folder, name + ".tif"))
    band = ds.GetRasterBand(1)
    data = band.ReadAsArray().astype(np.float64)
    nodata = band.GetNoDataValue()
    mask = np.isfinite(data)
    if nodata is not None:
        mask &= data != nodata
    return ds, data, mask


def write_cog(ds, values, mask, path, dtype, nodata, compress, level):
    """Writes values (already scaled to integers) as a cloud-optimised GeoTIFF."""
    mem = gdal.GetDriverByName("MEM").Create("", ds.RasterXSize, ds.RasterYSize, 1, dtype)
    mem.SetGeoTransform(ds.GetGeoTransform())
    mem.SetProjection(ds.GetProjection())
    band = mem.GetRasterBand(1)
    out = np.where(mask, values, nodata)
    band.WriteArray(out)
    band.SetNoDataValue(nodata)
    options = [f"COMPRESS={compress}", "PREDICTOR=2", "BLOCKSIZE=256", "OVERVIEWS=AUTO",
               "RESAMPLING=AVERAGE", "BIGTIFF=IF_SAFER"]
    if compress == "ZSTD":
        options.append(f"LEVEL={level}")
    if compress == "DEFLATE":
        options.append(f"LEVEL={level}")
    gdal.Translate(path, mem, format="COG", creationOptions=options)
    return os.path.getsize(path)


rows = []
results = {}
for name in GLOBAL_GB:
    ds, data, mask = read(name)
    original = os.path.getsize(os.path.join(folder, name + ".tif"))
    info = gdal.Info(ds, format="json")
    dtype = info["bands"][0]["type"]
    compression = info.get("metadata", {}).get("IMAGE_STRUCTURE", {}).get("COMPRESSION", "none")
    variants = {}
    if name.startswith("wind-speed"):
        # 0.1 m/s steps in one byte (0 to 25.4 m/s), and 0.01 m/s in two bytes.
        v8 = np.clip(np.round(data * 10), 0, 254)
        variants["uint8, 0.1 m/s, ZSTD"] = write_cog(ds, v8, mask, f"/tmp/{name}_u8.tif", gdal.GDT_Byte, 255, "ZSTD", 19)
        v16 = np.clip(np.round(data * 100), 0, 65534)
        variants["uint16, 0.01 m/s, ZSTD"] = write_cog(ds, v16, mask, f"/tmp/{name}_u16.tif", gdal.GDT_UInt16, 65535, "ZSTD", 19)
    else:
        # 1 W/m² steps in two bytes, and a logarithmic scale in one byte (about 3 % steps).
        p16 = np.clip(np.round(data), 0, 65534)
        variants["uint16, 1 W/m², ZSTD"] = write_cog(ds, p16, mask, f"/tmp/{name}_u16.tif", gdal.GDT_UInt16, 65535, "ZSTD", 19)
        plog = np.clip(np.round(np.log1p(np.maximum(data, 0)) * 32), 0, 254)
        variants["uint8, log scale, ZSTD"] = write_cog(ds, plog, mask, f"/tmp/{name}_log8.tif", gdal.GDT_Byte, 255, "ZSTD", 19)
    results[name] = (original, variants)
    rows.append((name, dtype, compression, original, int(mask.sum()), variants))

print("## Wind data compression test (Romania)\n")
print(f"GDAL {gdal.__version__}\n")
print("| Layer | Original type | Original compression | Original | Land cells |")
print("|---|---|---|---|---|")
for name, dtype, compression, original, cells, _ in rows:
    print(f"| {name} | {dtype} | {compression} | {original / MB:.1f} MB | {cells:,} |")

print("\n### Compressed sizes and the whole world, worked out from them\n")
print("| Layer | Variant | Romania | Ratio | Whole world (estimate) |")
print("|---|---|---|---|---|")
world = {}
for name, (original, variants) in results.items():
    for variant, size in variants.items():
        ratio = size / original
        estimate_gb = GLOBAL_GB[name] * ratio
        world[(name, variant)] = estimate_gb
        print(f"| {name} | {variant} | {size / MB:.1f} MB | {ratio:.2f} | {estimate_gb:.2f} GB |")

print("\n### Storage plans for the whole world\n")
plans = {
    "A: 10, 100, 200 m in uint8 + power density uint16": [
        ("wind-speed_10", "uint8, 0.1 m/s, ZSTD"), ("wind-speed_100", "uint8, 0.1 m/s, ZSTD"),
        ("wind-speed_200", "uint8, 0.1 m/s, ZSTD"), ("power-density_100", "uint16, 1 W/m², ZSTD")],
    "B: all 5 heights in uint8 + power density uint16": [
        ("wind-speed_10", "uint8, 0.1 m/s, ZSTD"), ("wind-speed_50", "uint8, 0.1 m/s, ZSTD"),
        ("wind-speed_100", "uint8, 0.1 m/s, ZSTD"), ("wind-speed_150", "uint8, 0.1 m/s, ZSTD"),
        ("wind-speed_200", "uint8, 0.1 m/s, ZSTD"), ("power-density_100", "uint16, 1 W/m², ZSTD")],
    "C: 10, 100, 200 m in uint8 + power density log uint8": [
        ("wind-speed_10", "uint8, 0.1 m/s, ZSTD"), ("wind-speed_100", "uint8, 0.1 m/s, ZSTD"),
        ("wind-speed_200", "uint8, 0.1 m/s, ZSTD"), ("power-density_100", "uint8, log scale, ZSTD")],
    "D: 10, 100, 200 m in uint16 + power density uint16": [
        ("wind-speed_10", "uint16, 0.01 m/s, ZSTD"), ("wind-speed_100", "uint16, 0.01 m/s, ZSTD"),
        ("wind-speed_200", "uint16, 0.01 m/s, ZSTD"), ("power-density_100", "uint16, 1 W/m², ZSTD")],
}
print("| Plan | Whole world (estimate) |")
print("|---|---|")
for plan, parts in plans.items():
    print(f"| {plan} | **{sum(world[p] for p in parts):.1f} GB** |")

# How well do 50 m and 150 m follow from the neighbouring heights (power law between them)?
_, w10, m10 = read("wind-speed_10")
_, w50, m50 = read("wind-speed_50")
_, w100, m100 = read("wind-speed_100")
_, w150, m150 = read("wind-speed_150")
_, w200, m200 = read("wind-speed_200")
q = lambda a: np.round(a * 10) / 10  # what uint8 storage keeps


def between(v1, h1, v2, h2, h):
    alpha = np.log(v2 / v1) / np.log(h2 / h1)
    return v1 * (h / h1) ** alpha


ok = m10 & m50 & m100 & m150 & m200 & (w10 > 0.1) & (w100 > 0.1) & (w200 > 0.1)
print("\n### Working out the missing heights instead of storing them\n")
print("| Height | From | Mean error | 95 % of cells within | Worst | Mean relative error |")
print("|---|---|---|---|---|---|")
for label, est, actual in [
    ("50 m", between(q(w10[ok]), 10, q(w100[ok]), 100, 50), w50[ok]),
    ("150 m", between(q(w100[ok]), 100, q(w200[ok]), 200, 150), w150[ok]),
]:
    err = np.abs(est - actual)
    rel = err / np.maximum(actual, 0.1)
    print(f"| {label} | stored neighbours (uint8) | {err.mean():.3f} m/s | {np.percentile(err, 95):.3f} m/s "
          f"| {err.max():.2f} m/s | {rel.mean() * 100:.2f} % |")
store_err = np.abs(q(w100[ok]) - w100[ok])
print(f"\nRounding to 0.1 m/s alone: mean error {store_err.mean():.3f} m/s, worst {store_err.max():.3f} m/s.")
print(f"\nMean wind speed in Romania at 100 m: {w100[ok].mean():.2f} m/s.")
