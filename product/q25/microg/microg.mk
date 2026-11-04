# microG built into the image (SABLE_Q25_MICROG=YES, off by default from 2.0, Q4 and later).
# Staged to vendor/sable/q25/microg by scripts/stage-product.sh, which includes
# this file from sable-q25-framework.mk only when the APKs passed their sha256
# check (product/q25/microg/prebuilts.tsv). Remove it with SABLE_Q25_MICROG=NO.
PRODUCT_PACKAGES += \
    GmsCore \
    FakeStore

# ro.sable.microg tells Sable apps and the qualification checks that microG is
# built into the image. ro.sable.microg.spoofing (frameworks/base 0550) is set
# for every Q4+ image by scripts/stage-product.sh, so the add-on works too.
PRODUCT_PRODUCT_PROPERTIES += \
    ro.sable.microg=true
