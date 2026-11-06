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
* An existing 23.2 tree is moved to 24.0 by `bootstrap`: it reverts the Sable framework patches, runs `repo init -b lineage-24.0` and syncs, keeping the downloaded objects.

## Status

62 apply as is, 11 refreshed, 2 apply once the earlier ports are in, 31 ported by hand. Nothing is left to port. Twenty-three more are new on 24.0: Settings 1601 and SetupWizard 0415 (the text size choice), frameworks/base 0314 and Settings 1602 (the lock screen Peek), frameworks/base 0315 and Settings 1603 (the compact PIN pad and size tiles), frameworks/base 0316 and Settings 1604 (compact Settings rows and the Sable page), frameworks/base 0317 (the next event on the lock screen), frameworks/base 0318 (the Peek as an app column beside the newest notification), frameworks/base 0319 (Alt+Space opens Command from any app), SetupWizard 0416 (the Full Sable, Essentials or Stock Android step), SetupWizard 0417 (the BlackBerry 10 or BlackBerry 7 look step), frameworks/base 0320 with Settings 1606 (Picture Password), frameworks/base 0321 (built-in Picture Password pictures), Settings 1607 (the homepage in BlackBerry 10 order), Settings 1608 (Deep sleeping apps), and Settings 1605 with device tree 1503 (the trackpad Keys or Pointer choice).

Every patch applies to the 24.0 heads in order. That is not yet a build: the ports below were
written against the 24.0 sources without compiling them, so the first 24.0 build is the real test.

| Patch | 24.0 status |
|---|---|
| `device/xelex/Q25/1501-SableOS-type-on-the-Q25-currency-key.patch` | applies as is |
| `frameworks/base/0101-SableOS-read-default-large-Quick-Settings-tiles-from.patch` | ported by hand |
| `frameworks/base/0102-SableOS-Quick-Settings-tiles-use-the-Sable-card-and-.patch` | ported by hand |
| `frameworks/base/0103-SableOS-apply-the-pure-black-theme-at-boot-and-after-setup.patch` | ported by hand |
| `frameworks/base/0301-SableOS-keyboard-notification-commands-in-the-shade.patch` | applies as is; the row's notification now always comes from `entryAdapter`, since 24.0 removed `NotificationBundleUi` and `entryLegacy` |
| `frameworks/base/0302-SableOS-Alt-shade-shortcuts-for-keyboards-without-Fn.patch` | refreshed (context moved) |
| `frameworks/base/0303-SableOS-Space-toggles-and-Alt-Enter-opens-details-on.patch` | ported by hand |
| `frameworks/base/0304-SableOS-keyboard-hint-line-and-type-ahead-on-the-loc.patch` | ported by hand |
| `frameworks/base/0305-SableOS-Sable-lock-screen-clock-and-unlock-text.patch` | refreshed (context moved) |
| `frameworks/base/0306-SableOS-keep-the-lock-screen-clock-on-the-current-ti.patch` | applies as is |
| `frameworks/base/0307-SableOS-Sable-locked-glance-emergency-shortcut-and-b.patch` | ported by hand |
| `frameworks/base/0310-SableOS-three-column-labeled-Quick-Settings-grid-fou.patch` | ported by hand; the compact-row column count uses `Flow.hydratedStateOf(initialValue)`, since 24.0 made `HydratedActivatable.hydrator` private |
| `frameworks/base/0311-SableOS-Sable-Quick-Settings-header-Hub-summary-row-.patch` | ported by hand |
| `frameworks/base/0312-SableOS-slash-searches-the-notifications-in-the-shade.patch` | ported by hand |
| `frameworks/base/0313-SableOS-Alt-Shift-R-cycles-the-Tall-canvas-of-the-ap.patch` | refreshed (context moved) |
| `frameworks/base/0314-SableOS-lock-screen-Peek-one-row-per-app.patch` | new on 24.0 |
| `frameworks/base/0315-SableOS-compact-PIN-pad-and-Lock-screen-size.patch` | new on 24.0 |
| `frameworks/base/0316-SableOS-compact-Settings-rows-on-short-screens.patch` | new on 24.0 |
| `frameworks/base/0317-SableOS-next-calendar-event-on-the-lock-screen.patch` | new on 24.0 |
| `frameworks/base/0318-SableOS-lock-screen-Peek-as-an-app-column-beside-the.patch` | new on 24.0 |
| `frameworks/base/0319-SableOS-Alt-Space-opens-Sable-Command-from-any-app.patch` | new on 24.0 |
| `frameworks/base/0320-SableOS-Picture-Password-as-a-quick-second-unlock.patch` | new on 24.0 |
| `frameworks/base/0321-SableOS-built-in-Picture-Password-pictures.patch` | new on 24.0 |
| `frameworks/base/0501-SableOS-notification-light-for-every-alerting-channe.patch` | applies as is |
| `frameworks/base/0550-SableOS-microG-signature-spoofing-on-user-builds-whe.patch` | ported by hand |
| `kernel/xelex/mt6789/0201-SableOS-report-the-fuel-gauge-s-learned-battery-health.patch` | applies as is |
| `kernel/xelex/mt6789/0202-SableOS-stop-the-Q25-keyboard-driver-logging-every-k.patch` | applies as is |
| `kernel/xelex/mt6789/0901-SableOS-let-the-system-set-the-Q25-keyboard-backligh.patch` | applies as is |
| `kernel/xelex/mt6789/0902-SableOS-no-phantom-Q25-keys-after-the-screen-wakes.patch` | applies as is (driver identical on 24.0) |
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
| `packages/apps/DocumentsUI/1002-SableOS-Files-keys-for-copy-move-rename-new-folder-a.patch` | refreshed (context moved); adds `androidx.annotation.Nullable`, which 24.0 FilesActivity no longer imports (first 24.0 build, 2026-11-05) |
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
| `packages/apps/Launcher3/0003-SableOS-Recents-key-and-button-open-Sable-Start-s-Ac.patch` | refreshed; 24.0 dropped `touchInteractionService` and `CommandType.SHOW_WITH_FOCUS` from OverviewCommandHelper, so Active Frames open from the process's application and the Recents set is TOGGLE and TOGGLE_WITH_FOCUS |
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
| `packages/apps/Settings/1601-SableOS-Text-size-choice-in-Settings-Display.patch` | new on 24.0 |
| `packages/apps/Settings/1602-SableOS-Lock-screen-peek-row-in-Settings-Security.patch` | new on 24.0 |
| `packages/apps/Settings/1603-SableOS-size-tiles-for-Display-Text-and-Lock-screen.patch` | new on 24.0 |
| `packages/apps/Settings/1604-SableOS-Sable-settings-page-in-one-list.patch` | new on 24.0 |
| `packages/apps/Settings/1605-SableOS-Trackpad-Keys-or-Pointer-in-Settings-Keyboard.patch` | new on 24.0 |
| `packages/apps/Settings/1606-SableOS-Picture-Password-row-on-the-Sable-page.patch` | new on 24.0 |
| `packages/apps/Settings/1607-SableOS-Settings-top-level-in-BlackBerry-10-order.patch` | new on 24.0 |
| `packages/apps/Settings/1608-SableOS-Deep-sleeping-apps-with-Put-unused-apps-to-sleep.patch` | new on 24.0 |
| `packages/apps/Settings/1609-SableOS-one-blue-accent-and-no-accent-row-2.0-black-a.patch` | new on 24.0 |
| `packages/apps/SetupWizard/0401-SableOS-keyboard-first-focus-in-setup-steps.patch` | ported by hand |
| `packages/apps/SetupWizard/0402-SableOS-Sable-Keyboard-readiness-step-before-Wi-Fi-a.patch` | ported by hand |
| `packages/apps/SetupWizard/0403-SableOS-Esc-returns-to-the-previous-setup-step.patch` | ported by hand |
| `packages/apps/SetupWizard/0404-SableOS-keyboard-test-step-after-keyboard-readiness.patch` | ported by hand |
| `packages/apps/SetupWizard/0405-SableOS-privacy-summary-step-after-Wi-Fi.patch` | ported by hand |
| `packages/apps/SetupWizard/0406-SableOS-Sable-accent-on-the-Theme-step-Theme-after-P.patch` | ported by hand |
| `packages/apps/SetupWizard/0407-SableOS-Ready-to-start-text-on-the-final-setup-page.patch` | ported by hand |
| `packages/apps/SetupWizard/0408-SableOS-drop-the-LineageOS-features-step.patch` | ported by hand |
| `packages/apps/SetupWizard/0409-SableOS-navy-cards-and-a-cyan-focus-ring-in-setup.patch` | ported by hand |
| `packages/apps/SetupWizard/0410-SableOS-Step-n-of-N-on-every-owner-setup-step.patch` | ported by hand |
| `packages/apps/SetupWizard/0411-SableOS-keyboard-check-on-Welcome-keyboard-steps-onl.patch` | ported by hand |
| `packages/apps/SetupWizard/0412-SableOS-Restore-hidden-until-validated-fail-closed.patch` | ported by hand |
| `packages/apps/SetupWizard/0413-SableOS-Ready-screen-rows-as-the-setup-design-shows-.patch` | ported by hand |
| `packages/apps/SetupWizard/0414-SableOS-keep-Next-on-screen-in-short-windows.patch` | ported by hand |
| `packages/apps/SetupWizard/0415-SableOS-text-size-choice-on-the-Theme-step.patch` | new on 24.0 |
| `packages/apps/SetupWizard/0416-SableOS-Sable-apps-step-Full-Essentials-or-Stock.patch` | new on 24.0 |
| `packages/apps/SetupWizard/0417-SableOS-Look-step-BlackBerry-10-or-BlackBerry-7-style.patch` | new on 24.0 |
| `packages/apps/SetupWizard/0418-SableOS-no-accent-or-pure-black-choice-on-the-Theme-s.patch` | new on 24.0 |
| `packages/apps/SetupWizard/0419-SableOS-keyboard-first-black-Ready-screen-no-swipe-up.patch` | new on 24.0 |
| `device/xelex/mt6789-common/1502-SableOS-let-Settings-drive-the-Q25-keyboard-firmware.patch` | ported by hand |
| `device/xelex/mt6789-common/1503-SableOS-let-init-set-the-Q25-trackpad-mode.patch` | new on 24.0 |

## What the hand ports changed

* **SetupWizard (0401-0414):** LineageOS 24.0 rewrote SetupWizard in Kotlin under `app/src/main/`
  and replaced the old navigation bar with the setupcompat footer bar. The Sable steps keep their
  Java classes (moved to `app/src/main/java`, and `app/Android.bp` now builds Java too); the hooks
  in upstream classes (`BaseSetupWizardActivity`, `SubBaseActivity`, `WelcomeActivity`,
  `ThemeSettingsActivity`, `NetworkSetupActivity`, `RestoreIntroActivity`) are rewritten in
  Kotlin. Notes:
  * The Sable pages drop their `NavigationLayout`; Next and Skip come from the footer bar.
  * 24.0 runs Wi-Fi before the SIM screen, so the order is Welcome, Language, Sable Keyboard,
    keyboard test, Wi-Fi, SIM, Privacy, Theme, then date and the rest. As on 23.2, Privacy and
    Theme follow the network steps and the LineageOS features step is gone.
  * The 24.0 Ready screen has no Start button; Enter (or Home) finishes setup, and the Sable
    Ready rows sit between the logo and the go-home hint.
  * In a short window only Welcome's own button bar needs moving; the footer bar already sits
    under the scroll view.
  * The Java classes were compile-checked against stubs of the 24.0 base API. The Kotlin was
    not compiled (no Kotlin toolchain here).
  * The Full Sable / Essentials / Stock Android choice is SetupWizard 0416, a step after
    Theme. The image still leaves out the LineageOS apps that a Sable app overrides in
    `product/q25/apps.tsv` (Messaging, Etar, Twelve, ExactCalculator), so Stock Android uses
    the LineageOS apps the image has (Aperture, Jelly) and keeps the Sable app elsewhere.
    Dropping an override ships that stock app too, and the step then picks it up.
* **SystemUI Quick Settings, lock screen and theme (frameworks/base 0101-0103, 0303, 0304,
  0307, 0310-0312), Launcher3 0002, LineageParts 0601 and Settings 1002, 1201, 1202, 1205:**
  ported by hand on 2026-11-04. Notes:
  * 0101 keeps the 24.0 split-internet-tile branch; only the default set reads the overlay array.
  * 0102 sets the tile radii in `res/values/shade_dimens.xml`, where 24.0 moved them.
  * 0304 uses indication type 20, because 24.0 took 17 to 19 for its own large-screen unlock
    hints (they stay off on the Q25).
