import json
from pathlib import Path

root = Path("src/main/resources/abeek-data")
want = set(range(2020, 2027))
print("=== 학과별 이수체계도/교과 데이터 현황 ===\n")

for d in sorted(p for p in root.iterdir() if p.is_dir()):
    files = {int(p.stem): p for p in d.glob("*.json") if p.stem.isdigit()}
    missing_years = sorted(want - set(files))
    lines = []
    for y in sorted(want):
        if y not in files:
            continue
        j = json.loads(files[y].read_text(encoding="utf-8"))
        courses = j.get("courses") or []
        ocr = j.get("flowchartOcr") or {}
        ocr_n = len(ocr.get("courses") or []) if isinstance(ocr, dict) else 0
        ocr_err = (ocr.get("error") or "")[:40] if isinstance(ocr, dict) else ""
        html_n = sum(1 for c in courses if not c.get("fromOcr"))
        ocr_course_n = sum(1 for c in courses if c.get("fromOcr"))
        img = "Y" if j.get("flowchartImageUrl") else "N"
        flag = []
        if ocr_n == 0:
            flag.append("OCR없음")
        if ocr_err:
            flag.append("OCR에러")
        if len(courses) == 0:
            flag.append("교과0")
        lines.append(
            f"  {y}: 교과{len(courses)}(HTML{html_n}/OCR승격{ocr_course_n}) "
            f"img={img} ocr박스={ocr_n} {' '.join(flag)}"
        )
    status = "완전" if not missing_years else f"부분(결측{missing_years})"
    print(f"{d.name} [{status}]")
    print("\n".join(lines) if lines else "  (파일 없음)")
    print()

print("CSE: CurriculumDataLoader 하드코딩 (abeek-data JSON 아님)")
print("사이트 미제공: GEO, ICE, DCON")
print()
print("참고: OCR 선수과목(prerequisites)은 휴리스틱 초안이라 needsReview=true 상태로 검수 필요")
