# Generates product/q25/wallpaper/sable_default_wallpaper.png (720x720, the Q25 panel):
#   python3 -I product/q25/wallpaper/make_wallpaper.py product/q25/wallpaper/sable_default_wallpaper.png
import sys, math
from PIL import Image
W=H=720
img=Image.new("RGB",(W,H))
px=img.load()
# 4x4 ordered (Bayer) dither: avoids banding on the 8-bit panel, same output every run.
BAYER=((0,8,2,10),(12,4,14,6),(3,11,1,9),(15,7,13,5))
# Base #121212 (Start's BB10 background), lifted toward slate at the top and a
# faint BB10-blue glow low right, so it reads as Sable but never fights white text.
for y in range(H):
    for x in range(W):
        t=y/(H-1)
        r=0x1A+(0x10-0x1A)*t; g=0x1E+(0x10-0x1E)*t; b=0x24+(0x12-0x24)*t
        d=math.hypot(x-W*0.85,y-H*0.9)/(W*0.75)
        glow=max(0.0,1-d)**2*0.22
        g+=glow*0x50; b+=glow*0x78
        n=(BAYER[y%4][x%4]+0.5)/16
        px[x,y]=(int(r+n),int(g+n),int(b+n))
img.save(sys.argv[1],optimize=True)
