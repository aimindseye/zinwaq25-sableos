# Phosphor Icons provenance (titan2-temp product lane)

The product-lane first-party icons reuse the SableOS R9 icon system. Selected glyph geometry comes from
Phosphor Icons; Sable supplies the surrounding tonal background, optical sizing, per-app accent rail and Android
resource composition. No runtime icon-library dependency and no network fetch.

- repository: https://github.com/phosphor-icons/core
- pinned commit: `2b75f3ad12b420c9504ef05df8d2564a28f8500e`
- upstream license: MIT (text in `LICENSE`, identical to the canonical SableOS copy)
- upstream copyright: Copyright (c) 2023 Phosphor Icons
- glyph weight: `assets/regular/<name>.svg` at the pinned commit; the verbatim files are in `glyphs/`
- canonical contract bound: `build/gates/r9-first-party-app-icon-contract.py` in `aimindseye/sableos` at
  `e97ea89cbd77a53390df8a14d93eb1a36531178c` (git blob `0fb3505bcb69b9ee5ba2bfc88cbb724148947291`); not modified here

`ICON_MANIFEST.tsv` is the machine-readable record: module, drawable, Phosphor glyph, sha256 of the pinned glyph
file, accent, origin, R9 equivalent, the Android `res` directory the icon is generated into (relative to the
sable-src root) and whether a pre-26 `mipmap/` fallback is needed. The five product modules (Setup, Radio
Diagnostics, Keyboard, Camera, Display Compatibility) and Sable Tools have no Panther identity, so each is a NEW
glyph in the same language (`origin=NEW_TITAN_GLYPH_PHOSPHOR_REGULAR`), not a Titan-specific replacement icon family.
The R9 common apps keep the Phosphor glyph and accent their R9 icon already used
(`origin=R9_PANTHER_GLYPH_PHOSPHOR_REGULAR`). Sable Messages and Sable Text Reader had hand-drawn art, which is
replaced by the nearest Phosphor glyph (`origin=R9_ART_REPLACED_BY_PHOSPHOR_REGULAR`; chat-text, text-align-left).

`tools/gen_first_party_icons.py` regenerates, from the pinned glyph files, every legacy R9 drawable and the
adaptive launcher icon (background, foreground, monochrome layers and `mipmap-anydpi-v26/<name>{,_round}.xml`).
`--check` fails if any generated file differs from the generator output. The Q25 `tests/run.sh` runs it.

Glyphs added for the Q25 adaptive-icon work (verbatim `assets/regular/<name>.svg` at the pinned commit, fetched
from raw.githubusercontent.com): bomb, book-open-text, calculator, calendar-dots, chat-circle-dots, chat-text,
cloud-sun, envelope-simple, grid-four, grid-nine, music-notes, text-align-left, wrench.

SableScreens is a prototype and has no product icon.
