# Patches

* `sable-src/` Q25 changes to the imported Sable app sources (the `zinwa-q25`
  keyboard and camera profiles). `scripts/import-sable-sources.sh` applies them
  in name order after every import. Regenerate a patch with `git diff` against a
  fresh import when you change these files.

Reserved:

* `device/` changes to the LineageOS Q25 device tree, carried as `git
  format-patch` files and applied by a future `scripts/apply-patches.sh`. Prefer
  upstreaming to LineageOS.
* `framework/` the SableOS GrapheneOS patch set (`sableos/patches/android-17-
  grapheneos-2026081300`) retargeted to LineageOS 23.2 in phase Q4.
