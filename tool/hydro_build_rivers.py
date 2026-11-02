"""Turns RiverATLAS river reaches into map features for tippecanoe.

Usage: python3 tool/hydro_build_rivers.py <RiverATLAS shapefile or zip> <out.geojsons>

Reads every RiverATLAS_*.shp (also straight from the zip, through /vsizip/, so the
~20 GB of attribute tables never have to be unpacked) and writes one line per reach
(GeoJSONSeq) with the values the map and the point card use:

  qa  long-term mean flow, m³/s                 (dis_m3_pyr)
  qn  mean flow of the driest month, m³/s       (dis_m3_pmn)
  qx  mean flow of the wettest month, m³/s      (dis_m3_pmx)
  q   available flow = qa x (1 - ECOLOGICAL_SHARE): the rest stays in the river
  h   hydraulic head along the reach, m = stream gradient (sgr_dk_rav, dm/km) / 10 x LENGTH_KM
  p   estimated potential, kW = 9.81 x q x h x EFFICIENCY
  l   reach length, km

Reaches with less than MIN_FLOW mean flow are left out (no use for hydropower).
Each reach gets a tippecanoe minzoom from its mean flow, so the zoomed-out map shows
the big rivers only. ECOLOGICAL_SHARE and EFFICIENCY match the backend (HydroData).
"""
import json
import math
import os
import subprocess
import sys
import time
import zipfile

ECOLOGICAL_SHARE = 0.10
EFFICIENCY = 0.80
MIN_FLOW = 0.1
FIELDS = ["dis_m3_pyr", "dis_m3_pmn", "dis_m3_pmx", "sgr_dk_rav", "LENGTH_KM"]
# Mean flow (m³/s) from which a reach shows at each zoom.
MINZOOM = [(2000, 0), (500, 3), (150, 4), (50, 5), (15, 6), (5, 7), (1.5, 8), (0.5, 9), (0, 10)]


def sig(v, digits=3):
    """Rounds to a few significant digits, so equal values repeat and the tiles stay small."""
    if v == 0 or not math.isfinite(v):
        return 0
    d = digits - int(math.floor(math.log10(abs(v)))) - 1
    r = round(v, d)
    return int(r) if r == int(r) and abs(r) >= 1 else r


def minzoom(qa):
    for limit, z in MINZOOM:
        if qa >= limit:
            return z
    return 10


def reach(props):
    """The map values of one reach, or None when it is left out."""
    get = {k.lower(): v for k, v in props.items()}
    qa = get.get("dis_m3_pyr")
    if qa is None or qa < MIN_FLOW:
        return None
    qn = max(get.get("dis_m3_pmn") or 0, 0)
    qx = max(get.get("dis_m3_pmx") or 0, qa)
    grad = get.get("sgr_dk_rav")
    length = get.get("length_km") or 0
    head = max(grad, 0) / 10.0 * length if grad is not None and grad > -999 else 0
    q = qa * (1 - ECOLOGICAL_SHARE)
    p = 9.81 * q * head * EFFICIENCY
    return {"p": sig(p), "q": sig(q), "qa": sig(qa), "qn": sig(qn), "qx": sig(qx), "h": sig(head),
            "l": sig(length)}


def sources(path):
    """The RiverATLAS shapefiles, as paths GDAL can open."""
    if path.lower().endswith(".zip"):
        with zipfile.ZipFile(path) as z:
            names = [n for n in z.namelist() if n.lower().endswith(".shp")]
        return ["/vsizip/" + os.path.abspath(path) + "/" + n for n in sorted(names)]
    if path.lower().endswith(".shp"):
        return [path]
    out = []
    for root, _, files in os.walk(path):
        out += [os.path.join(root, f) for f in files if f.lower().endswith(".shp")]
    return sorted(out)


def main(src, out_path):
    shps = sources(src)
    if not shps:
        sys.exit("no .shp files in " + src)
    print("shapefiles:", *shps, sep="\n  ", flush=True)
    started = time.time()
    stats = {"read": 0, "kept": 0, "byZoom": {}, "maxP": 0, "maxQ": 0, "maxH": 0}
    with open(out_path, "w") as out:
        for shp in shps:
            layer = os.path.splitext(os.path.basename(shp))[0]
            cmd = ["ogr2ogr", "-f", "GeoJSONSeq", "/vsistdout/", shp, layer, "-select", ",".join(FIELDS),
                   "-lco", "COORDINATE_PRECISION=5", "-lco", "RS=NO"]
            proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, text=True, bufsize=1 << 20)
            n0 = stats["read"]
            for line in proc.stdout:
                line = line.strip().lstrip("\x1e")
                if not line:
                    continue
                f = json.loads(line)
                stats["read"] += 1
                values = reach(f.get("properties") or {})
                if values is None or not f.get("geometry"):
                    continue
                z = minzoom(values["qa"])
                stats["kept"] += 1
                stats["byZoom"][z] = stats["byZoom"].get(z, 0) + 1
                stats["maxP"] = max(stats["maxP"], values["p"])
                stats["maxQ"] = max(stats["maxQ"], values["qa"])
                stats["maxH"] = max(stats["maxH"], values["h"])
                out.write(json.dumps({"type": "Feature", "tippecanoe": {"minzoom": z},
                                      "properties": values, "geometry": f["geometry"]},
                                     separators=(",", ":")) + "\n")
            if proc.wait() != 0:
                sys.exit("ogr2ogr failed on " + shp)
            print(f"{layer}: {stats['read'] - n0} reaches, {(time.time() - started) / 60:.1f} min", flush=True)
    stats["byZoom"] = dict(sorted(stats["byZoom"].items()))
    print(json.dumps(stats))
    with open(out_path + ".json", "w") as f:
        json.dump(stats, f)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
