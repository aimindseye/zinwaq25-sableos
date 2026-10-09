# Lessons from the Titan 2 lane

The Titan 2 SableOS port is a Treble system image on a stock MediaTek vendor. As
of 2026-10 it has not reached a stable boot: its first Sable-composed image
entered a reboot loop and a later image failed a bootclasspath check. These are
the lessons this port builds in from the start.

1. **Control first.** Titan 2 added Sable content before a source-built image was
   proven to boot on the current firmware, so the reboot loop couldn't be
   bisected. Q25 phase Q1 is a plain LineageOS build for exactly this reason.
2. **Retain what you flash.** Titan 2's known-good N1B image and target-files were
   not kept, and its pinned source objects later disappeared upstream, so the
   control could never be rebuilt. `collect-artifacts.sh` keeps images, the
   pinned manifest and hashes together; keep that directory.
3. **Record the firmware basis.** A Titan 2 image that booted on stock V01.00.13
   was never re-tested on V01.00.14. Fill `STOCK_BASIS.md` before every flash.
4. **One layer at a time.** Each Q phase adds one kind of change and stays
   revertable (`stage Q1` removes the Sable layer entirely).
5. **Don't stretch evidence across devices.** Titan 2 camera/key/display numbers
   say nothing about the Q25. Profiles start at `EvidenceLevel.None`.
6. **Prefer a real device tree over a GSI when one exists.** The Q25 has one;
   that removes most of the system/vendor compatibility work that stalled Titan 2.
