#!/usr/bin/env python3
"""누락된 이수체계도 OCR 재수집 (base64 임베드 / 한글 URL / AI OCR 미실행 분)."""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from scrape_abeek import (  # noqa: E402
    DEPARTMENTS,
    OUT_DIR,
    discover_year_pages,
    fetch_html,
    find_flowchart_image,
    ocr_flowchart,
    synthesize_courses_from_ocr,
    apply_design_levels,
)
from bs4 import BeautifulSoup

BASE = "https://abeek.sejong.ac.kr"

# 재처리 대상
TARGETS = [
    ("AERO", 2026),
    ("CIVIL", 2026),
    ("AIROBOT", 2026),
    ("AI", 2021),
    ("AI", 2022),
    ("AI", 2023),
]


def dept_meta(code: str) -> dict:
    for d in DEPARTMENTS:
        if d["code"] == code:
            return d
    # AI is in DEPARTMENTS
    raise KeyError(code)


def update_one(code: str, year: int) -> None:
    meta = dept_meta(code)
    years = discover_year_pages(meta["prefix"])
    href = years.get(year)
    path = OUT_DIR / code / f"{year}.json"
    if not href:
        print(f"[SKIP] {code} {year}: 사이트에 연도 페이지 없음")
        return

    url = f"{BASE}/abeek/{href}"
    print(f"[OCR] {code} {year}: {url}")
    html = fetch_html(url)
    if not html:
        print(f"  FAIL fetch")
        return
    soup = BeautifulSoup(html, "lxml")
    img = find_flowchart_image(soup)
    if not img:
        print(f"  FAIL no image")
        return

    img_ref = "data:embedded" if img.startswith("data:") else img
    print(f"  image={'embedded-base64' if img.startswith('data:') else img[:100]}")
    ocr = ocr_flowchart(img, code, year)
    n = len(ocr.get("courses") or [])
    err = ocr.get("error")
    print(f"  ocr_boxes={n} err={err}")

    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
    else:
        data = {
            "departmentCode": code,
            "departmentName": meta["name"],
            "year": year,
            "sourceUrl": url,
            "requirement": {},
            "courses": [],
            "needsReview": True,
        }

    data["sourceUrl"] = url
    data["flowchartImageUrl"] = img_ref
    data["flowchartOcr"] = ocr
    data["needsReview"] = True

    # 교과가 비어 있으면 OCR로 채움
    if not data.get("courses") and n:
        data["courses"] = synthesize_courses_from_ocr(ocr)
        apply_design_levels(data["courses"], data.get("requirement") or {})
        data["coursesSource"] = "OCR_SYNTH"

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"  saved {path}")


def main() -> int:
    for code, year in TARGETS:
        try:
            update_one(code, year)
        except Exception as e:
            print(f"[ERR] {code} {year}: {e}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
