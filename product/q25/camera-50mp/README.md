# Optional 50 MP rear camera

**Off by default** (`SABLE_Q25_CAMERA_50MP=NO` in `build/config/q25.env`). Not built or run on a Q25
yet. Turn it on only for a device test:

```bash
SABLE_Q25_CAMERA_50MP=YES bash build/sable.sh q25 Q2 stage   # or Q4
python3 scripts/camera-50mp.py check                          # STOCK / APPLIED per part
```

The Q25's Samsung ISOCELL JN1 sensor can deliver 8160 x 6144, but the stock camera stack only saves
12.5 MP. This is the Lineage port of [bellhopsw/q25-50mp](https://github.com/bellhopsw/q25-50mp) at
`caf855d5adc9578f3231f73947b6872b8786db8a` (v0.1.0-alpha.2-lineage), built from source in our tree:

| Part | Where | What it does | Licence |
|---|---|---|---|
| Kernel | `kernel/0001-s5kjn1-50mp-capture-mode.patch`, applied to `kernel/xelex/mt6789` | The s5kjn1 driver's capture scenario becomes the full 8160 x 6144 mode (10 fps max) and reports 4-cell Bayer, so the HAL uses its remosaic path. Register table from the Nothing Phone 2a JN1 driver | GPL-2.0 (kernel) |
| Remosaic library | `remosaic_shim_v4_rc1.c`, `Android.bp` (staged to `vendor/sable/q25/camera-50mp`) | Replaces the vendor `libremosaic_wrapper.so`: PDAF repair, quad equalisation, denoise, demosaic to an ordinary Bayer frame. Exports the five functions `libcameracustom.plugin.so` imports | MIT (`LICENSE`) |
| Vendor byte edits | `scripts/camera-50mp.py`, applied to `vendor/xelex/Q25` | 62 bytes in three MediaTek libraries: offer 8160 x 6144 to apps, drop the MediaTek-camera-only vendor-tag check, raise the remosaic ISO limit 200 to 3200, and send only requests wider than 4096 px down the 50 MP path | edits to the proprietary blobs we already build from |

`scripts/stage-product.sh` reverts all of it at the start of every stage and applies it when the
option is on. `camera-50mp.py` checks every size and byte (and that the kernel patch applies) before
writing anything, and refuses a different kernel or blob drop. Run
`python3 scripts/camera-50mp.py revert` before `repo sync` of the kernel or the vendor blobs.

## Verified without a Q25

* The kernel patch is based on our pinned kernel commit `2a873a35` and applies to it.
* All three libraries in our pinned blobs (`TheMuppets/proprietary_vendor_xelex_Q25` `b8c2c8f`) have
  the expected sizes and stock bytes, and the patched `libmtkcam_featurepolicy.so` hashes to the
  sha256 the upstream project publishes for its working Lineage image (`d43db9ca...`).
* The library source is byte-identical to upstream's release source (md5 `35f58872...`) and compiles
  cleanly with `-Wall -Werror -Wno-unused-function` on a host compiler; not yet with the Android
  toolchain.
* `tests/test_camera_50mp.py` covers apply, revert, idempotence and refusals on a synthetic tree.

## To check on the device

Upstream reports (one phone, Lineage 23.2 nightlies 20260927 and 20261004): 50 MP stills in about
2.3-3 s, clean but soft (no sharpening by default), good light only; manual ISO/shutter and ISO above
3200 skip the 50 MP path. On a Q25:

1. `C_verify`-style checks: `imgsensor_isp6s.ko` loads, `logcat | grep RemosaicShim` shows one
   `process: 8160x6144 ... done` line per High-res shot and none for normal photos.
2. Sable Camera: Auto stays at the conventional size and High-res offers 8160 x 6144
   (`CameraDeviceProfile.ZinwaQ25.remosaicJpegSizes`, patch `sable-src/0012`).
3. Shutter lag and preview for ordinary photos (the capture scenario now uses the 50 MP sensor mode).
4. Sharpening (`persist.vendor.remoshim.sharpen`) and whether calibration should persist in
   `/data/vendor/camera` (needs a sepolicy rule; the library keeps it in memory until a reboot).

Credit: bellhopsw. The owner will contact the author once this works on a Q25.
