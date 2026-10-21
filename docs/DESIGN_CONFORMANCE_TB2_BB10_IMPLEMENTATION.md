# SableOS Titan 2 / Q25 design-conformance implementation ledger

Status: **PARTIAL — source patch under review, not full design or device qualification**.

## Authority and version comparison
- User-supplied `DESIGN_CONFORMANCE_Q25.md`: 24 visual design artifacts; checked against `claude/project-thread-p8uj72`.
- `aimindseye/zinwaq25-sableos` `main` is commit `f312db6f9c4b84046cbbd646d7e4b7d04e42d6cd`. PR #25 merged the earlier thread, including the document's seven "Fixed for next build" rows. Do not repeat those patches.
- Canonical visual assets: `sableos-project/platform_sable/docs/design/artifacts/`, especially `sable-start-titan2-3page.svg`.
- Canonical Titan 2 HOME ownership: `sableos-project/platform_sable/docs/C3B_INTEGRATION_BOUNDARY.md` and `aimindseye/sableos/docs/CURRENT_HOME_AND_C3B_INTEGRATION_AUTHORITY.md` require Launcher3QuickStep-hosted Sable presentation; Q25 standalone `SableLauncher` **does not qualify this Track B requirement**.
- BlackBerry 10 *UI Guidelines* (2015), especially pp. 9–16, 22–32 and 120–123: communication first; contextually restrained bars; action affordances; 2-column Active Frames, icon-based app grid, keyboard shortcut flow. This patch is an original SableOS adaptation, not a copy of copyrighted UI artwork.

## Implemented in this feature branch — portable HOME presentation only
1. `HomeLayout.MODE_BB10` is the new default for fresh homes; persisted `list` and `tiles` settings remain supported.
2. BB10-inspired 2-column **Recent activity** cards backed only by Sable Start *session launch history*; never claim live Android tasks; max four; private-profile entries excluded.
3. Four-column grid of user-selected Home apps, with compact icons/labels; private-profile entries excluded; quick bar retained below; All Apps accessible.
4. Keyboard focus and Enter/tap to reopen; long-press/menu context via existing launch/peek callbacks; Edit Home offers all three modes.
5. Glance left and Hub right stay intact; user-customized required Command and All Apps slots continue to use `QuickBar`.
6. Pure `HomeLayoutTest` updated for mode persistence/normalization. Android 16 native HOME/Recents compile and live device acceptance remain pending.

**Not an Active Frames runtime implementation:** a real Android task snapshot/list requires Quickstep task APIs in the actual Track B Launcher3 host. Session-launch shortcuts intentionally do not show closing/running indicators.

## All unresolved rows from the 24-artifact review

"Source partial" means prior Q25 features exist but the named gap is **not implemented/verified closed here**. Every row requires source + build + 720x720 device captures before marking PASS.

| Artifact | Remaining gap or known conflict | Owner/safe implementation gate |
|---|---|---|
| All Apps | Privacy filter chips, slot picker, permission-match grouping | Launcher3 hosted Sable All Apps; Android PackageManager grants truth; profile/lock test |
| Command | Best result, per-result actions, locked state, system Alt+Space | Start plus Android input dispatch; never intercept typing into editors |
| Hub | Subfilters, slash search, Menu actions, counts-only private | Hub APK + notification permission and work/private profile capture |
| Messages | End/Esc to latest, PageUp, thread search, new-message chip | SMS owner app/UI; keyboard/edit text tests |
| Phone | Printed digit mapping and recents privacy | Stock Dialer/device; no role override until calls/SMS recheck |
| Contacts | Route subtitle, Hub chip, locked states | Contacts/Sable Hub integrations; no private text on locked phone |
| Lockscreen | Count row, private card, Locked and emergency hint | SystemUI/Keyguard; **no bare E** (Q25 E is digit 2); choose safe dedicated shortcut with emergency-call proof |
| Quick Settings | Hub summary, hint, slash search, 2-vs-3 columns | SystemUI host; default 2-column retained until visual+accessibility acceptance |
| Settings | Keyboard/input, Diagnostics and global focus | Settings APK/framework; Diagnostics top-level (not under Setup-only Sable category); permission checks |
| Setup | Step counter, Wi-Fi key hints, navy cards; restore still live | SetupWizard; fail closed/hide restore unless restore qualification passes |
| Mobile Manager | Control Center, Freezer, Student mode | System privileged API; avoid nonfunctional switches |
| Private/Dual | Secure Vault, Dual apps, App lock stored but unenforced | Security boundary; **hide ineffective App lock UI** until actual launch gate; dual requires framework/device gates |
| Connectivity | Card hub; stored display profiles not applied | Settings/SystemUI and window manager; store-only UI must not imply effect |
| Camera | Z/R, trackpad movement guard, Sable Camera default, reticle/dock | Sable Camera + Camera2 device; RAW only if supported; don't silently change camera default |
| Browser | T/B/L collision, tabs, reader, start cards | Browser WebView source; editor-aware keys and focus |
| Files | J/K/T/E/V/F, preview panes, conflict with Alt-letter | DocumentsUI/Files source; file access grants and keyboard safety |
| Mail/Calendar | Calendar week/NOW and C/T/V/J/K/R; Mail keys mostly implemented | Calendar source; mail provider guard from Q25 PR #20; device mail launch still unverified |
| Media | Key labels, caption strip, S-share ambiguity | Gallery app; preserve Android Share until design resolution |
| Utilities (was Missing) | Notes, Recorder, Clock, Utilities home absent | New first-party apps and persistent local storage; not bundled yet |

Rows **already identified Done in user review**: start-three-page *in Q25 code only*, battery usage, toolbox. Q25 subscreen is N/A, but Titan 2 rear-screen and Magic-key behavior require a separate real-device-specific contract.

## Resolve design conflicts safely
- Emergency shortcut: **do not assign bare E**; use an explicit modifier only after emergency dialer accessibility/security validation.
- Diagnostics: preserve Setup-only `Settings > Sable` and provide a separate authorized Settings entry later.
- Quick settings: retain two columns until 720×720 and 1.3× font testing proves three columns usable.
- Restore in Setup: fail closed rather than advertising unqualified recovery; implementation still open.
- Legacy LineageOS legal/Trust/contributors: preserve legal/system obligations, suppress only misleading branding when reviewed.

## TB2 build reality, 2026-10-19
Uploaded `tb2-sable-v12-build.log`: Q25 platform/apps/common/reader/Mail/Text Reader **all five Gradle roots completed**; TB2 then failed *before Android systemimage compile* at `Cannot locate config makefile for product "tdgsi_arm64_ab"` and `No release config set for target`. Those are GSI build-driver/product registration gates, **not evidence the conformance gaps are fixed**. `tb2-sable/q25-pinned` is a **commit-pinned immutable source checkout**; update pin and rebuild only following reviewed Q25 patch and actual Gradle/Soong pass. Existing TB1 phone/IMS/stock vendor must remain untouched.

## Promotion requirements
- Android Studio/Gradle `sablestart-presentation-check:testDebugUnitTest` + `lintDebug` + Kotlin compilation PASS on the changed revision.
- Full Q25 `tests/run.sh` plus design/device profile unit tests PASS; SonarCloud live gate run on that revision.
- Track B Launcher3QuickStep host links same Sable Start sources, with HOME, Recents, gestures, private profile and keyboard acceptance evidence.
- **Every partial row above separately verified**; do not claim complete conformance because this BB10 HOME layout patch passes.
