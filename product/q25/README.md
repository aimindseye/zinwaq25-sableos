# Sable product layer for the Q25

This directory is a template. `scripts/stage-product.sh` copies it into the
Android tree at `vendor/sable/q25` and installs `extra-product.mk` as
`vendor/extra/product.mk`.

```text
vendor/lineage/config/common.mk
    inherit-product-if-exists vendor/extra/product.mk     (LineageOS hook)
        ifeq TARGET_PRODUCT lineage_Q25
            inherit vendor/sable/q25/sable-q25.mk
                props: ro.sable.profile.id=zinwa-q25, updater pointed away (Q2/Q3)
                -include release.mk          (generated: ro.sable.release, build source)
                -include sable-q25-apps.mk   (generated from apps.tsv)
                Android.bp                   (generated android_app_import modules)
                -include sable-q25-framework.mk  (generated; Q4+: SableLauncher, overlays)
                src/                         (Q4+: Sable Start + design sources)
                overlay/                     (Q4+: from product/common/overlay)
                updater/                     (Q4+: SableNoUpdater, overrides the LineageOS Updater)
```

`apps.tsv` is the single list of Sable apps. Change it there; the Android.bp and
`sable-q25-apps.mk` are generated so they only name APKs that exist.

Two apps need privileges from Q4, and only from Q4:

* App display compatibility (`signing=platform`): platform certificate, privileged, runs as
  `android.uid.system` so it may set another app's aspect ratio. Not staged before Q4.
* Secure Vault (`signing=privileged`): privileged for "Lock now" (`MODIFY_QUIET_MODE`); an
  ordinary app before Q4.

`permissions/privapp-permissions-sable-q25.xml` allowlists exactly what their manifests request
(`tests/run.sh` checks this). `sysconfig/preinstalled-packages-sable-vault.xml` installs Secure
Vault only in the Private Space profile.

`permissions/default-permissions-sable-q25.xml` (Q4+) grants at first boot, revocably: READ_CONTACTS
to Sable Start for the Hub page's Contacts tab, and READ_SMS, READ_CONTACTS and READ_CALL_LOG to
Sable Hub, which has no launcher entry to ask from since Start's Hub page became the one Hub.

A `Q1` build stages nothing and removes `vendor/extra/product.mk`, so you get the
plain LineageOS control image.
