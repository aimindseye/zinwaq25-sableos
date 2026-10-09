# Return to stock

Status: **not yet verified for the Q25.** Fill this in with tested steps before
anyone relies on it.

What is known:

* The Q25 bootloader unlocks with standard `fastboot flashing unlock`.
* Zinwa ships stock OTA updates (`ro.fota.version=Q25_26.03.2026` in the stock
  release LineageOS used).
* Stock Q25 images are shared in this Google Drive folder (folder title
  "With-GMS"): https://drive.google.com/drive/folders/1RlhjXInYh7t_ITkWS6fqKY4quZAAKmuT.
  Its contents haven't been checked by this project: before relying on it,
  record each file's name, size and SHA-256 in `STOCK_BASIS.md` and confirm the
  build matches your device. Never copy the images into this repository.
* MediaTek devices can usually be recovered from BROM mode with tools such as
  SP Flash Tool or mtkclient, given a matching scatter/firmware package. Whether
  the Q25's BROM is reachable and unprotected has not been checked here.
* The MarathonOS/Droidian port publishes Q25 GPT images, including
  `gpt_untouched.bin` (stock layout), in
  https://github.com/MarathonOS/marathon-zinwa-q25/tree/main/gpt. Treat it as
  community material, not verified stock.

Before your first flash, do this so you can come back:

1. Note your stock build and board name (`fastboot getvar product`).
2. If you have a way to dump partitions (for example mtkclient from BROM), back
   up at least `boot_a/b`, `vendor_boot_a/b`, `dtbo_a/b`, `vbmeta*`, `super`,
   `nvram`, `nvdata`, `nvcfg`, `protect1`, `protect2`, `persist`, `proinfo`.
   `nvram`/`nvdata` hold IMEI and radio calibration; losing them can break
   cellular for good.
3. Keep the backups off the device and out of this repository.

Contributions with a tested procedure are welcome.
