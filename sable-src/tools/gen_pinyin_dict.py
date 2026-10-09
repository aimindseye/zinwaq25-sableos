#!/usr/bin/env python3
"""Builds the Sable keyboard's Simplified Chinese pinyin dictionary asset, offline and reproducibly.

Usage: gen_pinyin_dict.py <out.tsv>            (writes the asset)
       gen_pinyin_dict.py --check <asset.tsv>  (regenerates in memory; exit 1 unless byte-identical)

Inputs are the two vendored, hash-pinned archives named in third_party/pinyin_zh/PROVENANCE.env, read directly
with zipfile. No other data input exists. This script performs no network access and has no frequency or
data-path override.

CC-CEDICT: the entry's own simplified form and bracketed pinyin are used. Pinyin is lowercased, tone digits are
removed, u: becomes v, syllable separators are removed, and only keys that are entirely [a-z] are kept.
Unihan: only the properties listed in PROVENANCE.env (UNIHAN_ADMITTED_PROPERTIES) are read from
Unihan_Readings.txt. Tone-marked readings are decomposed, tone marks are dropped, the diaeresis becomes v and
the circumflex is dropped.

Output lines: pinyin<TAB>word<TAB>weight. The weight is a deterministic neutral ranking weight by input and
property, NOT a usage frequency (NUMERIC_WEIGHT_IS_CORPUS_FREQUENCY=NO). Rows are unique per (pinyin, word)
(the highest weight wins) and sorted by pinyin ascending, weight descending, then word by code point.
"""
import hashlib
import io
import os
import re
import sys
import unicodedata
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROVENANCE = os.path.join(ROOT, 'third_party', 'pinyin_zh', 'PROVENANCE.env')
HAN = re.compile(r'^[一-鿿]+$')
KEY = re.compile(r'^[a-z]+$')
CEDICT_LINE = re.compile(r'^(\S+) (\S+) \[([^\]]*)\] /')
CEDICT_SYLLABLE = re.compile(r'^([A-Za-z]+|[A-Za-z]*u:[A-Za-z]*)[1-5]$')
MAX_WORD_CHARS = 6
TONE_MARKS = {0x0300, 0x0301, 0x0304, 0x030C}
DIAERESIS = 0x0308
CIRCUMFLEX = 0x0302
EXTRA_LETTERS = {'ɡ': 'g'}
WEIGHT_UNIHAN_TGHZ2013 = 30
WEIGHT_UNIHAN_MANDARIN = 20
WEIGHT_CEDICT = 10
UNIHAN_WEIGHTS = {'kTGHZ2013': WEIGHT_UNIHAN_TGHZ2013, 'kMandarin': WEIGHT_UNIHAN_MANDARIN}


def die(msg):
    sys.stderr.write('PINYIN_GENERATOR_FAIL: %s\n' % msg)
    sys.exit(1)


def load_pins():
    pins = {}
    for line in open(PROVENANCE, encoding='utf8'):
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, v = line.split('=', 1)
            pins[k] = v
    return pins


def sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()


def open_input(pins, prefix, zip_key, member_key, member_hash_key):
    zpath = os.path.join(ROOT, pins[zip_key])
    raw = open(zpath, 'rb').read()
    if sha256_bytes(raw) != pins[prefix + '_ZIP_SHA256']:
        die('%s zip sha256 differs from the pin' % prefix)
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        data = z.read(pins[member_key])
    if sha256_bytes(data) != pins[member_hash_key]:
        die('%s member sha256 differs from the pin' % prefix)
    return data.decode('utf8')


def cedict_key(pinyin_field):
    out = []
    for syl in pinyin_field.split(' '):
        if not CEDICT_SYLLABLE.match(syl):
            return None
        out.append(syl[:-1].lower().replace('u:', 'v'))
    key = ''.join(out)
    return key if KEY.match(key) else None


def unihan_key(reading):
    out = []
    for ch in unicodedata.normalize('NFD', reading):
        cp = ord(ch)
        if cp in TONE_MARKS or cp == CIRCUMFLEX:
            continue
        out.append('v' if cp == DIAERESIS else EXTRA_LETTERS.get(ch, ch))
    key = ''.join(out).replace('uv', 'v')
    return key if KEY.match(key) else None


def cedict_rows(text):
    rows = []
    for line in text.split('\n'):
        if not line or line.startswith('#'):
            continue
        m = CEDICT_LINE.match(line)
        if not m:
            continue
        word = m.group(2)
        if not HAN.match(word) or len(word) > MAX_WORD_CHARS:
            continue
        key = cedict_key(m.group(3))
        if key:
            rows.append((key, word, WEIGHT_CEDICT))
    return rows


def unihan_rows(text, admitted):
    rows = []
    for line in text.split('\n'):
        if not line or line.startswith('#'):
            continue
        parts = line.split('\t')
        if len(parts) != 3 or parts[1] not in admitted:
            continue
        cp = int(parts[0][2:], 16)
        word = chr(cp)
        if not HAN.match(word):
            continue
        for tok in parts[2].split(' '):
            reading = tok.split(':', 1)[1] if parts[1] == 'kTGHZ2013' else tok
            for r in reading.split(','):
                key = unihan_key(r)
                if key:
                    rows.append((key, word, UNIHAN_WEIGHTS[parts[1]]))
    return rows


def build(pins):
    admitted = pins['UNIHAN_ADMITTED_PROPERTIES'].split(',')
    if sorted(admitted) != sorted(UNIHAN_WEIGHTS):
        die('UNIHAN_ADMITTED_PROPERTIES must equal %s' % sorted(UNIHAN_WEIGHTS))
    ce = open_input(pins, 'CC_CEDICT', 'CC_CEDICT_ARTIFACT_PATH', 'CC_CEDICT_MEMBER', 'CC_CEDICT_MEMBER_SHA256')
    uh = open_input(pins, 'UNIHAN', 'UNIHAN_ARTIFACT_PATH', 'UNIHAN_READINGS_PATH', 'UNIHAN_READINGS_SHA256')
    best = {}
    for key, word, w in cedict_rows(ce) + unihan_rows(uh, admitted):
        if best.get((key, word), 0) < w:
            best[(key, word)] = w
    rows = sorted(best.items(), key=lambda kv: (kv[0][0], -kv[1], [ord(c) for c in kv[0][1]]))
    return ''.join('%s\t%s\t%d\n' % (k, w, wt) for (k, w), wt in rows).encode('utf8'), len(rows)


def main(argv):
    pins = load_pins()
    body, n = build(pins)
    if len(argv) == 3 and argv[1] == '--check':
        if open(argv[2], 'rb').read() != body:
            die('asset differs from a fresh regeneration')
        print('PINYIN_CORPUS_REGENERATION=PASS_BYTE_IDENTICAL entries=%d' % n)
        return
    if len(argv) != 2:
        die('usage: gen_pinyin_dict.py <out.tsv> | --check <asset.tsv>')
    if n != int(pins['PINYIN_OUTPUT_ENTRIES']) or sha256_bytes(body) != pins['PINYIN_OUTPUT_SHA256']:
        sys.stderr.write('PINYIN_GENERATOR_NOTE: output %d entries sha256 %s differs from the pins\n' % (n, sha256_bytes(body)))
    with open(argv[1], 'wb') as fh:
        fh.write(body)
    print('PINYIN_GENERATOR_WROTE entries=%d bytes=%d sha256=%s' % (n, len(body), sha256_bytes(body)))


if __name__ == '__main__':
    main(sys.argv)
