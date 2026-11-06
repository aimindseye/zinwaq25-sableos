# Sable Phone

A new Phone app for the Q25, BlackBerry 10 style and keyboard first, that replaces the LineageOS
Dialer from Q4 (owner decision 2026-11-06: a new Sable app rather than more Dialer patches). This
is phase 1 of the plan: the phone that works. Spam screening (phase 2) and the extras (phase 3)
follow in their own changes.

```text
PACKAGE=sable-phone
APP=org.sableos.phone (sable-src/apps/titan2/platform/phone, sable-src 0138)
ROLE=android.app.role.DIALER default holder (config_defaultDialer, SableFrameworkOverlay sable_phone.xml)
SYSTEM_DIALER=Telecom incall_default_class / dialer_default_class (SableTelecomOverlay), so emergency calls bind Sable Phone's call screen
REPLACES=LineageOS Dialer (Soong "Dialer"), Q4 only (scripts/stage-product.sh); before Q4 both ship and the user picks
SPEED_DIAL_CONTRACT=unchanged from Dialer 0710 (org.sableos.dialer.speeddial, org.sableos.permission.SPEED_DIAL, org.sableos.dialer.action.SPEED_DIAL)
DATA=CallLog and ContactsContract providers only; no copy, no second database; speed dial and two settings in device-protected preferences
NETWORK=none (no INTERNET permission)
PRIVILEGED_PERMISSIONS=READ_PRIVILEGED_PHONE_STATE (*#06# IMEI)
REFERENCE=github.com/mybabysexy/bb10-dialer (PolyForm Noncommercial 1.0.0): ideas only, no code copied
HOST_TESTS=PASS (31: keys, in-call keys, speed dial, call grouping, search, formats, special codes, strings)
BUILD=gradle :phone:assembleRelease PASS (R8, 259 KB), detekt and ktlint clean
DEVICE_TESTED=NO   (gates Q4-SABLE-PHONE, Q4-SABLE-PHONE-CALLS, Q4-SABLE-PHONE-EMERGENCY)
```

## What a user gets

**Main screen** (`PhoneActivity`): three tabs, Calls, Contacts and Dial pad, on the 720x720
display, with the 2 dp accent focus outline on every row and key for the trackpad.

* Calls are grouped under Today, Yesterday and the date. Back-to-back calls with the same number
  on one day fold into one row, "Name (3)"; a missed call never folds into answered ones and is
  red. Each row says Incoming, Outgoing, Missed, Declined, Blocked or Voicemail, the number when
  the name is a contact's, the time and the length.
* Typing letters searches: contacts by any word of the name (or initials), then calls by name.
  Digits search numbers anywhere in them. With Sable private mode on (Hub), names and numbers are
  replaced by "Contact hidden", as in Dialer 0705.
* A digit (Alt layer, number row or numpad) opens the dial pad with that digit. Holding a plain
  letter speed dials it; a letter with nothing on it opens Speed dial to choose a contact.
* Enter or a trackpad click calls the row (a contact with several numbers asks which). A long
  press offers Call, Send message, Edit number before calling, Copy number, Add to contacts and
  Remove from Calls.
* The Call key calls the focused row; on the dial pad it calls the number, and on an empty dial
  pad it fills in the last number called.
* Menu: Speed dial, Space answers calls on or off, Blocked numbers (the system list), Call settings
  and Calling accounts.
* A banner offers to make Sable Phone the default when it is not (before Q4 the stock Dialer is
  still installed).

**Dial pad** (`DialPadView`): the number, up to two matching contacts to call instead, the twelve
keys with their letters, Delete (hold clears) and Call. On the hardware keyboard W E R S D F Z X C
are 1-9 and the key's own number from the key character map gives 0, `*` and `#`; holding 0
makes `+`, holding 1 calls voicemail. `*#06#` shows the IMEI of each SIM slot and
`*#*#code#*#*` is sent to the app that listens for it (TelephonyManager.sendDialerSpecialCode).

**Call screen** (`InCallActivity`, `PhoneInCallService`): always dark, over the lock screen, the
screen turned on for an incoming call.

* Ringing: the caller's name or number, Decline and Answer. The Call key answers, Space answers
  when the setting is on (default on), End declines. The press that answered is swallowed so its
  release cannot reach the controls that replace Answer.
* In a call: the timer, Mute, Speaker (Audio with a Bluetooth route list when a headset is
  connected), Keypad, Hold, Add call and Merge or Swap, over a red End call. Keys: M S H D and End.
  With the keypad open every dial key sends its DTMF tone and Back closes it.
* A second call shows under the first; selecting it swaps.
* Dual SIM with no default: the SIMs to choose from.
* The proximity sensor turns the screen off at the ear on the earpiece or a wired headset, never
  on speaker or Bluetooth.

**Notifications** (`CallNotifications`): the incoming call as a heads-up with Answer and Decline
(full screen when locked or asleep), the ongoing call with Hang up, and missed calls, which Telecom
hands to the default Phone app (`MissedCallReceiver`, only Telecom can send it) so they come from
Sable Phone with Call back. Opening Calls clears them and marks the missed calls seen. Telecom
rings and vibrates; the channels are silent.

## Image wiring

| Piece | Where |
|---|---|
| The app | `product/q25/apps.tsv` row `SablePhone`, signing `privileged` (privileged from Q4) |
| Replaces the Dialer | `scripts/stage-product.sh` adds `overrides: ["Dialer"]` at Q4, as it adds LatinIME for the keyboard |
| Default Phone app | `product/common/overlay/SableFrameworkOverlay/res/values/sable_phone.xml` (`config_defaultDialer`) |
| System dialer for Telecom | `product/common/overlay/SableTelecomOverlay` (`incall_default_class`, `dialer_default_class`); Telecom builds its system dialer component from `config_defaultDialer` and `incall_default_class` (DefaultDialerCache) and binds it for emergency calls |
| Privileged permission | `product/q25/permissions/privapp-permissions-sable-q25.xml` |
| First-boot grants | `product/q25/permissions/default-permissions-sable-q25.xml` |
| Sable Start | `CoreAppIdentity` maps `org.sableos.phone` to the Phone role (glyph, tile, quick slot, BB7 favourites); speed dial asks Sable Phone first, then the Dialer; the Hub bar opens Calls for its missed call notifications |
| Sable Hub | `org.sableos.phone` may read private mode |

The Dialer patches 0701-0710 stay: they still apply and build, but from Q4 the Dialer does not
ship, so the gates Q4-PHONE-CORE, Q4-PHONE-DIAL-KEYS, Q4-PHONE-RECENTS-PRIVACY and
Q4-PHONE-CONTACTS(-LOOK) for the Phone app are covered by the Q4-SABLE-PHONE gates instead
(the Contacts app gates are unchanged).

## Not in phase 1

Spam screening (CallScreeningService, prefix rules, STIR/SHAKEN verification badge), call notes,
visual voicemail, HD and Wi-Fi call labels, call recording, RTT and video calls, and the 18
translations. Until the translations land, Sable Phone is in English on every phone language.

## Why the BB10 dialer was only a reference

`mybabysexy/bb10-dialer` is a Compose BB10-style dialer for the Q25 and P26. Its licence is
PolyForm Noncommercial 1.0.0, so copying its code would make that part of SableOS
non-commercial; it also depends on a separate BB10 UI kit. Sable Phone takes its screen list and
flow as ideas and is written from the Sable design tokens and the Sable Dialer patches' key models
(0703-0706, 0710), which are SableOS code.
