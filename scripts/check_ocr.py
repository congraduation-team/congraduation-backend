import json
from pathlib import Path

root = Path("src/main/resources/abeek-data")
for p in sorted(root.glob("*/*.json")):
    if p.name.startswith("_"):
        continue
    d = json.loads(p.read_text(encoding="utf-8"))
    o = d.get("flowchartOcr")
    if o and isinstance(o, dict) and (o.get("courses") or o.get("error")):
        n = len(o.get("courses") or [])
        pr = len(o.get("prerequisites") or [])
        err = (o.get("error") or "")[:80]
        print(f"{p.parent.name}/{p.name}: ocr={n} prereq={pr} err={err}")
