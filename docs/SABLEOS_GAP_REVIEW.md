# SableOS gap review for the Q25 (2026-10-09)

Inputs: the *SableOS Titan 2 / Product Work: Current Plan and Assignments*
(2026-10-04), every pull request in `aimindseye/sableos` (#1-#335) and
`aimindseye/titan2-temp` (#1-#34), and this repository at `main` 5815be7.
Question: which Sable features can the Q25 port carry **before a Q25 is in
hand**, and what is missing?

## Bottom line

* The Q25 port already builds from current SableOS source: `sableos` `main` is
  still `c538fc0` (later PRs #328-#335 target side branches), which is exactly
  what `sable-src/` imported.
* The real gap was the parallel product-source lane, `titan2-temp`. It holds
  Keyboard provisioning (P2), Reader v2 (P5A-P5F) and the Start, Setup and
  Weather reference sources (P1, P3, P4), none of which reached `sableos`
  `main`. **This change brings all of it in** and enables the full Sable app set
  (17 apps instead of 5, with Mail and Text Reader built from pinned upstreams).
* About 230 of the 335 `sableos` PRs are Titan 2 GSI, cellular and IMS work
  (N0-N1I, C3A/C3B). The Q25 doesn't need them: it is a full LineageOS device
  build with working vendor telephony. Their process lessons are already in
  [`LESSONS_FROM_TITAN2.md`](LESSONS_FROM_TITAN2.md).
* T1 and T2 are done in this change. T3 can be written without a Q25. T4 is new shared feature code that the plan assigns to Developers A
  and B. Everything marked *device* waits for the phone.
* Limits of this environment: Google's Maven repository is unreachable here,
  so no Gradle/AGP or AOSP build ran. The pure-Kotlin cores were compiled and
  tested with kotlinc: Keyboard 156/156, Camera 86/86. Android builds need the
  operator host.

## 1. The 2026-10-04 plan, item by item

The plan is partly stale. `sableos#295` is closed. `titan2-temp#28` and `#29`
were replaced by `#32` and `#34`, both merged. `sableos#303` (Battery plan) is
merged.

| Plan item | Q25? | Without a Q25? | State after this change |
|---|---|---|---|
| §2-3, §9, §11 Titan 2 cellular/IMS (N1I, PR #295, C3A/N1F, stock-vs-GSI) | No | n/a | Not applicable: LineageOS Q25 uses the vendor's own RIL/IMS |
| §4.1 P1 Sable Start keyboard-first | Yes | Source yes; HOME needs Q4 | `sable-src/reference/sable-start-type-to-find` imported; hosted as `SableLauncher` HOME at Q4 (T3, written) |
| §4.1 P2 Sable Keyboard provisioning | Yes | Yes | **Done**: `ImeContract`, `ImeLauncher`, settings status UI imported; tests pass |
| §4.1 P3 Setup keyboard-first flow | Yes | Source yes | Reference imported; LineageOS uses its own SetupWizard, not SetupWizard2 (T3) |
| §4.1 P4 Weather user-managed cities | Yes | Yes | Reference imported; adopting per-city cache keys in Sable Weather is T3 |
| §4.2 P5 Reader v2 (EPUB, PDF, comics, audiobooks, backup) | Yes | Yes | **Done**: `apps/common/reader` replaces the old Reader; in `apps.tsv` |
| §4.3 PR #28/#29 operator qualification | Titan 2 | n/a | Merged upstream as #32/#34 |
| §5 KF-A Attention/Hub, KF-B SystemUI, KF-C Sable Tools, KF-D All Apps | Yes | Yes (code), runtime on device | T4; plan assigns them to Dev A/B |
| §6 Battery usage/health (BH1-BH7) | Yes | Settings UI yes; charging control no | T4; no charging control (plan forbids it) |
| §7 Close stale PRs #265/#288 | No | n/a | Already closed |
| §8 C1 #82 duplicate Messages | Yes | Yes (composition) | Sable Messages has no SMS role (no `SMS_DELIVER`), so AOSP Messaging stays the SMS app and Sable Start hides it (T3, written) |
| §8 C2 #84 first-run crash evidence tooling | Yes | Yes (script) | T3: capture dropbox, `logcat -b crash`, exit-info, tombstones before clearing |
| §8 C3 #83 icon/launch audit | Yes | Static part yes | T3: static icon audit of the 15 APKs; launch timing on device |
| §8 C4 #123 Reader status | Yes | Yes | Reader v2 source now in this repo; runtime pending |
| §8 D IR-002/005/011/014/015 work packages | Yes | Yes | T3 is the Q25 version of these packages |

## 2. What the `sableos` PRs contain, by group

| PRs | Group | Q25 relevance |
|---|---|---|
| #1-#6, #33-#100 | Panther (Pixel 7) R1-R9: GrapheneOS base, Sable Start/Launcher3, apps, SystemUI | Apps imported; Launcher3/SystemUI patches are GrapheneOS-specific, retarget in T3 |
| #7-#21, #34-#62, #110, #315 | R8/R9 apps, design system, quality gates | Imported at `c538fc0` |
| #107-#124 | R10 multi-device foundation, keyboard-first design, Reader import | Design followed; Reader superseded by v2 |
| #125-#335 (most) | Titan 2 N0/N1 GSI, C3A/C3B, N1I cellular/IMS | Not applicable |
| #303 | Battery plan | T4 |
| #316 (draft) | Gitleaks scoping and offline Rust advisory checks | Optional CI idea for this repo |
| #328-#334 (drafts) | Titan 2 N1I-C2/D0 | Not applicable |

## 3. Remaining work, in tranches

**T1, done in this change.**
* Import `titan2-temp` `7570470`.
* Make the import reproducible: `--titan2-temp` overlay plus `patches/sable-src/`.
* Enable the full app set, including Messages and Reader v2.

**T2, Sable Mail and Text Reader: done in this change (build unverified).**
* Both are built the way `sableos` builds them: the pinned upstream
  (Thunderbird 23.0 for Mail, Vaachak Text Reader) with
  `apply_sable_flavor.py` and `verify_sable_*.py` applied.
* `apps.tsv` rows use `flavor:mail` and `flavor:textreader`.
* `build-apps.sh` caches each upstream once, with network access authorized
  explicitly.
* The first real build runs on the operator host.

**T3, LineageOS framework integration (phase Q4, no Q25 needed to write).**
* **IR-005, written:** at Q4 Sable Keyboard overrides LatinIME, so it is the
  only and default IME. Gated on Q3-TEXT.
* **IR-002, written:** LineageOS 23.2 has no Trebuchet; it ships
  Launcher3QuickStep. Q4 builds Sable Start as the standalone `SableLauncher`
  HOME app, as SableOS R9 does, and a Launcher3 patch keeps Launcher3QuickStep
  for Recents only.
* **Branding:** Sable branding in Settings > About and Setup.
* **IR-014:** keyboard-first steps in the LineageOS SetupWizard.
* **IR-015:** per-city cache keys in Sable Weather.
* **#82, written:** `SableLauncher` already hides `com.android.messaging`
  (also LineageOS's Messaging package), which stays the SMS app. Gate
  Q4-MESSAGES checks it on the phone.
* **#84:** crash-evidence capture script.
* **#83:** static icon audit.
* **Density:** an overlay for the open choice in
  [`../device-profile/DISPLAY.md`](../device-profile/DISPLAY.md).

The patches are written against LineageOS 23.2 and verified by an operator
build (the tree is about 200 GB). Boot and UI checks wait for the device.

**T4, keyboard-first design implementations (shared code).**
* KF-A Attention/Hub.
* KF-B SystemUI convergence.
* KF-C Sable Tools.
* KF-D All Apps privacy.
* BH1 Battery Settings.

The designs are final in `platform_sable`, and the plan assigns them to
Developers A and B for all Sable devices. Writing them only in this
repository would fork shared apps, so T4 needs an owner decision.

## 4. Waits for the device

* R0 backup and stock-restore rehearsal.
* Key scan codes and the Sym layer.
* Camera ids.
* Radio, IMS and VoLTE state.
* Final density.
* Every boot, runtime and power gate in [`QUALIFICATION.md`](QUALIFICATION.md).
