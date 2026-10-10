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
| Keyboard backlight | The `bbqX0kbd` kernel driver sets it over I2C (`REG_BKL`): full on screen-on, off on screen-off, Right-Alt + Z/X/0 adjust it (confirmed on a Q25 by the owner). No sysfs LED or lights HAL entry. Q4 adds a Settings switch and brightness slider through kernel and Settings patches 0901 and `product/q25/init/sable-keyboard-backlight.rc` | written, not run on device |
| Critical text entry | Lock-screen PIN/password, Wi-Fi password, SIM PIN must work from the hardware keyboard and the soft-keyboard fallback (`CRITICAL_TEXT_ENTRY_GATES.md`) | gate in `QUALIFICATION.md` |

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
