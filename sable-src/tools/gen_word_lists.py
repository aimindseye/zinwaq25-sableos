#!/usr/bin/env python3
"""Generates the keyboard's suggestion data (assets/words_<lang>.tsv, assets/emoji_en.tsv).

Inputs (see assets/words.NOTICE.txt for sources and sha256), run from a directory that holds them:
  en es fr de it nl sv pl tr pt_BR   the AOSP LatinIME <lang>_wordlist.combined.gz files, gunzipped, under those names
  fw_<lang>.txt for hi bn ta te ml ur  hermitdave/FrequencyWords content/2018/<lang>/<lang>_50k.txt (or _full.txt)
  gemoji.json                        github/gemoji db/emoji.json
Output: `word<TAB>frequency` per language (Latin: the 40 000 most frequent plain words, no profanity-flagged,
non-word or numeric entries; Indic and Urdu: every word in the language's script seen at least three times), and
`emoji<TAB>keyword keyword ...`. Edit `out` below, then run it.
"""
import re,sys,json,os
out='/home/user/zinwaq25-sableos/sable-src/apps/titan2/platform/keyboard/src/main/assets'
src={'en':'en','es':'es','fr':'fr','de':'de','pt':'pt_BR','it':'it','nl':'nl','sv':'sv','pl':'pl','tr':'tr'}
LIMIT=40000
for lang,f in src.items():
    ws={}
    for line in open(f,encoding='utf-8'):
        m=re.match(r'\s*word=([^,]+),f=(\d+),flags=([^,]*)(.*)',line)
        if not m: continue
        w,fr,fl,rest=m.group(1),int(m.group(2)),m.group(3),m.group(4)
        if 'not_a_word' in rest or 'possibly_offensive' in rest or fr==0: continue
        if not re.fullmatch(r"[^\W\d_]+(?:['’-][^\W\d_]+)*",w): continue
        if len(w)>24: continue
        ws[w]=max(fr,ws.get(w,0))
    items=sorted(ws.items(),key=lambda x:(-x[1],x[0]))[:LIMIT]
    with open(f'{out}/words_{lang}.tsv','w',encoding='utf-8') as o:
        for w,fr in items: o.write(f'{w}\t{fr}\n')
    print(lang,len(ws),len(items),items[-1])

# ---- Indic and Urdu lists from hermitdave/FrequencyWords (content/2018/<lang>/<lang>_50k.txt or _full.txt)
# Input names in the working directory: fw_<lang>.txt. Only words wholly in the language's script block, seen at
# least MIN_COUNT times (the source is subtitle text, so rare entries are mostly noise), at most LIMIT kept.
INDIC = {
    'hi': [(0x0900, 0x097F)], 'bn': [(0x0980, 0x09FF)], 'ta': [(0x0B80, 0x0BFF)],
    'te': [(0x0C00, 0x0C7F)], 'ml': [(0x0D00, 0x0D7F)],
    'ur': [(0x0600, 0x06FF), (0x0750, 0x077F), (0xFB50, 0xFDFF), (0xFE70, 0xFEFF)],
}
MIN_COUNT = 3
JOINERS = {0x200C, 0x200D}
import unicodedata
def in_script(w, blocks):
    return all(any(a <= ord(c) <= b for a, b in blocks) or ord(c) in JOINERS for c in w)
def wordlike(w):
    cats = [unicodedata.category(c) for c in w]
    return cats[0][0] == 'L' and all(k[0] in 'LM' or ord(c) in JOINERS for k, c in zip(cats, w))
for lang, blocks in INDIC.items():
    ws = {}
    for line in open(f'fw_{lang}.txt', encoding='utf-8'):
        parts = line.split()
        if len(parts) != 2 or not parts[1].isdigit():
            continue
        w, n = parts[0], int(parts[1])
        if n >= MIN_COUNT and 2 <= len(w) <= 24 and in_script(w, blocks) and wordlike(w):
            ws[w] = ws.get(w, 0) + n
    items = sorted(ws.items(), key=lambda x: (-x[1], x[0]))[:LIMIT]
    with open(f'{out}/words_{lang}.tsv', 'w', encoding='utf-8') as o:
        for w, n in items:
            o.write(f'{w}\t{n}\n')
    print(lang, len(ws), len(items), items[:3], items[-1])

# emoji
d=json.load(open('gemoji.json',encoding='utf-8'))
with open(f'{out}/emoji_en.tsv','w',encoding='utf-8') as o:
    n=0
    for e in d:
        kws=[]
        for k in e['aliases']+e.get('tags',[])+[e['description']]:
            for p in re.split(r'[ _]+',k.lower()):
                p=p.strip(':')
                if re.fullmatch(r"[a-z0-9+\-']{2,}",p) and p not in kws: kws.append(p)
        o.write(e['emoji']+'\t'+' '.join(kws)+'\n'); n+=1
    print('emoji',n)
