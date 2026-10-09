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
file, accent, origin. The five product modules (Setup, Radio Diagnostics, Keyboard, Camera, Display Compatibility)
have no Panther identity, so each is a NEW glyph in the same language (`origin=NEW_TITAN_GLYPH_PHOSPHOR_REGULAR`),
not a Titan-specific replacement icon family. `tools/gen_first_party_icons.py` regenerates every drawable from
the pinned glyph files; the static gate fails if a drawable differs from the generator output.

SableScreens is a prototype and has no product icon.
