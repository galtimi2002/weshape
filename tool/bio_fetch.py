"""Downloads the biomass sources for tool/bio_build.py.

Usage:
  python3 tool/bio_fetch.py agb <out folder>    ESA CCI Biomass above-ground biomass, newest version
                                               and year, aggregated to 1 km (or 10 km) if offered
  python3 tool/bio_fetch.py spam <out folder>   MapSPAM 2020 global production GeoTIFFs (zip)
Each prints what it finds and the saved path last.

The ESA files come from the CEDA archive (open, no login). MapSPAM sits on the Harvard
Dataverse behind a short guestbook form; the API download is tried first, and when the
guestbook blocks it, put the zip in the R2 bucket as bio/src/spam2020_production.zip (the
workflow takes it from there).
"""
import html
import json
import os
import re
import sys
import urllib.parse
import urllib.request

UA = {"User-Agent": "weshape-bio-build (GitHub Actions)"}
CEDA = "https://dap.ceda.ac.uk/neodc/esacci/biomass/data/agb/maps/"
DATAVERSE = "https://dataverse.harvard.edu"
SPAM_DOI = "doi:10.7910/DVN/SWPENT"


def get(url, timeout=120):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout) as r:
        return r.read()


def save(url, path):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=600) as r, open(path, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
    print(f"saved {os.path.getsize(path) / 1e6:.1f} MB")
    print(path)


def listing(url):
    text = get(url).decode("utf-8", "replace")
    hrefs = [html.unescape(h) for h in re.findall(r'href="([^"?#]+)"', text)]
    out = []
    for h in hrefs:
        full = urllib.parse.urljoin(url, h)
        if full.startswith(url) and full != url:
            out.append(full)
    return sorted(set(out))


def agb(out):
    versions = [u for u in listing(CEDA) if re.search(r"/v\d+(\.\d+)?/?$", u)]
    versions.sort(key=lambda u: [int(x) for x in re.findall(r"\d+", u.rstrip("/").split("/")[-1])])
    print("versions:", *versions, sep="\n  ")
    # Breadth-first through the newest version, looking for aggregated whole-world files.
    best = None
    for version in reversed(versions):
        queue, seen, files = [version if version.endswith("/") else version + "/"], set(), []
        while queue and len(seen) < 80:
            url = queue.pop(0)
            if url in seen:
                continue
            seen.add(url)
            try:
                for item in listing(url):
                    if item.endswith("/"):
                        queue.append(item)
                    elif item.lower().endswith((".nc", ".tif")):
                        files.append(item)
            except Exception as e:  # noqa: BLE001 - print and keep looking
                print("cannot list", url, e)
        agg = [f for f in files if re.search(r"(1000m|01000m|1km|10000m|10km)", f, re.I)]
        print(f"{version}: {len(files)} files, {len(agg)} aggregated, e.g.", *agg[:12], sep="\n  ")
        if agg:
            def rank(f):
                year = max([int(y) for y in re.findall(r"(?<!\d)(20\d\d)(?!\d)", f)] or [0])
                fine = 1 if re.search(r"(1000m|01000m|1km)", f, re.I) else 0
                agb_only = 0 if re.search(r"_sd|change|diff", f, re.I) else 1
                return (agb_only, fine, year)
            best = max(agg, key=rank)
            break
    if not best:
        sys.exit("no aggregated biomass file found")
    os.makedirs(out, exist_ok=True)
    save(best, os.path.join(out, os.path.basename(urllib.parse.urlparse(best).path)))


def spam(out):
    meta = json.loads(get(f"{DATAVERSE}/api/datasets/:persistentId/?persistentId={SPAM_DOI}"))
    files = meta["data"]["latestVersion"]["files"]
    for f in files:
        d = f["dataFile"]
        print(f"  {d['id']}  {d['filename']}  {d.get('filesize', 0) / 1e6:.1f} MB")
    prod = [f["dataFile"] for f in files if re.search(r"global_production.*geotiff.*\.zip$", f["dataFile"]["filename"], re.I)]
    if not prod:
        sys.exit("no global production zip in the dataset")
    os.makedirs(out, exist_ok=True)
    save(f"{DATAVERSE}/api/access/datafile/{prod[0]['id']}", os.path.join(out, "spam2020_production.zip"))


if __name__ == "__main__":
    {"agb": agb, "spam": spam}[sys.argv[1]](sys.argv[2])
