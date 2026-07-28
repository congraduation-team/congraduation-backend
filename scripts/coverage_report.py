import json
from pathlib import Path

root = Path("src/main/resources/abeek-data")
want = set(range(2020, 2027))
rows = []
for d in sorted(p for p in root.iterdir() if p.is_dir()):
    years = {}
    for p in sorted(d.glob("*.json")):
        j = json.loads(p.read_text(encoding="utf-8"))
        y = j.get("year") or int(p.stem)
        req = j.get("requirement") or {}
        years[y] = {
            "reqRows": len(req.get("rows") or []),
            "general": req.get("generalMinCredits"),
            "bsm": req.get("bsmMinCredits"),
            "major": req.get("majorMinCredits"),
            "design": req.get("designMinCredits"),
            "courses": len(j.get("courses") or []),
        }
    have = set(years) & want
    missing = sorted(want - set(years.keys()))
    no_req = [y for y in sorted(have) if years[y]["reqRows"] == 0]
    if have == want and not no_req:
        status = "완전"
    elif have:
        status = "부분"
    else:
        status = "없음"
    print(f"{d.name:10} {status:4} 보유={sorted(have)} 결측연도={missing} 요건표없음={no_req}")
    rows.append({"code": d.name, "status": status, "have": sorted(have), "missing": missing, "no_req": no_req})

print("\nCSE: CurriculumDataLoader 하드코딩으로 2020-2026 있음 (JSON 폴더 없음)")
print("사이트 미제공(스크래퍼 당시): GEO, ICE, DCON / AI 메뉴 연도목록 없음")
