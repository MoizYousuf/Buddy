"""Store Studio: Trend Studio's Etsy data in the phone app.
Reads the Trend Studio data folder (config etsy.data_dir: report.json + the newest snapshot, read only), answers per-store questions fast, and makes
design images with Gemini (the owner's Google AI Pro plan) so they can be finished in Canva.
Text AI: Antigravity CLI (Gemini) when installed and signed in, else Claude. Both are on plans the owner already has."""
import glob, json, os, re, statistics, subprocess, threading, time, uuid
from pathlib import Path

from config import CFG, chrome_desc
import config

TS = config.path(CFG["etsy"]["data_dir"])
RUNS = Path(__file__).parent / "etsy_runs"
DESIGNS = TS / "designs"
CLAUDE = str(Path.home() / ".local/bin/claude")
AGY = Path.home() / ".local/bin/agy"
_runs, _lock = {}, threading.Lock()


def _report():
    return json.loads((TS / "report.json").read_text())


def _snapshot():
    f = sorted(glob.glob(str(TS / "snapshots" / "*.json")))[-1]
    return json.loads(Path(f).read_text())


def engine():
    return "gemini" if AGY.exists() else "claude"


def _ai(prompt, timeout=150):
    """One quick text answer. Gemini (agy) first when present; Claude if agy is missing or fails."""
    if AGY.exists():
        r = subprocess.run([str(AGY), "-p", prompt, "--print-timeout", f"{timeout}s"], capture_output=True, text=True,
                           timeout=timeout + 15, cwd=str(TS))
        if r.returncode == 0 and "{" in r.stdout:
            return r.stdout, "gemini"
    r = subprocess.run([CLAUDE, "-p", prompt, "--output-format", "text", "--model", "sonnet"], capture_output=True,
                       text=True, timeout=timeout, cwd=str(TS))
    return r.stdout, "claude"


def _json(out):
    return json.loads(out[out.index("{"):out.rindex("}") + 1])


# ---------- overview and one store ----------

def _trend(hist):
    if not hist or len(hist) < 2:
        return 0
    week = [h for h in hist if h[0] >= time.strftime("%Y-%m-%d", time.localtime(time.time() - 7 * 86400))] or hist[-2:]
    return hist[-1][1] - week[0][1]


def overview():
    r = _report()
    hist = r.get("history", {})
    stores = []
    for s in r.get("stores", []):
        n = s["name"]
        tasks = [t for t in r.get("all_tasks", []) if t.get("store") == n]
        stores.append({"name": n, "sales": s.get("sales"), "per_month": s.get("sales_per_month"), "rating": s.get("rating"),
                       "listings": s.get("listings_seen"), "avg_price": s.get("avg_price"), "niche": s.get("niche"),
                       "health": s.get("health"), "week_sales": _trend(hist.get(n)), "todo": len(tasks),
                       "top": (s.get("categories") or [["", 0]])[0][0],
                       "img": next((i.get("img") for i in s.get("top_items", []) if i.get("img")), None)})
    stores.sort(key=lambda x: -(x["sales"] or 0))
    ev = [e for e in r.get("events", []) if e.get("days_left", 99) <= 45][:5]
    return {"generated": r.get("generated"), "stores": stores, "events": ev, "engine": engine(),
            "summary": (r.get("tasks") or {}).get("summary", "")}


def store(name):
    r = _report()
    s = next((x for x in r.get("stores", []) if x["name"] == name), None)
    if not s:
        raise ValueError("No such store")
    tasks = [t for t in r.get("all_tasks", []) if t.get("store") == name]
    order = {"high": 0, "medium": 1, "low": 2}
    tasks.sort(key=lambda t: order.get(t.get("priority"), 3))
    keep = ("name", "url", "sales", "age", "rating", "sales_per_month", "listings_seen", "avg_price", "categories",
            "top_items", "niche", "market", "market_price", "price_position", "missing_themes", "own_themes",
            "keyword_hits", "rivals", "audit", "health", "ai")
    out = {k: s.get(k) for k in keep}
    out["history"] = r.get("history", {}).get(name, [])
    out["tasks"] = tasks
    out["events"] = [e for e in r.get("events", []) if e.get("days_left", 99) <= 45][:4]
    try:   # this month's sales the owner logged in the Money book
        import money
        out["earned_month"] = money.listing()["month_total"]["income_by_store"].get(name, 0)
    except Exception:  # noqa: BLE001
        out["earned_month"] = 0
    return out


# ---------- search: "I want to make a funeral program" → what sells and what to make ----------

STOP = {"a", "an", "the", "to", "for", "of", "and", "me", "my", "i", "make", "want", "banana", "hai", "ka", "ki", "ke",
        "mujhe", "chahiye", "kya", "design", "template"}


def _market(query):
    words = [w for w in re.findall(r"[a-z0-9]+", query.lower()) if w not in STOP and len(w) > 1]
    snap = _snapshot()
    seen, rows = set(), []
    pools = list(snap.get("search", {}).values()) + [v.get("items", []) for v in snap.get("comp", {}).values()] + \
        [v.get("items", []) for v in snap.get("shops", {}).values()]
    for items in pools:
        for x in items:
            t = (x.get("title") or "").lower()
            if x.get("id") in seen or not words:
                continue
            hit = sum(w in t for w in words)
            if hit >= (len(words) if len(words) <= 2 else -(-len(words) * 3 // 5)):
                seen.add(x.get("id"))
                rows.append(x | {"hit": hit})
    prices = [float(x["price"]) for x in rows if re.fullmatch(r"[\d.]+", str(x.get("price") or ""))]
    rows.sort(key=lambda x: (-x["hit"], not x.get("bestseller"), -int(re.sub(r"\D", "", str(x.get("carts") or "0")) or 0)))
    return {"words": words, "count": len(rows), "bestsellers": sum(bool(x.get("bestseller")) for x in rows),
            "ads": sum(bool(x.get("ad")) for x in rows), "price_median": round(statistics.median(prices), 2) if prices else None,
            "price_low": min(prices) if prices else None, "price_high": max(prices) if prices else None,
            "shops": sorted({x.get("shop") for x in rows if x.get("shop")})[:12],
            "top": [{k: x.get(k) for k in ("title", "price", "url", "img", "shop", "bestseller", "carts", "reviews")} for x in rows[:12]]}


def _search_run(rid, store_name, query):
    try:
        m = _market(query)
        s = store(store_name) if store_name else {}
        mine = [i.get("title") for i in (s.get("top_items") or [])][:15]
        prompt = (
            "You are an Etsy product strategist for digital memorial/funeral products (Canva templates, PNG shirt designs, "
            f"signs, programs). The owner asks (may be Urdu/Hindi/English): \"{query}\".\n"
            f"Store: {store_name or 'any of our stores'}; niche: {s.get('niche')}; avg price ${s.get('avg_price')}; "
            f"its top listings: {json.dumps(mine)}; themes it lacks: {json.dumps(s.get('missing_themes'))}.\n"
            f"Etsy market data for this search, collected today: {json.dumps(m)[:7000]}\n"
            f"Real buyer search phrases from Etsy's search box: {json.dumps([x['q'] for x in buyer_phrases(None, 60)['phrases'] if any(w in x['q'] for w in m['words'])][:20])}\n"
            f"{lessons()}\n"
            "Using ONLY this data plus general Etsy know-how, give 3 product ideas this store should make now. Be concrete: "
            "what exactly to design, colours, motif, layout, text, sizes, what's included, price, title and 13 tags. "
            "Also a short image prompt for Gemini to make the main design/mockup (no real people's faces, no brand logos, "
            "leave clear areas for name/dates as placeholders like 'NAME' and '1950 - 2026'), and Canva steps to finish it.\n"
            'Reply ONLY JSON: {"verdict": "2 lines in simple Roman Urdu: is it worth it and why (numbers)", '
            '"demand": "high|medium|low", "competition": "high|medium|low", "ideas": [{"name": "...", "type": '
            '"Canva template|Shirt PNG|Sign|...", "why": "Roman Urdu, with numbers", "design": "exact design brief in English", '
            '"colors": ["..."], "price": 0.0, "etsy_title": "...", "tags": ["13 tags"], "includes": "...", '
            '"image_prompt": "...", "canva_steps": ["step 1", "..."], "edge": "how we beat the top listings"}], '
            '"avoid": "one line Roman Urdu: what not to do"}')
        out, used = _ai(prompt)
        res = _json(out) | {"market": m, "engine": used}
        _done(rid, "done", res)
    except Exception as e:  # noqa: BLE001
        _done(rid, "error", {"error": str(e)[:300]})


def _done(rid, status, data):
    d = _runs[rid] | {"status": status, "finished": time.time(), **data}
    with _lock:
        _runs[rid] = d
    RUNS.mkdir(exist_ok=True)
    (RUNS / f"{rid}.json").write_text(json.dumps(d, ensure_ascii=False))


def search(store_name, query):
    query = (query or "").strip()[:300]
    if not query:
        raise ValueError("Type what you want to make")
    rid = uuid.uuid4().hex[:10]
    with _lock:
        _runs[rid] = {"id": rid, "status": "running", "store": store_name, "query": query, "started": time.time()}
    threading.Thread(target=_search_run, args=(rid, store_name, query), daemon=True).start()
    return {"id": rid}


def result(rid):
    rid = re.sub(r"[^a-f0-9]", "", rid)
    with _lock:
        if rid in _runs:
            return _runs[rid]
    f = RUNS / f"{rid}.json"
    if f.exists():
        return json.loads(f.read_text())
    raise ValueError("Not found")


def history(store_name=None):
    RUNS.mkdir(exist_ok=True)
    out = []
    for f in sorted(RUNS.glob("*.json"), key=lambda p: -p.stat().st_mtime)[:40]:
        try:
            d = json.loads(f.read_text())
        except ValueError:
            continue
        if d.get("status") == "done" and (not store_name or d.get("store") == store_name):
            out.append({k: d.get(k) for k in ("id", "store", "query", "finished", "verdict", "demand")})
    return {"runs": out}


# ---------- designs: Gemini (owner's AI Pro) makes the images ----------

def design_prompt(folder, image_prompt, n=2):
    return ("Make 2 images with Google Gemini (the owner's own Google AI Pro plan) for an Etsy digital product.\n"
            "Use Claude in Chrome: if more than one browser is connected, select the one on THIS Mac (" + chrome_desc() + "). "
            "Open https://gemini.google.com/app in a new tab, start a new chat, pick image creation (Nano Banana).\n"
            f"Image 1 = the PRINT ARTWORK only: \"{image_prompt}\" — but as a flat, front-on, centred print design ONLY, "
            "isolated on a plain solid pure white background, NO t-shirt, NO mockup, NO table, NO shadow, crisp clean edges, "
            "every object clearly separated with a little space between them, text in clean bold lettering, placeholders like "
            "NAME and 1950 - 2026 kept as text, square image.\n"
            "Image 2 = a product mockup showing exactly that same artwork (ask in the same chat: 'now show this exact design "
            "printed on a product mockup').\n"
            "Then, in the same chat, for each of the main parts of the artwork (up to 4, e.g. the eagle, the flag, the frame or "
            "badge, the banner/ribbon, a photo frame, flowers) ask: 'show only the <part> from this exact design, exactly the "
            "same shape, colours and style, alone and complete on a plain pure white background, no text'. Save them as "
            "part_1.png ... part_4.png, and write the part names, one per line in the same order, in parts.txt.\n"
            "No watermark, no real people's faces, no brand logos. Download each image (Gemini's download button), then move "
            f"them into {folder} as print_art.png and mockup.png (convert with sips to PNG if needed; look in ~/Downloads for "
            f"the newest Gemini_Generated_Image files). If Gemini asks to sign in or says the image limit is reached, stop "
            f"and write that in {folder}/status.txt. Never type passwords. When done write 'done' in {folder}/status.txt. "
            "Reply with one line.")


LAYERS = str(Path(__file__).parent / "maclayers")


def make_layers(folder, src=None):
    """Background, elements, people and text as separate same-size layers + a 4500x5400 300-dpi print PNG."""
    folder = Path(folder)
    src = src or next((folder / f for f in ("print_art.png", "design1.png") if (folder / f).exists()), None)
    if not src:
        raise ValueError("No design image yet")
    try:
        kind = json.loads((folder / "meta.json").read_text()).get("type", "").lower()
    except (OSError, ValueError):
        kind = ""
    # print canvas by product: shirts 4500x5400; programs/cards/flyers US Letter landscape or portrait at 300 dpi
    size = ["4500", "5400"] if re.search(r"shirt|png|sticker|tumbler|mug", kind) or not kind else \
        (["3300", "2550"] if re.search(r"bifold|trifold|program|brochure", kind) else ["2550", "3300"])
    r = subprocess.run([LAYERS, str(src), str(folder / "layers"), *size], capture_output=True, text=True, timeout=300)
    if r.returncode != 0:
        raise RuntimeError("Layers failed: " + r.stderr[-200:])
    names = (folder / "parts.txt").read_text().split("\n") if (folder / "parts.txt").exists() else []
    for i, part in enumerate(sorted(folder.glob("part_*.png")), 1):   # Gemini's separate parts, background removed
        nm = re.sub(r"[^a-z0-9]+", "_", (names[i - 1] if i <= len(names) else f"part {i}").lower()).strip("_") or f"part_{i}"
        subprocess.run([LAYERS, "--cutout", str(part), str(folder / "layers" / f"piece_{i}_{nm}.png")], capture_output=True, timeout=120)
    if size == ["4500", "5400"]:   # shirts/PNG: colour SVG too (Cricut / SVG buyers)
        subprocess.run([str(Path(__file__).parent / "macsvg"), str(folder / "layers" / "print.png"), str(folder / "layers" / "design.svg")],
                       capture_output=True, timeout=300)
    for f in (folder / "layers").glob("*.png"):     # small previews for the phone
        subprocess.run(["sips", "-Z", "700", str(f), "--out", str(folder / "layers" / ("p_" + f.name))], capture_output=True)
    return json.loads((folder / "layers" / "layers.json").read_text())


_layering = set()


def _auto_layers(folder):
    try:
        make_layers(folder)
    except Exception as e:  # noqa: BLE001
        (Path(folder) / "layers_error.txt").write_text(str(e))
    finally:
        _layering.discard(str(folder))


def design(rid, idx, start_job, prompt=None):
    r = result(rid)
    idea = (r.get("ideas") or [{}])[int(idx)] if r.get("ideas") else {}
    p = (prompt or idea.get("image_prompt") or "").strip()[:1500]
    if not p:
        raise ValueError("No image prompt")
    did = uuid.uuid4().hex[:10]
    folder = DESIGNS / did
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "meta.json").write_text(json.dumps({"id": did, "run": rid, "idea": idea.get("name"), "prompt": p,
                                                  "type": idea.get("type", ""), "store": r.get("store"), "started": time.time()}, ensure_ascii=False))
    # Gemini app driven by a plain script (no Claude credits); its own Chrome profile has the owner's Google login
    py = str(TS.parent / ".venv" / "bin" / "python")
    proc = subprocess.Popen([py, str(TS.parent / "gemini_images.py"), str(folder), p],
                            stdout=open(folder / "run.log", "w"), stderr=subprocess.STDOUT, start_new_session=True)
    jid = ""
    meta = json.loads((folder / "meta.json").read_text()) | {"pid": proc.pid, "engine": "gemini-script"}
    (folder / "meta.json").write_text(json.dumps(meta, ensure_ascii=False))
    return {"id": did, "job_id": jid}


def design_status(did, job_of):
    did = re.sub(r"[^a-f0-9]", "", did)
    folder = DESIGNS / did
    meta = json.loads((folder / "meta.json").read_text())
    files = sorted(f.name for f in folder.iterdir() if f.suffix.lower() in (".png", ".jpg", ".jpeg", ".webp"))
    st = (folder / "status.txt").read_text().strip() if (folder / "status.txt").exists() else ""
    j = job_of(meta.get("job", "")) or {} if meta.get("job") else {}
    if meta.get("pid"):
        try:
            os.kill(meta["pid"], 0); running = not (folder / "status.txt").exists()
        except OSError:
            running = False
        prog = (folder / "progress.txt").read_text().splitlines() if (folder / "progress.txt").exists() else []
        j = {"log": ["      " + x for x in prog]}
    else:
        running = j.get("status") == "running"
    lay = folder / "layers" / "layers.json"
    if files and not running and not lay.exists() and not (folder / "layers_error.txt").exists() and str(folder) not in _layering:
        _layering.add(str(folder)); threading.Thread(target=_auto_layers, args=(str(folder),), daemon=True).start()
    if str(folder) in _layering:
        running = True; st = "Making layers and the print file…"
    layers = json.loads(lay.read_text()) if lay.exists() else None
    if layers:
        layers["files"] = sorted(f.name for f in (folder / "layers").glob("*.png") if not f.name.startswith("p_"))
        layers["svg"] = (folder / "layers" / "design.svg").exists()
    return meta | {"files": files, "note": st, "running": running, "layers": layers,
                   "status": "done" if files and not running else ("running" if running else ("error" if not files else "done")),
                   "steps": [l[6:] for l in j.get("log", [])][-6:]}


def designs(store_name=None):
    out = []
    if DESIGNS.exists():
        for d in sorted(DESIGNS.iterdir(), key=lambda p: -p.stat().st_mtime)[:30]:
            try:
                m = json.loads((d / "meta.json").read_text())
            except (OSError, ValueError):
                continue
            files = sorted(f.name for f in d.iterdir() if f.suffix.lower() in (".png", ".jpg", ".jpeg", ".webp"))
            if files and (not store_name or m.get("store") == store_name):
                lay = d / "layers" / "layers.json"
                layers = json.loads(lay.read_text()) if lay.exists() else None
                if layers:
                    layers["files"] = sorted(f.name for f in (d / "layers").glob("*.png") if not f.name.startswith("p_"))
                    layers["svg"] = (d / "layers" / "design.svg").exists()
                out.append(m | {"files": files, "layers": layers})
    return {"designs": out}


def image_path(did, name):
    did = re.sub(r"[^a-f0-9]", "", did)
    name = name.replace("\\", "/")
    sub = "layers/" if name.startswith("layers/") else ""
    f = (DESIGNS / did / (sub + Path(name).name))
    return f if f.exists() and f.suffix.lower() in (".png", ".jpg", ".jpeg", ".webp", ".svg") else None


def relayer(did):
    did = re.sub(r"[^a-f0-9]", "", did)
    folder = DESIGNS / did
    for f in ("layers_error.txt",):
        (folder / f).unlink(missing_ok=True)
    return make_layers(folder)


# ---------- listing pack: photos, buyer PDF, listing text, AI-rules guard, Canva Bulk Create CSV ----------

PACK = str(Path(__file__).parent / "macpack")
TRADEMARKS = ["disney", "marvel", "pixar", "nike", "adidas", "nfl", "nba", "mlb", "nhl", "ncaa", "harley", "coca cola", "starbucks",
              "barbie", "hello kitty", "pokemon", "mickey", "minnie", "snoopy", "peanuts", "dallas cowboys", "yankees", "lakers",
              "taylor swift", "elvis", "harry potter", "star wars", "grateful dead", "army strong", "semper fi", "usmc",
              "us army", "u.s. army", "us navy", "u.s. navy", "air force", "marines", "coast guard", "purple heart"]
DISCLOSURE = "Designed by {store}. Made with the help of AI design tools, then edited and checked by us."


def _words(t):
    return {w for w in re.findall(r"[a-z]+", (t or "").lower()) if len(w) > 2}


def _guard_check(title, tags, desc, store):
    warn = []
    blob = " ".join([title, " ".join(tags), desc]).lower()
    for tm in TRADEMARKS:
        if re.search(r"\b" + re.escape(tm) + r"\b", blob):
            warn.append(f"'{tm}' may be a trademark or protected name: check before listing (military branch names/seals are protected).")
    if "handmade" in blob or "hand made" in blob:
        warn.append("Don't call AI-assisted digital items 'handmade'.")
    mine = []
    try:
        snap = _snapshot()
        for v in snap.get("shops", {}).values():
            mine += [i.get("title", "") for i in v.get("items", [])]
    except (OSError, ValueError, IndexError):
        pass
    tw = _words(title)
    for m in mine:
        mw = _words(m)
        if tw and mw and len(tw & mw) / len(tw | mw) >= 0.7:
            warn.append(f"Very close to one of our listings: \"{m[:80]}\". Etsy dislikes near-duplicates: change the angle or bundle it.")
            break
    tags = [t for t in tags if len(t) <= 20][:13]
    return tags, warn


def make_pack(did, link=""):
    did = re.sub(r"[^a-f0-9]", "", did)
    folder = DESIGNS / did
    meta = json.loads((folder / "meta.json").read_text())
    lay = folder / "layers" / "layers.json"
    if not lay.exists():
        make_layers(folder)
    L = json.loads(lay.read_text())
    idea = {}
    try:
        idea = next((i for i in result(meta["run"]).get("ideas", []) if i.get("name") == meta.get("idea")), {})
    except (ValueError, KeyError, OSError):
        pass
    store = meta.get("store") or ""
    texts = [t["text"] for t in L.get("texts", [])]
    parts = (folder / "parts.txt").read_text().split("\n") if (folder / "parts.txt").exists() else []
    prompt = (
        "Write an Etsy listing for this digital product. Product idea: " + json.dumps({k: idea.get(k) for k in
        ("name", "type", "design", "includes", "price", "etsy_title", "tags", "colors")}, ensure_ascii=False) +
        f"\nStore: {store}. Text in the design: {texts}. Separate parts: {parts}. Print size: {L.get('print')} px at 300 dpi.\n"
        "Rules: title max 140 characters, buyer words first; exactly 13 tags, each max 20 characters; never say handmade; "
        "no trademarks, team, brand or military-branch names/seals; description in English with sections: short intro, "
        "WHAT YOU GET, HOW IT WORKS, FAQ (3 Q&As), and a note that it's a digital download.\n"
        'Reply ONLY JSON: {"title": "", "tags": [], "description": "", "includes": ["5 short lines"], '
        '"formats": ["4 short lines"], "steps": ["3 short buyer steps for Canva"], "colors": ["hex colours used"], '
        '"alt": ["alt text for 5 photos"], "fields": ["column names for Canva Bulk Create, one per editable text"], '
        '"rows": [["8 realistic sample rows, same order as fields, fictional names only"]], "brand_kit": "suggested Brand Kit name"}')
    out, used = _ai(prompt, timeout=200)
    d = _json(out)
    tags, warn = _guard_check(d.get("title", ""), d.get("tags", []), d.get("description", ""), store)
    desc = d.get("description", "").strip() + "\n\n" + DISCLOSURE.format(store=store or "our shop")
    pack = folder / "pack"
    pack.mkdir(exist_ok=True)
    pieces = sorted(str(f) for f in (folder / "layers").glob("p_piece_*.png")) or \
        sorted(str(f) for f in (folder / "layers").glob("p_element_*.png"))
    accent = next((c for c in d.get("colors", []) if re.fullmatch(r"#[0-9A-Fa-f]{6}", c or "") and c.upper() not in ("#FFFFFF", "#000000")), "#6C4DFF")
    spec = {"out": str(pack), "store": store, "title": d.get("title", ""), "subtitle": " · ".join(d.get("formats", [])[:2]),
            "mockup": str(folder / "mockup.png") if (folder / "mockup.png").exists() else "",
            "print": str(folder / ("print_art.png" if (folder / "print_art.png").exists() else "design1.png")),
            "layers": pieces[:8], "includes": d.get("includes", []), "formats": d.get("formats", []), "steps": d.get("steps", []),
            "colors": [c for c in d.get("colors", []) if c][:6], "accent": accent, "link": (link or "").strip()}
    (pack / "spec.json").write_text(json.dumps(spec, ensure_ascii=False))
    r = subprocess.run([PACK, str(pack / "spec.json")], capture_output=True, text=True, timeout=300)
    if r.returncode != 0:
        raise RuntimeError("Listing photos failed: " + r.stderr[-200:])
    import csv
    fields = [str(f) for f in d.get("fields", [])] or ["NAME", "DATES"]
    with open(pack / "canva_bulk_create.csv", "w", newline="") as f:
        w = csv.writer(f); w.writerow(fields)
        for row in d.get("rows", [])[:12]:
            if isinstance(row, list):
                w.writerow([str(x) for x in row][:len(fields)])
    info = {"title": d.get("title", ""), "tags": tags, "description": desc, "alt": d.get("alt", []), "warnings": warn,
            "price": idea.get("price"), "brand_kit": d.get("brand_kit", ""), "fields": fields, "link": spec["link"], "engine": used,
            "files": sorted(f.name for f in pack.iterdir() if f.suffix in (".jpg", ".pdf", ".csv")), "made": time.time()}
    (pack / "pack.json").write_text(json.dumps(info, ensure_ascii=False))
    for f in pack.glob("listing_*.jpg"):
        subprocess.run(["sips", "-Z", "900", str(f), "--out", str(pack / ("p_" + f.name))], capture_output=True)
    return info


def pack_info(did):
    did = re.sub(r"[^a-f0-9]", "", did)
    if did in _packing:
        return {"running": True}
    e = DESIGNS / did / "pack" / "error.txt"
    if e.exists():
        return {"error": e.read_text()}
    f = DESIGNS / did / "pack" / "pack.json"
    return json.loads(f.read_text()) if f.exists() else {"none": True}


def pack_file(did, name):
    did = re.sub(r"[^a-f0-9]", "", did)
    f = DESIGNS / did / "pack" / Path(name).name
    return f if f.exists() and f.suffix.lower() in (".jpg", ".pdf", ".csv") else None


_packing = set()


def start_pack(did, link=""):
    did = re.sub(r"[^a-f0-9]", "", did)
    if did in _packing:
        return {"started": True}
    _packing.add(did)
    (DESIGNS / did / "pack").mkdir(parents=True, exist_ok=True)
    (DESIGNS / did / "pack" / "error.txt").unlink(missing_ok=True)

    def run():
        try:
            make_pack(did, link)
        except Exception as e:  # noqa: BLE001
            (DESIGNS / did / "pack" / "error.txt").write_text(str(e)[:300])
        finally:
            _packing.discard(did)
    threading.Thread(target=run, daemon=True).start()
    return {"started": True}


# ---------- Phase C: real data ----------

EXTRAS = TS / "extras"
LISTED = TS / "listed.json"


def _extras(n=40):
    return [(Path(f).stem, json.loads(Path(f).read_text())) for f in sorted(glob.glob(str(EXTRAS / "*.json")))[-n:]]


def _num(x):
    try:
        return int(re.sub(r"[^\d]", "", str(x)) or 0)
    except ValueError:
        return 0


def buyer_phrases(store_name=None, limit=40):
    """Etsy's own search suggestions = what buyers really type. Ranked by how many seeds suggest them."""
    ex = _extras(3)
    if not ex:
        return {"phrases": [], "note": "Collected with tomorrow's Etsy data"}
    count = {}
    for _, d in ex:
        for seed, qs in d.get("suggest", {}).items():
            for pos, q in enumerate(qs):
                count[q] = count.get(q, 0) + (10 - min(pos, 9))
    mine = " ".join(i.get("title", "").lower() for i in (store(store_name).get("top_items") or [])) if store_name else ""
    out = [{"q": q, "score": s, "have": all(w in mine for w in q.split()) if mine else None}
           for q, s in sorted(count.items(), key=lambda x: -x[1])][:limit]
    return {"phrases": out, "date": ex[-1][0]}


def rank_history(store_name, days=14):
    """Where our store first appears in Etsy search, per keyword, over the last snapshots (1 = top)."""
    files = sorted(glob.glob(str(TS / "snapshots" / "*.json")))[-days:]
    rows = {}
    for f in files:
        snap = json.loads(Path(f).read_text())
        for kw, items in snap.get("search", {}).items():
            pos = next((i + 1 for i, x in enumerate(items) if x.get("shop") == store_name), None)
            rows.setdefault(kw, []).append([Path(f).stem, pos])
    out = [{"keyword": k, "history": v, "now": v[-1][1], "best": min([p for _, p in v if p] or [None]) if any(p for _, p in v) else None}
           for k, v in rows.items()]
    out.sort(key=lambda r: (r["now"] is None, r["now"] or 99))
    return {"ranks": out}


def _signals_by_day(days=40):
    """listing id -> per day: Bestseller badge, carts, how many of our tracked searches show it, best search position."""
    out = {}
    for f in sorted(glob.glob(str(TS / "snapshots" / "*.json")))[-days:]:
        day, snap = Path(f).stem, json.loads(Path(f).read_text())
        seen = {}
        for kw, items in snap.get("search", {}).items():
            for pos, x in enumerate(items, 1):
                r = seen.setdefault(x.get("id"), {"best": False, "carts": 0, "searches": 0, "pos": None, "title": x.get("title"), "img": x.get("img")})
                r["best"] |= bool(x.get("bestseller")); r["carts"] = max(r["carts"], _num(x.get("carts")))
                r["searches"] += 1; r["pos"] = min(r["pos"] or pos, pos)
        for shop in list(snap.get("shops", {}).values()) + list(snap.get("comp", {}).values()):
            for x in shop.get("items", []):
                r = seen.setdefault(x.get("id"), {"best": False, "carts": 0, "searches": 0, "pos": None, "title": x.get("title"), "img": x.get("img")})
                r["best"] |= bool(x.get("bestseller")); r["carts"] = max(r["carts"], _num(x.get("carts")))
        for lid, r in seen.items():
            out.setdefault(lid, []).append([day, r])
    return out


def listing_signals(store_name):
    """Our listings: Bestseller badge, people with it in their cart, and how often Etsy search shows it (vs a week ago)."""
    sig = _signals_by_day(10)
    snap = _snapshot().get("shops", {}).get(store_name, {})
    out = []
    for it in snap.get("items", []):
        ser = sig.get(it.get("id"))
        if not ser or not it.get("id") or (it.get("title") or "").lower().startswith("create new collection"):
            continue
        now, old = ser[-1][1], (ser[-8][1] if len(ser) >= 8 else ser[0][1])
        out.append({"id": it.get("id"), "title": it.get("title"), "img": it.get("img"), "price": it.get("price"),
                    "best": now["best"], "carts": now["carts"], "searches": now["searches"], "pos": now["pos"],
                    "searches_week": now["searches"] - old["searches"]})
    out.sort(key=lambda x: (-int(x["best"]), -x["carts"], -x["searches"]))
    return {"listings": out, "date": _snapshot().get("date", "")[:10]}


# learning loop: owner marks a pack as listed; the collector then follows that listing every day

def mark_listed(did, url):
    did = re.sub(r"[^a-f0-9]", "", did)
    m = re.search(r"/listing/(\d+)", url or "")
    if not m:
        raise ValueError("Paste the Etsy listing link (…/listing/123456…)")
    meta = json.loads((DESIGNS / did / "meta.json").read_text())
    rows = json.loads(LISTED.read_text()) if LISTED.exists() else []
    rows = [r for r in rows if r.get("design") != did]
    rows.append({"design": did, "listing_id": m.group(1), "url": url.split("?")[0], "store": meta.get("store"),
                 "idea": meta.get("idea"), "type": meta.get("type"), "listed_on": time.strftime("%Y-%m-%d")})
    LISTED.write_text(json.dumps(rows, indent=1))
    return {"ok": True, "listed": len(rows)}


def performance():
    """Each listed pack at day 7 and day 30: Bestseller, carts, search visibility. Feeds the next suggestions."""
    rows = json.loads(LISTED.read_text()) if LISTED.exists() else []
    sig = _signals_by_day(45)
    out = []
    for r in rows:
        series = [(d, s) for d, s in sig.get(r["listing_id"], []) if d >= r["listed_on"]]
        def at(days):
            target = time.strftime("%Y-%m-%d", time.localtime(time.mktime(time.strptime(r["listed_on"], "%Y-%m-%d")) + days * 86400))
            hit = [s for d, s in series if d >= target]
            return {"fav": hit[0]["searches"], "carts": hit[0]["carts"], "best": hit[0]["best"]} if hit else None
        last = series[-1][1] if series else None
        out.append(r | {"day7": at(7), "day30": at(30), "now": {"fav": last["searches"], "carts": last["carts"], "best": last["best"]} if last else None,
                        "days": int((time.time() - time.mktime(time.strptime(r["listed_on"], "%Y-%m-%d"))) // 86400)})
    return {"packs": out}


def lessons():
    """One short text of what worked and what didn't, for the AI prompts."""
    p = [x for x in performance()["packs"] if x.get("days", 0) >= 7 and x.get("now")]
    if not p:
        return ""
    p.sort(key=lambda x: -(x["now"]["fav"] + 3 * x["now"]["carts"] + (50 if x["now"].get("best") else 0)))
    good = [f"{x['idea']} ({x['type']}): in {x['now']['fav']} searches, {x['now']['carts']} carts{', Bestseller' if x['now'].get('best') else ''}" for x in p[:3]]
    bad = [f"{x['idea']} ({x['type']}): in {x['now']['fav']} searches" for x in p[-3:] if x not in p[:3]]
    return "What our past listings did: worked: " + "; ".join(good) + (". Weak: " + "; ".join(bad) if bad else "") + "."


# ---------- Phase D: alerts to the phone ----------

ALERT_STATE = Path(__file__).parent / "etsy_alerts.json"


def check_alerts(push):
    """Event reminders 30/14/7/3 days before, and new Bestseller badges on competitors' listings. Once each."""
    st = json.loads(ALERT_STATE.read_text()) if ALERT_STATE.exists() else {"sent": []}
    sent = set(st["sent"])
    n = 0
    try:
        r = _report()
    except (OSError, ValueError):
        return 0
    for e in r.get("events", []):
        d = e.get("days_left")
        step = min((t for t in (30, 14, 7, 3) if d is not None and 0 < d <= t), default=None)   # report may skip a day
        if step:
            key = f"ev:{e.get('name')}:{e.get('date')}:{step}"
            if key not in sent:
                push("system", f"📅 {e.get('name')} in {d} days", (e.get("prepare") or "") + " · Open Store Studio for ideas.", "Stores")
                sent.add(key); n += 1
    files = sorted(glob.glob(str(TS / "snapshots" / "*.json")))[-2:]
    if len(files) == 2:
        old, new = (json.loads(Path(f).read_text()) for f in files)
        before = {x.get("id") for v in old.get("comp", {}).values() for x in v.get("items", []) if x.get("bestseller")}
        fresh = []
        for shop, v in new.get("comp", {}).items():
            for x in v.get("items", []):
                if x.get("bestseller") and x.get("id") not in before and f"bs:{x.get('id')}" not in sent:
                    fresh.append((shop, x))
        for shop, x in fresh[:3]:
            push("system", f"🏆 {shop} got a new Bestseller", f"{x.get('title', '')[:120]} · ${x.get('price')}. See what they did.", "Stores")
            sent.add(f"bs:{x.get('id')}"); n += 1
        for shop, x in fresh[3:]:
            sent.add(f"bs:{x.get('id')}")
    st["sent"] = sorted(sent)[-2000:]
    ALERT_STATE.write_text(json.dumps(st))
    return n


def start_watch(push):
    def loop():
        while True:
            try:
                check_alerts(push)
            except Exception:  # noqa: BLE001
                pass
            time.sleep(3600)
    threading.Thread(target=loop, daemon=True).start()
