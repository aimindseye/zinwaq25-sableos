# Q25 key map (to be captured)

Fill from `adb shell getevent -lt` on a real Q25. Leave `?` until measured.

| Physical key | Linux code | Android keycode | Notes |
|---|---|---|---|
| Call (green) | ? | ? | kl maps 169 and 231 to CALL |
| End / Power (red) | ? | ? | |
| Menu (BlackBerry) | ? | ? | kl maps 139 and 127 to MENU |
| Back | ? | ? | kl maps 158 to BACK |
| Trackpad move | ? | ? | DPAD keys or REL events? |
| Trackpad click | ? | ? | |
| Alt | ? | ALT_LEFT? | |
| Shift (left/right) | ? | ? | |
| Sym | ? | ? | |
| Speaker / mic key (0) | ? | ? | kcm gives alt 0 |
| Volume up / down / mute | ? | ? | side keys |
| Convenience key | ? | ? | |

## Leads from q25toolbox (LineageOS 23, not verified here)

[nozerorma/q25toolbox](https://github.com/nozerorma/q25toolbox) (README at `64ed9cd`) reports these
from its own Q25 testing. Confirm each with `getevent -lt` before relying on it.

| Key | Reported | Note |
|---|---|---|
| Currency key | scan code 41 (`GRAVE`) | Described as miswired in the keyboard firmware |
| Recents key | scan code 580 (`APP_SWITCH`) | The system opens its own Overview on it even when an accessibility service consumes the key |
| Right Shift | scan code 54 | |
| Keylayout | `/vendor/usr/keylayout/Q25_keyboard.kl` | A replacement must be labelled `u:object_r:vendor_keylayout_file:s0`; with `vendor_file` the system silently falls back to `Generic.kl` |
| Keyboard driver | `bbqX0kbd.ko`, i2c device `6-001f`, driver `Q25_keyboard` | Never unbind and rebind it: a panel-on event while unbound is a kernel NULL dereference. Reload with a uevent remove/add on its input node |
| Lock screen | Enter and keypad keys activate whatever has focus | The PIN pad needs keyboard handling (critical text entry gate) |

