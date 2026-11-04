# Accessibility on SableOS

SableOS keeps every accessibility option that LineageOS ships and adds one Sable control: a
text size choice. It does not include Google's accessibility apps, so a screen reader is an
add-on, like [microG](MICROG.md).

## Text size

Settings > Display > Text size, and the Theme step in setup, offer four sizes:

| Choice | Font scale | Notes |
|---|---|---|
| Small | 0.9× | More lines on the square screen |
| Medium | 1.0× | The default |
| Large | 1.15× | |
| Larger | 1.3× | The biggest size SableOS lays out and tests on the 411 dp screen |

Sable apps switch to their compact layouts as the text grows, so labels still fit at Larger
(`SableResponsive`, `LARGE_FONT_SCALE = 1.3`). Android's Display size and text page is still
there, with its Font size slider (up to 2×), bold text and high contrast text. Sizes above
Larger work, but stock Android screens may clip on the square display. A size set there shows
as "custom" in Text size.

Display size (density) is a different setting: SableOS ships 280 for the Q25's panel, and a
larger Display size shrinks the screen to fewer dp, which clips far more than text size does.
See [DISPLAY](../device-profile/DISPLAY.md).

Patches: Settings 1601, SetupWizard 0415. Qualification: Q4-TEXT-SIZE.

## Speech

SableOS ships one text-to-speech engine, eSpeak NG 1.52.0 (Sable Speech), built from source
and working offline in about 100 languages. It is the only engine on the phone, so Android
uses it by default: Text Reader, Maps and any app that speaks use it with no setup. Change its
voice, rate and pitch in Settings > Accessibility > Text-to-speech output. It has no app grid
icon and requests no permission. An engine installed later (for example from F-Droid) can be
chosen on the same page.

eSpeak NG sounds robotic next to Google's voices, but it is small and fully local.
Source and licence: `sable-src/apps/r8/espeak` (GPL-3.0). Qualification: Q4-SPEECH.

### Speak what's focused

Text Reader adds a small accessibility service, Speak what's focused. Turn it on in
Settings > Accessibility. As you move with the trackpad or keys, it speaks the button, setting
or field that takes focus, for example "Wi-Fi, switch, on" or "Search, edit box". It is not a
screen reader:

- Touch and keys work exactly as they do without it. It does not turn on explore by touch and
  does not intercept keys, so typing, shortcuts and the trackpad are unchanged.
- It speaks only what takes focus. It does not read whole screens, notifications or web pages.
  To hear a page of text, share it to Text Reader or use Text Reader's Play button.
- It is quiet while the phone is locked, so a PIN or a lock screen notification is never read
  aloud. It names a password field but never reads what is typed in it.
- It stops talking when the screen turns off.

Qualification: Q4-SPEAK-FOCUS.

## What is included

Settings > Accessibility has Android's own options: magnification, colour correction, colour
inversion, extra dim, remove animations, caption preferences, mono audio and audio balance,
time to take action, the accessibility shortcut, and for the hardware keyboard sticky, slow
and bounce keys.

## What is not included

TalkBack, Select to Speak and Switch Access are part of Google's Android Accessibility Suite.
LineageOS and SableOS do not ship it. Apart from Speak what's focused, SableOS does not bundle
a full screen reader, because none has been tested with the Q25's keyboard and trackpad yet.

## Adding a screen reader

Any screen reader from F-Droid or the developer's site installs like a normal app. Turn it on
in Settings > Accessibility > Downloaded apps. SableOS does not recommend one yet: none has been
qualified on the Q25. Report problems with Sable's keys or focus as a
GitHub issue. On the Q25, the Recents key still opens Overview while an accessibility
service holds the keyboard ([KEYMAP](../device-profile/KEYMAP.md)).
