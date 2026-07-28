import json
from pathlib import Path

root = Path("src/main/resources/abeek-data")
checks = [
    ("AERO", 2026), ("CIVIL", 2026), ("AIROBOT", 2026),
    ("AI", 2021), ("AI", 2022), ("AI", 2023),
]
print("=== 재처리 결과 ===")
for code, year in checks:
    p = root / code / f"{year}.json"
    j = json.loads(p.read_text(encoding="utf-8"))
    ocr = j.get("flowchartOcr") or {}
    print(
        f"{code} {year}: courses={len(j.get('courses') or [])} "
        f"ocr={len(ocr.get('courses') or [])} "
        f"prereq={len(ocr.get('prerequisites') or [])} "
        f"img={j.get('flowchartImageUrl')} "
        f"err={ocr.get('error')}"
    )

print("\n=== 사이트에 없어 불가 ===")
print("EE 2022-2026, AI 2020/2024-2026, GEO/ICE/DCON 2020-2026: 공식 페이지 없음")
