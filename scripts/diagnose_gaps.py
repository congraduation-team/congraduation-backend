import json
import re
from pathlib import Path

import requests
from bs4 import BeautifulSoup

BASE = "https://abeek.sejong.ac.kr"

DEPTS = {
    "AERO": "14",
    "CIVIL": "08",
    "AIROBOT": "17",
    "AI": "18",
    "EE": "01",
    "GEO": "09",
    "ICE": "02",
    "DCON": "04",
    "NANO": "15",
    "EICE": "19",
}


def fetch(url: str) -> str | None:
    try:
        r = requests.get(url, timeout=40)
        if r.status_code != 200:
            print(f"  HTTP {r.status_code} {url}")
            return None
        return r.content.decode("euc-kr", "replace")
    except Exception as e:
        print(f"  ERR {url}: {e}")
        return None


def years_on_index(prefix: str):
    html = fetch(f"{BASE}/abeek/program{prefix}02.html")
    if not html:
        return {}
    soup = BeautifulSoup(html, "lxml")
    found = {}
    for a in soup.select("a[href]"):
        href = (a.get("href") or "").strip()
        if not re.match(rf"program{prefix}02_\d+\.html", href):
            continue
        text = a.get_text(" ", strip=True)
        m = re.search(r"(20\d{2})", text)
        y = int(m.group(1)) if m else None
        page = fetch(f"{BASE}/abeek/{href}")
        if page and y is None:
            m2 = re.search(r"(20\d{2})", BeautifulSoup(page, "lxml").get_text(" ")[:2000])
            y = int(m2.group(1)) if m2 else None
        if y:
            found[y] = href
            # image
            imgs = re.findall(r'src="([^"]+\.(?:jpg|png|jpeg|gif))"', page or "", re.I)
            imgs = [i for i in imgs if "fontawesome" not in i.lower() and "icon" not in i.lower()]
            print(f"  {y}: {href} imgs={imgs[:2]}")
    return found


print("=== Discover missing targets ===")
for code, prefix in DEPTS.items():
    print(f"\n{code} (program{prefix}02)")
    years_on_index(prefix)

# Check local gaps
print("\n=== Local gaps 2020-2026 ===")
root = Path("src/main/resources/abeek-data")
want = set(range(2020, 2027))
for d in sorted(p for p in root.iterdir() if p.is_dir()):
    files = {int(p.stem) for p in d.glob("*.json") if p.stem.isdigit()}
    missing = sorted(want - files)
    weak = []
    for y in sorted(files & want):
        j = json.loads((d / f"{y}.json").read_text(encoding="utf-8"))
        ocr = j.get("flowchartOcr") or {}
        ocr_n = len(ocr.get("courses") or []) if isinstance(ocr, dict) else 0
        err = ocr.get("error") if isinstance(ocr, dict) else None
        if ocr_n == 0 or err or not j.get("flowchartImageUrl"):
            weak.append(f"{y}(ocr={ocr_n},img={bool(j.get('flowchartImageUrl'))},err={str(err)[:50] if err else ''})")
    if missing or weak:
        print(f"{d.name}: missing={missing} weak={weak}")
