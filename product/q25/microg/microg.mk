# microG and Aurora Store (SABLE_Q25_MICROG=YES, the default, from Q4).
# Staged to vendor/sable/q25/microg by scripts/stage-product.sh, which includes
# this file from sable-q25-framework.mk only when the APKs passed their sha256
# check (product/q25/microg/prebuilts.tsv). Remove it with SABLE_Q25_MICROG=NO.
PRODUCT_PACKAGES += \
    GmsCore \
    FakeStore \
    AuroraStore

# frameworks/base 0550: microG's restricted signature spoofing (LineageOS
# ComputerEngine.isMicrogSigned) also on user builds. ro.sable.microg tells
# Sable apps and the qualification checks that microG is on the image.
PRODUCT_PRODUCT_PROPERTIES += \
    ro.sable.microg=true \
    ro.sable.microg.spoofing=true
