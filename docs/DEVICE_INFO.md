# Zinwa Q25: device information

Status: **public/planning data plus facts read from the LineageOS device tree.
Nothing here was measured on hardware by this project yet.**

## Hardware

| Item | Value | Source |
|---|---|---|
| Marketing name | Zinwa Q25, Q25 Pro | Zinwa, LineageOS wiki |
| Form factor | BlackBerry Classic (Q20) chassis with new mainboard; also sold as a conversion kit | Zinwa product page, Wikipedia |
| Codename | `Q25` | LineageOS |
| Board names | `q20_v12_factory`, `q20_v1_factory` | `board-info.txt` |
| SoC | MediaTek Helio G99, `mt6789` | LineageOS wiki, `device.mk` |
| CPU | 2x Cortex-A76 @ 2.2 GHz, 6x Cortex-A55 @ 2.0 GHz | LineageOS wiki |
| GPU | Mali-G57 MC2 | LineageOS wiki |
| RAM | 12 GB (LPDDR4x per Zinwa; LPDDR5 per LineageOS wiki) | conflicting public sources |
| Storage | 256 GB UFS 2.x, microSD | Zinwa, LineageOS wiki |
| Display | 3.5" 720x720 IPS LCD, 1:1, about 290 ppi; build density 193 | wiki, `BoardConfig.mk` |
| Keyboard | Built-in QWERTY, `Q25_keyboard` (kl, kcm, idc in device tree) | device tree |
| Pointer | Optical trackpad | Wikipedia |
| Cameras | 50 MP rear with LED flash, 8 MP front | LineageOS wiki |
| Battery | 3000 mAh, non-removable | Zinwa, wiki |
| Cellular | 2G GSM/CDMA, 3G UMTS/CDMA2000, 4G LTE; no 5G; single nano-SIM | wiki, Wikipedia |
| Wi-Fi / BT | 802.11 a/b/g/n/ac/ax, Bluetooth 5.2 | wiki |
| Other | NFC (ST, `android.hardware.nfc-service.st`), FM radio (MT6631), 3.5 mm jack, USB-C, GPS, accelerometer, gyroscope, compass, proximity, light | device tree, wiki |
| Dimensions | 131 x 72.4 x 10.2 mm, about 178 g | Wikipedia |

## Software and partitions (from the LineageOS tree)

| Item | Value |
|---|---|
| Stock OS | Android 14, fingerprint `Xelex/Xelex10_Ultra/Xelex10_Ultra:14/20240427/UP1v:user/release-keys` |
| Stock FOTA used for blobs | `Q25_26.03.2026` (`system_ext.prop`) |
| Shipping API level | 34 |
| Kernel | GKI, `kernel/xelex/mt6789`, `gki_defconfig mgk.config entry_level.config q20_v12_factory.config`, clang r416183b |
| A/B | Virtual A/B (`launch_with_vendor_ramdisk`) |
| Boot image | header v4, 64 MiB `boot`, `vendor_boot` same size, 8 MiB `dtbo` |
| Super | 9,663,676,416 bytes; group `mediatek_dynamic_partitions` holds system, system_ext, product, vendor, vendor_dlkm, odm_dlkm |
| Filesystems | ext4 for logical partitions, f2fs `userdata` with FBE v2 + metadata encryption |
| Recovery | in `vendor_boot` (`BOARD_MOVE_RECOVERY_RESOURCES_TO_VENDOR_BOOT`) |
| AVB | enabled; vbmeta, vbmeta_system (system, system_ext, product), vbmeta_vendor (vendor); test key unless `AVB_CUSTOM_KEY_PATH` set |
| GSI keys | Device tree installs developer GSI AVB keys into the ramdisk (`developer_gsi_keys.mk`) |
| Boot modes | Volume Up + Power, then pick fastboot or recovery from the menu |

## Open facts to capture on hardware

See [`STOCK_BASIS.md`](STOCK_BASIS.md). In short: exact stock build, bootloader
version, whether `q20_v1_factory` units differ, real RAM type, camera sensor ids,
trackpad event type, and whether the bootloader accepts a custom AVB key.

## Sources

* LineageOS wiki: https://wiki.lineageos.org/devices/Q25/
* LineageOS device tree: https://github.com/LineageOS/android_device_xelex_Q25
* Zinwa product page: https://zinwa.com/product/q25-full-device/
* Wikipedia: https://en.wikipedia.org/wiki/Zinwa_Q25
* Droidian port: https://github.com/MarathonOS/marathon-zinwa-q25
