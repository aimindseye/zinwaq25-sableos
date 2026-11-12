# SableOS gaps for the Zinwa Q25

Status: 2026-11-12. SableOS 1.0 (LineageOS 23.2, Android 16) was released on
2026-10-29. SableOS 2.0 (LineageOS 24.0, Android 17) is built from `main`:
every item in the 2.0 plan is in the code, the framework patches apply to
LineageOS 24.0 ([LINEAGE_24_PORT](LINEAGE_24_PORT.md)), and the Sable apps
build and pass their unit tests, Lint, detekt, ktlint and SonarCloud in GitHub
Actions. What 2.0 adds is in [`../releases/2.0.md`](../releases/2.0.md).

This page lists only what is still open. How each part was built and tested is
in [`implementation/`](implementation/); the device gates are in
[`QUALIFICATION.md`](QUALIFICATION.md).

## 1. Before the 2.0 release

These need the phone or the release machine, not more code:

* **Device checks.** Build `main`, flash it and record the Q4-* gates in
  [`QUALIFICATION.md`](QUALIFICATION.md); most 2.0 rows are still `NOT_RUN`
  ([#27](https://github.com/aimindseye/zinwaq25-sableos/issues/27)).
* **Sable Audio.** Check on the phone which Bluetooth codecs (LDAC, aptX, AAC)
  the Q25's audio stack really offers, and whether a USB-C DAC plays.
* **Release keys.** Sign the 2.0 image with SableOS's own keys rather than the
  LineageOS test keys (Q5 in the [roadmap](../ROADMAP.md)).
* **Updating from 1.0.** 1.0 to 2.0 keeps data only once that update is
  qualified; until then the release notes say to back up and install clean.

On hold by owner decision: the App Compatibility work and the extra look
options. The Hub is opened over apps by holding the Recents key; a swipe
gesture for it was not built.

## 2. Needs an owner decision

| Decision | Today |
|---|---|
| Attention outputs for the Q25 | `ro.sable.attention.outputs` is not set, so every attention output is off. Set it once outputs are proven on the phone. |
| Charging-limit controls (BH6) | Not allowed by the design without a device-verified backend. They stay hidden. |
| Hub permissions at first boot | Hub has no launcher entry, so it can't ask for SMS, contacts and call-log access itself; they are granted at first boot (revocable) in `default-permissions-sable-q25.xml`. Say if Hub should ask instead. |
| Settings > Sable > Sable Hub row | Settings 1103 still removes it; Hub settings open from the home's Hub and from Settings' per-app Sable Hub page. Say if the row should come back. |
| MMS sending and group replies | Sable Messages receives MMS but sends SMS only; group threads can't be answered. A follow-up if wanted. |
| Aspect profiles and rotation | Android 16 applies 16:9, 4:3 and Mini only when the display ignores apps' orientation requests, so any stored aspect profile turns that on for every app. Landscape is 270 degrees. |
| Contacts title | The design says "People"; the app is still called "Contacts". |

## 3. Known limits

* No attention output driver ships yet: Hub decides what should alert, but
  nothing drives a light until a device output is validated.
* The status LED can show green and blue only, and the board blinks it green
  by itself while charging (the kernel's LED setup is copied from the Q20
  board). So Android's charging light is off; the LED is solid green when full
  and blue for notifications and low battery ([kf-a](implementation/kf-a.md)).
* The brightness slider has no visible text label, and the focus ring stays at
  Android's 3dp.
* The boot animation is Sable's (shield and SableOS wordmark, product/q25/bootanimation); LineageOS's is replaced through TARGET_BOOTANIMATION.
* Sable app corners are a user setting (App display compatibility > Sable app
  style: Compact, the default, or Rounded). Shapes drawn through the Sable theme
  follow it. Views that hard-code a radius (parts of Sable Start, Reader, Hub,
  Calendar and Messages), the keyboard-first platform apps with their own
  palette (Camera, DisplayCompat, Tools) and Sable Mail keep their own corners.
* Keyboard type-ahead in Settings covers the Battery screens only.
* Battery and Sable Tools strings are English only.
* Connected apps in Hub still calls its ordering list "favorites", while the
  toggle is now "Hub priority".
* Titan 2 GSI, cellular and IMS work does not apply: the Q25 runs a full
  LineageOS device build with the vendor's own radio stack.
* Settings > Apps: no search by permission words or "sensitive access" filter,
  no "show system apps" in Network Manager, and the privacy row is cut at three
  labels plus "+N" ([settings-ui5](implementation/settings-ui5.md)).
* Phone: `/` does not open search (it does in Contacts), and "No contacts
  under X" is a short pop-up message, not a line in the list. Person detail
  stays LineageOS's own. The Call button stays round, the incoming-call buttons
  keep their shape, and a few upstream panels (RTT) stay white
  ([phone-contacts](implementation/phone-contacts.md)).
* Sable Messages sends SMS only (no MMS sending), and MMS download needs mobile
  data and the carrier's MMS settings.
* The lock screen keeps upstream's clock without a PIN; the Sable glance shows
  only with a PIN or password.
