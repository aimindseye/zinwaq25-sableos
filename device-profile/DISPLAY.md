# Q25 display profile

Status: **values from public specs and the LineageOS device tree; not measured
on a Q25.** Fields follow `platform_sable/docs/DISPLAY_AND_ATTENTION_PROFILE_MODEL.md`.
Nothing here is inherited from Titan 2.

```text
main_display:
  class: SQUARE_KEYBOARD_LCD
  physical_size: 3.5 inch
  resolution: 720x720
  physical_ppi: about 290
  logical_density: 280 (SABLE_LCD_DENSITY; LineageOS BoardConfig.mk has 193)
  smallest_width_dp: 411 (720 * 160 / 280)
  refresh_modes: 60 Hz expected, NOT_MEASURED
  panel_type: IPS LCD (original BlackBerry Classic panel)
  natural_orientation: portrait (square)
  rotation_policy: locked to natural, keyboard below the screen
  cutout_policy: none expected
  rounded_corner_policy: none expected
  touch_association: built-in touchscreen
  aod_policy: NO_BY_DEFAULT (LCD)
secondary_display: none
```

## How it differs from Titan 2

| | Titan 2 | Q25 |
|---|---|---|
| Size | 4.5" | 3.5" |
| Resolution | 1440x1440 | 720x720 |
| Shape | square | square |
| Rear sub-screen | yes (2" class) | no |

Both are square, so Sable's square-aware layouts (DisplayCompat `SquareSafe`,
keyboard-first Start and Settings) apply to both. DisplayCompat reads the real
display size at runtime and needs no per-device table. Titan 2 sub-screen work
does not apply to the Q25.

## Density: 280 (decided 2026-10-22)

The Q25 uses density 280, chosen for its own panel rather than to match
Titan 2 layouts. The panel is about 290 ppi, so at 280 a dp is close to its
nominal 1/160 inch: text and touch targets come out at the physical size
Android intends. The 720x720 screen is then 411 dp square.

LineageOS uses 193 (597 dp), which makes every dp about two thirds of its
usual size: tiny text, and full-width square content (game boards, grids)
taller than the screen.

The value is the one line `SABLE_LCD_DENSITY := 280` in
`product/q25/sable-q25.mk`. It is written as `ro.sf.lcd_density` to the
product partition, which init loads after vendor. `tests/check-sable-design.py`
checks it. Sable layouts follow from the resulting size through resource
qualifiers (`notlong` for square screens, `sw<N>dp`) and the runtime screen
size, not from the device model. Square game boards are capped at 60 % of the
screen height so the board and its controls show together.

Preview on a phone running an older image: `adb shell wm density 280`
(undo with `adb shell wm density reset`).

## Tall canvas, resolution hotkey and rotation

Some phone apps expect a tall screen. App display compatibility can give one app a taller
canvas while it is in front (720x772, 720x960, 720x1280 or 720x1440): the display's forced
size changes, the app lays out as on a tall phone and is shrunk into the square with side bars,
and the native 720x720 comes back as soon as another app comes forward or the screen locks.
Every preset keeps the 720 px width, so the smallest width stays 411 dp at density 280 and the
taskbar never appears. Alt+Shift+R cycles the app in front through the favourite sizes. A
system rotation override (Follow apps, Auto, Portrait, Landscape) sits in the same Display tools
screen. Details: [q4-review](../docs/implementation/q4-review.md#display-tools).
