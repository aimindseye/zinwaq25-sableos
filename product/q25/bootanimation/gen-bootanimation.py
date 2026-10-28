#!/usr/bin/env python3
"""Builds the SableOS boot animation (bootanimation.zip) for the Zinwa Q25.

Replaces LineageOS's animation through TARGET_BOOTANIMATION (sable-q25.mk). The
720x720 screen gets 360x360 frames that bootanimation scales up 2x, so the zip
stays small. part0 plays once: the Sable shield fades and grows in on the Sable
window colour, then "SableOS" appears under it. part1 loops until boot ends: an
accent bar under the wordmark sweeps left to right.

Inputs: the shield from SableSetupWizardOverlay (the same mark Setup shows) and
the Inter font. Run from the repo root:
    python3 product/q25/bootanimation/gen-bootanimation.py
"""

import io
import pathlib
import zipfile

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[3]
SHIELD = ROOT / "product/common/overlay/SableSetupWizardOverlay/res/drawable-nodpi/sableos_shield.png"
FONT = pathlib.Path("/usr/share/fonts/opentype/inter/Inter-SemiBold.otf")
OUT = pathlib.Path(__file__).resolve().parent / "bootanimation.zip"

SCREEN = 720
FRAME = 360
FPS = 15
BG = (0x05, 0x07, 0x0A)  # SableSystemTokens window (dark)
TEXT = (0xF7, 0xF9, 0xFC)
MUTED = (0x2A, 0x33, 0x3D)
ACCENT = (0x4D, 0x9C, 0xFF)  # the default Sable accent
INTRO_FRAMES = 18
WORD_FRAMES = 8
LOOP_FRAMES = 24
SHIELD_SIZE = 150
SHIELD_TOP = 70
WORD_TOP = 240
BAR_TOP = 292
BAR_W = 140
BAR_H = 3
SEGMENT = 36


def ease(t):
    return 1 - (1 - t) ** 3


def base(shield_alpha, shield_scale, word_alpha):
    img = Image.new("RGB", (FRAME, FRAME), BG)
    if shield_alpha > 0:
        size = max(1, round(SHIELD_SIZE * shield_scale))
        mark = SHIELD_IMG.resize((size, size), Image.LANCZOS)
        r, g, b, a = mark.split()
        a = a.point(lambda v: round(v * shield_alpha))
        mark = Image.merge("RGBA", (r, g, b, a))
        x = (FRAME - size) // 2
        y = SHIELD_TOP + (SHIELD_SIZE - size) // 2
        img.paste(mark, (x, y), mark)
    if word_alpha > 0:
        draw = ImageDraw.Draw(img)
        colour = tuple(round(BG[i] + (TEXT[i] - BG[i]) * word_alpha) for i in range(3))
        w = draw.textlength("SableOS", font=WORD_FONT)
        draw.text(((FRAME - w) / 2, WORD_TOP), "SableOS", font=WORD_FONT, fill=colour)
    return img


def bar(img, offset):
    draw = ImageDraw.Draw(img)
    left = (FRAME - BAR_W) // 2
    draw.rectangle([left, BAR_TOP, left + BAR_W - 1, BAR_TOP + BAR_H - 1], fill=MUTED)
    start = left + offset
    end = min(left + BAR_W, start + SEGMENT)
    start = max(left, start)
    if end > start:
        draw.rectangle([start, BAR_TOP, end - 1, BAR_TOP + BAR_H - 1], fill=ACCENT)
    return img


def png(img):
    buf = io.BytesIO()
    img.quantize(colors=128, method=Image.Quantize.FASTOCTREE).save(buf, "PNG", optimize=True)
    return buf.getvalue()


SHIELD_IMG = Image.open(SHIELD).convert("RGBA")
WORD_FONT = ImageFont.truetype(str(FONT), 34)


def main():
    frames = {"part0": [], "part1": []}
    for i in range(INTRO_FRAMES):
        t = ease((i + 1) / INTRO_FRAMES)
        frames["part0"].append(bar(base(t, 0.85 + 0.15 * t, 0), -SEGMENT))
    for i in range(WORD_FRAMES):
        frames["part0"].append(bar(base(1, 1, (i + 1) / WORD_FRAMES), -SEGMENT))
    travel = BAR_W + SEGMENT
    for i in range(LOOP_FRAMES):
        frames["part1"].append(bar(base(1, 1, 1), round(i * travel / LOOP_FRAMES) - SEGMENT))

    desc = f"{SCREEN} {SCREEN} {FPS}\nc 1 0 part0 #{BG[0]:02X}{BG[1]:02X}{BG[2]:02X}\n" \
           f"c 0 0 part1 #{BG[0]:02X}{BG[1]:02X}{BG[2]:02X}\n"
    # bootanimation reads the zip in place: entries must be stored, not compressed.
    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_STORED) as z:
        z.writestr(zipfile.ZipInfo("desc.txt", (2026, 1, 1, 0, 0, 0)), desc)
        for part, imgs in frames.items():
            for n, img in enumerate(imgs):
                z.writestr(zipfile.ZipInfo(f"{part}/{n:03d}.png", (2026, 1, 1, 0, 0, 0)), png(img))
    print(f"BOOTANIMATION={OUT} bytes={OUT.stat().st_size}")


if __name__ == "__main__":
    main()
