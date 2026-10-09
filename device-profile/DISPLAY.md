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
  logical_density: 193 (TARGET_SCREEN_DENSITY in LineageOS BoardConfig.mk)
  smallest_width_dp: about 597 (720 * 160 / 193)
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

## Open decision: density

LineageOS uses density 193, which gives a 597 dp wide screen: lots of room, but
on a 290 ppi panel each dp is about two thirds of its usual physical size, so
text and touch targets are small. Sable's keyboard-first layouts were designed
around roughly 400 to 480 dp. A density near 240 (480 dp) or 280 (411 dp) may
suit Sable better. Decide on hardware: override it with
`PRODUCT_PROPERTY_OVERRIDES += ro.sf.lcd_density=<value>` in
`product/q25/sable-q25.mk`, and record the result here.
