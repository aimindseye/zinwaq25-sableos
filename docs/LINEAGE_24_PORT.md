# LineageOS 24.0 port (SableOS 2.0)

SableOS 2.0 builds on LineageOS 24.0 (Android 17, release config `cp2a`; owner decision
2026-11-04). This page tracks the move of every frameworks, apps and device patch in
`patches/framework/` from lineage-23.2 to lineage-24.0.

How it was checked: for each project, the files each patch touches were fetched from the
LineageOS `lineage-24.0` branch heads of 2026-11-04 and the project's patches were applied in
name order with `git apply`. Patches that applied only with fuzz were regenerated against the
24.0 files. The same run against lineage-23.2 applied all but one patch (LineageParts 0601,
whose translations moved after it was written), so the method matches the real tree.

Still to verify on a 24.0 build: everything. A clean apply is not a build or a device test;
the Q4 gates in [QUALIFICATION.md](QUALIFICATION.md) apply unchanged.

## What changed in the device projects

* LineageOS 24.0 splits the Q25 tree. Shared MT6789 parts (sepolicy, fstab, recovery) moved to
  `device/xelex/mt6789-common`, with its own blobs in `vendor/xelex/mt6789-common`. Both are now
  pinned in `build/config/q25.env` and `manifests/local_manifests/sable_q25.xml`.
* `device/xelex/Q25` still sets `TARGET_SCREEN_DENSITY := 193`; SableOS keeps 280
  (`SABLE_LCD_DENSITY`, [DISPLAY](../device-profile/DISPLAY.md)).
* An existing 23.2 tree is re-initialised by `bootstrap`, because the new projects are missing.

## Status

62 apply as is, 11 refreshed, 2 apply once the earlier ports are in, 17 ported by hand, 14 still to port (SetupWizard).

Until the last group is ported, `stage Q4` (and so the full release) stops at the first patch that
does not apply; Q1 to Q3 (no framework patches) are unaffected.

| Patch | 24.0 status |
|---|---|
| `device/xelex/Q25/1501-SableOS-type-on-the-Q25-currency-key.patch` | applies as is |
| `frameworks/base/0101-SableOS-read-default-large-Quick-Settings-tiles-from.patch` | ported by hand |
| `frameworks/base/0102-SableOS-Quick-Settings-tiles-use-the-Sable-card-and-.patch` | ported by hand |
| `frameworks/base/0103-SableOS-apply-the-pure-black-theme-at-boot-and-after-setup.patch` | ported by hand |
| `frameworks/base/0301-SableOS-keyboard-notification-commands-in-the-shade.patch` | applies as is |
| `frameworks/base/0302-SableOS-Alt-shade-shortcuts-for-keyboards-without-Fn.patch` | refreshed (context moved) |
| `frameworks/base/0303-SableOS-Space-toggles-and-Alt-Enter-opens-details-on.patch` | ported by hand |
| `frameworks/base/0304-SableOS-keyboard-hint-line-and-type-ahead-on-the-loc.patch` | ported by hand |
| `frameworks/base/0305-SableOS-Sable-lock-screen-clock-and-unlock-text.patch` | refreshed (context moved) |
| `frameworks/base/0306-SableOS-keep-the-lock-screen-clock-on-the-current-ti.patch` | applies as is |
| `frameworks/base/0307-SableOS-Sable-locked-glance-emergency-shortcut-and-b.patch` | ported by hand |
| `frameworks/base/0310-SableOS-three-column-labeled-Quick-Settings-grid-fou.patch` | ported by hand |
| `frameworks/base/0311-SableOS-Sable-Quick-Settings-header-Hub-summary-row-.patch` | ported by hand |
| `frameworks/base/0312-SableOS-slash-searches-the-notifications-in-the-shade.patch` | ported by hand |
| `frameworks/base/0313-SableOS-Alt-Shift-R-cycles-the-Tall-canvas-of-the-ap.patch` | refreshed (context moved) |
| `frameworks/base/0501-SableOS-notification-light-for-every-alerting-channe.patch` | applies as is |
| `frameworks/base/0550-SableOS-microG-signature-spoofing-on-user-builds-whe.patch` | ported by hand |
| `kernel/xelex/mt6789/0201-SableOS-report-the-fuel-gauge-s-learned-battery-health.patch` | applies as is |
| `kernel/xelex/mt6789/0202-SableOS-stop-the-Q25-keyboard-driver-logging-every-k.patch` | applies as is |
| `kernel/xelex/mt6789/0901-SableOS-let-the-system-set-the-Q25-keyboard-backligh.patch` | applies as is |
| `lineage-sdk/0150-SableOS-pure-black-dark-theme-by-default.patch` | applies as is |
| `lineage-sdk/0502-SableOS-default-notification-light-color-unless-the-.patch` | applies as is |
| `lineage-sdk/0504-SableOS-battery-light-blinks-while-charging-solid-gr.patch` | applies as is |
| `packages/apps/Aperture/0801-SableOS-let-Sable-Camera-be-the-default-still-camera.patch` | applies as is |
| `packages/apps/Contacts/0751-SableOS-pure-contact-list-letter-and-number-model-fo.patch` | applies as is |
| `packages/apps/Contacts/0752-SableOS-Contacts-list-with-A-Z-rail-letter-keys-and-.patch` | applies as is |
| `packages/apps/Contacts/0753-SableOS-pure-route-subtitle-and-privacy-model-for-Co.patch` | applies as is |
| `packages/apps/Contacts/0754-SableOS-route-subtitle-Hub-route-chip-and-privacy-li.patch` | applies as is |
| `packages/apps/Contacts/0755-SableOS-Sable-dark-palette-focus-outline-and-type-sc.patch` | applies as is |
| `packages/apps/Contacts/0756-SableOS-Contacts-follows-the-system-Dark-theme.patch` | applies as is |
| `packages/apps/Dialer/0701-SableOS-pure-contact-list-letter-and-number-model-fo.patch` | applies as is |
| `packages/apps/Dialer/0702-SableOS-Phone-contacts-list-with-A-Z-rail-letter-key.patch` | applies as is |
| `packages/apps/Dialer/0703-SableOS-single-key-mute-speaker-and-dialpad-on-the-in.patch` | applies as is |
| `packages/apps/Dialer/0704-SableOS-Space-answers-a-ringing-call-on-the-in-call-.patch` | applies as is |
| `packages/apps/Dialer/0705-SableOS-pure-dial-screen-key-and-Recents-privacy-mod.patch` | applies as is |
| `packages/apps/Dialer/0706-SableOS-hardware-digits-on-the-dial-screen-and-Recen.patch` | applies as is |
| `packages/apps/Dialer/0707-SableOS-Sable-dark-palette-and-focus-outline-for-Pho.patch` | applies as is |
| `packages/apps/Dialer/0708-SableOS-Sable-header-Recents-rows-dial-pad-keys-and-.patch` | applies as is |
| `packages/apps/Dialer/0709-SableOS-Phone-follows-the-system-Dark-theme.patch` | applies as is |
| `packages/apps/Dialer/0710-SableOS-speed-dial-on-letter-keys.patch` | applies as is |
| `packages/apps/DocumentsUI/1001-SableOS-pure-Files-keyboard-model.patch` | applies as is |
| `packages/apps/DocumentsUI/1002-SableOS-Files-keys-for-copy-move-rename-new-folder-a.patch` | refreshed (context moved) |
| `packages/apps/DocumentsUI/1003-SableOS-Files-single-letter-keys-per-the-design.patch` | applies as is |
| `packages/apps/DocumentsUI/1004-SableOS-Files-letter-keys-and-the-hint-dock.patch` | refreshed (context moved) |
| `packages/apps/FMRadio/1061-SableOS-pure-FM-bridge-protocol-for-Sable-Media.patch` | applies as is |
| `packages/apps/FMRadio/1062-SableOS-Sable-Media-drives-FM-through-a-bridge-servi.patch` | applies as is |
| `packages/apps/Glimpse/0801-SableOS-Sable-keyboard-focus-ring-on-gallery-thumbna.patch` | applies as is |
| `packages/apps/Glimpse/1021-SableOS-pure-Photos-keyboard-model-and-album-type-to.patch` | applies as is |
| `packages/apps/Glimpse/1022-SableOS-Photos-keys-in-the-viewer-the-grid-and-the-a.patch` | applies as is |
| `packages/apps/Glimpse/1023-SableOS-Photos-keys-follow-the-design-s-S-add-Z-grid.patch` | applies as is |
| `packages/apps/Glimpse/1024-SableOS-Photos-caption-strips-grid-J-K-and-Z-zoom-mo.patch` | applies as is |
| `packages/apps/Jelly/1041-SableOS-pure-Browser-keyboard-model.patch` | applies as is |
| `packages/apps/Jelly/1042-SableOS-Browser-keys-for-the-address-bar-tabs-find-a.patch` | applies as is |
| `packages/apps/Jelly/1043-SableOS-Browser-keys-follow-the-design-s-T-B-L-and-a.patch` | applies as is |
| `packages/apps/Jelly/1044-SableOS-Browser-start-page-command-dock-reader-mode-.patch` | applies as is |
| `packages/apps/Launcher3/0001-SableOS-keep-Launcher3QuickStep-for-Recents-only-not.patch` | refreshed (context moved) |
| `packages/apps/Launcher3/0002-SableOS-hide-the-Launcher3QuickStep-home-settings-la.patch` | ported by hand |
| `packages/apps/Launcher3/0003-SableOS-Recents-key-and-button-open-Sable-Start-s-Ac.patch` | refreshed (context moved) |
| `packages/apps/LineageParts/0503-SableOS-show-Automagic-notification-light-color-off-.patch` | applies as is |
| `packages/apps/LineageParts/0504-SableOS-keep-the-battery-light-on-when-fully-charged.patch` | applies as is |
| `packages/apps/LineageParts/0601-SableOS-no-LineageOS-contributors-statistics-or-Trus.patch` | ported by hand |
| `packages/apps/Settings/0101-SableOS-Sable-appearance-authority-and-Display-Accen.patch` | applies as is |
| `packages/apps/Settings/0102-SableOS-SableOS-version-row-in-About-phone.patch` | applies as is |
| `packages/apps/Settings/0103-SableOS-Sable-app-corner-style-in-the-appearance-aut.patch` | applies as is |
| `packages/apps/Settings/0104-SableOS-seed-the-system-palette-from-the-Sable-accen.patch` | applies as is |
| `packages/apps/Settings/0201-SableOS-battery-health-and-usage-model-with-host-tes.patch` | applies as is |
| `packages/apps/Settings/0202-SableOS-Battery-health-and-Charging-protection-in-Se.patch` | refreshed (context moved) |
| `packages/apps/Settings/0203-SableOS-keyboard-inspection-for-the-battery-usage-ch.patch` | applies as is |
| `packages/apps/Settings/0300-SableOS-Sable-Attention-and-per-app-Sable-Hub-entrie.patch` | applies as is |
| `packages/apps/Settings/0601-SableOS-Applications-privacy-Network-Manager-and-App.patch` | applies as is |
| `packages/apps/Settings/0602-SableOS-sensitive-access-under-app-names-and-the-App.patch` | applies as is |
| `packages/apps/Settings/0603-SableOS-Network-Manager-in-Settings-Apps-Network-acc.patch` | applies as is |
| `packages/apps/Settings/0604-SableOS-read-only-App-security-privacy-page.patch` | refreshed (context moved) |
| `packages/apps/Settings/0901-SableOS-Keyboard-light-info-in-Settings-Keyboard.patch` | applies as is |
| `packages/apps/Settings/1001-SableOS-Sable-entries-in-Settings-Display-and-App-in.patch` | refreshed (context moved) |
| `packages/apps/Settings/1002-SableOS-keyboard-type-ahead-on-the-Settings-homepage.patch` | ported by hand |
| `packages/apps/Settings/1101-SableOS-Mobile-Manager-App-lock-Focus-mode-and-profi.patch` | applies as is |
| `packages/apps/Settings/1102-SableOS-Mobile-Manager-App-lock-Focus-mode-and-Priva.patch` | applies as is |
| `packages/apps/Settings/1103-SableOS-Settings-Sable-holds-Sable-Setup-only.patch` | applies after the earlier ports |
| `packages/apps/Settings/1201-SableOS-Settings-wide-Sable-focus-ring.patch` | ported by hand |
| `packages/apps/Settings/1202-SableOS-Keyboard-input-homepage-row-Sable-Tools-link.patch` | ported by hand |
| `packages/apps/Settings/1203-SableOS-hide-the-LineageOS-version-row-in-About-phon.patch` | applies as is |
| `packages/apps/Settings/1204-SableOS-App-Control-Center-Freezer-and-Student-mode-.patch` | applies as is |
| `packages/apps/Settings/1205-SableOS-App-Control-Center-Freezer-and-Student-mode-.patch` | ported by hand |
| `packages/apps/Settings/1206-SableOS-turn-on-the-cloned-apps-page-for-Dual-Apps.patch` | applies as is |
| `packages/apps/Settings/1207-SableOS-Connected-devices-card-hub.patch` | applies as is |
| `packages/apps/Settings/1208-SableOS-App-blocker-on-Android-s-Restricted-backgro.patch` | applies as is |
| `packages/apps/Settings/1209-SableOS-security-changes-seen-on-this-phone-and-trac.patch` | applies as is |
| `packages/apps/Settings/1301-SableOS-battery-voltage-row-and-the-Q25-s-learned-hea.patch` | applies as is |
| `packages/apps/Settings/1401-SableOS-Settings-homepage-row-opens-Sable-Setup-dire.patch` | applies after the earlier ports |
| `packages/apps/Settings/1501-SableOS-Keyboard-firmware-screen-and-1122-on-the-Q25.patch` | refreshed (context moved) |
| `packages/apps/SetupWizard/0401-SableOS-keyboard-first-focus-in-setup-steps.patch` | to port |
| `packages/apps/SetupWizard/0402-SableOS-Sable-Keyboard-readiness-step-before-Wi-Fi-a.patch` | to port |
| `packages/apps/SetupWizard/0403-SableOS-Esc-returns-to-the-previous-setup-step.patch` | to port |
| `packages/apps/SetupWizard/0404-SableOS-keyboard-test-step-after-keyboard-readiness.patch` | to port |
| `packages/apps/SetupWizard/0405-SableOS-privacy-summary-step-after-Wi-Fi.patch` | to port |
| `packages/apps/SetupWizard/0406-SableOS-Sable-accent-on-the-Theme-step-Theme-after-P.patch` | to port |
| `packages/apps/SetupWizard/0407-SableOS-Ready-to-start-text-on-the-final-setup-page.patch` | to port |
| `packages/apps/SetupWizard/0408-SableOS-drop-the-LineageOS-features-step.patch` | to port |
| `packages/apps/SetupWizard/0409-SableOS-navy-cards-and-a-cyan-focus-ring-in-setup.patch` | to port |
| `packages/apps/SetupWizard/0410-SableOS-Step-n-of-N-on-every-owner-setup-step.patch` | to port |
| `packages/apps/SetupWizard/0411-SableOS-keyboard-check-on-Welcome-keyboard-steps-onl.patch` | to port |
| `packages/apps/SetupWizard/0412-SableOS-Restore-hidden-until-validated-fail-closed.patch` | to port |
| `packages/apps/SetupWizard/0413-SableOS-Ready-screen-rows-as-the-setup-design-shows-.patch` | to port |
| `packages/apps/SetupWizard/0414-SableOS-keep-Next-on-screen-in-short-windows.patch` | to port |
| `device/xelex/mt6789-common/1502-SableOS-let-Settings-drive-the-Q25-keyboard-firmware.patch` | ported by hand |

## Why the rest need porting

* **SetupWizard (14):** LineageOS 24.0 rewrote SetupWizard in Kotlin under `app/src/main/`;
  every Sable step is re-done against the new activities. SableOS 2.0 also moves the
  Full Sable / Essentials / Stock Android choice into setup, so these are re-planned, not copied.
* **Done:** SystemUI Quick Settings, lock screen and theme (frameworks/base 0101-0103, 0303, 0304,
  0307, 0310-0312), Launcher3 0002, LineageParts 0601 and Settings 1002, 1201, 1202, 1205 were
  ported by hand on 2026-11-04. Notes:
  * 0101 keeps the 24.0 split-internet-tile branch; only the default set reads the overlay array.
  * 0102 sets the tile radii in `res/values/shade_dimens.xml`, where 24.0 moved them.
  * 0304 uses indication type 20, because 24.0 took 17 to 19 for its own large-screen unlock
    hints (they stay off on the Q25).
