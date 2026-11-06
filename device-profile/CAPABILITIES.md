# Zinwa Q25 capability profile

Template: `platform_sable/docs/device-capabilities/ZINWA_Q27.md`. The Q25 does
not inherit Titan 2 or Q27 evidence.

Files in this folder:

* `CAPABILITIES.md` (this file): summary and status per capability.
* [`DISPLAY.md`](DISPLAY.md): screen geometry, density and how it differs from Titan 2.
* [`KEYMAP.md`](KEYMAP.md): physical key scan codes, to capture on hardware.
* [`BATTERY.md`](BATTERY.md) and [`battery/zinwa-q25.conf`](battery/zinwa-q25.conf): battery
  health and charging capabilities read by Settings > Battery; all `UNKNOWN` until measured.

Where the profile lives in Sable apps (selected by `ro.sable.profile.id=zinwa-q25`):

* Keyboard: `KeyLayout.ZinwaQ25Kcm` in `sable-src/apps/titan2/platform/keyboard/.../core/KeyLayout.kt`.
* Camera: `CameraDeviceProfile.ZinwaQ25` in `sable-src/apps/titan2/platform/camera/.../core/DeviceProfile.kt`.
* DisplayCompat: no table; it reads the real display size at runtime.
* Settings > Battery: `/system_ext/etc/sable/battery/zinwa-q25.conf` from `battery/zinwa-q25.conf`.

```text
DEVICE=zinwa-q25
SUPPORT_LEVEL=RESEARCH
ASSURANCE=N1_INTEGRATED_VENDOR_BSP_PORT (target), not yet reached
INTERACTION=keyboard-first
DISPLAY_CLASS=SQUARE_KEYBOARD_LCD (720x720, 3.5", density 280 (vendor 193); see DISPLAY.md)
PANEL=LCD (no AOD expected)
KEYBOARD_PROFILE=zinwa-q25 (Q25_keyboard kl/kcm; Sable KeyLayout.ZinwaQ25Kcm), NOT_VERIFIED
POINTER_PROFILE=optical trackpad, DPAD key events on Q25_keyboard (measured 2026-11-04, KEYMAP.md)
CRITICAL_TEXT_ENTRY=required, NOT_RUN
CAMERA_PROFILE=zinwa-q25 (Sable CameraDeviceProfile.ZinwaQ25), EvidenceLevel.None
TELEPHONY=LTE + MTK IMS (VoLTE/VoWiFi via hardware/mediatek), NOT_RUN
NFC=ST, NOT_RUN
FM=MT6631, NOT_RUN
BATTERY_PROFILE=zinwa-q25 (battery/zinwa-q25.conf), every fact UNKNOWN, charging mutation NOT_AUTHORIZED
BOOT=Virtual A/B, boot header v4, recovery in vendor_boot
UNLOCK=fastboot flashing unlock (per LineageOS)
RELOCK_WITH_CUSTOM_KEY=UNKNOWN
PUBLIC_FLASH=NO
```

| Capability | Status | Evidence |
|---|---|---|
| Boots LineageOS 23.2 | PASS upstream (official LineageOS device) | LineageOS builds; not re-run by this project |
| Boots SableOS | NOT_RUN | |
| Hardware keyboard | NOT_RUN | |
| Trackpad navigation | PASS on LineageOS 24.0 (owner, 2026-11-04: scrolls Chrome) | DPAD keys, [`KEYMAP.md`](KEYMAP.md) |
| Calls / SMS / data | NOT_RUN | |
| VoLTE | NOT_RUN | |
| Camera (rear 50 MP, front 8 MP) | NOT_RUN | |
| Wi-Fi / BT / NFC / GPS / FM | NOT_RUN | |
| USB host (OTG drives, keyboards) | PASS (drive) | owner, 2026-11-06, rooted Q25: `pm list features` has `android.hardware.usb.host` and `usb.accessory`; `dumpsys usb` port `supported_modes=dual` (seen as device/sink on a computer, `can_change_data_role=false`, so the role follows the cable or OTG adapter); no `/sys/class/typec` ports. With an OTG adapter the phone reports "USB charging connected device" and a SanDisk Phone Drive mounts and shows in Files (owner, 2026-11-06, current build before SableOS) |
| Wired video out (USB-C DisplayPort) | NO | owner, 2026-11-06: `/sys/class/drm` has only `card0-DSI-1` (the built-in panel) and writeback connectors, no DP connector; MT6789 has no DisplayPort Alt Mode. Casting is the only video out |
| Battery health facts (capacity, cycles, dates) | UNKNOWN | [`BATTERY.md`](BATTERY.md); shown as unavailable until evidence |
| Charging controls (limit, adaptive, overnight) | NOT_AUTHORIZED | BH6; capability discovery only |
