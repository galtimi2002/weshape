"""How fast can the backend read the buildings around one place straight from Overture Maps?

The home assessment needs the outline of the user's house (and its neighbours, for shade). Instead of
copying billions of buildings, the backend could read only the small square around the place from
Overture's public bucket, and keep the answer in R2 for the next time. This measures whether that is
fast enough, three ways:

  A. every building file of the release, letting DuckDB skip what is out of the square
     (a fresh connection for the first place, then the same connection for the others);
  B. the same, with DuckDB's metadata cache on, as a long-running backend would have it;
  C. an index of each file's area built once (the step a backend would do once per release),
     then only the files that touch the square.

Usage: python3 tool/building_speed.py <Overture release, e.g. 2026-10-21.0>
Writes a Markdown table to stdout and to the GitHub step summary.
"""
import json
import os
import sys
import time

import duckdb

# name: (latitude, longitude)
PLACES = {
    "Florești (RO)": (46.7472, 23.4937),
    "Cluj-Napoca (RO)": (46.7704, 23.5914),
    "Budapest (HU)": (47.4979, 19.0402),
    "Munich (DE)": (48.1488, 11.4600),
    "Madrid (ES)": (40.4168, -3.7038),
    "Lagos (NG)": (6.5244, 3.3792),
    "Nairobi (KE)": (-1.2921, 36.8219),
    "Jakarta (ID)": (-6.2088, 106.8456),
    "São Paulo (BR)": (-23.5505, -46.6333),
    "Austin (US)": (30.3450, -97.7800),
}

# Half the side of the square read around a place, in degrees: about 0.5 km each way.
HALF_LAT = 0.0045


def square(lat, lon):
    import math
    half_lon = HALF_LAT / max(0.2, math.cos(math.radians(lat)))
    return lon - half_lon, lat - HALF_LAT, lon + half_lon, lat + HALF_LAT


def connect(cache):
    con = duckdb.connect()
    for sql in ("INSTALL httpfs", "LOAD httpfs", "INSTALL spatial", "LOAD spatial", "SET s3_region='us-west-2'"):
        con.execute(sql)
    con.execute(f"SET enable_object_cache = {'true' if cache else 'false'}")
    return con


def query(con, files, box):
    w, s, e, n = box
    src = json.dumps(files) if isinstance(files, list) else f"'{files}'"
    sql = f"""
    SELECT count(*), sum(octet_length(ST_AsWKB(geometry)))
    FROM read_parquet({src}, hive_partitioning=1)
    WHERE bbox.xmin <= {e} AND bbox.xmax >= {w} AND bbox.ymin <= {n} AND bbox.ymax >= {s}
    """
    try:
        return con.execute(sql).fetchone()
    except duckdb.Error:
        return con.execute(sql.replace("ST_AsWKB(geometry)", "geometry")).fetchone()


def timed(fn):
    t = time.time()
    out = fn()
    return out, time.time() - t


def main(release):
    glob = f"s3://overturemaps-us-west-2/release/{release}/theme=buildings/type=building/*"
    results = {name: {} for name in PLACES}

    # A and B: every file, DuckDB prunes by the statistics in each file.
    for label, cache in (("A", False), ("B", True)):
        con = connect(cache)
        for name, (lat, lon) in PLACES.items():
            (n, size), sec = timed(lambda: query(con, glob, square(lat, lon)))
            results[name][label] = (n, size, sec)
            print(label, name, n, size, f"{sec:.1f}s", flush=True)

    # C: an index of the area each file covers, then only the files that touch the square.
    con = connect(True)
    files, sec_list = timed(lambda: [r[0] for r in con.execute(f"SELECT file FROM glob('{glob}')").fetchall()])
    print(f"{len(files)} files listed in {sec_list:.1f}s", flush=True)

    def build_index():
        rows = con.execute(f"""
            SELECT filename, min(bbox.xmin), min(bbox.ymin), max(bbox.xmax), max(bbox.ymax)
            FROM read_parquet('{glob}', filename=true, hive_partitioning=1)
            GROUP BY filename""").fetchall()
        return {r[0]: r[1:] for r in rows}

    # Reading the min/max per file reads only the bbox columns; it is what a backend would do once
    # per release and keep (a few KB).
    index, sec_index = timed(build_index)
    print(f"index of {len(index)} files built in {sec_index:.1f}s", flush=True)
    for name, (lat, lon) in PLACES.items():
        w, s, e, n = square(lat, lon)
        hit = [f for f, (fw, fs, fe, fn) in index.items() if fw <= e and fe >= w and fs <= n and fn >= s]
        if not hit:
            results[name]["C"] = (0, 0, 0.0, 0)
            continue
        (cnt, size), sec = timed(lambda: query(con, hit, (w, s, e, n)))
        results[name]["C"] = (cnt, size, sec, len(hit))
        print("C", name, cnt, size, f"{sec:.1f}s", f"{len(hit)} file(s)", flush=True)

    def cell(r):
        return f"{r[2]:.1f} s" if r else "–"

    rows = []
    for name in PLACES:
        r = results[name]
        n, size = r["A"][0], r["A"][1] or 0
        files_hit = r["C"][3] if len(r["C"]) > 3 else 0
        rows.append(f"| {name} | {n:,} | {size / 1e6:.1f} MB | {cell(r['A'])} | {cell(r['B'])} | {cell(r['C'])} ({files_hit} file) |")
    table = "\n".join([
        f"### Buildings around one place, read straight from Overture {release}",
        "",
        f"Square of about 1 × 1 km. {len(files)} building files in the release; the file index (C) "
        f"took {sec_index:.0f} s to build once.",
        "",
        "| Place | Buildings | Outline data | A: all files | B: all files, cached metadata | C: indexed files |",
        "|---|---|---|---|---|---|",
        *rows,
    ])
    print(table)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(table + "\n")


if __name__ == "__main__":
    main(sys.argv[1])
