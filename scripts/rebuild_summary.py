import json
from pathlib import Path

root = Path("src/main/resources/abeek-data")
summary = []
for d in sorted(p for p in root.iterdir() if p.is_dir()):
    years = []
    for p in sorted(d.glob("*.json")):
        j = json.loads(p.read_text(encoding="utf-8"))
        ocr = j.get("flowchartOcr") or {}
        years.append({
            "year": j.get("year"),
            "courses": len(j.get("courses") or []),
            "requirementRows": len((j.get("requirement") or {}).get("rows") or []),
            "ocrBoxes": len(ocr.get("courses") or []),
            "ocrPrereqs": len(ocr.get("prerequisites") or []),
            "ocrError": ocr.get("error"),
            "needsReview": j.get("needsReview", True),
            "sourceUrl": j.get("sourceUrl"),
        })
    summary.append({
        "departmentCode": d.name,
        "years": years,
    })

out = root / "_summary.json"
out.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"wrote {out} depts={len(summary)}")
for s in summary:
    ys = ", ".join(str(y["year"]) for y in s["years"])
    ocr_ok = sum(1 for y in s["years"] if y["ocrBoxes"] > 0)
    print(f"  {s['departmentCode']}: {ys} (ocr {ocr_ok}/{len(s['years'])})")
