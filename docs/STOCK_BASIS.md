# Stock basis

Status: **placeholder. Fill before the first flash.**

Adapted from the SableOS Titan 2 stock-basis record. A Q25 artifact is only
described as device-specific once these fields are recorded for the device it
was tested on. Commit only normalized, non-identifying values; never serials,
IMEI, or images.

```text
retail_variant=            (Q25 / Q25 Pro / conversion kit)
board=                     (fastboot getvar product: q20_v12_factory | q20_v1_factory)
region=
stock_build_display=       (Settings > About)
stock_fingerprint=         (ro.build.fingerprint)
stock_fota_version=        (ro.fota.version, e.g. Q25_26.03.2026)
android_release=
security_patch=
bootloader_version=        (fastboot getvar version-bootloader)
baseband_version=
kernel_version=
vendor_api=                (ro.vendor.api_level)
active_slot_at_capture=
unlocked=
```

## Blob basis

The local manifest uses TheMuppets blobs extracted from
`Xelex/Xelex10_Ultra/Xelex10_Ultra:14/20240427/UP1v:user/release-keys`, the
2026-03-26 release (see `proprietary-files.txt` header in the device tree). If
your device runs a newer stock build, note it here and check modem/IMS behaviour
closely.
