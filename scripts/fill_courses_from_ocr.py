"""OCR 박스가 과목명/학점으로 쪼개진 경우도 최대한 과목 초안 생성."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "abeek-data"
hangul = re.compile(r"[가-힣A-Za-z]")

for p in sorted(root.glob("*/*.json")):
    if p.name.startswith("_"):
        continue
    data = json.loads(p.read_text(encoding="utf-8"))
    if data.get("courses"):
        continue
    ocr = data.get("flowchartOcr") or {}
    items = list(ocr.get("courses") or [])
    if not items:
        continue

    filled = []
    # 1) 한 박스에 학점 포함
    for it in items:
        text = (it.get("text") or "").strip()
        m = re.search(r"^(.+?)\s*[\(（]\s*([\d.]+)\s*[-−–]\s*([\d.]+)", text)
        if m:
            name = m.group(1).strip()
            credits, design = float(m.group(2)), float(m.group(3))
        else:
            m2 = re.search(r"^(.+?)\s*[\(（]\s*([\d.]+)\s*[\)）]?", text)
            if not m2 or not hangul.search(m2.group(1)):
                continue
            name, credits, design = m2.group(1).strip(), float(m2.group(2)), 0.0
        if len(name) < 2:
            continue
        term = it.get("estimatedTerm")
        gy = sm = None
        if term and re.match(r"[1-4]-[12]", str(term)):
            gy, sm = map(int, term.split("-"))
        filled.append({
            "name": name,
            "roleLabel": "OCR",
            "role": "ELECTIVE",
            "category": "MAJOR",
            "credits": credits,
            "designCredits": design,
            "designLevel": "ELEMENT" if design > 0 else "NONE",
            "recommendedTerm": term,
            "gradeYear": gy,
            "semester": sm,
            "electiveArea": "NONE",
            "fromOcr": True,
        })

    # 2) 학점 패턴 없으면 한글 과목명 후보만 (학점 3 가정)
    if not filled:
        for it in items:
            text = (it.get("text") or "").strip()
            if len(text) < 3 or not hangul.search(text):
                continue
            if re.fullmatch(r"[1-4]\s*[-−]?\s*[12]", text.replace(" ", "")):
                continue
            if text in ("전문교양", "전공", "교양", "필수", "선택", "BSM", "MSC", "기초과학"):
                continue
            if re.fullmatch(r"[\d.]+", text):
                continue
            term = it.get("estimatedTerm")
            gy = sm = None
            if term and re.match(r"[1-4]-[12]", str(term)):
                gy, sm = map(int, term.split("-"))
            filled.append({
                "name": text,
                "roleLabel": "OCR",
                "role": "ELECTIVE",
                "category": "MAJOR",
                "credits": 3.0,
                "designCredits": 0.0,
                "designLevel": "NONE",
                "recommendedTerm": term,
                "gradeYear": gy,
                "semester": sm,
                "electiveArea": "NONE",
                "fromOcr": True,
                "creditsAssumed": True,
            })

    if not filled:
        print(f"still empty {p.parent.name}/{p.name}")
        continue
    data["courses"] = filled
    data["coursesSource"] = "OCR_SYNTH"
    data["needsReview"] = True
    p.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"filled {p.parent.name}/{p.name}: {len(filled)}")
