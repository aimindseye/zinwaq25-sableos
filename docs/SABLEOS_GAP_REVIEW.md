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
* T1 to T4 are all written in this repository. The owner decided there are no
  Developers A or B, so T4 was implemented here too, for every Sable device
  (no device-model branching). Everything marked *device* waits for the phone.
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
| §4.1 P3 Setup keyboard-first flow | Yes | Source yes | **Written**: LineageOS SetupWizard patches 0401-0402 (keyboard-first focus, Sable Keyboard step) |
| §4.1 P4 Weather user-managed cities | Yes | Yes | **Written**: user-managed cities with per-city cache keys in Sable Weather |
| §4.2 P5 Reader v2 (EPUB, PDF, comics, audiobooks, backup) | Yes | Yes | **Done**: `apps/common/reader` replaces the old Reader; in `apps.tsv` |
| §4.3 PR #28/#29 operator qualification | Titan 2 | n/a | Merged upstream as #32/#34 |
| §5 KF-A Attention/Hub, KF-B SystemUI, KF-C Sable Tools, KF-D All Apps | Yes | Yes (code), runtime on device | **Written**: [KF-A](implementation/kf-a.md), [KF-B](implementation/kf-b.md), [KF-C](implementation/kf-c.md), [KF-D](implementation/kf-d.md) |
| §6 Battery usage/health (BH1-BH7) | Yes | Settings UI yes; charging control no | **Written**: BH1-BH5 as Settings patches 0201-0203 ([Battery](implementation/battery.md)); BH6 not authorized; BH7 needs the device |
| §7 Close stale PRs #265/#288 | No | n/a | Already closed |
| §8 C1 #82 duplicate Messages | Yes | Yes (composition) | Sable Messages has no SMS role (no `SMS_DELIVER`), so AOSP Messaging stays the SMS app and Sable Start hides it (T3, written) |
| §8 C2 #84 first-run crash evidence tooling | Yes | Yes (script) | **Written**: `scripts/capture-crash-evidence.sh` ([CRASH_EVIDENCE.md](CRASH_EVIDENCE.md)) |
| §8 C3 #83 icon/launch audit | Yes | Static part yes | **Written**: `scripts/audit-app-icons.py` (in CI); no app has an adaptive or monochrome icon yet (report-only); launch timing on device |
| §8 C4 #123 Reader status | Yes | Yes | Reader v2 source now in this repo; runtime pending |
| §8 D IR-002/005/011/014/015 work packages | Yes | Yes | T3 is the Q25 version of these packages |

## 2. What the `sableos` PRs contain, by group

| PRs | Group | Q25 relevance |
|---|---|---|
| #1-#6, #33-#100 | Panther (Pixel 7) R1-R9: GrapheneOS base, Sable Start/Launcher3, apps, SystemUI | Apps imported; Launcher3/SystemUI patches are GrapheneOS-specific, retarget in T3 |
| #7-#21, #34-#62, #110, #315 | R8/R9 apps, design system, quality gates | Imported at `c538fc0` |
| #107-#124 | R10 multi-device foundation, keyboard-first design, Reader import | Design followed; Reader superseded by v2 |
| #125-#335 (most) | Titan 2 N0/N1 GSI, C3A/C3B, N1I cellular/IMS | Not applicable |
| #303 | Battery plan | Written (BH1-BH5) |
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

**T3, LineageOS framework integration (phase Q4): written.**
* IR-005 Sable Keyboard as the only IME and IR-002 Sable Start (`SableLauncher`)
  as HOME, with Launcher3QuickStep kept for Recents (Launcher3 patch 0001).
* #82: `SableLauncher` hides `com.android.messaging`, which stays the SMS app.
* Branding and density: KF-B (About phone row, "Welcome to SableOS", one
  `SABLE_LCD_DENSITY` value).
* IR-014: LineageOS SetupWizard patches 0401-0402.
* IR-015: per-city cache keys in Sable Weather.
* #84 crash-evidence script and #83 static icon audit
  ([`implementation/t3.md`](implementation/t3.md)).

**T4, keyboard-first designs and Battery: written.**
* KF-A Attention/Hub: Hub follows Android's notification policy; Settings and
  shade handoff (Settings 0300, frameworks/base 0301).
* KF-B SystemUI convergence: shared token table, three overlays, Quick
  Settings patches 0101-0102, appearance provider and About row (Settings
  0101-0102).
* KF-C Sable Tools: one Tools app; Radio Diag folded in.
* KF-D All Apps privacy row in Sable Start and responsive polish in Media, Hub
  and Calendar.
* Battery BH1-BH5 in Settings > Battery (0201-0203); BH6 charging control is
  not authorized and stays closed.

None of the Android code was compiled here (Google Maven is blocked), so the
first operator build compiles Sable Start, Hub, Media, Calendar, Weather,
Tools, Settings and SystemUI and fixes what the compiler finds. The pure logic
underneath is unit-tested. Each package's notes list what still needs the
phone; the device gates are Q4-* in [`QUALIFICATION.md`](QUALIFICATION.md).

## 4. Waits for the device

* R0 backup and stock-restore rehearsal.
* Key scan codes and the Sym layer.
* Camera ids.
* Radio, IMS and VoLTE state.
* Final density.
* Every boot, runtime and power gate in [`QUALIFICATION.md`](QUALIFICATION.md).
