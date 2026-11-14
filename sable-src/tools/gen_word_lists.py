#!/usr/bin/env python3
"""Generates the keyboard's suggestion data (assets/words_<lang>.tsv, assets/emoji_en.tsv).

Inputs (see assets/words.NOTICE.txt for sources and sha256), run from a directory that holds them:
  en es fr de it nl sv pl tr pt_BR   the AOSP LatinIME <lang>_wordlist.combined.gz files, gunzipped, under those names
  gemoji.json                        github/gemoji db/emoji.json
Output: the 40 000 most frequent plain words per language (no profanity-flagged, non-word or numeric entries) as
`word<TAB>frequency`, and `emoji<TAB>keyword keyword ...`. Edit `out` below, then run it.
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
