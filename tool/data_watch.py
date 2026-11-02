"""Checks whether the map data sources have published something newer than what we built from.

Usage: python3 tool/data_watch.py <state.json> [--acknowledge]

Each check returns a short fingerprint of a source (its newest version, release, ...). The
fingerprints of the last acknowledged run are kept in <state.json> (the workflow keeps that file
in R2). When a fingerprint differs, the source is listed and the exit code is 1, so the scheduled
run turns red and GitHub sends an e-mail. It stays red every month until someone runs the
workflow by hand with "acknowledge", which saves the current fingerprints. Without a state file
the current fingerprints are saved as the starting point.

A check that cannot reach its source prints a warning and is skipped: a site being down is not a
new release.
"""
import json
import os
import re
import sys
import urllib.parse
import urllib.request

UA = {"User-Agent": "weshape-data-watch (GitHub Actions)"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read()


def get_json(url):
    return json.loads(get(url))


def esa_biomass():
    """ESA CCI Biomass (forest residues): the newest version folder in the CEDA archive."""
    base = "https://dap.ceda.ac.uk/neodc/esacci/biomass/data/agb/maps/"
    text = get(base).decode("utf-8", "replace")
    versions = set(re.findall(r'href="[^"]*?/?(v\d+(?:\.\d+)*)/?"', text))
    if not versions:
        raise RuntimeError("no version folders listed")
    return max(versions, key=lambda v: [int(x) for x in re.findall(r"\d+", v)])


def mapspam():
    """MapSPAM (crop residues): the 2020 dataset's version, and any newer SPAM year on the Dataverse."""
    d = get_json("https://dataverse.harvard.edu/api/datasets/:persistentId/?persistentId=doi:10.7910/DVN/SWPENT")
    v = d["data"]["latestVersion"]
    found = [f"2020 v{v.get('versionNumber')}.{v.get('versionMinorNumber')}"]
    q = urllib.parse.quote('"Spatially-Disaggregated Crop Production Statistics"')
    s = get_json(f"https://dataverse.harvard.edu/api/search?q={q}&type=dataset&per_page=100")
    years = set()
    for item in s["data"]["items"]:
        for y in re.findall(r"\b(20\d\d)\b", item.get("name", "")):
            if int(y) > 2020:
                years.add(y)
    found += sorted(years)
    return ", ".join(found)


def heat_flow():
    """Global Heat Flow Database (geothermal): the releases named on the IHFC data page."""
    text = get("https://www.ihfc-iugg.org/products/global-heat-flow-database/data").decode("utf-8", "replace")
    releases = sorted(set(re.findall(r"\bR(20\d\d)\b", text)))
    if not releases:
        raise RuntimeError("no releases named on the page")
    return "R" + releases[-1]


def soil_temperature():
    """SoilTemp (geothermal ground temperature): the newest Zenodo version of the record."""
    d = get_json("https://zenodo.org/api/records/4558732/versions/latest")
    return f"record {d['id']} ({d.get('metadata', {}).get('version') or d.get('created', '')[:10]})"


def river_atlas():
    """RiverATLAS / HydroATLAS (hydro): the number of versions of the figshare item."""
    versions = get_json("https://api.figshare.com/v2/articles/20087486/versions")
    return f"{len(versions)} version(s)"


CHECKS = {
    "ESA CCI Biomass (biomass: forest)": esa_biomass,
    "MapSPAM (biomass: crops)": mapspam,
    "Global Heat Flow Database (geothermal)": heat_flow,
    "SoilTemp (geothermal)": soil_temperature,
    "RiverATLAS (hydro)": river_atlas,
}

WHAT_TO_DO = {
    "ESA CCI Biomass (biomass: forest)": "run 'Build biomass map data' (it takes the newest version itself)",
    "MapSPAM (biomass: crops)": "download the new production GeoTIFF zip, put it in R2 as "
                                "bio/src/spam2020_production.zip, check the file names, run 'Build biomass map data'",
    "Global Heat Flow Database (geothermal)": "update the DOI / IHFC link in tool/geo_fetch_heatflow.py, run 'Build geothermal map data'",
    "SoilTemp (geothermal)": "update SOIL_URL in geo-build.yml, run 'Build geothermal map data'",
    "RiverATLAS (hydro)": "check the new version, update tool/hydro_build_rivers.py, run 'Build hydro map data'",
}


def main(state_path, acknowledge=False):
    old = {}
    if os.path.exists(state_path):
        with open(state_path) as f:
            old = json.load(f)
    now, rows, changed = dict(old), [], []
    for name, check in CHECKS.items():
        try:
            value = check()
        except Exception as e:  # noqa: BLE001 - a site being down is not a release
            print(f"::warning::{name}: could not check ({e})")
            rows.append((name, old.get(name, "-"), "could not check"))
            continue
        now[name] = value
        if name in old and old[name] != value:
            changed.append(name)
            rows.append((name, old[name], f"**{value}** ← new"))
        else:
            rows.append((name, value, "same" if name in old else "recorded"))

    lines = ["### Map data sources", "", "| Source | Built from | Now |", "|---|---|---|"]
    lines += [f"| {n} | {a} | {b} |" for n, a, b in rows]
    if changed:
        lines += ["", "**Something new was published.** What to do:"]
        lines += [f"- {n}: {WHAT_TO_DO[n]}" for n in changed]
        lines += ["", "Then run this workflow by hand with *acknowledge* ticked, so it stops reporting it."]
    report = "\n".join(lines)
    print(report)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(report + "\n")

    if not old or acknowledge or not changed:
        with open(state_path, "w") as f:
            json.dump(now, f, indent=2, ensure_ascii=False)
        print("state saved")
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], "--acknowledge" in sys.argv[2:]))
