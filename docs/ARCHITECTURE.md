# Architecture

```text
 Sable apps (common source, no Q25 fork)     sable-src/ (+ patches/sable-src) -> scripts/build-apps.sh
   Keyboard, Camera, DisplayCompat, Setup,          |   (also built and checked in GitHub Actions)
   Tools, Calculator, Hub, Weather, Mail, ...       v  hashed APKs
                                              vendor/sable/q25   (product/q25 template)
 Sable product layer  ----------------------> ro.sable.profile.id=zinwa-q25, updater off,
                                              android_app_import modules
 Q4 framework layer   ----------------------> SableLauncher (HOME), product/common/overlay,
                                              patches/framework applied to LineageOS projects
                                              (Launcher3, SystemUI, Settings, SetupWizard,
                                              Dialer, Contacts, Glimpse); reverted at Q1/Q2
                                                    ^
                                              vendor/extra/product.mk  (LineageOS hook)
                                                    ^
 LineageOS 23.2 (Android 16 QPR2)             vendor/lineage/config/common.mk
 lineage_Q25 product, unmodified              device/xelex/Q25, kernel/xelex/mt6789,
                                              hardware/mediatek, sepolicy_vndr,
                                              vendor/xelex/Q25 (TheMuppets blobs)
                                                    |
                                                    v
 Outputs                                      lineage-*-Q25.zip (renamed sableos-q25-*),
                                              boot, vendor_boot, dtbo, vbmeta images
```

## Principles (from SableOS portability rules)

1. **One common product core.** Apps are shared with the other SableOS devices.
   Device facts come from `ro.sable.profile.id` and the Q25 device profile, not
   from forked code.
2. **Bounded device adapter.** Everything Q25-specific is in `build/devices/q25.sh`,
   `device-profile/`, the local manifest, the product layer and the patch sets in
   `patches/`.
3. **Don't fork the device tree.** The official LineageOS tree is used as-is. If a
   change is needed, carry it as a reviewed patch in this repo (`patches/device/`)
   and try to upstream it to LineageOS.
4. **Fail closed.** Capabilities that aren't proven are `NO` in the adapter. There
   is no flash function until a flash contract is proven.
5. **Evidence over claims.** A phase is done when its gate has device evidence
   (`QUALIFICATION.md`).

## Why the `vendor/extra` hook

LineageOS's `vendor/lineage/config/common.mk` begins with
`$(call inherit-product-if-exists, vendor/extra/product.mk)`, so a downstream can
add packages and properties without touching LineageOS or the device tree. Our
`vendor/extra/product.mk` only acts when `TARGET_PRODUCT` is `lineage_Q25`. The
product keeps the name `lineage_Q25` because LineageOS's build (kernel, `bacon`
target, version naming) keys off a `lineage_` product name.

## Moving off LineageOS later (Q6)

An AOSP- or GrapheneOS-derived base needs replacements for what the Q25 tree
takes from LineageOS: `hardware/lineage/compat`, Lineage power/light HALs,
Lineage's kernel build glue, `LineageSDK` overlays and Lineage's sepolicy
include. That's why it comes after the device is proven on LineageOS.
