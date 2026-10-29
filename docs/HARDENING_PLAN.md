# Hardening plan: GrapheneOS-style protections on the Q25

Status: **plan only. Nothing here is implemented or measured on a phone.**

SableOS for the Q25 is a LineageOS build. This document lists the hardening
features GrapheneOS is known for, says which ones SableOS already has, which can
be ported, and what stands in the way. It exists so that hardening work is done
feature by feature, each with a test, and so that nobody claims more protection
than the build gives.

The GrapheneOS feature list below is written from memory of the GrapheneOS
documentation, not copied from it. Check each feature against the current
[GrapheneOS features page](https://grapheneos.org/features) and the licence of
its source repository before porting. Code taken from GrapheneOS is credited in
`NOTICE`. RestlessOS (`sableos-project/treble_restlessos`) is a GrapheneOS-based
Treble GSI; its patches target GrapheneOS's Android tree, so they are a
reference, not something to apply to the LineageOS tree.

## The limit that comes first: the build and boot state

Hardening inside Android is worth little if the device is open at a lower level.
SableOS 1.0 ships as follows:

| Fact | Effect on hardening |
| --- | --- |
| Build type `userdebug` | `adb root` works while USB debugging is on. A `user` build exists in the scripts and has not been tested on a phone |
| Bootloader unlocked | Anyone with the phone can flash it. GrapheneOS-style verified boot needs a locked bootloader with the owner's own AVB key |
| Custom AVB key | Unknown whether the Q25 bootloader accepts one (`docs/STOCK_BASIS.md`) |

So the first step is the `user` build and the AVB question (gates `Q5-KEYS` and
`Q5-AVB` in the qualification list), not an extra feature.

## Feature inventory

Status words: **Have** (Android or LineageOS ships it, or SableOS added it),
**Port** (worth porting), **Evaluate** (needs a test before deciding),
**N/A** (not possible on the Q25 or not wanted).

| Feature (GrapheneOS) | Status | Notes and where it would live |
| --- | --- | --- |
| Verified boot with the owner's key | Blocked | Needs a locked bootloader and a custom AVB key; see above |
| Secure, current `user` build | Port first | Test the `user` variant (`build/sable.sh q25 full all --variant user`), then check `adb root` is refused |
| SELinux enforcing | Have (default) | Verify on the phone: `adb shell getenforce` should say `Enforcing` |
| `hardened_malloc` | Evaluate | Standalone allocator in `external/` plus a bionic change. RestlessOS dropped it for Scudo because of boot loops on devices with a 39-bit address space. Check the kernel's `ARM64_VA_BITS`, test vendor HALs and the graphics stack. Medium risk |
| Exec-based app spawning | Evaluate | Frameworks and bionic patches. Breaks root tools, which SableOS does not ship. Costs some app start time and memory |
| Hardened kernel configuration | Port (config) | The kernel is a GKI kernel built from `kernel/xelex/mt6789`. Compare its config with a hardened one and carry changes as kernel config patches in `patches/framework/kernel/` |
| Memory tagging (MTE) | N/A | Needs Armv8.5; the Q25's Cortex-A76 and A55 cores do not have it |
| Network permission toggle | Have (different) | Mobile Manager > Network access blocks an app from all networks (Settings 0603, 1204) |
| Sensors permission toggle | Port | Framework and Settings patches; larger |
| Contact scopes, storage scopes | Port later | Framework patches; large and touch the permission controller |
| Auto-reboot after being locked for a time | Port | Small service-level change in frameworks/base; improves protection at rest |
| Bluetooth and Wi-Fi auto-off timeouts | Port | Settings and system service changes; small to medium |
| PIN layout scrambling | Evaluate | LineageOS has a scramble option on its lock screen; check it works with the Sable lock screen, which takes the PIN from the physical keyboard |
| Random MAC address per network | Have (default) | Android default; verify in Wi-Fi settings |
| Private DNS | Have (Android) | Settings > Network > Private DNS |
| Hardened WebView (Vanadium) | N/A for now | Requires building Chromium; SableOS ships LineageOS's WebView |
| USB-C port control | N/A | Depends on Pixel hardware |
| Auditor, GrapheneOS camera and PDF apps | N/A | Auditor needs hardware attestation; SableOS has its own apps |
| Sandboxed Google Play | N/A | SableOS uses microG and Aurora Store, not Google Play |
| App security inspection | Have (Sable) | Settings > Apps > App security & privacy (0604, 1209): read-only, evidence-labelled |
| Per-app background and freeze controls | Have (Sable) | Mobile Manager: App blocker, Freezer, App Control Center (1205, 1208) |
| Native debugging protection | Evaluate | Relevant only once `adb root` is gone (`user` build) |

## Suggested order

1. **`user` build and AVB.** Build, boot and qualify the `user` variant; answer
   whether the bootloader takes a custom AVB key. Add the result to
   `docs/QUALIFICATION.md`.
2. **Kernel configuration review.** Read the Q25's `.config` (from the build, or
   `/proc/config.gz`), list the hardening options that are off, and patch the
   ones that are safe. Measure boot and a night's battery use before and after.
3. **`hardened_malloc` experiment.** On a test build only, behind a build flag,
   with a crash and boot check. Keep it off by default until a week of daily
   use shows no new crashes.
4. **Exec spawning.** After hardened_malloc, because both change process
   start-up.
5. **Auto-reboot and radio timeouts.** Small changes with a clear benefit.
6. **Sensors permission, then contact and storage scopes.** Large framework
   work; only after the Lineage 24.0 decision, so it is ported once.

## Rules for each feature

- One patch series per feature, numbered, with a host test where the logic can be
  pure Java or shell.
- A row in `docs/QUALIFICATION.md` that says how to check it on the phone, and
  stays `NOT_RUN` until someone has.
- A line in `NOTICE` for any code or design taken from GrapheneOS, and the
  licence checked first.
- A note in the user guide only when the feature is visible to the user.
- Do not claim "hardened" or "secure" in release notes. Say which protections are
  on and what is still open (see the first table).

## Open questions

- Does the Q25 bootloader accept a custom AVB key? (`docs/STOCK_BASIS.md`)
- What is the Q25 kernel's virtual address size and version?
  (`adb shell uname -r` and `ARM64_VA_BITS`)
- Does any vendor HAL on the Q25 depend on the default allocator's behaviour?
- Should the Lineage 24.0 move happen first, so framework hardening patches are
  written once against Android 17?
