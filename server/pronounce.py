"""Pronunciation check: how clearly each word of a target sentence was said.

Whisper transcribes the learner (no hint of the target, so it can't "help"), and gives a confidence per token.
Words are lined up against the target sentence: a word that was heard as itself scores its confidence;
a word heard as something else, or missing, scores low. Weak words get IPA (espeak-ng) and a tip for
sounds Urdu speakers often swap.
"""
import difflib
import json
import os
import re
import subprocess
import tempfile

WHISPER = "/opt/homebrew/bin/whisper-cli"
ESPEAK = "/opt/homebrew/bin/espeak-ng"
NUMBERS = {"0": "zero", "1": "one", "2": "two", "3": "three", "4": "four", "5": "five", "6": "six", "7": "seven",
           "8": "eight", "9": "nine", "10": "ten", "11": "eleven", "12": "twelve", "20": "twenty"}

# (pattern in the word, tip in English, tip in Roman Urdu) for sounds Urdu speakers commonly change
TIPS = [
    ("th", "Put your tongue lightly between your teeth for 'th' — not 't' or 'd'.",
     "'th' ke liye zabaan halki si daanton ke beech rakhein — 't' ya 'd' nahi."),
    ("v", "'v': top teeth touch the bottom lip. 'w': round your lips, no teeth.",
     "'v' mein oopar ke daant neeche wale honth ko chhoote hain; 'w' mein sirf honth gol hote hain."),
    ("w", "'w': round your lips, no teeth. Don't say it like 'v'.",
     "'w' mein honth gol karein, daant nahi lagte. Ise 'v' jaisa na bolein."),
    ("ed", "Say the '-ed' ending: 'worked' = work-t, 'needed' = need-id.",
     "'-ed' ka aakhri hissa bolein: 'worked' = work-t, 'needed' = need-id."),
    ("z", "'z' buzzes like a bee — not 'j'.", "'z' bhanware jaisi awaaz hai — 'j' nahi."),
    ("sk", "Don't add a vowel before 'sk/st/sp': say 'school', not 'iss-kool'.",
     "'sk/st/sp' se pehle 'i' na lagayein: 'school' bolein, 'iss-kool' nahi."),
    ("st", "Don't add a vowel before 'st': say 'study', not 'iss-tudy'.",
     "'st' se pehle 'i' na lagayein: 'study' bolein, 'iss-tudy' nahi."),
]
GENERAL = ("Listen and copy the stress — say it slowly, then at normal speed.",
           "Sun kar zor (stress) waise hi dein — pehle aahista, phir normal raftar se.")


def _norm(w):
    w = w.lower().replace("’", "'").strip(".,!?;:\"()")
    return NUMBERS.get(w, w)


def _ipa(word):
    try:
        return subprocess.run([ESPEAK, "-q", "-v", "en-us", "--ipa", word], capture_output=True, text=True, timeout=5).stdout.strip()
    except Exception:
        return ""


def _tip(word):
    w = word.lower()
    for pat, en, ro in TIPS:
        if (pat == "ed" and w.endswith("ed")) or (pat in ("sk", "st") and w.startswith(pat)) or (pat not in ("ed", "sk", "st") and pat in w):
            return {"en": en, "roman": ro}
    return {"en": GENERAL[0], "roman": GENERAL[1]}


def score(wav, target, whisper_model):
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "a.wav")
        with open(path, "wb") as f:
            f.write(wav)
        subprocess.run([WHISPER, "-m", whisper_model, "-f", path, "-l", "en", "-ojf", "-of", os.path.join(d, "out"),
                        "-np", "-t", "6"], capture_output=True, timeout=120)
        data = json.load(open(os.path.join(d, "out.json")))

    # tokens -> heard words with the average confidence of their pieces
    heard = []
    for seg in data.get("transcription", []):
        for tok in seg.get("tokens", []):
            text = tok.get("text", "")
            if text.startswith("[_") or not text.strip():
                continue
            if text.startswith(" ") or not heard:
                heard.append([text.strip(), [tok.get("p", 0)]])
            else:
                heard[-1][0] += text
                heard[-1][1].append(tok.get("p", 0))
    heard = [(w, sum(ps) / len(ps)) for w, ps in heard if _norm(w)]
    said = " ".join(w for w, _ in heard)

    target_words = [w for w in re.findall(r"[\w'’]+", target)]
    a = [_norm(w) for w in target_words]
    b = [_norm(w) for w, _ in heard]
    words = [{"word": w, "score": 0, "heard": None} for w in target_words]
    for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, a, b, autojunk=False).get_opcodes():
        if tag == "equal":
            for k in range(i2 - i1):
                p = heard[j1 + k][1]
                if heard[j1 + k][0].strip(".,!?").isdigit():
                    p = max(p, 0.9)  # Whisper writes numbers as digits; that isn't a pronunciation problem
                words[i1 + k].update(score=round(p * 100), heard=heard[j1 + k][0])
        elif tag == "replace":
            for k in range(i2 - i1):
                j = j1 + min(k, j2 - j1 - 1)
                words[i1 + k].update(score=20, heard=heard[j][0])
        # "delete": target words that weren't heard keep score 0; extra heard words ("insert") are ignored

    for w in words:
        w["level"] = "good" if w["score"] >= 75 else "ok" if w["score"] >= 45 else "bad"
        if w["level"] != "good":
            w["ipa"] = _ipa(w["word"])
            w["tip"] = _tip(w["word"])
    overall = round(sum(w["score"] for w in words) / max(1, len(words)))
    return {"overall": overall, "said": said, "words": words}
