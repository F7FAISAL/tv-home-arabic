#!/usr/bin/env python3
"""Build launcher res/raw/espn_clubs.json: our TheSportsDB club id -> ESPN team id. The launcher asks ESPN's
all-competitions schedule for that id (EspnFallback), so no competition list is needed. Matching is by name against
each ESPN league's team list (the Champions League's for clubs outside the six leagues); unmatched clubs are printed
for a manual look.

    python3 tools/espn_clubs.py launcher/app/src/main/res/raw/clubs.json launcher/app/src/main/res/raw/espn_clubs.json
"""
import json
import sys
import unicodedata
import urllib.request

CLUBS, OUT = sys.argv[1], sys.argv[2]
LEAGUE_CODE = {"4668": "ksa.1", "4328": "eng.1", "4335": "esp.1", "4332": "ita.1", "4331": "ger.1", "4334": "fra.1",
               "4480": "uefa.champions"}
# tsdb id -> espn id, for names the matcher cannot pair or pairs wrongly (checked by hand against ESPN's lists)
MANUAL = {"133738": "86",      # Real Madrid (the matcher drops "Real" and finds Atlético)
          "133714": "160",     # Paris Saint-Germain (not Paris FC)
          "133619": "331",     # Brighton & Hove Albion
          "133654": "122",     # Köln = FC Cologne
          "133719": "169",     # Rennes = Stade Rennais
          "135497": "2980",    # Bodø/Glimt
          "133768": "148",     # PSV Eindhoven
          "138341": "21922"}   # Sabah Baku = Sabah FK


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.load(r)


def norm(n):
    n = unicodedata.normalize("NFD", n)
    n = "".join(c for c in n if unicodedata.category(c) != "Mn").lower()
    for a, b in (("-", " "), (".", " "), ("'", "")):
        n = n.replace(a, b)
    drop = {"fc", "cf", "afc", "sc", "ac", "ssc", "as", "club", "de", "saudi", "1", "04", "05", "1899", "1846", "1907",
            "1909", "1910", "1913", "1948", "calcio", "us", "ogc", "rc", "stade", "olympique", "sv", "vfb", "vfl", "tsg", "fsv",
            "bv", "borussia", "ud", "rcd", "ca", "real", "sporting", "sd", "cd"}
    words = [w for w in n.split() if w not in drop]
    return " ".join(words)


ALIAS = {"paris sg": "paris saint germain", "inter milan": "internazionale", "bayern munich": "bayern munich",
         "athletic bilbao": "athletic", "deportivo de a coruna": "deportivo la coruna", "neom": "neom",
         "wolverhampton wanderers": "wolverhampton wanderers", "brighton and hove albion": "brighton hove albion",
         "koln": "cologne", "hamburg": "hamburg", "mainz": "mainz", "paderborn": "paderborn", "psv eindhoven": "psv"}

clubs = json.load(open(CLUBS))["leagues"]
espn_teams = {}
for lid, code in LEAGUE_CODE.items():
    d = get(f"https://site.api.espn.com/apis/site/v2/sports/soccer/{code}/teams?limit=200")
    espn_teams[code] = [(t["team"]["id"], t["team"]["displayName"]) for t in d["sports"][0]["leagues"][0]["teams"]]

out, missing = {}, []
for lg in clubs:
    code = LEAGUE_CODE.get(lg["id"], "uefa.champions")
    for tid, en in lg["teams"]:
        tid = str(tid)
        a = norm(ALIAS.get(en.lower(), en))
        hit = MANUAL.get(tid)
        if hit is None:
            for eid, name in espn_teams[code]:
                b = norm(name)
                if a == b or (len(a) > 3 and len(b) > 3 and (a in b or b in a)):
                    hit = eid
                    break
        if hit is None:
            missing.append((code, tid, en))
            continue
        e = out.setdefault(tid, {"espn": hit, "name": en})
        if e["espn"] != hit:
            print(f"conflict {en}: {e['espn']} vs {hit} in {code}")
json.dump({"source": "ESPN public site API, team ids per league (built from res/raw/clubs.json)", "clubs": out},
          open(OUT, "w"), ensure_ascii=False, indent=1)
print(f"mapped {len(out)} clubs; unmatched {len(missing)}")
for m in missing:
    print("  unmatched", m, "| ESPN has:", [n for _, n in espn_teams[m[0]] if norm(n)[:3] == norm(m[2])[:3]][:4])
