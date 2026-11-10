"""Builds the index of the Overture Maps building files: which part of the world each file covers.

The backend looks up the buildings around one place straight from Overture's public bucket. With this
index it opens only the one or two files (of about 500) that touch the place, which takes about a
second instead of several (see tool/building_speed.py).

Reads only the footer of each file (the min/max statistics Parquet keeps for the bbox columns), so it
takes minutes, not the 17 of reading the bbox columns themselves; files without statistics are read
the slow way.

Usage: python3 tool/building_index.py <Overture release, e.g. 2026-10-21.0> <output.json>

Output:
  {"release": "2026-10-21.0", "built": "2026-11-10T18:00:00Z",
   "base": "https://overturemaps-us-west-2.s3.us-west-2.amazonaws.com/release/2026-10-21.0/theme=buildings/type=building/",
   "files": [["part-00000-....parquet", xmin, ymin, xmax, ymax], ...]}
"""
import datetime
import json
import sys
import time

import duckdb

BUCKET = "overturemaps-us-west-2"
HTTPS = f"https://{BUCKET}.s3.us-west-2.amazonaws.com/"


def main(release, out_path):
    prefix = f"release/{release}/theme=buildings/type=building/"
    glob = f"s3://{BUCKET}/{prefix}*"
    con = duckdb.connect()
    for sql in ("INSTALL httpfs", "LOAD httpfs", "SET s3_region='us-west-2'"):
        con.execute(sql)

    t = time.time()
    files = [r[0] for r in con.execute(f"SELECT file FROM glob('{glob}') ORDER BY file").fetchall()]
    print(f"{len(files)} files listed in {time.time() - t:.1f}s", flush=True)
    if not files:
        sys.exit(f"No building files in {glob}")

    # Footer statistics of the four bbox columns, per row group, folded per file.
    t = time.time()
    meta = con.execute(f"""
        SELECT file_name,
               min(CASE WHEN regexp_matches(path_in_schema, 'bbox.{{0,2}}xmin$') THEN TRY_CAST(stats_min_value AS DOUBLE) END),
               min(CASE WHEN regexp_matches(path_in_schema, 'bbox.{{0,2}}ymin$') THEN TRY_CAST(stats_min_value AS DOUBLE) END),
               max(CASE WHEN regexp_matches(path_in_schema, 'bbox.{{0,2}}xmax$') THEN TRY_CAST(stats_max_value AS DOUBLE) END),
               max(CASE WHEN regexp_matches(path_in_schema, 'bbox.{{0,2}}ymax$') THEN TRY_CAST(stats_max_value AS DOUBLE) END)
        FROM parquet_metadata('{glob}')
        GROUP BY file_name""").fetchall()
    boxes = {r[0]: r[1:] for r in meta}
    print(f"footer statistics of {len(boxes)} files read in {time.time() - t:.1f}s", flush=True)

    def good(box):
        if box is None or any(v is None for v in box):
            return False
        w, s, e, n = box
        return -180.5 <= w <= e <= 180.5 and -90.5 <= s <= n <= 90.5

    missing = [f for f in files if not good(boxes.get(f))]
    if missing:
        print(f"{len(missing)} file(s) without usable statistics, reading their bbox columns", flush=True)
        for f in missing:
            boxes[f] = con.execute(f"""
                SELECT min(bbox.xmin), min(bbox.ymin), max(bbox.xmax), max(bbox.ymax)
                FROM read_parquet('{f}')""").fetchone()
    bad = [f for f in files if not good(boxes.get(f))]
    if bad:
        sys.exit(f"No area for {len(bad)} file(s), e.g. {bad[0]}")

    index = {
        "release": release,
        "built": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "base": HTTPS + prefix,
        "files": [[f.rsplit("/", 1)[1], *[round(v, 7) for v in boxes[f]]] for f in files],
    }
    with open(out_path, "w") as fh:
        json.dump(index, fh, separators=(",", ":"))
    print(f"index of {len(files)} files written to {out_path}", flush=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
