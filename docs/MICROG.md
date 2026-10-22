# Play Store apps: microG and Aurora Store

From Q4, the SableOS Q25 image can carry:

| App | Package | What it does |
|---|---|---|
| microG Services (GmsCore) 0.3.17 | `com.google.android.gms` | Open-source stand-in for Google Play services: Google sign-in, Cloud Messaging (push), the location API used by maps, and SafetyNet (off) |
| microG Companion ("FakeStore") 0.3.17 | `com.android.vending` | Stands in for the Play Store package so apps that look for it start |
| Aurora Store 4.8.4 | `com.aurora.store` | Installs and updates Play Store apps such as LinkedIn |

It is on by default (owner decision, 2026-10-21) and controlled by one build flag,
`SABLE_Q25_MICROG` (`build/config/q25.env`). There is no Google Play Store and no Google
Play services on the image, so there is nothing that wakes the phone to poll location the
way Play services does.

Files: `product/q25/microg/` (pins, Soong modules, permission and default files),
`scripts/fetch-microg.sh`, and frameworks/base patch 0550.

## Building with it

The APKs are not in git. Fetch them once per version; each is checked against the sha256
pinned in `product/q25/microg/prebuilts.tsv` and deleted if it doesn't match:

```bash
NETWORK_FETCH_AUTHORIZED=YES bash build/sable.sh q25 Q4 microg   # downloads to ~/sable-q25/microg-cache
bash build/sable.sh q25 Q4 microg --check                       # verify only
bash build/sable.sh q25 Q4 stage                                # stages them (MICROG=YES)
bash build/sable.sh q25 Q4 build
```

If the build host can't reach GitHub or GitLab, download the three files from the URLs in
`prebuilts.tsv` on another machine, copy them to the host, and run
`bash scripts/fetch-microg.sh --from DIR`. The files are taken only if their sha256
matches the pin.

To build without microG and Aurora Store: `SABLE_Q25_MICROG=NO bash build/sable.sh q25 Q4 stage`.
They are never staged before Q4 (Q2 and Q3 have no privileged apps).

**Partition space.** The three APKs add about 117 MB to the product partition (GmsCore
alone is 103 MB). The first Q4 build with microG (2026-10-22) overflowed the dynamic
partition group by 905,216 bytes; Sable Calculator now ships as a shrunk release build,
which freed about 25 MB, and the image fits. If a later change overflows again, move another
debug-built Sable app to release ([`BUILD.md`](BUILD.md#troubleshooting)) or build with
`SABLE_Q25_MICROG=NO` and install microG and Aurora Store afterwards as normal apps
(see "Without the build flag" below).

GmsCore is privileged and its release APK keeps its dex compressed, which Soong's
presigned-APK check rejects. Repacking it would break the microG signature, so
`Android.bp` skips that one check for GmsCore (`skip_preprocessed_apk_checks`).

### Updating the pins

Change `version`, `url` and `sha256` in `prebuilts.tsv` in one reviewed commit. For
microG, take the APK digests from the release page
(`https://github.com/microg/GmsCore/releases/tag/<tag>`, use the files without `-hw`; each
has a `.asc` PGP signature). For Aurora Store, download the standard APK (not "preload"
and not "hw") from the GitLab release and hash it yourself. Then run
`NETWORK_FETCH_AUTHORIZED=YES bash scripts/fetch-microg.sh`.

## Signature spoofing

Apps check that Play services is signed by Google. microG passes that check through
signature spoofing. LineageOS 23.2 already has a restricted form of it
(`ComputerEngine.isMicrogSigned`, LineageOS change "Allow signature spoofing for microG
Companion/Services"). It spoofs only when all of these hold:

* the package is `com.google.android.gms` or `com.android.vending`;
* the APK is signed with the microG release key (so only official microG builds, never
  a re-signed or third-party app);
* the signature it asks for is Google's own one signature, and nothing else.

Upstream allows this on debuggable (userdebug) builds only. Patch 0550 keeps every check
above and also allows it when `ro.sable.microg.spoofing=true`, a read-only property set
only by `product/q25/microg/microg.mk`. So spoofing also works on a future user build, and
only when the image carries microG. No other app can get a spoofed signature, and there is
no permission a user could grant to another app to get one. The APKs are installed exactly
as microG signed them (`presigned`).

## Battery, location and privacy defaults

GmsCore reads its defaults from `/product/etc/com.google.android.gms/settings.xml`
(`product/q25/microg/etc/com.google.android.gms/settings.xml`). Anything you change in
microG Settings replaces these.

| Setting | Default on SableOS | Where to turn it on |
|---|---|---|
| Wi-Fi and cell network location (the UnifiedNlp-style online lookups, "moving" Wi-Fi, learning and caching, contributing to the location database) | Off. GPS still works | microG Settings > Location: Wi-Fi location, Mobile network location, and their sources |
| Address lookup (Nominatim geocoder) | Off | microG Settings > Location |
| Location timeline | Off | microG Settings > Location |
| SafetyNet / Play Integrity (DroidGuard) | Off | microG Settings > Google SafetyNet |
| Exposure Notifications | Off | microG Settings > Exposure Notifications |
| Google device registration (check-in) | Off | microG Settings > Google device registration |
| Cloud Messaging (push) | Off | microG Settings > Cloud Messaging (needs device registration on) |
| Background location for microG | Not granted (only "while in use" location is granted at first boot) | Settings > Apps > microG Services > Permissions > Location > Allow all the time |

Also:

* The system network location provider is not switched to microG (no
  `config_enableNetworkLocationOverlay`). Apps that ask Android for network location get
  none; apps that use the Google location API get GPS through microG.
* Power allowlist (`etc/sysconfig/sable-microg.xml`): only GmsCore, and only
  `allow-in-power-save`, so push messages arrive in Doze once Cloud Messaging is on. No
  `allow-unthrottled-location` (that would let microG read location at full rate in the
  background) and no Data Saver exemption. Companion and Aurora Store get no exemption.
* First-boot runtime grants (`etc/default-permissions/default-permissions-sable-microg.xml`):
  GmsCore gets foreground location, accounts, phone state and notifications; Companion
  gets accounts and notifications; Aurora Store gets notifications. None is fixed, so
  each can be revoked in Settings > Apps.
* Privileged permissions (`etc/permissions/privapp-permissions-sable-microg.xml`): only
  GmsCore is a privileged app. Companion and Aurora Store are normal apps.

## Installing apps with Aurora Store (for example LinkedIn)

1. Open **Aurora Store** from All Apps.
2. The first time, choose how to sign in:
   * **Anonymous**: uses a shared Aurora account. Nothing links the apps to you. Free apps
     only, and the shared account is sometimes rate-limited ("too many requests"); try
     again later.
   * **Google account**: sign in with your own account (Aurora asks in a web page). Needed
     for paid apps you own and for apps restricted to your country. Google sees the
     downloads, as it would with the Play Store. A secondary account is a good choice.
3. Let Aurora Store install apps when it asks: **Settings > Apps > Special app access >
   Install unknown apps > Aurora Store > Allow**. Each install still shows the system
   confirmation.
4. Search for **LinkedIn**, open it, press **Install**, then confirm.
5. Updates: Aurora Store > Updates. It checks in the background with normal job
   scheduling (no power exemption); turn the automatic check off in Aurora's settings if
   you prefer to update by hand.

## What works and what doesn't

| Works | Notes |
|---|---|
| Most apps that need Play services just to start (LinkedIn, banking apps without Play Integrity, messengers) | |
| Push notifications through Cloud Messaging | Turn on Google device registration and Cloud Messaging in microG Settings first. Apps registered before that need to be opened once (or reinstalled) to register. microG Settings > Cloud Messaging lists apps and lets you block one |
| Google sign-in in apps ("Sign in with Google") | Add the account in microG Settings > Google account (or Settings > Passwords & accounts). Some apps refuse microG's sign-in; email/password sign-in works then |
| Maps inside apps (Google Maps SDK) | microG draws maps with its own renderer; most apps show maps. Location comes from GPS unless you turn network location on |
| In-app location through the Google API | Foreground; GPS only by default |

| Doesn't work, or not reliably | Why |
|---|---|
| Apps that require Play Integrity / SafetyNet (some banks, Google Wallet tap-to-pay, some games) | Off by default; even when on, microG can't pass strong integrity on an unlocked bootloader |
| Paid apps and in-app purchases bought through Google Play | Companion's billing support is limited; paid downloads need Aurora with your own account |
| Google Play Games, Android Auto, Wear OS companion apps, Google Pay | Need real Google services |
| Find My Device, backup to Google Drive | Not implemented, or off |
| Exposure Notifications | Off by default |

microG's own checklist: **microG Settings > Self-Check**. Every item should be ticked
once device registration and Cloud Messaging are on; "Battery optimizations ignored" is
ticked by the image's power allowlist.

## Removing it

* **From one phone, without rebuilding**: Settings > Apps > Aurora Store / microG
  Services / microG Companion > Disable. System apps can be disabled but not uninstalled.
  Remove the Google account first (Settings > Passwords & accounts).
* **From the image**: `SABLE_Q25_MICROG=NO bash build/sable.sh q25 Q4 stage`, then
  build. The packages, their permission files and `ro.sable.microg*` are gone; frameworks/base
  0550 stays applied but does nothing without the property.
* **Without the build flag**: on a userdebug build (the default variant) microG's
  official APKs also work installed as normal apps, because the LineageOS spoofing check
  doesn't require a system app: `adb install` GmsCore, then Companion, then Aurora Store.
  They then have no privileged permissions (no system network location provider, no Doze
  exemption unless you allow it when microG asks).

## Checks on the phone

Gate `Q4-MICROG` in [`QUALIFICATION.md`](QUALIFICATION.md):

```bash
adb shell getprop ro.sable.microg                       # true
adb shell getprop ro.sable.microg.spoofing              # true
adb shell pm list packages -s | grep -E 'com.google.android.gms|com.android.vending|com.aurora.store'
adb shell dumpsys package com.google.android.gms | grep -E 'signatures|apkSigningVersion'
#   one entry: the microG release key (dumpsys shows the real signer; only the
#   PackageInfo that apps receive carries Google's signature, see the self-check)
adb shell pm path com.google.android.gms                # /product/priv-app/GmsCore/...
adb shell dumpsys deviceidle whitelist | grep -E 'gms|vending|aurora'   # only com.google.android.gms
adb shell dumpsys package com.google.android.gms | grep -E 'ACCESS_(FINE|COARSE|BACKGROUND)_LOCATION'
#   FINE and COARSE granted=true, BACKGROUND not granted
adb shell am start -n com.google.android.gms/org.microg.gms.ui.SettingsActivity   # microG Settings
```

In microG Settings > Self-Check, "System spoofs signature" is ticked (microG compares the
signature apps see with Google's, SHA-1 `38918a45...5788`). LineageOS's built-in spoofing
has no permission, so microG shows no "grants signature spoofing permission" row. Then the battery check: with a Google account added, Cloud
Messaging on and the screen off for an hour,
`adb shell dumpsys location | grep -A3 com.google.android.gms` shows no active network
location request and `adb shell dumpsys batterystats --charged com.google.android.gms`
shows no wakelock above a few minutes.
