#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
세종대 ABEEK 사이트에서 학과별 2020~2026 공학인증요건/교과과정/이수체계도(OCR) 수집.

사용:
  python scripts/scrape_abeek.py                 # HTML + OCR
  python scripts/scrape_abeek.py --html-only     # HTML만
  python scripts/scrape_abeek.py --ocr-only      # 기존 JSON에 OCR만 병합
  python scripts/scrape_abeek.py --dept MECH     # 특정 학과만
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
import time
from collections import defaultdict
from pathlib import Path
from typing import Any
from urllib.parse import urljoin, quote

import requests
from bs4 import BeautifulSoup

BASE = "https://abeek.sejong.ac.kr"
OUT_DIR = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "abeek-data"
TEMP_DIR = Path(__file__).resolve().parents[1] / "data" / "ocr-temp"

# 컴퓨터공학(CSE)은 기존 시드 유지 → 스크래핑 대상에서 제외
DEPARTMENTS: list[dict[str, str]] = [
    {"code": "ARCH", "name": "건축공학", "prefix": "07"},
    {"code": "CIVIL", "name": "토목공학", "prefix": "08"},
    {"code": "ENV", "name": "환경융합공학", "prefix": "10"},
    {"code": "GEO", "name": "지구정보공학", "prefix": "09"},
    {"code": "ENERGY", "name": "에너지자원공학", "prefix": "11"},
    {"code": "MECH", "name": "기계공학", "prefix": "13"},
    {"code": "AERO", "name": "항공우주공학", "prefix": "14"},
    {"code": "NANO", "name": "나노신소재공학", "prefix": "15"},
    {"code": "NUCLEAR", "name": "원자력공학", "prefix": "12"},
    {"code": "EICE", "name": "전자정보통신공학", "prefix": "19"},
    {"code": "EE", "name": "전자공학", "prefix": "01"},
    {"code": "ICE", "name": "정보통신공학", "prefix": "02"},
    {"code": "SW", "name": "소프트웨어", "prefix": "05"},
    {"code": "DCON", "name": "디지털콘텐츠", "prefix": "04"},
    {"code": "SEC", "name": "정보보호학", "prefix": "06"},
    {"code": "DS", "name": "데이터사이언스학", "prefix": "16"},
    {"code": "AIROBOT", "name": "AI로봇학", "prefix": "17"},
    {"code": "AI", "name": "인공지능", "prefix": "18"},
]

YEAR_MIN, YEAR_MAX = 2020, 2026
SESSION = requests.Session()
SESSION.headers.update({
    "User-Agent": "Mozilla/5.0 (compatible; SejongAbeekScraper/1.0)",
})


def fetch_html(url: str) -> str | None:
    try:
        r = SESSION.get(url, timeout=40)
        if r.status_code != 200:
            print(f"  [HTTP {r.status_code}] {url}")
            return None
        # 사이트가 euc-kr / utf-8 혼재
        if r.encoding is None or r.encoding.lower() in ("iso-8859-1", "ascii"):
            r.encoding = r.apparent_encoding or "euc-kr"
        # content-type에 charset이 없어도 raw로 euc-kr 시도
        text = r.content.decode("euc-kr", errors="replace")
        if "공학인증" not in text and "ABEEK" not in text and "이수" not in text:
            text = r.content.decode("utf-8", errors="replace")
        return text
    except Exception as e:
        print(f"  [ERR] {url}: {e}")
        return None


def absolute_url(src: str) -> str:
    if not src:
        return src
    if src.startswith("data:") or src.startswith("http"):
        return src
    from urllib.parse import unquote
    decoded = unquote(src)
    parts = decoded.split("/")
    enc = "/".join(quote(p, safe="._-~") if re.search(r"[^\x00-\x7f]", p) else p for p in parts)
    return urljoin(BASE + "/", enc.lstrip("/"))


def extract_year(text: str) -> int | None:
    text = clean_text(text)
    m = re.search(r"(20\d{2})\s*[년학]", text)
    if m:
        return int(m.group(1))
    m = re.fullmatch(r"(20\d{2})", text)
    if m:
        return int(m.group(1))
    m = re.search(r"\b(20\d{2})\b", text)
    return int(m.group(1)) if m else None


def discover_year_pages(prefix: str) -> dict[int, str]:
    """index page → {year: relative_html}"""
    index = f"{BASE}/abeek/program{prefix}02.html"
    html = fetch_html(index)
    if not html:
        return {}
    soup = BeautifulSoup(html, "lxml")
    found: dict[int, str] = {}
    for a in soup.select("a[href]"):
        href = (a.get("href") or "").strip()
        if not re.match(rf"program{prefix}02_\d+\.html", href):
            continue
        y = extract_year(a.get_text(" ", strip=True))
        page_url = f"{BASE}/abeek/{href}"
        if y is None:
            page_html = fetch_html(page_url)
            if page_html:
                y = extract_year(BeautifulSoup(page_html, "lxml").get_text(" ", strip=True)[:1500])
        if y and YEAR_MIN <= y <= YEAR_MAX:
            found[y] = href
    return found


def clean_text(s: str) -> str:
    s = re.sub(r"\s+", " ", s or "").strip()
    s = s.replace("\xa0", " ").replace("&nbsp;", " ")
    return s


def parse_credits(raw: str) -> tuple[float, float]:
    """'3(1.5)' / '15학점' / '60학점 (설계 12학점)' / '60(설계12)' → (credits, design)"""
    raw = clean_text(raw).replace(",", "")
    # 60학점 (설계 12학점) / 60학점(설계12학점)
    m = re.search(r"([\d.]+)\s*학점?\s*\(?\s*설계\s*([\d.]+)\s*학점?\s*\)?", raw)
    if m:
        return float(m.group(1)), float(m.group(2))
    # 60(설계12)
    m = re.search(r"([\d.]+)\s*\(\s*설계\s*([\d.]+)\s*\)", raw)
    if m:
        return float(m.group(1)), float(m.group(2))
    # 3(1.5) / 54 (12)
    m = re.search(r"([\d.]+)\s*\(\s*([\d.]+)\s*\)", raw)
    if m:
        return float(m.group(1)), float(m.group(2))
    m2 = re.search(r"([\d.]+)", raw)
    if m2:
        return float(m2.group(1)), 0.0
    return 0.0, 0.0


def categorize_label(label: str) -> str:
    t = label.upper()
    if "MSC" in t or "BSM" in t or "기초과학" in label or ("수학" in label and "전산" in label):
        return "BSM"
    if "교양" in label or label.strip() in ("인필",):
        if "전공" in label:
            return "MAJOR"
        if "MSC" in t or "BSM" in t:
            return "BSM"
        return "GENERAL"
    if "전공" in label:
        return "MAJOR"
    if "인증필수선택" in label or "인증선택" in label:
        return "GENERAL"
    return "MAJOR"


def parse_role(label: str) -> str:
    if "인증필수선택" in label or "인증선택" in label:
        return "CERT_ELECTIVE"
    if "MSC" in label.upper() or "BSM" in label.upper():
        if "인선" in label:
            return "ELECTIVE"
        return "BSM_REQUIRED"
    if "인필" in label or "인증필수" in label:
        return "REQUIRED"
    if "인선" in label:
        return "ELECTIVE"
    return "ELECTIVE"


def _is_requirement_table(table) -> bool:
    rows = table.select("tr")
    if len(rows) < 2:
        return False
    text = clean_text(table.get_text(" ", strip=True))
    header = clean_text(rows[0].get_text(" ", strip=True))
    # DS/SEC 스타일: 헤더에 최소이수학점, 또는 ABEEK/교과구분
    header_hit = any(k in header.replace(" ", "") for k in (
        "ABEEK", "교과구분", "최소이수학점", "최소이수", "전문교양"
    )) and ("학점" in header or "ABEEK" in header or "교과" in header)
    # 본문에 전문교양+BSM/MSC+전공이 같이 있으면 요건표
    body_hit = (
        ("전문교양" in text or (text.startswith("교양") or " 교양 " in f" {text} "))
        and ("BSM" in text.upper() or "MSC" in text.upper() or "기초과학" in text)
        and "전공" in text
    )
    # 커리큘럼 표 제외
    if "교과목명" in text and "학년" in text and "학기" in text:
        return False
    return header_hit or body_hit


def parse_requirement_table(soup: BeautifulSoup) -> dict[str, Any]:
    """ABEEK/학과 공학인증요건 테이블 파싱 (ABEEK 헤더 없는 DS·SEC 표 포함)."""
    req: dict[str, Any] = {
        "generalMinCredits": None,
        "bsmMinCredits": None,
        "majorMinCredits": None,
        "designMinCredits": None,
        "totalMinCredits": None,
        "rows": [],
        "rawNotes": [],
    }

    candidate = None
    for table in soup.select("table"):
        if _is_requirement_table(table):
            candidate = table
            break
    if candidate is None:
        return req

    rows = candidate.select("tr")
    last_cat = ""

    for tr in rows:
        cells = [clean_text(td.get_text(" ", strip=True)) for td in tr.find_all(["td", "th"])]
        if len(cells) < 2:
            continue
        joined0 = cells[0].replace(" ", "")
        # 헤더 스킵
        if any(h in joined0 for h in ("교과구분", "ABEEK교과", "구분")) and "학점" in "".join(cells):
            if "전문교양" not in cells[0] or "최소" in "".join(cells):
                # SEC 헤더가 ['전문교양','최소이수학점',...] 인 경우
                if "최소" in "".join(cells) and cells[0] in ("전문교양", "교양"):
                    continue
            if "교과" in joined0 or joined0.startswith("ABEEK"):
                continue

        cat = cells[0]
        credits_raw = cells[1] if len(cells) > 1 else ""

        # 헤더 행 스킵
        if "교과" in cat and "구분" in cat:
            continue
        if "최소" in credits_raw and not re.search(r"\d", credits_raw):
            continue
        if cat in ("전문교양", "교양") and "최소" in "".join(cells[1:3]) and not re.search(r"\d", credits_raw):
            continue

        # SEC BSM rowspan 연속행: ['인증선택', '과목목록...'] 또는 ['인증필수', ...]
        if cat in ("인증필수", "인증선택", "인증필수선택") and last_cat:
            note = " ".join(cells[1:]) if len(cells) > 1 else ""
            if req["rows"] and req["rows"][-1]["category"] == last_cat:
                req["rows"][-1]["note"] = (req["rows"][-1].get("note") or "") + f" / {cat}: {note}"
                req["rawNotes"].append(f"{cat}: {note}")
            continue

        # 셀 배치: [구분, 학점, 비고...] 또는 [구분, 학점, 인증필수, 과목...]
        # 학점 칸이 아니면(인증필수 등) → rowspan으로 학점 칸이 비어 이전 카테고리 연속
        if "학점" not in credits_raw and not re.search(r"^\d", credits_raw) and cat not in (
            "전문교양", "교양", "BSM", "MSC", "전공", "인증최소 이수학점", "인증최소이수학점"
        ):
            continue

        note_parts = cells[2:]
        note = " ".join(note_parts)

        # BSM 4칸: BSM | 18학점 | 인증필수 | 과목들
        if len(cells) >= 4 and cells[2] in ("인증필수", "인증선택", "인증필수선택"):
            note = f"{cells[2]}: " + " ".join(cells[3:])

        c, d = parse_credits(credits_raw)
        # 전공 칸에 설계가 note에만 있는 경우
        if d == 0 and "전공" in cat:
            m = re.search(r"설계\s*([\d.]+)\s*학점", credits_raw + " " + note)
            if m:
                d = float(m.group(1))

        row = {"category": cat, "minCredits": c, "designCredits": d, "note": note}
        req["rows"].append(row)
        req["rawNotes"].append(note)
        last_cat = cat

        cat_u = cat.upper()
        if "전문교양" in cat or cat.strip() == "교양" or "일반교양" in cat:
            req["generalMinCredits"] = c
        elif "MSC" in cat_u or "BSM" in cat_u or "기초과학" in cat:
            req["bsmMinCredits"] = c
        elif "전공" in cat:
            req["majorMinCredits"] = c
            if d > 0:
                req["designMinCredits"] = d
            m = re.search(r"설계\s*([\d.]+)", note + " " + credits_raw + " " + cat)
            if m and (req["designMinCredits"] is None or req["designMinCredits"] == 0):
                req["designMinCredits"] = float(m.group(1))
        elif "인증최소" in cat.replace(" ", ""):
            req["totalMinCredits"] = c
            if d > 0:
                req["designMinCredits"] = req["designMinCredits"] or d

    # 설계학점 note에서 재추출
    if not req["designMinCredits"]:
        joined = " ".join(req["rawNotes"])
        m = re.search(r"설계\s*(?:교과목\s*)?([\d.]+)\s*학점", joined)
        if m:
            req["designMinCredits"] = float(m.group(1))
        else:
            m2 = re.search(r"([\d.]+)\s*학점\s*\(?\s*설계", joined)
            # weaker — ignore
            _ = m2

    # 설계 과목 목록 추출 (◇ / ▪ / - 기초설계 : 형태, (3/3)·(3, 설계1.5) 모두)
    design_courses = {"BASIC": [], "ELEMENT": [], "COMPREHENSIVE": []}
    joined = "\n".join(req["rawNotes"])
    for level, patterns in [
        ("BASIC", [
            r"(?:◇|▪|\*)\s*기초설계\s*(?:\([^)]*\))?\s*[:：]\s*(.+?)(?=(?:◇|▪|\*)\s*(?:요소|종합)설계|※|$)",
            r"-\s*기초설계\s*\(설계\s*[\d.]+학점\)\s*[:：]\s*(.+?)(?=-\s*요소설계|▪|※|$)",
            r"기초설계\s*\(설계\s*[\d.]+학점\)\s*[:：]\s*(.+?)(?=요소설계|▪|※|$)",
            r"기초설계\s*[:：]\s*(.+?)(?=요소설계\s*[:：]|종합설계\s*[:：]|※|$)",
        ]),
        ("ELEMENT", [
            r"(?:◇|▪|\*)\s*요소설계\s*(?:\([^)]*\))?\s*[:：]\s*(.+?)(?=(?:◇|▪|\*)\s*종합설계|※|$)",
            r"-\s*요소설계\s*\([^)]*\)\s*[:：]\s*(.+?)(?=-\s*종합설계|▪|※|$)",
            r"요소설계\s*\([^)]*\)\s*[:：]\s*(.+?)(?=종합설계|▪|※|$)",
            r"요소설계\s*[:：]\s*(.+?)(?=종합설계\s*[:：]|※|$)",
        ]),
        ("COMPREHENSIVE", [
            r"(?:◇|▪|\*)\s*종합설계\s*(?:\([^)]*\))?\s*[:：]\s*(.+?)(?=(?:◇|▪|\*|※|⇒)|$)",
            r"-\s*종합설계\s*\([^)]*\)\s*[:：]\s*(.+?)(?=▪|※|$)",
            r"종합설계\s*\([^)]*\)\s*[:：]\s*(.+?)(?=▪|※|$)",
            r"종합설계\s*[:：]\s*(.+?)(?=※|⇒|$)",
        ]),
    ]:
        for p in patterns:
            m = re.search(p, joined, re.S)
            if m:
                chunk = clean_text(m.group(1))
                parts = re.split(r",(?![^()]*\))", chunk)
                for part in parts:
                    part = clean_text(part)
                    if not part or part.startswith("⇒") or part.startswith("▪"):
                        continue
                    name = part.split("(")[0].strip(" –-*·")
                    dc = 0.0
                    dm = re.search(r"설계\s*([\d.]+)", part)
                    if dm:
                        dc = float(dm.group(1))
                    else:
                        dm2 = re.search(r"\([\d.]+\s*[,，/]\s*([\d.]+)\)", part)
                        if dm2:
                            dc = float(dm2.group(1))
                    if name:
                        design_courses[level].append({"name": name, "designCredits": dc, "raw": part})
                break
    req["designCourses"] = design_courses

    # 인증선택 요건
    joined = " ".join(req["rawNotes"])
    cert = {"minCourses": 0, "minCredits": 0, "minAreas": 0}
    if "인증필수선택" in joined or "인증선택" in joined:
        if re.search(r"2과목\s*이상", joined):
            cert["minCourses"] = 2
        if re.search(r"6학점\s*이상", joined):
            cert["minCredits"] = 6
        if re.search(r"2개\s*영역", joined):
            cert["minAreas"] = 2
        # SEC: 둘 중 반드시 한 과목 이상
        if re.search(r"한\s*과목\s*이상", joined) and cert["minCourses"] == 0:
            cert["minCourses"] = 1
    req["certElective"] = cert

    for k in ("generalMinCredits", "bsmMinCredits", "majorMinCredits", "designMinCredits"):
        if req[k] is None:
            req[k] = 0
    return req


def _header_index(headers: list[str], *keywords: str) -> int | None:
    for i, h in enumerate(headers):
        for kw in keywords:
            if kw in h.replace(" ", ""):
                return i
    return None


def parse_curriculum_table(soup: BeautifulSoup) -> list[dict[str, Any]]:
    """입학자 교과과정 테이블. 컬럼 수가 학과마다 다름:
    - 5열: 학년|학기|인증구분|교과목명|학점
    - 6열: 학년|학기|이수구분|인증구분|교과목명|학점
    """
    courses: list[dict[str, Any]] = []
    current_year = None
    current_semester = None

    for table in soup.select("table"):
        rows = table.select("tr")
        if not rows:
            continue
        headers = [clean_text(th.get_text(" ", strip=True)) for th in rows[0].find_all(["th", "td"])]
        header_join = "".join(headers)
        if "교과목" not in header_join:
            continue
        if "ABEEK" in header_join and "교과구분" in header_join.replace(" ", ""):
            continue

        idx_grade = _header_index(headers, "학년")
        idx_sem = _header_index(headers, "학기")
        idx_complete = _header_index(headers, "이수구분")
        idx_cert = _header_index(headers, "인증구분")
        idx_name = _header_index(headers, "교과목")
        idx_credit = _header_index(headers, "학점")
        if idx_name is None or idx_credit is None:
            continue

        table_courses: list[dict[str, Any]] = []
        for tr in rows[1:]:
            cells = tr.find_all(["td", "th"])
            texts = [clean_text(c.get_text(" ", strip=True)) for c in cells]
            if not any(texts) or "소계" in "".join(texts):
                continue

            # rowspan으로 앞 셀이 비면 이전 학년/학기 유지. 셀 수 < 헤더면 앞에 패딩
            if len(texts) < len(headers):
                # 학년/학기가 rowspan으로 빠진 경우
                missing = len(headers) - len(texts)
                texts = ([""] * missing) + texts

            def cell(i: int | None) -> str:
                if i is None or i >= len(texts):
                    return ""
                return texts[i]

            g = cell(idx_grade)
            s = cell(idx_sem)
            if re.fullmatch(r"[1-4]", g):
                current_year = int(g)
            if re.fullmatch(r"[12]", s):
                current_semester = int(s)

            name = cell(idx_name)
            credit_raw = cell(idx_credit)
            cert = cell(idx_cert) or cell(idx_complete)
            complete = cell(idx_complete)
            role_label = cert
            if complete and cert and complete != cert:
                role_label = f"{cert}({complete})" if cert else complete
            elif complete and not cert:
                role_label = complete

            if not name or name in ("교과목명", "교과목"):
                continue
            if name in ("인필", "인선") and not credit_raw:
                continue
            # 잘못 파싱된 학점명 스킵
            if re.fullmatch(r"[\d.]+\s*학점.*", name):
                continue

            credits, design = parse_credits(credit_raw)
            term = f"{current_year}-{current_semester}" if current_year and current_semester else None

            elective_area = "NONE"
            blob = name + role_label
            if "역사와사상" in blob:
                elective_area = "HISTORY_THOUGHT"
            elif "경제와사회" in blob:
                elective_area = "ECONOMY_SOCIETY"
            elif "문화와예술" in blob:
                elective_area = "CULTURE_ART"

            name_clean = re.sub(r"^\[.*?\]\s*", "", name).strip()
            # category: 이수구분/인증구분 힌트
            cat_src = f"{complete} {cert}"
            category = categorize_label(cat_src if cat_src.strip() else role_label)
            if "전공" in complete or "전공" in cert:
                category = "MAJOR"
            elif "MSC" in cat_src.upper() or "BSM" in cat_src.upper() or "기초" in complete:
                if "교양" not in complete:
                    category = "BSM" if ("MSC" in cat_src.upper() or "BSM" in cat_src.upper()) else category

            table_courses.append({
                "name": name_clean,
                "roleLabel": role_label or cert or complete,
                "role": parse_role(role_label or cert or complete),
                "category": category,
                "credits": credits,
                "designCredits": design,
                "designLevel": "ELEMENT" if design > 0 else "NONE",
                "recommendedTerm": term,
                "gradeYear": current_year,
                "semester": current_semester,
                "electiveArea": elective_area,
            })

        # 가장 많은 과목을 가진 테이블 채택
        if len(table_courses) > len(courses):
            courses = table_courses
    return courses


def apply_design_levels(courses: list[dict], req: dict) -> None:
    basic_names = {re.sub(r"\s+", "", x["name"]) for x in req.get("designCourses", {}).get("BASIC", [])}
    elem_names = {re.sub(r"\s+", "", x["name"]) for x in req.get("designCourses", {}).get("ELEMENT", [])}
    comp_names = {re.sub(r"\s+", "", x["name"]) for x in req.get("designCourses", {}).get("COMPREHENSIVE", [])}

    def norm(n: str) -> str:
        return re.sub(r"\s+", "", n)

    for c in courses:
        n = norm(c["name"])
        if any(b and (b in n or n in b) for b in basic_names):
            c["designLevel"] = "BASIC"
        elif any(b and (b in n or n in b) for b in comp_names):
            c["designLevel"] = "COMPREHENSIVE"
        elif c["designCredits"] > 0:
            if any(b and (b in n or n in b) for b in elem_names):
                c["designLevel"] = "ELEMENT"
            else:
                c["designLevel"] = "ELEMENT"


def find_flowchart_image(soup: BeautifulSoup) -> str | None:
    for img in soup.select("img.img, .zoom_box img, img"):
        src = img.get("src") or ""
        if not src:
            continue
        if src.startswith("data:image/"):
            return src  # base64 임베드 이수체계도
        low = src.lower()
        if any(x in low for x in (".jpg", ".jpeg", ".png", ".gif", ".webp")):
            if "fontawesome" in low or "icon" in low:
                continue
            return absolute_url(src)
    return None


def _load_image_bytes(image_url: str) -> bytes:
    if image_url.startswith("data:image"):
        import base64
        _header, b64 = image_url.split(",", 1)
        return base64.b64decode(b64)

    from urllib.parse import unquote
    candidates = [image_url]
    if "%" in image_url and image_url.startswith(BASE):
        path = image_url[len(BASE):]
        candidates.append(urljoin(BASE + "/", unquote(path).lstrip("/")))

    last_err = None
    for cand in candidates:
        try:
            r = SESSION.get(cand, timeout=90)
            if r.status_code == 200 and len(r.content) > 1000:
                return r.content
            last_err = f"HTTP {r.status_code} {cand[:120]}"
        except Exception as e:
            last_err = str(e)
    raise RuntimeError(last_err or "image fetch failed")


def ocr_flowchart(image_url: str, dept_code: str, year: int) -> dict[str, Any]:
    """이미지 임시 저장 후 EasyOCR → 삭제. data: URI / 한글 URL 지원."""
    result: dict[str, Any] = {
        "imageUrl": ("data:embedded" if image_url.startswith("data:") else image_url),
        "needsReview": True,
        "courses": [],
        "prerequisites": [],
        "note": "OCR 기반 초안. 선수과목(화살표)은 자동 검출이 불완전하므로 검수 필요.",
    }
    try:
        import easyocr
        from PIL import Image
        import io
        import numpy as np
    except Exception as e:
        result["error"] = f"OCR deps missing: {e}"
        return result

    try:
        content = _load_image_bytes(image_url)
        TEMP_DIR.mkdir(parents=True, exist_ok=True)
        suffix = ".png"
        if not image_url.startswith("data:") and re.search(r"jpe?g", image_url, re.I):
            suffix = ".jpg"
        tmp_path = TEMP_DIR / f"{dept_code}_{year}{suffix}"
        tmp_path.write_bytes(content)
        img = Image.open(io.BytesIO(content)).convert("RGB")
        w, h = img.size
        reader = getattr(ocr_flowchart, "_reader", None)
        if reader is None:
            print("  [OCR] loading EasyOCR (ko)...")
            reader = easyocr.Reader(["ko", "en"], gpu=False)
            ocr_flowchart._reader = reader
        detections = reader.readtext(np.array(img), detail=1, paragraph=False)

        # x 좌표로 학기 컬럼 추정 (1-1 ~ 4-2 = 8열)
        items = []
        for box, text, conf in detections:
            text = clean_text(text)
            if not text or conf < 0.3:
                continue
            xs = [p[0] for p in box]
            ys = [p[1] for p in box]
            cx, cy = sum(xs) / 4, sum(ys) / 4
            items.append({
                "text": text,
                "confidence": round(float(conf), 3),
                "x": round(cx, 1),
                "y": round(cy, 1),
                "xRatio": round(cx / w, 4),
                "yRatio": round(cy / h, 4),
            })

        # 학기 라벨 탐지
        term_labels = {}
        for it in items:
            m = re.fullmatch(r"([1-4])\s*[-−–]?\s*([12])", it["text"].replace(" ", ""))
            if m:
                term_labels[f"{m.group(1)}-{m.group(2)}"] = it["xRatio"]

        def guess_term(xr: float) -> str | None:
            if not term_labels:
                # 균등 8열 가정 (좌측 여백 고려해 0.05~0.95)
                col = min(7, max(0, int((xr - 0.05) / 0.9 * 8)))
                y, s = divmod(col, 2)
                return f"{y + 1}-{s + 1}"
            best, best_d = None, 1e9
            for term, tx in term_labels.items():
                d = abs(tx - xr)
                if d < best_d:
                    best, best_d = term, d
            return best

        course_like = []
        for it in items:
            t = it["text"]
            # 학점 표기 (3-1) 등 포함 과목 후보
            if len(t) < 2:
                continue
            if re.fullmatch(r"[1-4]\s*[-−–]?\s*[12]", t.replace(" ", "")):
                continue
            if t in ("전문교양", "기초과학", "BSM", "MSC", "전공", "교양", "필수", "선택"):
                continue
            it2 = dict(it)
            it2["estimatedTerm"] = guess_term(it["xRatio"])
            course_like.append(it2)

        result["courses"] = course_like
        result["termLabelPositions"] = term_labels

        # 선수과목 휴리스틱: 같은 행(y)에서 왼쪽→오른쪽 인접 과목을 추천 선수(점선 수준)
        # 신뢰도 낮음 → recommended 로만 표시
        by_row: dict[int, list] = defaultdict(list)
        for c in course_like:
            if re.search(r"[\(（].*\d", c["text"]) or len(c["text"]) >= 4:
                by_row[int(c["y"] // max(h / 20, 1))].append(c)
        prereqs = []
        for row_items in by_row.values():
            row_items = sorted(row_items, key=lambda x: x["x"])
            for i in range(len(row_items) - 1):
                a, b = row_items[i], row_items[i + 1]
                if b["x"] - a["x"] < w * 0.25:
                    prereqs.append({
                        "from": a["text"],
                        "to": b["text"],
                        "type": "RECOMMENDED_OCR_HEURISTIC",
                        "needsReview": True,
                    })
        result["prerequisites"] = prereqs

        # 임시 파일 삭제 (다운로드 영구 보관 안 함)
        try:
            tmp_path.unlink(missing_ok=True)
        except Exception:
            pass
    except Exception as e:
        result["error"] = str(e)
    return result


def scrape_page(dept: dict, year: int, href: str, do_ocr: bool) -> dict[str, Any]:
    url = f"{BASE}/abeek/{href}"
    print(f"  → {dept['code']} {year}: {url}")
    html = fetch_html(url)
    if not html:
        return {}
    soup = BeautifulSoup(html, "lxml")
    req = parse_requirement_table(soup)
    courses = parse_curriculum_table(soup)
    apply_design_levels(courses, req)
    img = find_flowchart_image(soup)
    img_ref = None
    if img:
        img_ref = "data:embedded" if img.startswith("data:") else img
    payload = {
        "departmentCode": dept["code"],
        "departmentName": dept["name"],
        "year": year,
        "sourceUrl": url,
        "requirement": req,
        "courses": courses,
        "flowchartImageUrl": img_ref,
        "flowchartOcr": None,
        "needsReview": True,
        "scrapedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
    }
    if do_ocr and img:
        print(f"    OCR: {'embedded-base64' if img.startswith('data:') else img}")
        payload["flowchartOcr"] = ocr_flowchart(img, dept["code"], year)
        # 교과과정 테이블이 비어 있으면 OCR 텍스트로 초안 과목 채움
        if not courses and payload["flowchartOcr"]:
            payload["courses"] = synthesize_courses_from_ocr(payload["flowchartOcr"])
            apply_design_levels(payload["courses"], req)
    return payload


def synthesize_courses_from_ocr(ocr: dict) -> list[dict[str, Any]]:
    """OCR 박스에서 '(학점' 또는 '(N-M)' 패턴이 있는 항목을 과목 초안으로 승격."""
    out = []
    for item in ocr.get("courses") or []:
        text = item.get("text") or ""
        # 예: 자료구조및실습(3-0) / 공학설계기초(3-3)
        m = re.search(r"^(.+?)\s*[\(（]\s*([\d.]+)\s*[-−–]\s*([\d.]+)\s*[\)）]", text)
        if not m:
            m2 = re.search(r"^(.+?)\s*[\(（]\s*([\d.]+)\s*[\)）]", text)
            if not m2:
                continue
            name, credits, design = m2.group(1).strip(), float(m2.group(2)), 0.0
        else:
            name, credits, design = m.group(1).strip(), float(m.group(2)), float(m.group(3))
        if len(name) < 2:
            continue
        term = item.get("estimatedTerm")
        grade, sem = None, None
        if term and re.match(r"[1-4]-[12]", term):
            grade, sem = map(int, term.split("-"))
        out.append({
            "name": name,
            "roleLabel": "OCR",
            "role": "ELECTIVE",
            "category": "MAJOR",
            "credits": credits,
            "designCredits": design,
            "designLevel": "ELEMENT" if design > 0 else "NONE",
            "recommendedTerm": term,
            "gradeYear": grade,
            "semester": sem,
            "electiveArea": "NONE",
            "fromOcr": True,
        })
    return out


def save_json(data: dict) -> Path:
    dept = data["departmentCode"]
    year = data["year"]
    d = OUT_DIR / dept
    d.mkdir(parents=True, exist_ok=True)
    path = d / f"{year}.json"
    if path.exists():
        try:
            old = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            old = {}
        # OCR·기존 교과 보존 (HTML 재수집 시 덮어쓰지 않음)
        if not data.get("flowchartOcr") and old.get("flowchartOcr"):
            data["flowchartOcr"] = old["flowchartOcr"]
        if not data.get("courses") and old.get("courses"):
            data["courses"] = old["courses"]
            data["coursesSource"] = old.get("coursesSource", data.get("coursesSource"))
        if not data.get("flowchartImageUrl") and old.get("flowchartImageUrl"):
            data["flowchartImageUrl"] = old["flowchartImageUrl"]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--html-only", action="store_true")
    ap.add_argument("--ocr-only", action="store_true")
    ap.add_argument("--dept", action="append", help="학과 코드 (예: MECH). 반복 가능")
    ap.add_argument("--year", type=int, action="append")
    args = ap.parse_args()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    targets = DEPARTMENTS
    if args.dept:
        codes = {c.upper() for c in args.dept}
        targets = [d for d in DEPARTMENTS if d["code"] in codes]

    do_ocr = not args.html_only
    summary = []

    for dept in targets:
        print(f"\n=== {dept['name']} ({dept['code']}) ===")
        if args.ocr_only:
            for path in sorted((OUT_DIR / dept["code"]).glob("*.json")) if (OUT_DIR / dept["code"]).exists() else []:
                data = json.loads(path.read_text(encoding="utf-8"))
                if args.year and data["year"] not in args.year:
                    continue
                img = data.get("flowchartImageUrl")
                if img:
                    print(f"  OCR-only {data['year']}")
                    data["flowchartOcr"] = ocr_flowchart(img, dept["code"], data["year"])
                    if not data.get("courses") and data.get("flowchartOcr"):
                        data["courses"] = synthesize_courses_from_ocr(data["flowchartOcr"])
                        apply_design_levels(data["courses"], data.get("requirement") or {})
                    save_json(data)
            continue

        years = discover_year_pages(dept["prefix"])
        if not years:
            print("  (연도 페이지 없음)")
            summary.append({"dept": dept["code"], "years": []})
            continue
        if args.year:
            years = {y: h for y, h in years.items() if y in args.year}

        done = []
        for year in sorted(years):
            data = scrape_page(dept, year, years[year], do_ocr=do_ocr)
            if data:
                p = save_json(data)
                print(f"    saved {p.name} courses={len(data.get('courses', []))} req_rows={len(data.get('requirement', {}).get('rows', []))}")
                done.append(year)
            time.sleep(0.3)
        summary.append({"dept": dept["code"], "years": done})

    summary_path = OUT_DIR / "_summary.json"
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nSummary → {summary_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
