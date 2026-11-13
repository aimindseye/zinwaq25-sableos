# Sources and provenance

| Upstream | Used for | Pin | Licence |
|---|---|---|---|
| [LineageOS/android](https://github.com/LineageOS/android) `lineage-24.0` | OS base manifest (SableOS 1.0 used `lineage-23.2`) | branch; exact revisions recorded per sync | Apache-2.0 and others (AOSP) |
| [LineageOS/android_device_xelex_Q25](https://github.com/LineageOS/android_device_xelex_Q25) | Device tree | `1f4e295b5b972bd5e5ea0ec01f8a87154973b464` | Apache-2.0 |
| [LineageOS/android_kernel_xelex_mt6789](https://github.com/LineageOS/android_kernel_xelex_mt6789) | Kernel | `2a873a3511ee0eeead1442e60145093192a4535d` | GPL-2.0 |
| [LineageOS/android_hardware_mediatek](https://github.com/LineageOS/android_hardware_mediatek) | MTK HALs, IMS frameworks | `68f9be72a32bca66e7c63d69e9739b18f13c8b48` | Apache-2.0 |
| [LineageOS/android_device_mediatek_sepolicy_vndr](https://github.com/LineageOS/android_device_mediatek_sepolicy_vndr) | Vendor sepolicy | `b1c50f4903504168e7bd0e32b44c72d139d38d09` | Apache-2.0 |
| [TheMuppets/proprietary_vendor_xelex_Q25](https://github.com/TheMuppets/proprietary_vendor_xelex_Q25) | Proprietary blobs | `b8c2c8f90a211dbe16d14b198743e4ba8ea000e4` | proprietary (redistributed by TheMuppets; never copied here) |
| aimindseye/sableos (private) | Sable apps, product pattern, build entry point pattern | `c538fc0e57e4592e11b27c93870b79ee48f7fbfc` | published here under Apache-2.0 by the owner (see `THIRD_PARTY_NOTICES.md` for exceptions) |
| aimindseye/titan2-temp (private) | Keyboard P2, Reader v2 (P5A-P5F), reference sources layered over the sableos import | `7570470d1c5a27ef94ed4751f5ccb91af41761fe` | as sableos |
| [thunderbird/thunderbird-android](https://github.com/thunderbird/thunderbird-android) | Upstream for the Sable Mail flavor | `THUNDERBIRD_23_0` (`c733317d933c22bcd790a4790306f30ccf2026b8`) | Apache-2.0 |
| [vaachak-platform/vaachak-textreader](https://github.com/vaachak-platform/vaachak-textreader) | Upstream for the Sable Text Reader flavor | `50fca365baae9869264716569830690fb62029a7` | see repo |
| [connectbot/connectbot](https://github.com/connectbot/connectbot) | Upstream for the Sable Terminal flavor | `v1.10.9` (`21ce6f47cf16785bf2314b4ea931900164c179c1`) | Apache-2.0 |
| [aimindseye/tenseven-launcher](https://github.com/aimindseye/tenseven-launcher) | Tasks/Remind domain ported into Sable Tasks (`sable-src/apps/r8/android/tasks`) | `72ee8251091c338cda9c4b7fc9a9b91de266cb3d` | Apache-2.0 |
| [espeak-ng/espeak-ng](https://github.com/espeak-ng/espeak-ng) | Upstream for the Sable Speech flavor (text-to-speech engine) | `1.52.0` (`4870adfa25b1a32b4361592f1be8a40337c58d6c`) | GPL-3.0-or-later |
| [bitfireAT/davx5-ose](https://github.com/bitfireAT/davx5-ose) | DAVx5: CalDAV/CardDAV sync for Google, iCloud, Yahoo and other calendars | `v4.5.21-ose` (`f07633b0bb2482aec5cdfe6bd63c9a4b12b4faa2`) | GPL-3.0 |
| [bitfireAT/icsx5](https://github.com/bitfireAT/icsx5) | ICSx5: calendar link (webcal/ICS) subscriptions such as Outlook's | `v2.4.3` (`81036b50c10d331ec13fc107f5beae5ca32b72de`) | GPL-3.0 |
| [alphacep/vosk-api](https://github.com/alphacep/vosk-api) | Cosgrove hold-to-talk: `com.alphacephei:vosk-android` (with JNA) from Maven Central, and the small English model `vosk-model-small-en-us-0.15` the phone downloads when the owner asks | `0.3.75` (aar sha256 `ab2f8b91ac8051561aa325546b35fed9a68b36b8121bac5c6fb927525c4adfad`, in `gradle/verification-metadata.xml`) | Apache-2.0 (JNA: Apache-2.0 or LGPL-2.1, used under Apache-2.0) |
| [aimindseye/rustmix-wave](https://github.com/aimindseye/rustmix-wave) | The idea of add-on Lua games for Sable Games Console, and Tilt Maze, Motion 2048 and Sokoban Tilt ported into `sable-src/apps/r8/android/games/src/console/assets/console/` (sable-src 0207) | `main` (`6feeeb4f` on 2026-11-13) | MIT (the owner's own project) |
| [luaj/luaj](https://github.com/luaj/luaj) | Lua 5.2 interpreter for Sable Games Console: `org.luaj:luaj-jse` from Maven Central, console flavor only | `3.0.1` (jar sha256 `9b1f0a3e8f68427c6d74c2bf00ae0e6dbfce35994d3001fed4cef6ecda50be55`, in `gradle/verification-metadata.xml`) | MIT |
| [bellhopsw/q25-50mp](https://github.com/bellhopsw/q25-50mp) | Optional 50 MP camera (`product/q25/camera-50mp`, off by default) | `caf855d5adc9578f3231f73947b6872b8786db8a` | MIT (library, tools); GPL-2.0 (kernel change) |
| [microg/GmsCore](https://github.com/microg/GmsCore) releases | microG Services and Companion APKs (`product/q25/microg`, `SABLE_Q25_MICROG`, Q4+) | `v0.3.18.252432` (`f686900c68aa84d1605b52304f6615f3593c6f50`); APK sha256 in `prebuilts.tsv` | Apache-2.0 |
| [AuroraOSS/AuroraStore](https://gitlab.com/AuroraOSS/AuroraStore) releases | Aurora Store APK (same) | `4.8.4`; APK sha256 in `prebuilts.tsv` | GPL-3.0 |
| [lineageos4microg/android_vendor_partner_gms](https://github.com/lineageos4microg/android_vendor_partner_gms), [docker-lineage-cicd](https://github.com/lineageos4microg/docker-lineage-cicd) | Reference for the microG permission, sysconfig and default files and the user-build spoofing change | reference (`f264d80a`, `60356101`) | Apache-2.0 / GPL-3.0 |
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
match) and say why in the commit. The Mail, Text Reader, Terminal, Speech, DAVx5 and ICSx5 pins live in
`sable-src/apps/r8/{mail,textreader,connectbot,espeak,davx5,icsx5}/upstream.env`; the sableos and titan2-temp
pins are recorded in `sable-src/SOURCE_IMPORT.txt`. The LineageOS framework
patches name their base commits in [`../patches/README.md`](../patches/README.md).
