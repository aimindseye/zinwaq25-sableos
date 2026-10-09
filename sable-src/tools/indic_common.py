"""Shared helpers for the Indic layout and phonetic generators: map a Devanagari character to the parallel character of
another Indic script by Unicode name (ISCII-derived blocks name parallel characters alike)."""
import unicodedata as ud

# Scripts that name the long vowels E/O "EE"/"OO" (and the short ones "E"/"O").
EE_SCRIPTS = ("TAMIL", "TELUGU", "KANNADA", "MALAYALAM", "GURMUKHI")
# Scripts with distinct short e / o letters (Gurmukhi has only the long pair).
SHORT_EO_SCRIPTS = ("TAMIL", "TELUGU", "KANNADA", "MALAYALAM")

EE_MAP = {"LETTER E": "LETTER EE", "LETTER O": "LETTER OO", "LETTER SHORT E": "LETTER E", "LETTER SHORT O": "LETTER O",
          "VOWEL SIGN E": "VOWEL SIGN EE", "VOWEL SIGN O": "VOWEL SIGN OO",
          "VOWEL SIGN SHORT E": "VOWEL SIGN E", "VOWEL SIGN SHORT O": "VOWEL SIGN O"}

# Name differences for signs.
SIGN_ALIAS = {"GURMUKHI": {"SIGN ANUSVARA": "SIGN BINDI", "SIGN CANDRABINDU": "SIGN ADAK BINDI"}}

def map_char(ch, script):
    try: name = ud.name(ch)
    except ValueError: return None
    if not name.startswith("DEVANAGARI "): return None
    rest = name[len("DEVANAGARI "):]
    if script in EE_SCRIPTS: rest = EE_MAP.get(rest, rest)
    rest = SIGN_ALIAS.get(script, {}).get(rest, rest)
    for cand in (rest, rest.replace("LETTER", "VOWEL", 1)):
        try: return ud.lookup(script + " " + cand)
        except KeyError: pass
    if rest == "OM":
        try: return ud.lookup(script + " OM")
        except KeyError: pass
    return None

def derive_token(tok, script):
    out = ""
    for ch in tok:
        m = map_char(ch, script)
        if m is None: return None
        out += m
    return out
