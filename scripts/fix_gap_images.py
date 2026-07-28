import json
import re
from pathlib import Path
from urllib.parse import quote, unquote, urljoin

import requests
from bs4 import BeautifulSoup

BASE = "https://abeek.sejong.ac.kr"
SESSION = requests.Session()
SESSION.headers["User-Agent"] = "Mozilla/5.0"


def fetch(url: str):
    r = SESSION.get(url, timeout=40)
    return r.status_code, r.content.decode("euc-kr", "replace"), r.content


def absolute_candidates(src: str) -> list[str]:
    if not src:
        return []
    out = []
    if src.startswith("http"):
        out.append(src)
        return out
    # keep original (may already be partially encoded)
    out.append(urljoin(BASE + "/", src.lstrip("/")))
    # decode then encode each segment
    decoded = unquote(src)
    parts = decoded.split("/")
    enc = "/".join(
        quote(p, safe="._-~") if re.search(r"[^\x00-\x7f]", p) else p for p in parts
    )
    out.append(urljoin(BASE + "/", enc.lstrip("/")))
    # spaces as %20
    out.append(urljoin(BASE + "/", quote(decoded, safe="/:._-~%").lstrip("/")))
    # unique
    seen = set()
    uniq = []
    for u in out:
        if u not in seen:
            seen.add(u)
            uniq.append(u)
    return uniq


def inspect(label: str, url: str):
    code, html, _ = fetch(url)
    soup = BeautifulSoup(html, "lxml")
    print(f"==== {label} HTTP={code}")
    for img in soup.find_all("img"):
        src = img.get("src") or ""
        if "fontawesome" in src.lower():
            continue
        print(" img:", src)
    for m in re.findall(r"window\.open\(['\"]([^'\"]+)['\"]", html):
        print(" open:", m)
    # data attributes / background
    for tag in soup.find_all(True):
        for attr, val in tag.attrs.items() if hasattr(tag, "attrs") else []:
            if isinstance(val, str) and re.search(r"\.(png|jpg|jpeg|gif|PNG|JPG)$", val):
                print(f" attr {attr}:", val[:150])


inspect("AERO2026", f"{BASE}/abeek/program1402_20.html")
inspect("CIVIL2026", f"{BASE}/abeek/program0802_20.html")
inspect("AIROBOT2026", f"{BASE}/abeek/program1702_11.html")

# AIROBOT URL tries
_, html, _ = fetch(f"{BASE}/abeek/program1702_11.html")
soup = BeautifulSoup(html, "lxml")
src = None
for img in soup.find_all("img"):
    s = img.get("src") or ""
    if s and "fontawesome" not in s.lower():
        src = s
        break
print("AIROBOT src:", src)
if src:
    for cand in absolute_candidates(src):
        try:
            r = SESSION.get(cand, timeout=30)
            print(f" TRY {r.status_code} len={len(r.content)} {cand}")
        except Exception as e:
            print(" TRY FAIL", cand, e)

# AI pages OCR readiness
for y, href in [(2021, "program1802_1.html"), (2022, "program1802_2.html"), (2023, "program1802_3.html")]:
    _, html, _ = fetch(f"{BASE}/abeek/{href}")
    soup = BeautifulSoup(html, "lxml")
    imgs = [img.get("src") for img in soup.find_all("img") if img.get("src") and "fontawesome" not in (img.get("src") or "").lower()]
    print(f"AI {y} imgs:", imgs)
