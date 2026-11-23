"""Credit guard: one ledger of Google Flow credits (kids videos, naat videos, autopilot), a daily limit, and a check before
any Flow job starts. Balance comes from studio.credits() (Flow's own number, read by Claude after each run)."""
import datetime
import json
import os
import re
import threading
import time
from pathlib import Path

import studio

CFG = Path(__file__).parent / "credit_guard.json"
from config import SONGS as NAAT


def settings():
    d = {"daily_limit": 120, "renew_day": 1, "low_alert": 100, "monthly": 1000, "extra": {}}
    try:
        d.update(json.load(open(CFG)))
    except (OSError, ValueError):
        pass
    return d


def save(daily_limit=None, renew_day=None, low_alert=None):
    s = settings()
    if daily_limit is not None: s["daily_limit"] = max(0, min(1000, int(daily_limit)))
    if renew_day is not None: s["renew_day"] = max(1, min(28, int(renew_day)))
    if low_alert is not None: s["low_alert"] = max(0, int(low_alert))
    json.dump(s, open(CFG, "w"), indent=1)
    return summary()


def allow_extra(amount):
    """One-time raise of today's limit (gone tomorrow)."""
    s = settings()
    s["extra"] = {"date": datetime.date.today().isoformat(), "amount": max(0, min(1000, int(amount)))}
    json.dump(s, open(CFG, "w"), indent=1)
    return summary()


def events():
    """Every recorded spend: {date, what, credits}."""
    out = []
    try:
        for j in json.load(open(studio.DATA / "flow_queue.json")):
            m = re.search(r"(\d+)\s*credits", j.get("note") or "")
            if m and j.get("finished"):
                out.append({"date": j["finished"][:10], "what": j.get("title", ""), "credits": int(m.group(1)), "kind": "kids"})
    except (OSError, ValueError):
        pass
    if NAAT.exists():
        for d in NAAT.iterdir():
            try:
                st = json.load(open(d / "status.json"))
            except (OSError, ValueError):
                continue
            if st.get("credits_used"):
                when = datetime.date.fromtimestamp((d / "status.json").stat().st_mtime).isoformat()
                try:
                    title = json.load(open(d / "brief.json")).get("title", d.name)
                except (OSError, ValueError):
                    title = d.name
                out.append({"date": when, "what": title, "credits": int(st["credits_used"]), "kind": "naat"})
    return sorted(out, key=lambda e: e["date"], reverse=True)


def _running_estimate(job):
    """Credits the jobs running right now have spent so far (clips made x 10)."""
    n = 0
    try:
        p = studio.flow_progress(job)
        if p.get("running"):
            n += p.get("credits_used", 0)
    except Exception:
        pass
    if NAAT.exists():
        for d in NAAT.iterdir():
            try:
                st = json.load(open(d / "status.json"))
                if st.get("stage") == "making video":
                    pj = json.load(open(d / "progress.json")) if (d / "progress.json").exists() else {}
                    n += int(pj.get("credits_used") or 0)
            except (OSError, ValueError):
                continue
    return n


def summary(job=None):
    s = settings()
    today = datetime.date.today()
    ev = events()
    spent_today = sum(e["credits"] for e in ev if e["date"] == today.isoformat())
    running = _running_estimate(job) if job else 0
    extra = s["extra"].get("amount", 0) if s["extra"].get("date") == today.isoformat() else 0
    limit = s["daily_limit"] + extra
    # month = since the last renew day
    start = today.replace(day=s["renew_day"]) if today.day >= s["renew_day"] else \
        (today.replace(day=1) - datetime.timedelta(days=1)).replace(day=s["renew_day"])
    nxt = (start + datetime.timedelta(days=32)).replace(day=s["renew_day"])
    month_spent = sum(e["credits"] for e in ev if e["date"] >= start.isoformat())
    bal = studio.credits()
    left = bal.get("left")
    days = max(1, (nxt - today).days)
    return {"settings": s, "today_spent": spent_today, "running": running, "today_limit": limit, "extra_today": extra,
            "today_left": max(0, limit - spent_today - running), "month_spent": month_spent, "balance": left,
            "balance_checked": bal.get("checked", ""), "renews": nxt.isoformat(), "days_to_renew": days,
            "per_day": (left // days) if left is not None else None, "videos_left": (left // 60) if left is not None else None,
            "events": ev[:30]}


def check(need, job=None):
    """Before a Flow job: is `need` credits allowed today? Returns (ok, message, allowed_amount)."""
    sm = summary(job)
    if sm["balance"] is not None and sm["balance"] < 50:
        return False, f"Credit guard: Flow has only {sm['balance']} credits left. Wait for the renewal on {sm['renews']}.", 0
    if sm["today_left"] <= 0:
        return False, (f"Credit guard: today's limit is used up ({sm['today_spent']}/{sm['today_limit']} credits). "
                       "Raise today's limit in Studio, or it runs tomorrow."), 0
    if need > sm["today_left"]:
        return False, (f"Credit guard: this needs about {need} credits but only {sm['today_left']} are left today "
                       f"(limit {sm['today_limit']}). Raise today's limit in Studio, or pick a smaller budget."), sm["today_left"]
    return True, "", need


def start_alerts(notify, job):
    def loop():
        sent = {}
        while True:
            try:
                sm = summary(job)
                d = datetime.date.today().isoformat()
                if sm["balance"] is not None and sm["balance"] < sm["settings"]["low_alert"] and sent.get("low") != d:
                    notify.push("system", "💳 Flow credits are low", f"{sm['balance']} left until {sm['renews']} (about {sm['videos_left']} videos).", "Studio")
                    sent["low"] = d
                if sm["today_spent"] + sm["running"] > sm["today_limit"] and sent.get("over") != d:
                    notify.push("system", "💳 Over today's credit limit", f"{sm['today_spent'] + sm['running']} of {sm['today_limit']} credits used today.", "Studio")
                    sent["over"] = d
            except Exception:
                pass
            time.sleep(600)
    threading.Thread(target=loop, daemon=True).start()
