"""The owner's own video rules, made from the app: describe what looked bad, Claude drafts rules, the owner adds them.
Every video job (kids and naat) is told to read OWNER_RULES.md and follow it above everything else."""
import json, subprocess, time, uuid
from pathlib import Path

from config import KF, KFS
STORE = KF / "owner_rules.json"
MD = KF / "OWNER_RULES.md"
CLAUDE = str(Path.home() / ".local/bin/claude")
NOTE = ("OWNER RULES: before planning or prompting, read " + KFS + "/OWNER_RULES.md (if it exists) and follow every "
        "rule in it in every image and video prompt and in QC; these rules win over all other files. ")


def _load():
    try:
        return json.loads(STORE.read_text())
    except (OSError, ValueError):
        return []


def _save(rules):
    STORE.write_text(json.dumps(rules, ensure_ascii=False, indent=1))
    lines = ["# Owner's video rules (made in the phone app; follow them, they win over every other file; [all] = every video, [kids] = kids videos only, [naat] = naat/song videos only)", ""]
    for r in rules:
        if r.get("on", True):
            lines.append(f"- [{r['for']}] {r['text']}")
    MD.write_text("\n".join(lines) + "\n")


def listing():
    return {"rules": _load()}


def draft(problem, kind="all"):
    """Claude turns the owner's complaint (any language) into short, concrete prompt/QC rules. Nothing is saved yet."""
    problem = (problem or "").strip()[:2000]
    if not problem:
        raise ValueError("Tell me what looked wrong")
    have = "\n".join("- " + r["text"] for r in _load()) or "(none)"
    bible = (KF / "PROMPT_BIBLE.md").read_text()[:6000] if (KF / "PROMPT_BIBLE.md").exists() else ""
    prompt = (f"You help make kids' YouTube videos with Google Flow (Veo). The owner says (may be Urdu/Hindi/English): \"{problem}\".\n"
              f"Write 2 to 6 short, concrete rules in English that fix this in future videos: exact words to add to image/video "
              f"prompts, negatives, or QC checks. No vague advice. Don't repeat rules that already exist.\n"
              f"Existing owner rules:\n{have}\nCurrent prompt bible (for context):\n{bible}\n"
              'Reply with ONLY JSON: {"rules": ["...", "..."], "summary": "one line in simple Roman Urdu of what these fix"}')
    r = subprocess.run([CLAUDE, "-p", prompt, "--output-format", "text"], capture_output=True, text=True, timeout=110, cwd=str(KF))
    out = r.stdout.strip()
    try:
        d = json.loads(out[out.index("{"):out.rindex("}") + 1])
    except ValueError:
        raise RuntimeError("Claude didn't give rules, try again")
    return {"rules": [str(x)[:400] for x in d.get("rules", [])][:6], "summary": str(d.get("summary", ""))[:300], "for": kind}


def add(texts, kind="all"):
    rules = _load()
    for t in texts:
        t = str(t).strip()[:400]
        if t:
            rules.append({"id": uuid.uuid4().hex[:8], "text": t, "for": kind if kind in ("all", "kids", "naat") else "all",
                          "on": True, "date": time.strftime("%Y-%m-%d")})
    _save(rules)
    return {"rules": rules}


def change(rid, on=None, delete=False, text=None):
    rules = _load()
    for r in rules:
        if r["id"] == rid:
            if on is not None: r["on"] = bool(on)
            if text: r["text"] = str(text).strip()[:400]
    if delete:
        rules = [r for r in rules if r["id"] != rid]
    _save(rules)
    return {"rules": rules}
