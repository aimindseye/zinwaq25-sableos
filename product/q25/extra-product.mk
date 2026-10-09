# Installed as vendor/extra/product.mk by scripts/stage-product.sh.
# LineageOS inherits this file first from vendor/lineage/config/common.mk.
# Only the Q25 product gets the Sable layer.
ifeq ($(TARGET_PRODUCT),lineage_Q25)
$(call inherit-product, vendor/sable/q25/sable-q25.mk)
endif
