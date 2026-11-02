"""Packs the river tiles from tippecanoe's .mbtiles into one tile file and one index,
the same format as the wind tiles (see gwa_render_tiles.py), so the backend reads any
tile with one range request.

Usage: python3 tool/hydro_pack_tiles.py <rivers.mbtiles> <out folder>

Writes <out>/rivers.tiles (gzip-compressed vector tiles, as tippecanoe stores them),
<out>/rivers.index ("WSTI", version 1, count, then key = z << 48 | x << 24 | y,
offset, length per tile, sorted, big-endian) and <out>/rivers.json (counts and sizes).
"""
import json
import os
import sqlite3
import struct
import sys
import time


def main(mbtiles, out):
    os.makedirs(out, exist_ok=True)
    db = sqlite3.connect(mbtiles)
    rows = db.execute("SELECT zoom_level, tile_column, tile_row FROM tiles").fetchall()
    keys = []
    for z, x, row in rows:
        y = (1 << z) - 1 - row  # mbtiles rows count from the south
        keys.append(((z << 48) | (x << 24) | y, z, x, row))
    keys.sort()
    index = []
    by_zoom = {}
    with open(os.path.join(out, "rivers.tiles"), "wb") as pack:
        for key, z, x, row in keys:
            (data,) = db.execute("SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
                                 (z, x, row)).fetchone()
            index.append((key, pack.tell(), len(data)))
            pack.write(data)
            count, size = by_zoom.get(z, (0, 0))
            by_zoom[z] = (count + 1, size + len(data))
    with open(os.path.join(out, "rivers.index"), "wb") as f:
        f.write(b"WSTI" + struct.pack(">ii", 1, len(index)))
        for key, offset, length in index:
            f.write(struct.pack(">qqi", key, offset, length))
    info = {"tiles": len(index), "bytes": sum(i[2] for i in index), "built": time.strftime("%Y-%m-%d"),
            "byZoom": {z: {"tiles": c, "MB": round(s / 1e6, 1)} for z, (c, s) in sorted(by_zoom.items())}}
    with open(os.path.join(out, "rivers.json"), "w") as f:
        json.dump(info, f, indent=1)
    print(json.dumps(info, indent=1))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
