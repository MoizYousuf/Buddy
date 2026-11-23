"""Money book: the owner's spending, and how much home (ghar) owes them.

Every entry is one spend or one 'received from home'. A spend has `whose`: "home" (a home expense the owner paid from
their own pocket, so home owes it back) or "me" (personal). Receipt / bill photos are read by Gemini (Antigravity),
Claude as fallback, to fill the total. Data stays on this Mac: server/money.json + server/money_photos/.
"""
import base64, json, re, subprocess, threading, time, uuid
from pathlib import Path

HERE = Path(__file__).parent
DB = HERE / "money.json"
PHOTOS = HERE / "money_photos"
AGY = Path.home() / ".local" / "bin" / "agy"
CLAUDE = str(Path.home() / ".local" / "bin" / "claude")
CATS = ["Hospital", "Doctor / Medicine", "Clothes", "Personal / Fun", "Grocery", "Bills", "Fuel / Transport", "Food", "School", "Shopping", "Repair", "Other"]
_lock = threading.Lock()


def _load():
    try:
        return json.loads(DB.read_text())
    except (OSError, ValueError):
        return {"entries": [], "budgets": {}}


def _save(d):
    tmp = DB.with_suffix(".tmp")
    tmp.write_text(json.dumps(d, ensure_ascii=False, indent=1))
    tmp.replace(DB)


def _num(x):
    try:
        return round(float(str(x).replace(",", "").strip() or 0), 2)
    except ValueError:
        raise ValueError("Amount must be a number")


# ---------- reading a bill photo ----------

ASK = ("Look at the image file {f}. It is a receipt, bill, invoice or a photo of a payment (it may be in Urdu or English). "
       "Reply with JSON only, nothing else: {{\"total\": number (the final amount paid, PKR unless another currency is shown), "
       "\"currency\": \"PKR\", \"shop\": \"\", \"date\": \"YYYY-MM-DD or empty\", \"items\": [{{\"name\": \"\", \"amount\": number}}], "
       "\"category\": one of " + json.dumps(CATS) + ", \"note\": \"one short line of what this was\", "
       "\"whose\": \"home\" for hospital, doctor, medicine, household or family needs, \"me\" for the owner's own clothes, "
       "fun, eating out, gadgets or personal things}}. "
       "If it is not a bill, total 0. Don't run any commands.")


def _read(f):
    p = ASK.format(f=f)
    outs = []
    if AGY.exists():
        try:
            r = subprocess.run([str(AGY), "-p", p, "--print-timeout", "120s"], capture_output=True, text=True, timeout=150, cwd=str(PHOTOS))
            outs.append((r.stdout, "gemini"))
        except subprocess.TimeoutExpired:
            pass
    for out, by in outs:
        if "{" in out:
            break
    else:
        r = subprocess.run([CLAUDE, "-p", p, "--output-format", "text", "--model", "sonnet", "--allowedTools", "Read"],
                           capture_output=True, text=True, timeout=150, cwd=str(PHOTOS))
        out, by = r.stdout, "claude"
    try:
        j = json.loads(out[out.index("{"):out.rindex("}") + 1])
    except ValueError:
        raise ValueError("Couldn't read this photo, type the amount yourself")
    j["total"] = _num(j.get("total", 0))
    j["by"] = by
    return j


def scan(image_b64):
    """Saves one photo and reads it. Returns {photo, total, items, note, category, ...}."""
    PHOTOS.mkdir(exist_ok=True)
    name = time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:6] + ".jpg"
    raw = base64.b64decode(image_b64)
    if len(raw) > 15_000_000:
        raise ValueError("Photo too big")
    (PHOTOS / name).write_bytes(raw)
    j = _read(PHOTOS / name)
    j["photo"] = name
    return j


def photo_path(name):
    f = PHOTOS / Path(name).name
    return f if f.exists() and f.parent == PHOTOS else None


# ---------- book ----------

def _date(v):
    """'2026-11-10', 'aaj', 'kal', 'parsu', '10 nov'... → YYYY-MM-DD (past days; unknown → today)."""
    import datetime as _dt, re as _re
    t = str(v or "").strip().lower()
    today = _dt.date.today()
    if _re.fullmatch(r"\d{4}-\d{2}-\d{2}", t):
        return t
    words = {"": 0, "aaj": 0, "today": 0, "kal": 1, "yesterday": 1, "parsu": 2, "parson": 2, "parso": 2, "narsu": 3}
    if t in words:
        return str(today - _dt.timedelta(days=words[t]))
    m = _re.search(r"(\d+)\s*din", t)
    if m:
        return str(today - _dt.timedelta(days=int(m.group(1))))
    m = _re.fullmatch(r"(\d{1,2})[-/ ](\d{1,2}|[a-z]+)", t)
    if m:
        mon = {"jan": 1, "feb": 2, "mar": 3, "apr": 4, "may": 5, "jun": 6, "jul": 7, "aug": 8, "sep": 9, "oct": 10, "nov": 11, "dec": 12}
        mo = int(m.group(2)) if m.group(2).isdigit() else mon.get(m.group(2)[:3], today.month)
        try:
            return str(_dt.date(today.year, mo, int(m.group(1))))
        except ValueError:
            pass
    return str(today)


def add(d):
    """d: kind spend|received, amount, note, category, whose home|me, date, photos[]."""
    kind = d.get("kind", "spend")
    if kind not in ("spend", "received", "pocket", "goal", "income"):
        raise ValueError("kind must be spend, received or pocket")
    amt = _num(d.get("amount"))
    if amt <= 0:
        raise ValueError("Amount must be more than 0")
    e = {"id": uuid.uuid4().hex[:10], "kind": kind, "amount": amt, "note": str(d.get("note", ""))[:300],
         "goal": d.get("goal"), "claimed": d.get("claimed"), "source": d.get("source"), "store": d.get("store"),
         "category": {"received": "Ghar se wapas mila", "pocket": "Pocket money", "goal": "Bachat",
                      "income": "Kamai: " + str(d.get("source") or "Other")}.get(kind) or d.get("category") or "Other",
         "whose": "home" if kind != "spend" else (d.get("whose") if d.get("whose") in ("home", "me") else "home"),
         "person": str(d.get("person") or "Ghar")[:30], "recurring": d.get("recurring"),
         "date": _date(d.get("date")), "local_id": d.get("_tmp") or d.get("local_id"), "photos": [Path(p).name for p in d.get("photos", [])][:10],
         "added": int(time.time())}
    with _lock:
        b = _load()
        if d.get("id"):   # edit
            old = next((x for x in b["entries"] if x["id"] == d["id"]), None)
            if not old:
                raise ValueError("Entry not found")
            e["id"], e["added"] = old["id"], old["added"]
            b["entries"] = [e if x["id"] == old["id"] else x for x in b["entries"]]
        else:
            if e.get("local_id") and any(x.get("local_id") == e["local_id"] for x in b["entries"]):
                return listing()          # the phone sent the same offline entry again
            b["entries"].append(e)
        _save(b)
    L = listing(e["date"][:7])
    if kind == "spend":
        _budget_alert(b, L, e)
    return listing()


def _budget_alert(b, L, e):
    """Notification once per month and limit when a category (or the owner's own spending) goes over / near its budget."""
    try:
        import notify
    except ImportError:
        return
    sent = b.setdefault("alerts_sent", {})
    mt = L["month_total"]
    checks = [(e["category"], mt["by_category"].get(e["category"], 0))] + ([("Mera apna (total)", mt["me"])] if e["whose"] == "me" else [])
    for cat, used in checks:
        lim = L["budgets"].get(cat)
        if not lim:
            continue
        for lvl, txt in ((1.0, "had se upar"), (0.8, "80% ho gaya")):
            key = f"{L['month']}|{cat}|{lvl}"
            if used >= lim * lvl:
                if key not in sent:
                    sent[key] = int(time.time())
                    with _lock:
                        x = _load(); x.setdefault("alerts_sent", {})[key] = sent[key]; _save(x)
                    notify.push("money", f"💰 {cat}: budget {txt}", f"Is mahine PKR {used:,.0f} / PKR {lim:,.0f}", "Money")
                break


def delete(eid):
    with _lock:
        b = _load()
        b["entries"] = [x for x in b["entries"] if x["id"] != eid]
        _save(b)
    return listing()


def set_budget(cat, amount):
    with _lock:
        b = _load()
        b.setdefault("budgets", {})
        if _num(amount) > 0:
            b["budgets"][cat] = _num(amount)
        else:
            b["budgets"].pop(cat, None)
        _save(b)
    return listing()


def listing(month=""):
    """All entries (newest first), the home balance (all time) and totals for one month (YYYY-MM, default this month)."""
    b = _load()
    es = sorted(b["entries"], key=lambda x: (x["date"], x["added"]), reverse=True)
    home_spent = sum(x["amount"] for x in es if x["kind"] == "spend" and x["whose"] == "home")
    received = sum(x["amount"] for x in es if x["kind"] == "received")
    pocket = sum(x["amount"] for x in es if x["kind"] in ("pocket", "income"))
    spent_all = sum(x["amount"] for x in es if x["kind"] in ("spend", "goal"))
    month = month or time.strftime("%Y-%m")
    m = [x for x in es if x["date"].startswith(month)]
    cats = {}
    for x in m:
        if x["kind"] == "spend":
            cats[x["category"]] = round(cats.get(x["category"], 0) + x["amount"], 2)
    months = sorted({x["date"][:7] for x in es} | {time.strftime("%Y-%m")}, reverse=True)
    budgets = b.get("budgets", {})
    me_m = sum(x["amount"] for x in m if x["kind"] == "spend" and x["whose"] == "me")
    over = [c for c, v in budgets.items() if (me_m if c == "Mera apna (total)" else cats.get(c, 0)) > v]
    return {"entries": es, "categories": CATS, "budget_names": ["Mera apna (total)"] + CATS, "months": months, "month": month,
            "balance": {"home_spent": round(home_spent, 2), "received": round(received, 2),
                        "home_owes": round(home_spent - received, 2),
                        "pocket": round(pocket, 2), "wallet": round(pocket + received - spent_all, 2)},
            "month_total": {"spent": round(sum(x["amount"] for x in m if x["kind"] == "spend"), 2),
                            "home": round(sum(x["amount"] for x in m if x["kind"] == "spend" and x["whose"] == "home"), 2),
                            "me": round(sum(x["amount"] for x in m if x["kind"] == "spend" and x["whose"] == "me"), 2),
                            "received": round(sum(x["amount"] for x in m if x["kind"] == "received"), 2),
                            "pocket": round(sum(x["amount"] for x in m if x["kind"] == "pocket"), 2),
                            "income": round(sum(x["amount"] for x in m if x["kind"] == "income"), 2),
                            "saved": round(sum(x["amount"] for x in m if x["kind"] in ("pocket", "income", "received"))
                                           - sum(x["amount"] for x in m if x["kind"] == "spend"), 2),
                            "income_by_store": income_by(m, "store"), "income_by_source": income_by(m, "source"),
                            "by_category": cats},
            "budgets": budgets, "over_budget": over, "people": people_balances(es), "people_names": b.get("people", ["Ghar"]),
            "recurring": b.get("recurring", []), "due_bills": due_bills(b), "goals": goals(b, es), "forecast": forecast(m, budgets, month),
            "unclaimed": round(sum(x["amount"] for x in es if x["kind"] == "spend" and x["whose"] == "home" and not x.get("claimed")), 2),
            "remind": b.get("remind", True)}


def report_text(month=""):
    """A plain text summary to share with home (WhatsApp), only home items."""
    L = listing(month)
    rows = [x for x in L["entries"] if x["date"].startswith(L["month"]) and x["whose"] == "home" and x["kind"] != "pocket"]
    lines = [f"Ghar ka hisaab {L['month']}"]
    for x in reversed(rows):
        sign = "−" if x["kind"] == "received" else ""
        lines.append(f"{x['date'][5:]}  {sign}PKR {x['amount']:,.0f}  {x['note'] or x['category']}")
    lines.append(f"\nGhar ka kharcha (mere paise se): PKR {L['balance']['home_spent']:,.0f}")
    lines.append(f"Mila: PKR {L['balance']['received']:,.0f}")
    lines.append(f"Baqi lene hain: PKR {L['balance']['home_owes']:,.0f}")
    return {"text": "\n".join(lines)}


# ---------- people (Abu, Bhai, Ghar...) ----------
# Every home entry has `person` (default "Ghar"); each person has their own "lene hain" balance.

def people_balances(es):
    out = {}
    for x in es:
        if x["kind"] == "spend" and x["whose"] != "home":
            continue
        if x["kind"] == "pocket":
            continue
        p = x.get("person") or "Ghar"
        o = out.setdefault(p, {"spent": 0.0, "received": 0.0})
        o["spent" if x["kind"] == "spend" else "received"] += x["amount"]
    return {p: {"spent": round(v["spent"], 2), "received": round(v["received"], 2), "owes": round(v["spent"] - v["received"], 2)}
            for p, v in out.items()}


def set_people(names):
    with _lock:
        b = _load()
        b["people"] = [str(n).strip()[:30] for n in names if str(n).strip()][:10] or ["Ghar"]
        _save(b)
    return listing()


# ---------- recurring bills (bijli, gas, fees...) ----------

def set_recurring(items):
    """items: [{name, amount, category, whose, person, day (1-28)}]"""
    clean = []
    for i in items[:30]:
        clean.append({"id": i.get("id") or uuid.uuid4().hex[:8], "name": str(i.get("name", ""))[:60], "amount": _num(i.get("amount", 0)),
                      "category": i.get("category") or "Bills", "whose": i.get("whose") if i.get("whose") in ("home", "me") else "home",
                      "person": i.get("person") or "Ghar", "day": max(1, min(28, int(i.get("day") or 1)))})
    with _lock:
        b = _load(); b["recurring"] = clean; _save(b)
    return listing()


def due_bills(b=None):
    """Recurring bills of this month that are due (day reached) and not added yet."""
    b = b or _load()
    m = time.strftime("%Y-%m"); today = int(time.strftime("%d"))
    done = {x.get("recurring") for x in b["entries"] if x["date"].startswith(m)}
    return [r for r in b.get("recurring", []) if r["day"] <= today and r["id"] not in done]


def add_bill(rid, amount=None):
    b = _load()
    r = next((x for x in b.get("recurring", []) if x["id"] == rid), None)
    if not r:
        raise ValueError("Bill not found")
    return add({"kind": "spend", "amount": amount or r["amount"], "note": r["name"], "category": r["category"], "whose": r["whose"],
                "person": r["person"], "recurring": rid})


def start_watch(push):
    """Hourly: tells the phone once when a recurring bill is due."""
    def loop():
        while True:
            try:
                b = _load(); sent = b.setdefault("alerts_sent", {}); changed = False
                for r in due_bills(b):
                    k = time.strftime("%Y-%m") + "|bill|" + r["id"]
                    if k not in sent:
                        sent[k] = int(time.time()); changed = True
                        push("money", f"🧾 {r['name']} ka bill", f"PKR {r['amount']:,.0f} · Money book mein ek tap se add karo", "Money")
                if changed:
                    with _lock:
                        x = _load(); x.setdefault("alerts_sent", {}).update(sent); _save(x)
                nightly(push)
            except Exception:  # noqa: BLE001
                pass
            time.sleep(600)
    threading.Thread(target=loop, daemon=True).start()


# ---------- voice / typed sentence → entry ----------

def parse(text):
    """'500 dawai ghar ka' → {amount, note, category, whose, kind, person}. Quick rules first, AI if needed."""
    t = text.strip()
    b = _load()
    people = b.get("people", ["Ghar"])
    nums = re.findall(r"\d[\d,]*(?:\.\d+)?", t)
    low = t.lower()
    j = {"amount": _num(nums[0]) if nums else 0, "note": re.sub(r"\d[\d,]*(?:\.\d+)?|rs|pkr|rupay|rupees?|rupe", "", t, flags=re.I).strip(" ,.-"),
         "kind": "pocket" if "pocket" in low else "received" if re.search(r"\bmil(a|e|i)\b|wapas|received", low) else "spend",
         "whose": "me" if re.search(r"\b(mera|meri|mere|apna|apni|personal)\b", low) else "home", "category": "Other",
         "person": next((p for p in people if p.lower() in low), "Ghar")}
    for c, words in {"Hospital": "hospital|aspatal", "Doctor / Medicine": "dawai|dawa|medicine|doctor|dr\\b|tablet",
                     "Clothes": "kapr|kapde|shirt|jeans|shoes|joote", "Grocery": "sabzi|grocery|ration|atta|doodh|milk",
                     "Bills": "bill|bijli|gas|internet|paani", "Fuel / Transport": "petrol|fuel|rickshaw|uber|careem|bus",
                     "Food": "khana|lunch|dinner|burger|pizza|chai", "Personal / Fun": "game|movie|fun|shauq"}.items():
        if re.search(words, low):
            j["category"] = c
            if c in ("Clothes", "Personal / Fun", "Food") and not re.search(r"\bghar\b", low):
                j["whose"] = "me"
            break
    return j


# ---------- PDF report ----------

def pdf(month="", person=""):
    """Month report with photos, made by headless Chrome. Returns the file path."""
    import html as H
    L = listing(month)
    rows = [x for x in L["entries"] if x["date"].startswith(L["month"]) and (not person or (x.get("person") or "Ghar") == person)]
    tr = []
    for x in reversed(rows):
        pics = "".join(f'<img src="file://{PHOTOS / p}">' for p in x.get("photos", []) if (PHOTOS / p).exists())
        kind = {"spend": ("🏠 Ghar" if x["whose"] == "home" else "🙋 Mera"), "received": "💵 Wapas mila", "pocket": "👛 Pocket"}[x["kind"]]
        tr.append(f"<tr><td>{x['date']}</td><td>{kind}</td><td>{H.escape(x.get('person') or 'Ghar')}</td><td>{H.escape(x['category'])}</td>"
                  f"<td>{H.escape(x['note'])}<div class=p>{pics}</div></td><td class=r>{x['amount']:,.0f}</td></tr>")
    mt, bal = L["month_total"], L["balance"]
    pb = "".join(f"<li>{H.escape(p)}: lene hain <b>PKR {v['owes']:,.0f}</b></li>" for p, v in L["people"].items())
    doc = f"""<html><head><meta charset=utf-8><style>body{{font-family:-apple-system,Helvetica;margin:28px;color:#1a1530}}
h1{{color:#0f766e;margin:0}}table{{width:100%;border-collapse:collapse;font-size:12px;margin-top:14px}}td,th{{border-bottom:1px solid #ddd;padding:6px;text-align:left;vertical-align:top}}
.r{{text-align:right;font-weight:700}}.box{{display:flex;gap:12px;margin-top:12px}}.box div{{flex:1;background:#ecfdf5;border-radius:10px;padding:10px}}
.p img{{height:70px;margin:4px 4px 0 0;border-radius:4px}}</style></head><body>
<h1>Money book · {L['month']}{' · ' + H.escape(person) if person else ''}</h1>
<div class=box><div>Kul kharcha<br><b>PKR {mt['spent']:,.0f}</b></div><div>Ghar ka<br><b>PKR {mt['home']:,.0f}</b></div>
<div>Mera apna<br><b>PKR {mt['me']:,.0f}</b></div><div>Ghar se lene hain<br><b>PKR {bal['home_owes']:,.0f}</b></div></div>
<ul>{pb}</ul><table><tr><th>Date</th><th>Kis ka</th><th>Person</th><th>Category</th><th>Detail</th><th class=r>PKR</th></tr>{''.join(tr)}</table></body></html>"""
    out = HERE / "money_reports"; out.mkdir(exist_ok=True)
    h = out / f"report-{L['month']}.html"; h.write_text(doc)
    f = out / f"Money_{L['month']}{'_' + re.sub(r'\\W', '', person) if person else ''}.pdf"
    subprocess.run(["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "--headless", "--disable-gpu", "--allow-file-access-from-files",
                    f"--print-to-pdf={f}", "--no-pdf-header-footer", f"file://{h}"], capture_output=True, timeout=90)
    if not f.exists():
        raise ValueError("PDF nahi bana")
    return f


# ---------- saving goals ----------

def goals(b, es):
    out = []
    for g in b.get("goals", []):
        saved = sum(x["amount"] for x in es if x["kind"] == "goal" and x.get("goal") == g["id"])
        out.append({**g, "saved": round(saved, 2), "done": saved >= g["target"]})
    return out


def set_goal(d):
    with _lock:
        b = _load(); gs = b.setdefault("goals", [])
        if d.get("delete"):
            b["goals"] = [g for g in gs if g["id"] != d["id"]]
        else:
            t = _num(d.get("target"))
            if t <= 0 or not str(d.get("name", "")).strip():
                raise ValueError("Goal ka naam aur amount do")
            gs.append({"id": uuid.uuid4().hex[:8], "name": str(d["name"])[:40], "target": t, "emoji": d.get("emoji") or "🎯",
                       "created": time.strftime("%Y-%m-%d")})
        _save(b)
    return listing()


# ---------- month forecast ----------

def forecast(m, budgets, month):
    import calendar
    y, mo = int(month[:4]), int(month[5:7])
    days = calendar.monthrange(y, mo)[1]
    if month != time.strftime("%Y-%m"):
        return {}
    today = int(time.strftime("%d"))
    spent = sum(x["amount"] for x in m if x["kind"] == "spend")
    per_day = spent / max(today, 1)
    cats = {}
    for x in m:
        if x["kind"] == "spend":
            cats[x["category"]] = cats.get(x["category"], 0) + x["amount"]
    me = sum(x["amount"] for x in m if x["kind"] == "spend" and x["whose"] == "me")
    warn = []
    for c, lim in budgets.items():
        used = me if c == "Mera apna (total)" else cats.get(c, 0)
        proj = used / max(today, 1) * days
        if used <= lim < proj:
            warn.append({"category": c, "projected": round(proj), "limit": lim})
    return {"per_day": round(per_day), "projected": round(per_day * days), "days_left": days - today, "warn": warn}


# ---------- AI advice ----------

def advice(month=""):
    L = listing(month)
    mt = L["month_total"]
    rows = [f"{x['date']} {x['kind']} {x['whose']} {x['category']} {x['amount']:.0f} {x['note'][:40]}" for x in L["entries"]
            if x["date"].startswith(L["month"])][:80]
    prompt = ("You are a friendly money coach for a young person in Pakistan. Reply in Roman Urdu (Urdu in English letters), "
              "short, warm, practical, 5 bullet points max, amounts in PKR. They get pocket money; home expenses they pay are claimed back from home. "
              f"Month {L['month']}: spent {mt['spent']:.0f}, home {mt['home']:.0f}, own {mt['me']:.0f}, pocket received {mt['pocket']:.0f}, "
              f"by category {json.dumps(mt['by_category'])}, budgets {json.dumps(L['budgets'])}, forecast {json.dumps(L['forecast'])}, "
              f"goals {json.dumps([{k: g[k] for k in ('name', 'target', 'saved')} for g in L['goals']])}. Entries:\n" + "\n".join(rows) +
              "\nGive: where money went, 2-3 ways to save, one goal tip. No markdown headers. Don't run any commands.")
    out = ""
    if AGY.exists():
        try:
            out = subprocess.run([str(AGY), "-p", prompt, "--print-timeout", "90s"], capture_output=True, text=True, timeout=120).stdout.strip()
        except subprocess.TimeoutExpired:
            pass
    if len(out) < 20:
        out = subprocess.run([CLAUDE, "-p", prompt, "--output-format", "text", "--model", "sonnet"], capture_output=True, text=True, timeout=150).stdout.strip()
    return {"text": out or "Abhi mashwara nahi ban saka, thori dair baad try karo."}


# ---------- claims sent to home ----------

def claim(ids=None):
    """Marks home entries as claimed (sent to home). Returns the claim text + photo names."""
    with _lock:
        b = _load(); today = time.strftime("%Y-%m-%d"); picked = []
        for x in b["entries"]:
            if x["kind"] == "spend" and x["whose"] == "home" and not x.get("claimed") and (not ids or x["id"] in ids):
                x["claimed"] = today; picked.append(x)
        _save(b)
    tot = sum(x["amount"] for x in picked)
    lines = ["Assalam o Alaikum, ye ghar ke kharche maine apni pocket se kiye hain:"] + \
            [f"• {x['date'][5:]}  PKR {x['amount']:,.0f}  {x['note'] or x['category']}" for x in picked] + \
            [f"\nKul: PKR {tot:,.0f}", f"Pehle ke baqi mila kar: PKR {listing()['balance']['home_owes']:,.0f} lene hain. Shukriya!"]
    return {"text": "\n".join(lines), "photos": [p for x in picked for p in x.get("photos", [])], "count": len(picked)}


def set_remind(on):
    with _lock:
        b = _load(); b["remind"] = bool(on); _save(b)
    return listing()


def nightly(push):
    """21:30 'aaj ka kharcha daala?' and Sunday weekly summary."""
    b = _load()
    if not b.get("remind", True):
        return
    today = time.strftime("%Y-%m-%d"); sent = b.setdefault("alerts_sent", {})
    if time.strftime("%H:%M") >= "21:30" and f"night|{today}" not in sent:
        n = sum(1 for x in b["entries"] if x["date"] == today)
        push("money", "🌙 Aaj ka hisaab", (f"Aaj {n} entries hain. Kuch reh to nahi gaya?" if n else "Aaj ka koi kharcha daala? Bill photo ya 🎤 se 10 second."), "Money")
        sent[f"night|{today}"] = 1
        if time.strftime("%a") == "Sun":
            wk = time.strftime("%Y-%m-%d", time.localtime(time.time() - 6 * 86400))
            sp = sum(x["amount"] for x in b["entries"] if x["kind"] == "spend" and x["date"] >= wk)
            push("money", "📊 Hafte ka khulasa", f"Is hafte PKR {sp:,.0f} kharch hua. Ghar se lene hain: PKR {listing()['balance']['home_owes']:,.0f}", "Money")
        with _lock:
            x = _load(); x.setdefault("alerts_sent", {}).update(sent); _save(x)


def income_by(m, key):
    out = {}
    for x in m:
        if x["kind"] == "income" and x.get(key):
            out[x[key]] = round(out.get(x[key], 0) + x["amount"], 2)
    return out


def today_summary():
    """Small block for the home 'Your day' card."""
    L = listing()
    return {"home_owes": L["balance"]["home_owes"], "wallet": L["balance"]["wallet"],
            "bills_due": [b["name"] for b in L["due_bills"]], "spent_month": L["month_total"]["spent"],
            "earned_month": L["month_total"]["income"], "unclaimed": L["unclaimed"],
            "over_budget": L["over_budget"], "forecast": L["forecast"]}


# ---------- assistant: one sentence → many entries ----------

SMART = """You keep the owner's money book (Pakistan, PKR). They speak Roman Urdu / English, maybe from voice.
How their money works: they get POCKET MONEY from home (kind "pocket"). They pay some HOME expenses from their own pocket
(hospital, doctor, medicine, lab tests, household, grocery, bills, family things, transport for a family hospital visit) = kind "spend",
whose "home" (home pays it back later). Their OWN things (clothes, food for themselves, cigarettes, fun, phone) = kind "spend", whose "me".
When home pays them back = kind "received". Earnings (Etsy, freelance, YouTube) = kind "income".
"Ghar se 5000 mile kharche ke liye" = pocket (or received if they say it's to settle what home owes).
Today is {today}. Turn the message into entries. Reply with JSON only:
{{"entries": [{{"kind": "spend|pocket|received|income", "amount": number, "note": "short, in their words", "category": one of {cats},
"whose": "home|me", "date": "YYYY-MM-DD", "person": "Ghar or a family name if said"}}], "say": "one short Roman Urdu line confirming what you added"}}
Message: "{text}"
Don't run any commands."""


def smart(text):
    import datetime as _dt
    p = SMART.format(today=str(_dt.date.today()), cats=json.dumps(CATS), text=str(text).replace('"', "'")[:1500])
    out = ""
    if AGY.exists():
        try:
            out = subprocess.run([str(AGY), "-p", p, "--print-timeout", "60s"], capture_output=True, text=True, timeout=80).stdout
        except subprocess.TimeoutExpired:
            pass
    if "{" not in out:
        out = subprocess.run([CLAUDE, "-p", p, "--output-format", "text", "--model", "haiku"], capture_output=True, text=True, timeout=90).stdout
    try:
        j = json.loads(out[out.index("{"):out.rindex("}") + 1])
    except ValueError:
        raise ValueError("Samajh nahi aaya, thora aur saaf bata do")
    before = {x["id"] for x in _load()["entries"]}
    for e in j.get("entries", [])[:20]:
        try:
            if _num(e.get("amount")) > 0:
                add(e)
        except ValueError:
            pass
    L = listing()
    new = [x for x in L["entries"] if x["id"] not in before]
    return L | {"added": new, "say": j.get("say", f"{len(new)} entries add ho gayi")}


def attach(image_b64):
    """Stores a bill photo sent later by the phone (it was read on the phone while offline); no AI call."""
    PHOTOS.mkdir(exist_ok=True)
    name = time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:6] + ".jpg"
    raw = base64.b64decode(image_b64)
    if len(raw) > 15_000_000:
        raise ValueError("Photo too big")
    (PHOTOS / name).write_bytes(raw)
    return {"photo": name}
