# Android Auto on the Q25

Android Auto needs Google's own app to be a **privileged system app**. A normal install
from Aurora Store is not enough: the phone must hold a small Google stub in `priv-app`
with system-level permissions, and the full app is then updated on top of it. SableOS
can build that stub into the image so nothing has to be flashed from recovery.

Like microG, this is **off by default**: the image carries no Google software unless you
ask for it (owner decision, 2026-11-04). The stub is Google's proprietary app, so it is
not in git; the build fetches it and checks its hash.

## Building it in

Android Auto needs microG, so build with both:

```bash
NETWORK_FETCH_AUTHORIZED=YES bash build/sable.sh q25 Q4 microg
NETWORK_FETCH_AUTHORIZED=YES bash build/sable.sh q25 Q4 androidauto
SABLE_Q25_MICROG=YES SABLE_Q25_ANDROID_AUTO=YES bash build/sable.sh q25 Q4 stage
bash build/sable.sh q25 Q4 build
```

`androidauto` downloads the NikGapps Android 14 Android Auto add-on, checks it against the
sha256 in `product/q25/androidauto/androidauto.tsv`, takes the stub out of it
(`AndroidAutoStubPrebuilt.apk`, `com.google.android.projection.gearhead`) and checks the
stub against its own pin. `--check` verifies only. If the build host can't reach
SourceForge, download the zip elsewhere and run
`bash build/sable.sh q25 Q4 androidauto --from NikGapps-Addon-14-AndroidAuto-20240728-signed.zip`.
Staging refuses to run with `SABLE_Q25_ANDROID_AUTO=YES` unless the stub passed its check
and microG is on.

What the image gets (`product/q25/androidauto`):

* the stub as a privileged product app, installed exactly as Google signed it;
* a privileged-permission list (`privapp-permissions-sable-androidauto.xml`), the same one
  NikGapps ships for this stub;
* default runtime grants (`default-permissions-sable-androidauto.xml`): location, microphone,
  phone, contacts, calendar, Bluetooth, nearby Wi-Fi and notifications, none of them fixed, so
  you can revoke them in Settings. SMS, call log and storage are not granted.

## After flashing

1. Open Aurora Store and update **Android Auto**. The stub is only a base: the update puts the
   full app on top and keeps the stub's privileges.
2. Run `adb shell pm path com.google.android.projection.gearhead`; it should list a path under
   `/product/priv-app/` and a second one for the update.
3. In Settings > Apps > Android Auto, check the battery setting is not "Restricted" and, for
   wireless Android Auto, that notification access and "Display over other apps" are allowed.

## What a new build does to a phone that already works

* **Your hand-flashed stub:** a flash replaces the product partition, so the stub from recovery is
  replaced by the built-in one. Nothing to undo.
* **Your data:** an upgrade flash without "format data" keeps the updated Android Auto and the
  permissions you gave with adb. A factory reset wipes them; the image's default grants then
  apply, and you update Android Auto from Aurora again.
* **Without `SABLE_Q25_ANDROID_AUTO=YES`:** the stock image has no stub, so after a flash you
  repeat the manual recovery steps.

## Not included

The NikGapps add-on also ships `AndroidAutoOverlay.apk`, a 3 KB resource overlay. It is not
staged because nothing has shown it is needed; if Android Auto misbehaves on a Q25, that is the
first thing to try.
