# Sources and provenance

| Upstream | Used for | Pin | Licence |
|---|---|---|---|
| [LineageOS/android](https://github.com/LineageOS/android) `lineage-23.2` | OS base manifest | branch; exact revisions recorded per sync | Apache-2.0 and others (AOSP) |
| [LineageOS/android_device_xelex_Q25](https://github.com/LineageOS/android_device_xelex_Q25) | Device tree | `1f4e295b5b972bd5e5ea0ec01f8a87154973b464` | Apache-2.0 |
| [LineageOS/android_kernel_xelex_mt6789](https://github.com/LineageOS/android_kernel_xelex_mt6789) | Kernel | `2a873a3511ee0eeead1442e60145093192a4535d` | GPL-2.0 |
| [LineageOS/android_hardware_mediatek](https://github.com/LineageOS/android_hardware_mediatek) | MTK HALs, IMS frameworks | `68f9be72a32bca66e7c63d69e9739b18f13c8b48` | Apache-2.0 |
| [LineageOS/android_device_mediatek_sepolicy_vndr](https://github.com/LineageOS/android_device_mediatek_sepolicy_vndr) | Vendor sepolicy | `b1c50f4903504168e7bd0e32b44c72d139d38d09` | Apache-2.0 |
| [TheMuppets/proprietary_vendor_xelex_Q25](https://github.com/TheMuppets/proprietary_vendor_xelex_Q25) | Proprietary blobs | `b8c2c8f90a211dbe16d14b198743e4ba8ea000e4` | proprietary (redistributed by TheMuppets; never copied here) |
| aimindseye/sableos (private) | Sable apps, product pattern, build entry point pattern | `c538fc0e57e4592e11b27c93870b79ee48f7fbfc` | published here under Apache-2.0 by the owner (see `THIRD_PARTY_NOTICES.md` for exceptions) |
| [sableos-project/platform_sable](https://github.com/sableos-project/platform_sable) | Design and portability rules | reference | see repo |
| [sableos-project/device_sable_titan2](https://github.com/sableos-project/device_sable_titan2) | Repo structure, boundaries | reference | see repo |
| [MarathonOS/marathon-zinwa-q25](https://github.com/MarathonOS/marathon-zinwa-q25) | GPT layouts, multiboot notes | reference | see repo |
| [LineageOS wiki: Q25](https://wiki.lineageos.org/devices/Q25/) | Device info, install flow | reference | CC BY-SA |
| [Zinwa Q25 product page](https://zinwa.com/product/q25-full-device/) | Specs | reference | n/a |
| [Q25 Tutorials (Notion)](https://duc1607.notion.site/Q25-Tutorials-2a5709b7d17c8086a351c999e13f5551) | Community guides | reference; page needs JavaScript and wasn't machine-read | n/a |
| [Q25 stock images (Google Drive, "With-GMS")](https://drive.google.com/drive/folders/1RlhjXInYh7t_ITkWS6fqKY4quZAAKmuT) | Stock firmware for return-to-stock and blob extraction | reference; contents not yet verified or hashed | Zinwa proprietary |
| [LineageOS Q25 builds](https://download.lineageos.org/devices/Q25/builds) | Official builds to compare against the Q1 control | reference | n/a |

To change a pin: update `build/config/q25.env` and
`manifests/local_manifests/sable_q25.xml` together (`tests/run.sh` checks they
match) and say why in the commit.
