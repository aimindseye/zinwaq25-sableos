# Keyboard, trackpad and keys

Status: **profile drafted from the LineageOS device tree; not verified on
hardware.** Follows `platform_sable/docs/KEYBOARD_FIRST_DEVICE_PROFILE_MODEL.md`,
`KEYBOARD_AND_POINTER_PROFILE_MODEL.md` and `NORMALIZED_KEY_INPUT_CONTRACT.md`.

## What the device tree gives us

The LineageOS tree installs three files to `/vendor/usr/`:

| File | Content |
|---|---|
| `idc/Q25_keyboard.idc` | `keyboard.layout = Q25_keyboard`, `keyboard.builtIn = 1`, `device.internal = 1` |
| `keylayout/Q25_keyboard.kl` | Based on AOSP's generic full-PC layout (481 lines): letters, digits, modifiers, DPAD, BACK (158), MENU (139), HOME (172), CALL (169/231), POWER (116), volume |
| `keychars/Q25_keyboard.kcm` | `type ALPHA` with the BlackBerry Alt legends below |

### Alt layer (from `Q25_keyboard.kcm`)

```text
Q #   W 1   E 2   R 3   T (   Y )   U _   I -   O +   P @
A *   S 4   D 5   F 6   G /   H :   J ;   K '   L "
Z 7   X 8   C 9   V ?   B !   N ,   M .   0 0
```

This matches the printed BlackBerry Classic legends. SPACE has a private-use alt
code (``) that needs checking on device.

## Sable profile `zinwa-q25`

Sable apps read `ro.sable.profile.id`; the Q25 layer sets it to `zinwa-q25`.

| Concern | Plan | Status |
|---|---|---|
| Sable Keyboard Alt layer | `KeyLayout.ZinwaQ25Kcm` holds the table above (`verified=false`); `forProfile("zinwa-q25")` selects it. The device's own kcm Alt chars still win when Android delivers them | done in source, not run on device |
| Sym key | Classic has a Sym key; scan code unknown. Capture with `getevent -lt` | unknown |
| Call / End keys | Generic kl maps CALL; End is not mapped by name. Capture scan codes; decide End = ENDCALL vs POWER | unknown |
| Menu / Back / Home | kl maps 139 MENU, 158 BACK, 172 HOME; confirm which physical keys send them | unknown |
| Trackpad | Optical trackpad; find out whether it reports as DPAD keys, a relative pointer (`REL_X/REL_Y`) or a touchpad. Sable wants DPAD focus navigation plus an optional pointer mode | unknown |
| Trackpad click | Expect `DPAD_CENTER` (353) or `BTN_MOUSE`; confirm | unknown |
| Keyboard backlight | The keyboard firmware owns its light: Sym+0 turns it on or off, and with the PaBaLF keyboard firmware Sym+Shift+1 to 9 sets the level. On a Q25 the firmware ignores host writes to the backlight register (`bbqX0kbd` logged 0x00 and 0x1E written to `REG_BKL` 0x05; the light did not change), so Settings has no switch or slider. Settings patch 0901 adds only a read-only Keyboard light row under System > Keyboard that explains those keys, shown when `ro.sable.keyboard_backlight` is true. The kernel 0901 module parameters and `product/q25/init/sable-keyboard-backlight.rc` stay in the tree but have no visible effect | host writes ignored on device; info row written, not run on device |
| Keyboard-first app shortcuts | From the q25toolbox review: Sable Calculator takes digits and operators from the keyboard (a plain key counts as its Alt character, so W E R = 1 2 3); Sable Keyboard picks candidates with Ctrl+1/2/3 or Ctrl+W/E/R and can send with Enter where the app offers Send (Settings switch, off by default); Phone in-call M mute, S speaker, D dialpad (Dialer 0703); Space answers a ringing call (setting, on by default; never hangs up) (Dialer 0704). Q25 key leads in `device-profile/KEYMAP.md` | written, not run on device |
| Critical text entry | Lock-screen PIN/password, Wi-Fi password, SIM PIN must work from the hardware keyboard and the soft-keyboard fallback (`CRITICAL_TEXT_ENTRY_GATES.md`) | gate in `QUALIFICATION.md` |
| Lock-screen PIN keys | The PIN bouncer (legacy `KeyguardPINView`, also SIM PIN/PUK) takes physical keys wherever focus is: `KeyguardSecurityContainerController.interceptMediaKey` (which the shade window root calls for every key before the view tree) hands them to `KeyguardPinBasedInputView.handleSableKeyEvent` while the PIN view is shown, and the view's `dispatchKeyEvent` does the same. Digits: number row and numpad, the kcm digit (Alt digit or `number:`), and for a plain or Alt press W E R S D F Z X C = 1-9 by key code, so a keyboard layout overlay that replaces the Q25 kcm's `number:`/`alt:` entries does not break it; 0 from KEY_0; Shift+letter is not a digit; Backspace deletes; Enter / numpad Enter press OK on key up. With a hardware keyboard the on-screen keys are not focusable (trackpad and Tab stay on the field) and the bouncer's message gets "Type PIN · Enter unlocks" as its second line. Entry goes through the on-screen keys; credential checking is upstream (frameworks/base 0304) | written, not run on device (Q4-LOCK-PIN-KEYS) |
| Lock-screen emergency shortcut | **Alt+Space** (either Alt; Sym is Right Alt) opens the emergency dialer from the locked glance and the PIN/password bouncer, or returns to a call in progress; the same as the bouncer's Emergency button, it never dials or unlocks. Chosen because Space has no Alt digit on the Q25 (the kcm gives a private-use character), Sym pages only with E and R, and plain Space keeps its confirm meaning; E (the design's key) types 2. Handled pre-IME so the PIN and password fields never see it (frameworks/base 0307) | written, not run on device (Q4-LOCK-EMERGENCY) |

## Lock screen clock shows the wrong time

Owner report, 2026-10. frameworks/base 0306 fixes the SystemUI side (the
always-on clock was ticked only by the non-wakeup `ACTION_TIME_TICK`, and a
time zone or 12/24h change while the clock listeners were unregistered was
not pushed to the clock). If the time is off by whole hours, or the status
bar is wrong too, the device clock or zone is the cause; check with:

```bash
adb shell date; adb shell date -u                       # device local time and UTC
adb shell getprop persist.sys.timezone                  # empty or GMT: no zone was ever set
adb shell settings get global auto_time                 # 1: network time (NTP/NITZ)
adb shell settings get global auto_time_zone            # 1: zone from NITZ (needs a SIM)
adb shell dumpsys time_detector | head -60              # last suggestions (network, telephony)
adb shell dumpsys time_zone_detector | head -60         # telephony zone suggestions
adb shell settings get system time_12_24                # 12, 24 or null (locale)
adb shell settings get secure doze_always_on            # 1: always-on display (clock while dozing)
adb shell dumpsys alarm | grep -B2 -A4 TIME_TICK | head -30
adb shell dumpsys activity service com.android.systemui | grep -i -A5 ClockEventController | head -40
```

Without a SIM (no NITZ) and without a location time zone provider, Android
keeps the zone it was given in setup (Date & time step) or GMT. Set it in
Settings > System > Date & time, or `adb shell cmd alarm set-timezone
Europe/London` (your zone). A lock screen that is minutes behind only after
dozing, and right after unlock, is the 0306 case.

## Keyboard firmware

The keyboard and trackpad run their own firmware on a separate controller.
Neither SableOS nor `restore-stock.sh` touches it. The community *Update
Keyboard Firmware* guide (PDF shared in the project, not stored here) updates
it from **stock** Android:

1. Get the firmware file from Zinwa (through the community mods).
2. Dial `*#*#1122#*#*` to open the hidden menu and press **Turn off**.
3. Connect USB (USB-A to USB-C recommended). Hold **Hang Up** and tap
   **Firmware upgrade**. A USB drive appears on the computer.
4. Copy the firmware file to the drive; it disconnects by itself.
5. Press **Exit** and **reboot the phone** (the guide stresses this).

Consequences for SableOS:

* Update the keyboard firmware **on stock before installing SableOS**. The
  `*#*#1122#*#*` menu is a stock app that LineageOS-based builds don't
  include, so later updates mean returning to stock or porting that tool
  (open item, Q3).
* Record the keyboard firmware version with the Q3 key captures, because
  scan codes and trackpad behaviour can change between firmware versions.
* The guide notes that in this mode the keyboard and trackpad work as a USB
  keyboard for the computer. The controller can present itself as USB HID,
  which is useful when debugging input.

## How to capture

```bash
adb shell getevent -lp          # list input devices and their capabilities
adb shell getevent -lt          # press each key / move the trackpad and note codes
adb shell dumpsys input         # keyboard type, layout and kcm Android picked
```

Write results into [`../device-profile/KEYMAP.md`](../device-profile/KEYMAP.md).
Never copy Titan 2 or Q27 key evidence into the Q25 profile.
