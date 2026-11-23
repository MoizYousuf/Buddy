"""App assistant: the owner tells the APP what to do ("ye bill add karo, pocket money 5000, ye masla theek karo").
Claude or Gemini turns it into actions the phone runs (money entries, ideas, open a screen). A "fix the app" request
starts a Claude job on the Android app's code (only Claude can write code; Gemini can't run tools headless).
"""
import json, subprocess, time
from pathlib import Path

import money

AGY = Path.home() / ".local" / "bin" / "agy"
CLAUDE = str(Path.home() / ".local" / "bin" / "claude")
APP = Path(__file__).resolve().parent.parent / "android"
SCREENS = ["Home", "Money", "Ideas", "Stores", "Videos", "Studio", "Naat", "Cv", "Settings", "Progress", "Alerts", "Routines",
           "Transfer", "Rules", "Mac", "Assistant", "Remote", "Cleaner"]

PROMPT = """You are the assistant INSIDE the owner's phone app (money book, ideas, Etsy Store Studio, YouTube/naat studio,
English practice). They speak Roman Urdu / English. Today is {today}.
Turn the message into actions. Reply with JSON only:
{{"say": "one short Roman Urdu line", "actions": [
  {{"type": "money", "kind": "spend|pocket|received|income", "amount": number, "note": "", "category": one of {cats},
    "whose": "home|me", "date": "YYYY-MM-DD", "person": "Ghar"}},
  {{"type": "idea", "text": "..."}},
  {{"type": "open", "screen": one of {screens}}},
  {{"type": "fix", "request": "what is broken or what to change in the app, in clear English"}}
]}}
Money rules: pocket money from home = pocket; home/family/hospital/medicine/household costs they paid = spend + whose home;
their own food/clothes/fun = spend + whose me; home paying them back = received; earnings = income.
Use "fix" only when they ask to fix/change/add something in the app itself.
Message: "{text}"
Don't run any commands."""


def _ai(p, engine):
    out = ""
    if engine == "gemini" and AGY.exists():
        try:
            out = subprocess.run([str(AGY), "-p", p, "--print-timeout", "60s"], capture_output=True, text=True, timeout=80).stdout
        except subprocess.TimeoutExpired:
            pass
    if "{" not in out:
        out = subprocess.run([CLAUDE, "-p", p, "--output-format", "text", "--model", "haiku"], capture_output=True, text=True, timeout=90).stdout
    return json.loads(out[out.index("{"):out.rindex("}") + 1])


def assist(text, engine, start_job, push):
    import datetime as _dt
    p = PROMPT.format(today=str(_dt.date.today()), cats=json.dumps(money.CATS), screens=json.dumps(SCREENS), text=str(text).replace('"', "'")[:1500])
    j = _ai(p, engine)
    acts = j.get("actions", [])[:30]
    for a in acts:
        if a.get("type") == "fix":
            if engine == "gemini":
                a["note"] = "Gemini code nahi likh sakta, ye kaam Claude karega."
            prompt = (f"The owner reported this about their Android app (Kotlin/Compose, in {APP}): \"{a.get('request')}\". "
                      "Find the cause, fix it with a small careful change, build with `./gradlew assembleDebug -q`, then copy "
                      "app/build/outputs/apk/debug/app-debug.apk to ~/Downloads/Buddy-latest.apk. Don't touch " + str(APP.parent / "server") + ". "
                      "Reply in short Roman Urdu: what was wrong, what you changed, and that the new APK is in Downloads.")
            a["job_id"] = start_job(prompt, APP)
    return {"say": j.get("say", ""), "actions": acts, "engine": engine}
