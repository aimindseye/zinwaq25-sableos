# Zinwa Q25 capability profile

Template: `platform_sable/docs/device-capabilities/ZINWA_Q27.md`. The Q25 does
not inherit Titan 2 or Q27 evidence.

```text
DEVICE=zinwa-q25
SUPPORT_LEVEL=RESEARCH
ASSURANCE=N1_INTEGRATED_VENDOR_BSP_PORT (target), not yet reached
INTERACTION=keyboard-first
DISPLAY_CLASS=SQUARE_COMPACT_KEYBOARD (720x720, 3.5", density 193)
PANEL=LCD (no AOD expected)
KEYBOARD_PROFILE=zinwa-q25 (Q25_keyboard kl/kcm), NOT_VERIFIED
POINTER_PROFILE=optical trackpad, event type UNKNOWN
CRITICAL_TEXT_ENTRY=required, NOT_RUN
CAMERA_PROFILE=zinwa-q25, EvidenceLevel.None
TELEPHONY=LTE + MTK IMS (VoLTE/VoWiFi via hardware/mediatek), NOT_RUN
NFC=ST, NOT_RUN
FM=MT6631, NOT_RUN
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
| Trackpad navigation | NOT_RUN | |
| Calls / SMS / data | NOT_RUN | |
| VoLTE | NOT_RUN | |
| Camera (rear 50 MP, front 8 MP) | NOT_RUN | |
| Wi-Fi / BT / NFC / GPS / FM | NOT_RUN | |
