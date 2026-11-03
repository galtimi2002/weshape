"""How complete is the open building data for the home assessment? Counts, for a few sample areas,
how many buildings Overture Maps has (it merges OpenStreetMap, Microsoft, Google, Esri and some
official sources) and how many of them carry a height, a number of floors and a roof shape.

Usage: python3 tool/building_check.py <Overture release, e.g. 2025-10-22.0>
Writes a Markdown table to stdout (and to the GitHub step summary when there is one).
"""
import os
import sys

import duckdb

# name: (west, south, east, north)
AREAS = {
    "Cluj-Napoca centre (RO)": (23.57, 46.76, 23.61, 46.78),
    "Florești, suburb of Cluj (RO)": (23.47, 46.74, 23.51, 46.76),
    "Feleacu, village (RO)": (23.59, 46.70, 23.62, 46.72),
    "Miercurea Ciuc (RO)": (25.79, 46.35, 25.82, 46.37),
    "Budapest XII (HU)": (18.98, 47.49, 19.02, 47.51),
    "Gödöllő (HU)": (19.34, 47.59, 19.37, 47.61),
    "Munich, Pasing (DE)": (11.44, 48.14, 11.47, 48.16),
    "Austin, suburbs (US)": (-97.80, 30.33, -97.76, 30.36),
}


def main(release):
    con = duckdb.connect()
    for sql in ("INSTALL httpfs", "LOAD httpfs", "INSTALL spatial", "LOAD spatial", "SET s3_region='us-west-2'"):
        con.execute(sql)
    src = f"s3://overturemaps-us-west-2/release/{release}/theme=buildings/type=building/*"
    rows = []
    for name, (w, s, e, n) in AREAS.items():
        q = f"""
        SELECT count(*) AS n,
               count(height) AS h,
               count(num_floors) AS f,
               count(roof_shape) AS r,
               count(CASE WHEN height IS NOT NULL OR num_floors IS NOT NULL THEN 1 END) AS hf,
               list_distinct(list(sources[1].dataset)) AS ds,
               count(CASE WHEN sources[1].dataset = 'OpenStreetMap' THEN 1 END) AS osm,
               count(CASE WHEN sources[1].dataset ILIKE '%microsoft%' THEN 1 END) AS ms,
               count(CASE WHEN sources[1].dataset ILIKE '%google%' THEN 1 END) AS goog,
               median(ST_Area_Spheroid(ST_FlipCoordinates({{geom}}))) AS area
        FROM read_parquet('{src}', hive_partitioning=1)
        WHERE bbox.xmin >= {w} AND bbox.xmax <= {e} AND bbox.ymin >= {s} AND bbox.ymax <= {n}
        """
        # Newer DuckDB reads the GeoParquet column as GEOMETRY, older ones as WKB bytes.
        try:
            r = con.execute(q.replace("{geom}", "geometry")).fetchone()
        except duckdb.Error:
            r = con.execute(q.replace("{geom}", "ST_GeomFromWKB(geometry)")).fetchone()
        n_, h, f, roof, hf, ds, osm, ms, goog, area = r
        pct = lambda x: f"{100 * x / n_:.0f}%" if n_ else "–"
        rows.append(f"| {name} | {n_:,} | {pct(osm)} / {pct(ms)} / {pct(goog)} | {pct(h)} | {pct(f)} | {pct(hf)} | {pct(roof)} | {area or 0:.0f} m² |")
        print(name, r, flush=True)
    table = "\n".join([
        f"### Open building data, Overture {release}",
        "",
        "| Area | Buildings | From OSM / Microsoft / Google | Height | Floors | Height or floors | Roof shape | Median footprint |",
        "|---|---|---|---|---|---|---|---|",
        *rows,
    ])
    print(table)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(table + "\n")


if __name__ == "__main__":
    main(sys.argv[1])
