# Sable product layer for the Q25

This directory is a template. `scripts/stage-product.sh` copies it into the
Android tree at `vendor/sable/q25` and installs `extra-product.mk` as
`vendor/extra/product.mk`.

```text
vendor/lineage/config/common.mk
    inherit-product-if-exists vendor/extra/product.mk     (LineageOS hook)
        ifeq TARGET_PRODUCT lineage_Q25
            inherit vendor/sable/q25/sable-q25.mk
                props: ro.sable.profile.id=zinwa-q25, updater pointed away
                -include release.mk          (generated: ro.sable.release, build source)
                -include sable-q25-apps.mk   (generated from apps.tsv)
                Android.bp                   (generated android_app_import modules)
                -include sable-q25-framework.mk  (generated; Q4+: SableLauncher, overlays)
                src/                         (Q4+: Sable Start + design sources)
                overlay/                     (Q4+: from product/common/overlay)
```

`apps.tsv` is the single list of Sable apps. Change it there; the Android.bp and
`sable-q25-apps.mk` are generated so they only name APKs that exist.

A `Q1` build stages nothing and removes `vendor/extra/product.mk`, so you get the
plain LineageOS control image.
