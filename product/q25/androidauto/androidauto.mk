# Android Auto stub built into the image (SABLE_Q25_ANDROID_AUTO=YES, off by default,
# Q4 and later, only together with SABLE_Q25_MICROG=YES). Staged to
# vendor/sable/q25/androidauto by scripts/stage-product.sh, which includes this file
# only when the APK passed its sha256 check (product/q25/androidauto/androidauto.tsv).
PRODUCT_PACKAGES += \
    AndroidAutoStub
