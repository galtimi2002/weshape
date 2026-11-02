"""Downloads the Global Heat Flow Database (release 2024) table from GFZ Data Services.

Usage: python3 tool/geo_fetch_heatflow.py <out folder> [DOI]

Follows the DOI to its landing page, finds the download folder on datapub.gfz-potsdam.de,
lists it, prints every file and the licence it finds, and downloads the biggest data table
(.csv, .xlsx, .txt or .zip). Prints the saved path last.
"""
import html
import os
import re
import sys
import urllib.parse
import urllib.request

DOI = "10.5880/fidgeo.2024.014"
UA = {"User-Agent": "weshape-geo-build (GitHub Actions)"}
TABLE = (".csv", ".xlsx", ".txt", ".tsv", ".zip")


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        return r.geturl(), r.read()


def main(out, doi=DOI):
    os.makedirs(out, exist_ok=True)
    landing, page = get("https://doi.org/" + doi)
    text = page.decode("utf-8", "replace")
    print("landing page:", landing)
    for lic in sorted(set(re.findall(r"(CC[ -]BY[^<\"]{0,20}|creativecommons\.org/licenses/[a-z\-/0-9.]+)", text))):
        print("licence:", lic.strip())
    links = sorted(set(html.unescape(m) for m in re.findall(r"https?://datapub\.gfz-potsdam\.de/download/[^\"'<> ]+", text)))
    print("download links:", *links, sep="\n  ")
    files = []
    for link in links:
        if link.lower().endswith(TABLE):
            files.append((link, 0))
            continue
        folder = link if link.endswith("/") else link + "/"
        try:
            _, listing = get(folder)
        except Exception as e:  # noqa: BLE001 - print and try the next link
            print("cannot list", folder, e)
            continue
        listing = listing.decode("utf-8", "replace")
        for href, rest in re.findall(r'href="([^"?]+)"[^\n]*?</a>(.*)', listing):
            if href.startswith(("/", "?", "..")):
                continue
            size = re.search(r"(\d+(?:\.\d+)?)([KMG])\s*$", rest.strip())
            n = 0
            if size:
                n = float(size.group(1)) * {"K": 1e3, "M": 1e6, "G": 1e9}[size.group(2)]
            url = urllib.parse.urljoin(folder, href)
            print(f"  file: {url}  {rest.strip()[-12:]}")
            if href.lower().endswith(TABLE) and not re.search(r"description|readme|template", href, re.I):
                files.append((url, n))
    if not files:
        sys.exit("no data table found")
    url = max(files, key=lambda f: f[1])[0]
    name = os.path.basename(urllib.parse.urlparse(url).path)
    path = os.path.join(out, name)
    _, data = get(url)
    with open(path, "wb") as f:
        f.write(data)
    print(f"saved {len(data) / 1e6:.1f} MB")
    print(path)


if __name__ == "__main__":
    main(*sys.argv[1:])
