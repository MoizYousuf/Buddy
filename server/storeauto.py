"""Store Studio helpers: one-tap design + listing pack, and daily trend alerts.

auto_make: idea → Gemini design → layers → listing pack, then one notification. Nothing is listed on Etsy.
trend_check: once a day compares Etsy data with earlier days (new buyer search phrases, our rank moving,
Bestseller badges appearing on a search) and sends at most 3 notifications.
"""
import glob, json, threading, time
from pathlib import Path

import etsy

HERE = Path(__file__).parent
STATE = HERE / "storeauto.json"
_running = {}


def _state():
    try:
        return json.loads(STATE.read_text())
    except (OSError, ValueError):
        return {}


def _save(s):
    STATE.write_text(json.dumps(s, ensure_ascii=False, indent=1))


# ---------- one tap: design + layers + pack ----------

def auto_make(rid, idx, start_job, job_of, push, prompt=None):
    d = etsy.design(rid, idx, start_job, prompt)
    did = d["id"]
    _running[did] = "design"

    def run():
        t0 = time.time()
        try:
            while time.time() - t0 < 50 * 60:          # design + layers
                st = etsy.design_status(did, job_of)
                if st.get("layers") or (st["status"] in ("done", "error") and not st.get("running") and time.time() - t0 > 60 and
                                        (st["status"] == "error" or (Path(etsy.DESIGNS / did / "layers_error.txt").exists()))):
                    break
                time.sleep(20)
            st = etsy.design_status(did, job_of)
            if not st.get("files"):
                push("system", "⚠️ Design nahi bana", (st.get("note") or "Gemini se image nahi aayi")[:150], "Stores")
                return
            _running[did] = "pack"
            etsy.start_pack(did, "")
            t1 = time.time()
            while time.time() - t1 < 10 * 60:
                p = etsy.pack_info(did)
                if not p.get("running"):
                    break
                time.sleep(10)
            p = etsy.pack_info(did)
            ok = not p.get("error") and not p.get("none")
            push("system", "🛍 Design + listing ready" if ok else "🛍 Design ready (pack nahi bana)",
                 f"{st.get('idea') or 'Design'} · {len((st.get('layers') or {}).get('files', []))} layers"
                 + (" · title, tags, photos tayyar. Canva link daal kar upload karo." if ok else ""), "Stores")
        except Exception as e:  # noqa: BLE001
            push("system", "⚠️ One-tap design ruk gaya", str(e)[:150], "Stores")
        finally:
            _running.pop(did, None)

    threading.Thread(target=run, daemon=True).start()
    return {"id": did, "auto": True}


def auto_status(did):
    return {"stage": _running.get(did, "done")}


# ---------- daily trend alerts ----------

def _snaps(n=8):
    return [json.loads(Path(f).read_text()) | {"_day": Path(f).stem}
            for f in sorted(glob.glob(str(etsy.TS / "snapshots" / "*.json")))[-n:]]


def trends():
    """What changed: new buyer phrases, our rank moves, new Bestseller badges per search."""
    out = []
    ex = etsy._extras(8)
    if len(ex) >= 2:
        def phrases(d):
            return {q for qs in d.get("suggest", {}).values() for q in qs}
        new = phrases(ex[-1][1]) - set().union(*[phrases(d) for _, d in ex[:-1]])
        for q in sorted(new)[:5]:
            out.append({"kind": "phrase", "title": f"Naya search: \"{q}\"", "body": "Buyers ab ye type kar rahe hain. Is par design banao."})
    sn = _snaps()
    if len(sn) >= 2:
        a, b = sn[-2], sn[-1]
        for kw, items in b.get("search", {}).items():
            old = a.get("search", {}).get(kw, [])
            nb, ob = sum(1 for x in items if x.get("best")), sum(1 for x in old if x.get("best"))
            if nb - ob >= 3:
                out.append({"kind": "hot", "title": f"🔥 \"{kw}\" garam hai", "body": f"Bestseller badges {ob} → {nb}. Is search mein maal bik raha hai."})
        try:
            stores = [s["name"] for s in etsy.overview().get("stores", [])]
        except Exception:  # noqa: BLE001
            stores = []
        for st in stores:
            for kw, items in b.get("search", {}).items():
                now = next((i + 1 for i, x in enumerate(items) if x.get("shop") == st), None)
                was = next((i + 1 for i, x in enumerate(a.get("search", {}).get(kw, [])) if x.get("shop") == st), None)
                if now and (was is None or was - now >= 5):
                    out.append({"kind": "rank", "title": f"📈 {st} upar aaya", "body": f"\"{kw}\": " + (f"#{was} → #{now}" if was else f"ab #{now} par dikh raha hai")})
                elif was and (now is None or now - was >= 8):
                    out.append({"kind": "drop", "title": f"📉 {st} neeche gaya", "body": f"\"{kw}\": #{was} → " + (f"#{now}" if now else "pehle page se bahar")})
    return {"trends": out}


def trend_check(push):
    s = _state(); day = time.strftime("%Y-%m-%d")
    if s.get("trend_day") == day or time.strftime("%H") < "11":
        return
    s["trend_day"] = day; _save(s)
    order = {"hot": 0, "phrase": 1, "rank": 2, "drop": 3}
    for t in sorted(trends()["trends"], key=lambda t: order.get(t["kind"], 9))[:3]:
        push("system", t["title"], t["body"], "Stores")


def start_watch(push):
    def loop():
        while True:
            try:
                trend_check(push)
            except Exception:  # noqa: BLE001
                pass
            time.sleep(1800)
    threading.Thread(target=loop, daemon=True).start()
