"""Backup of the phone's practice progress (streak, XP, mistakes). The phone sends its progress.json after changes;
the Mac keeps the latest copy plus one per day for 30 days (all inside the server folder, never committed)."""
import json
import time
from pathlib import Path

DIR = Path(__file__).parent / "practice"


def save(body):
    data = json.loads(body)
    if not isinstance(data, dict) or "days" not in data:
        raise ValueError("not a progress file")
    DIR.mkdir(exist_ok=True)
    (DIR / "progress.json").write_text(json.dumps(data, ensure_ascii=False))
    (DIR / f"progress-{time.strftime('%Y-%m-%d')}.json").write_text(json.dumps(data, ensure_ascii=False))
    for old in sorted(DIR.glob("progress-*.json"))[:-30]:
        old.unlink()
    return {"ok": True, "saved": time.strftime("%Y-%m-%d %H:%M"), "answers": sum(data.get("days", {}).values()),
            "mistakes": len(data.get("mistakes", []))}


def latest():
    f = DIR / "progress.json"
    if not f.exists():
        return {"none": True}
    return {"progress": json.loads(f.read_text()), "saved": time.strftime("%Y-%m-%d %H:%M", time.localtime(f.stat().st_mtime))}
