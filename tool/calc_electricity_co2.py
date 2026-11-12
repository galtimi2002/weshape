"""Builds the table of g CO2e per kWh of electricity by country for the calculations.

Usage: python3 tool/calc_electricity_co2.py <owid-energy-data.csv> <out.csv> --version TEXT
           [--current current.csv] [--force]

Source: Our World in Data energy-data (column carbon_intensity_elec, from Ember's yearly
electricity data; lifecycle g CO2e per kWh generated). For each country: typical = the latest year,
low/high = the lowest/highest of the last three years with data.

Rows the backend would reject are left out and listed: no two-letter code, last data more than
five years old, or a value under 10 g (no grid is that clean; Central African Republic shows 0).

Checks (exit code 1, nothing written, when one fails):
- at least 200 countries;
- with --current (the table in R2 now): no country changes by more than 50% AND more than 50 g,
  unless --force. Hydro countries can swing that much in a dry year: look, then rerun with force.
Countries that disappear are listed as a warning.
"""
import argparse
import csv
import datetime
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MIN_COUNTRIES = 200
MIN_G, MAX_G = 10, 1400
HEADER = "country,year,low,typical,high"


def iso_map():
    m = {}
    with open(os.path.join(HERE, "iso3_to_iso2.csv"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            a3, a2 = line.strip().split(",")
            m[a3] = a2
    return m


def build(owid_path):
    iso = iso_map()
    by, names = {}, {}
    with open(owid_path, newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            code = r["iso_code"] or ("OWID_KOS" if r["country"] == "Kosovo" else "")
            v = r.get("carbon_intensity_elec")
            if code and v and (len(code) == 3 or code == "OWID_KOS"):
                by.setdefault(code, []).append((int(r["year"]), float(v)))
                names[code] = r["country"]
    this_year = datetime.date.today().year
    rows, dropped = {}, []
    for code, values in sorted(by.items()):
        values.sort()
        last = values[-1][0]
        window = [x for y, x in values if y >= last - 2]
        low, typ, high = round(min(window)), round(values[-1][1]), round(max(window))
        name = names[code]
        if code not in iso:
            dropped.append(f"{name} ({code}): no two-letter code")
        elif last < this_year - 5:
            dropped.append(f"{name}: last data {last}")
        elif low < MIN_G:
            dropped.append(f"{name}: {low} g, not believable")
        elif high > MAX_G:
            dropped.append(f"{name}: {high} g, over {MAX_G}")
        else:
            rows[iso[code]] = (last, low, typ, high, name)
    return rows, dropped


def read_table(path):
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or line == HEADER:
                continue
            c, y, lo, t, hi = line.split(",")
            out[c] = (int(y), float(lo), float(t), float(hi))
    return out


def compare(rows, current):
    jumps, gone = [], []
    for c, old in current.items():
        if c not in rows:
            gone.append(c)
            continue
        new_t, old_t = rows[c][2], old[2]
        if abs(new_t - old_t) > 50 and abs(new_t - old_t) > 0.5 * old_t:
            jumps.append(f"{rows[c][4]} ({c}): {old_t:g} -> {new_t:g} g/kWh")
    return jumps, gone


def write(rows, path, version):
    with open(path, "w", encoding="utf-8") as f:
        f.write("# Lifecycle gCO2e per kWh of electricity generated in the country (imports and grid losses not included).\n")
        f.write(f"# version: {version}\n")
        f.write("# Source: Ember, Yearly Electricity Data, via Our World in Data energy-data (carbon_intensity_elec), CC BY 4.0.\n")
        f.write("# typical: the latest year; low/high: lowest/highest of the last three years with data.\n")
        f.write(HEADER + "\n")
        for c in sorted(rows):
            y, lo, t, hi, _ = rows[c]
            f.write(f"{c},{y},{lo},{t},{hi}\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("owid")
    ap.add_argument("out")
    ap.add_argument("--version", required=True)
    ap.add_argument("--current")
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()

    rows, dropped = build(a.owid)
    report = [f"**Electricity CO2 table {a.version}:** {len(rows)} countries."]
    if dropped:
        report.append("Left out: " + "; ".join(dropped) + ".")
    ok = True
    if len(rows) < MIN_COUNTRIES:
        report.append(f"**Failed:** only {len(rows)} countries (at least {MIN_COUNTRIES}).")
        ok = False
    if a.current and os.path.exists(a.current):
        jumps, gone = compare(rows, read_table(a.current))
        if gone:
            report.append("Warning, no longer in the data: " + ", ".join(gone) + ".")
        if jumps:
            report.append("Changed by more than 50% and 50 g: " + "; ".join(jumps) + ".")
            if not a.force:
                report.append("**Failed:** check these, then run the workflow again with \"force\" ticked.")
                ok = False
    text = "\n\n".join(report)
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text + "\n")
    if not ok:
        sys.exit(1)
    write(rows, a.out, a.version)


if __name__ == "__main__":
    main()
