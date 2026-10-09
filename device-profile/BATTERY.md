# Zinwa Q25 battery profile

What SableOS knows about the Q25 battery, where each fact comes from, and what
has to be measured on a phone before Settings > Battery may show it. The
machine-readable profile Settings reads is
[`battery/zinwa-q25.conf`](battery/zinwa-q25.conf) (schema:
`BATTERY_HEALTH_USAGE_UX.md` section 16). It inherits nothing from Titan 2,
Q27 or Panther.

```text
DEVICE=zinwa-q25
BATTERY_PROFILE=device-profile/battery/zinwa-q25.conf
INSTALLED_AS=/system_ext/etc/sable/battery/zinwa-q25.conf (phase Q4, see docs/implementation/battery.md)
DEVICE_EVIDENCE=NONE (no Q25 examined yet)
HEALTH_FACTS_QUALIFIED=NONE
CHARGING_MUTATION=NOT_AUTHORIZED (BH6)
```

## What is known without a device

These come from the vendor specification and from the pinned LineageOS
sources (`device/xelex/Q25` 1f4e295, `hardware/mediatek` 68f9be7). None is a
measurement, and none makes a health fact displayable.

| Fact | Value | Source | Used by Settings |
|---|---|---|---|
| Rated capacity | 3000 mAh, non-removable | Vendor specification (`README.md`) | Only as a sanity bound for a reported design capacity (`spec_design_capacity_mah`); never shown |
| Health HAL | `android.hardware.health-service.mediatek`, AIDL `IHealth/default` v4, the stock `libhealth_aidl_impl` with default `healthd_config` | `device.mk`, `hardware/mediatek/aidl/health` | Battery facts reach Settings only through this HAL and BatteryService |
| Fuel gauge | MediaTek gauge with the `FG_daemon` (`battery_meter`) | `rootdir/etc/init.mt6789.rc` | none |
| Charger ICs | `upm6922-charger`, `upm6722-standalone` (charge pump) on i2c-5 | `sepolicy/vendor/genfs_contexts` | none |
| Fast charging | `ro.vendor.mtk_fast_charging_support=1` | `vendor.prop` | none |
| Shutdown temperature | 58.0 C (`config_shutdownBatteryTemperature=580`) | `FrameworksResOverlay` | Read from the framework: the thermal-protection status line and the warm/hot thresholds (hot from 48.0 C) |
| Power profile capacity | `battery.capacity=1000` | `FrameworksResOverlay/res/xml/power_profile.xml` | A placeholder that disagrees with the 3000 mAh rating. BatteryStats mAh estimates are scaled wrongly; the percentage shares Settings shows are ratios and are not affected. Fix upstream or in a Sable overlay once measured |
| LineageOS charging control | Absent: no `vendor.lineage.health` HAL, so no `lineagehealth` service | `device.mk` | Charge limit renders "Not available on this device yet"; if a later tree adds the service it renders "Found on this device, not yet qualified" and the LineageOS controls stay hidden |

Whether the MediaTek kernel exports `charge_full`, `charge_full_design`,
`cycle_count`, `charge_counter`, `manufacturing_date`, `first_usage_date` or
`state_of_health` under `/sys/class/power_supply/battery`, and whether the
values mean what Android expects, is unknown. That is why every strict fact
is `UNKNOWN`.

## Profile keys and how to qualify each

Settings never reads sysfs. The steps below are for a person qualifying the
device with `adb`; they compare what Android reports with an independent
reference. A key moves to `YES` only with the evidence written into this file
(date, build, device serial redacted, readings), and to `NO` with evidence
that the device does not provide it.

| Key | Now | Evidence needed for YES |
|---|---|---|
| `health_capacity_supported` | UNKNOWN | `adb shell dumpsys battery` shows non-zero `Maximum capacity` and `Design capacity` (uAh). Design within 25% of 3000 mAh. Maximum capacity tracks a full discharge measurement (coulomb count from a USB power meter, full to cutoff) within 10% on two cycles. Optionally `dumpsys android.hardware.health.IHealth/default` state of health agrees within 10 points |
| `health_cycle_count_supported` | UNKNOWN | `dumpsys battery` / the `android.os.extra.CYCLE_COUNT` extra is non-negative and increments by about one per full-equivalent cycle over at least three cycles; survives reboot |
| `health_temperature_supported` | UNKNOWN | `temperature` in `dumpsys battery` within 3 C of a probe on the battery at rest and while charging. (Temperature is shown without evidence because every Android device reports it; set `NO` if it proves bogus, for example a constant 25.0 C) |
| `health_status_supported` | UNKNOWN | `health` reads 2 (good) normally and changes in an overheat or cold test; set `NO` if it never leaves 1 (unknown) |
| `health_charge_counter_supported` | UNKNOWN | `Charge counter` follows the level (counter / design is about level %) across a discharge |
| `health_manufacturing_date_supported` | UNKNOWN | `BATTERY_PROPERTY_MANUFACTURING_DATE` returns a plausible date that matches the cell label |
| `health_first_use_date_supported` | UNKNOWN | `BATTERY_PROPERTY_FIRST_USAGE_DATE` returns a date after manufacture that does not reset on factory reset |
| `usage_platform_accounting_supported` | UNKNOWN | Settings > Battery usage populates after a day of use and screen time matches Digital Wellbeing within 10%. (The usage summary shows unless this is `NO`) |
| `charge_limit_supported` | UNKNOWN | A Sable-owned bounded backend with SELinux policy and negative tests (BH6). Not authorized |
| `charge_limit_values` | empty | Only with `charge_limit_supported=YES` |
| `adaptive_charging_supported` | UNKNOWN | As for charge limit (BH6) |
| `scheduled_charging_supported` | UNKNOWN | As for charge limit (BH6) |
| `thermal_protection_status_supported` | UNKNOWN | Charging current drops when the battery is heated (charger IC JEITA behaviour) |
| `backend_provenance` | NONE | Name and version of the qualified charging backend |
| `mutable_backend_qualified` | NO | BH6 review complete. Even then the Settings gate stays closed until its code is changed in review |

## What Settings shows on a Q25 today

From the profile above, before any evidence:

```text
Battery
  Battery usage            Since <window start> · <top two consumers>   (Android accounting)
  Battery health           Partial data · temperature available
  Charging & protection    Device controls not yet qualified
  Battery saver            Android's control

Battery health
  Maximum capacity         Reported, but not yet verified on this device   (or Not reported)
  Cycle count              same
  Temperature              31.2 °C · Normal   Source: Android · reported by the platform
  Condition                Good               Source: Android · reported by the platform
  Full-charge / design capacity, remaining charge: not verified / not reported
  Manufactured, First used: omitted
  Recent history           temperature and level, sampled locally

Charging & protection
  Charge limit             Not available on this device yet   (disabled, focusable)
  Adaptive charging        Not available on this device yet
  Overnight charging       Not available on this device yet
  Thermal protection       Managed by Android and the charger hardware. Android shuts the phone down if the battery reaches 58 °C.
  Charging mode            shown only if Android reports a charging policy
```

The temperature and condition values above are examples; nothing here has run
on a Q25.
