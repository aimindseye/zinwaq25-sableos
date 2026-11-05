# Accessibility on SableOS

SableOS keeps every accessibility option that LineageOS ships and adds three Sable size
controls: Display size, Text size and Lock screen size, each a row of "A" tiles. It does not include Google's accessibility apps, so a screen reader is an
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

In Settings the four sizes are a row of "A" tiles, small to large: move along them with the
trackpad or arrow keys and press Enter or Space (or tap) to choose. TalkBack reads the chosen
one as "Large, selected".

## Display size

Settings > Display > Display size has five tiles around the panel's density: Smaller, Small,
Default, Large and Larger (0.86 to 1.14 times; on the Q25 241, 260, 280, 300 and 319 dpi, so
the screen is 478 to 361 dp wide). Default clears any forced density. Display size makes
everything bigger, not only text, and leaves fewer dp: Default is what SableOS lays out and
tests, and Large and Larger may clip stock Android screens on the square display. Android's
Display size and text page sets the same density; a value set there selects no tile. See
[DISPLAY](../device-profile/DISPLAY.md).

## Lock screen size

Settings > Display > Lock screen size has four tiles: Small, Default, Large and Larger
(0.85 to 1.3 times). It sizes the PIN pad's keys and digits and the locked glance's text and
peek icons. On the Q25 the PIN pad is compact at every size: wide, short keys (44 dp high at
Default) so the message, PIN row, all four rows of keys and the emergency button fit the
411 dp screen. Keys stop growing where the screen is full, so Large and Larger mainly enlarge
the digits; Small gives smaller keys with more space around them. The lock screen clock keeps
its size.

Patches: Settings 1601 and 1603, SetupWizard 0415, frameworks/base 0315. Qualification:
Q4-TEXT-SIZE, Q4-SIZE-TILES, Q4-LOCK-PIN-COMPACT.

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
