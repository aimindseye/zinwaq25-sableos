# Build SableOS for the Zinwa Q25

Status: **the Sable apps build and pass their quality gates in GitHub Actions
(section 7). Q1 and Q2 images were built on ai-g732 on 2026-10-15 and boot on a
Q25. Q4 (the framework layer) was first built on 2026-10-17 and boots. The
PR #28 Q4 image was built on 2026-10-22 after three fixes from that run (PR #31,
see Troubleshooting) and was flashed and reviewed on a Q25 the same day. PR #32
(the review fixes, Sable Messages as the default SMS app, the one Hub page and
the display tools) has not been built into an image yet.**

## 1. Host

* Linux x86_64 (Ubuntu 22.04/24.04 or Debian 12 are what LineageOS documents).
* 32 GB RAM recommended (16 GB works with zram/swap).
* 300 GB free disk for the tree, `out/` and ccache.
* Packages (Ubuntu/Debian):

```bash
sudo apt install bc bison build-essential ccache curl flex g++-multilib gcc-multilib \
  git git-lfs gnupg gperf imagemagick lib32readline-dev lib32z1-dev libelf-dev \
  liblz4-tool libsdl1.2-dev libssl-dev libxml2 libxml2-utils lzop pngcrush rsync \
  schedtool squashfs-tools xsltproc zip zlib1g-dev python3 python-is-python3 \
  openjdk-21-jdk adb fastboot
mkdir -p ~/bin && curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
chmod a+x ~/bin/repo && export PATH="$HOME/bin:$PATH"
git lfs install
```

* For the Sable apps only: an Android SDK (`ANDROID_HOME`, platform 36, build-tools
  36.0.0) and JDK 17 or 21.

Check everything:

```bash
bash build/sable.sh q25 Q2 doctor
```

Paths default to `~/sable-q25`. Put the tree elsewhere with
`export SABLE_BUILD_ROOT=/big/disk/sable-q25`.

## 2. Sync the source

```bash
NETWORK_FETCH_AUTHORIZED=YES bash build/sable.sh q25 Q2 bootstrap
```

This runs `repo init` on LineageOS `lineage-23.2`, installs
`manifests/local_manifests/sable_q25.xml` (device tree, kernel, MediaTek
hardware, sepolicy and TheMuppets blobs, all pinned), syncs, and writes the exact
revisions to `~/sable-q25/evidence/manifest-*.xml`.

It replaces `breakfast Q25`; don't run `breakfast`, because its
`roomservice.xml` would duplicate the pinned projects.

### Blobs from your own device (optional)

The local manifest pulls blobs from `TheMuppets/proprietary_vendor_xelex_Q25`,
which were extracted from the 2026-03-26 stock release. To use your own:

```bash
bash scripts/extract-blobs.sh --from-dump /path/to/extracted-stock
```

## 3. Q1: build the LineageOS control image first

Before adding anything Sable, prove the toolchain and device produce a bootable
image. This is the baseline every later problem is compared against.

```bash
bash build/sable.sh q25 Q1 stage        # makes sure no Sable layer is present
bash build/sable.sh q25 Q1 build        # lunch lineage_Q25-bp4a-userdebug; m bacon
bash build/sable.sh q25 Q1 artifacts
```

## 4. Q2: build SableOS

### 4a. Sable application sources

The app sources are already in `sable-src/`, imported from SableOS at a pinned
commit (see [`../apps/README.md`](../apps/README.md)). Build them:

```bash
bash build/sable.sh q25 Q2 apps          # every row with enabled=yes in product/q25/apps.tsv (all of them)
bash build/sable.sh q25 Q2 apps --all    # also rows set to enabled=no
```

APKs, a manifest and SHA256SUMS land in `~/sable-q25/apps-out/<stamp>/`
(`latest` points at the newest). `stage` packages whatever `latest` holds and does not
rebuild it, so run `apps` again after pulling new app sources, or the image ships the
old APKs. With the tree on another disk, set `SABLE_BUILD_ROOT` (and `ANDROID_HOME`)
in the same shell before every step, for example:

```bash
export SABLE_BUILD_ROOT=/srv/sable-build/workspaces/q25   # tree at $SABLE_BUILD_ROOT/android
export ANDROID_HOME=/srv/sable-build/toolchains/r8-a2/android-sdk
```

### 4b. Stage and build

```bash
bash build/sable.sh q25 Q2 stage
bash build/sable.sh q25 Q2 build
bash build/sable.sh q25 Q2 artifacts
```

`stage` copies `product/q25` to `vendor/sable/q25`, generates the app modules
from the APK manifest, and writes `vendor/extra/product.mk`. A props-only layer
with no apps is possible with `stage --allow-no-apps`.

`Q4` stages the same layer plus the framework integration: it copies Sable
Start and the Sable design sources into `vendor/sable/q25/src` and builds them
as `SableLauncher` (HOME, platform-signed), makes Sable Keyboard override
LatinIME, and applies `patches/framework` to the LineageOS projects (see
[`../patches/README.md`](../patches/README.md)). Staging Q1 or Q2 again reverts
those patches. Stage Q4 only after gate Q3-TEXT passes, because Sable Keyboard
is then the only keyboard on the phone.

```bash
bash scripts/apply-framework-patches.sh check   # after repo sync: do the patches still apply?
bash build/sable.sh q25 Q4 stage
bash build/sable.sh q25 Q4 build
```

### Optional: 50 MP rear camera

Off by default. `SABLE_Q25_CAMERA_50MP=YES` before `stage` (any release with the Sable layer) also
patches the kernel sensor driver and three vendor camera libraries and builds the remosaic library
from source. Untested on hardware; see
[`../product/q25/camera-50mp/README.md`](../product/q25/camera-50mp/README.md).

### microG and Aurora Store (Q4)

On by default (`SABLE_Q25_MICROG=YES`). Fetch the pinned, sha256-checked APKs before
`stage`: `NETWORK_FETCH_AUTHORIZED=YES bash build/sable.sh q25 Q4 microg` (or
`bash scripts/fetch-microg.sh --from DIR` with files downloaded elsewhere). `stage` fails if
they are missing; `SABLE_Q25_MICROG=NO` builds without them. They add about 117 MB to the
product partition. See [`MICROG.md`](MICROG.md).

## 5. Outputs

`$SABLE_BUILD_ROOT/artifacts/q25/<release>/<stamp>/` (default `~/sable-q25`):

```text
sableos-q25-<release>-YYYYMMDD-userdebug.zip   recovery-sideloadable package
boot.img  dtbo.img  vbmeta.img  vendor_boot.img
super_empty.img (if produced)
props/   build.prop files
pinned-manifest.xml  apps-manifest.tsv  BUILD_INFO.txt  SHA256SUMS.txt
```

Keep the whole directory for anything you flash.

### Publish a release

`release` packs the newest collected build into one zip with the images, the install
package, `SHA256SUMS.txt`, `FLASHING.md` ([`FLASHING_RELEASE.md`](FLASHING_RELEASE.md)) and
`RELEASE_NOTES.md`, and publishes it as a GitHub pre-release:

```bash
bash build/sable.sh q25 Q4 artifacts
bash build/sable.sh q25 Q4 release --tag alpha-20261029             # build the zip, print the gh command
bash build/sable.sh q25 Q4 release --tag alpha-20261029 --publish   # needs gh auth login
```

The notes start with `releases/<tag>.md`, or `releases/ALPHA_NOTES.md` when there is none,
then add the zip's SHA-256, the source commit and the commits since the previous tag. The tag
is created on the commit the build was made from, so that commit must be pushed. GitHub limits
a release file to 2 GiB; a bigger zip leaves the install package out and uploads it beside it.
Boot the image on a Q25 before publishing and add `--boot-tested`; without it the notes say
`Booted on a Q25 before upload: NO`.

## 6. Manual equivalent

What the scripts do, for reference:

```bash
cd ~/sable-q25/android
repo init -u https://github.com/LineageOS/android.git -b lineage-23.2 --git-lfs
cp <this repo>/manifests/local_manifests/sable_q25.xml .repo/local_manifests/
repo sync -c -j$(nproc) --no-tags
# Q2 only:
mkdir -p vendor/sable/q25 vendor/extra
cp <this repo>/product/q25/sable-q25.mk vendor/sable/q25/
cp <this repo>/product/q25/extra-product.mk vendor/extra/product.mk
# plus generated Android.bp / sable-q25-apps.mk / release.mk and APKs
source build/envsetup.sh
lunch lineage_Q25-bp4a-userdebug
m bacon
```

## 7. GitHub Actions

GitHub runs the same app build and the SableOS quality gates on pull requests
and on pushes to `main`. This is the set SableOS ran in GitHub Actions before
its own CI moved to a local machine (`build/local-ci/run.sh` in sableos). The
first four workflows were green for PR #9, the change that added them;
`SonarCloud` was added in PR #18.

* `Sable apps` (`.github/workflows/android.yml`): `scripts/build-apps.sh --all
  --root ...` for each Gradle project (platform apps, r8 apps, Reader, Mail, Text
  Reader), with unit tests, Android Lint, detekt, ktlint, Kover, and the Mail
  runtime-dependency check. Gradle verifies dependencies against the
  committed dependency-verification metadata. The r8 job also compiles Sable Start's
  presentation sources (`sablestart-presentation-check`); the `SableLauncher`
  module itself is a Soong module and only builds in the LineageOS tree. The
  APKs are uploaded as artifacts for inspection only. Image builds still use
  APKs built on the build host. Runs when `sable-src/`, `apps.tsv`,
  `build-apps.sh` or the workflow change.
* `Rust` (`rust.yml`): fmt, clippy, tests, `cargo audit`, `cargo deny` and line
  coverage for `sable-src/apps/r8/rust`. Runs when that directory changes.
* `Security` (`security.yml`): Gitleaks over the history and a MobSF source
  scan (findings recorded). Also runs weekly.
* `SonarCloud` (`sonar.yml`): runs `scripts/sonar-compile-commands.sh` (a
  `compile_commands.json` for the camera shim, with stub Android headers) and
  then the SonarSource scan action with `sonar-project.properties`, waiting on
  the quality gate. It runs on pull requests, on pushes to `main` and by hand;
  fork pull requests are skipped because they cannot read `SONAR_TOKEN`.
  Coverage is excluded until Kover/JaCoCo reports are imported, so the free
  plan's fixed "Sonar way" gate does not fail on 0% coverage. Project settings
  on sonarcloud.io: Automatic Analysis **off** (CI-based analysis refuses to run
  with it on), New Code = reference branch `main`, main branch named `main`.
  Taint findings that are false positives carry a `NOSONAR(rule)` comment that
  says why. The 25 `restrict` qualifiers in the shim (c:S1836) are kept on
  purpose and stay open.
* `checks` (`checks.yml`): `tests/run.sh` with shellcheck and xmllint. The
  framework pure tests (`tests/framework/run-pure-tests.sh`) are skipped there
  because no Kotlin compiler is installed.

The detekt/ktlint fixes these gates needed in Q25-edited app code are carried
as `patches/sable-src/0011-zinwa-q25-ci-quality-fixes.patch`, so a re-import
keeps them.

The LineageOS image itself is too large for hosted runners and stays on the
build host.

A change that only touches documentation (`docs/`, any Markdown file, `LICENSE`,
`NOTICE`) does not start `checks`, `Sable apps` or `Rust`; none of them reads
those files. `Security` still runs, so Gitleaks scans documentation changes too.
None of these checks is required on `main` yet; `SonarCloud` and the Gitleaks
job are the recommended first required checks, since they run on every pull request. If one is made a required status
check, a docs-only pull request will wait on it, because a workflow skipped by
its path filter never reports.

## Troubleshooting

* **`lunch` can't find the product:** check `device/xelex/Q25` synced and that
  `vendor/lineage/vars/aosp_target_release` exists (it gives `bp4a`). Override
  with `SABLE_Q25_TARGET_RELEASE`.
* **Kernel build fails on clang:** the tree expects
  `prebuilts/clang/kernel/linux-x86/clang-r416183b`, which the LineageOS manifest
  provides.
* **Duplicate project errors on sync:** remove `.repo/local_manifests/roomservice.xml`.
* **`GmsCore.apk: Contains compressed dex files and is privileged`:** the microG
  release APK keeps its dex compressed, and repacking it would break the signature
  LineageOS checks before allowing signature spoofing. `product/q25/microg/Android.bp`
  sets `skip_preprocessed_apk_checks: true` on GmsCore only (PR #31).
* **`sum of sizes of [...] is greater than BOARD_mediatek_dynamic_partitions_SIZE`:**
  the six dynamic partitions must fit in 4,827,643,904 bytes (half of `super` minus
  4 MiB, set by the device tree). The 2026-10-22 Q4 build with microG was 905,216
  bytes over; shipping Sable Calculator as an R8-shrunk release build instead of its
  ~30 MB debug APK fixed it (PR #31). The other r8 apps (Media, Hub, Weather,
  Calendar, Text Reader) still ship debug builds of about 30 MB each, so moving
  one of them to release is the next way to free space. Re-run `apps` after any
  change to `product/q25/apps.tsv`.
* **`SystemUI scene_container is enabled or unreadable`:** the Q4 build reads
  `com.android.systemui.scene_container` from the partition aconfig stores and
  from SystemUI's own `com_android_systemui_flags` declarations. It is a
  read-only flag, so it appears only in the declarations; on 2026-10-22 it read
  `DISABLED READ_ONLY`. Check it by hand with:

  ```bash
  find out/soong/.intermediates/frameworks/base/packages/SystemUI/aconfig/com_android_systemui_flags \
      -name '*.pb' -exec out/host/linux-x86/bin/aconfig dump-cache --dedup \
      --format='{fully_qualified_name}={state} {permission}' --cache {} \; | grep scene_container
  ```

  If it says `ENABLED`, the Sable lock screen (frameworks/base 0304-0307) would not
  show; don't flash that image.
* **`artifacts` says the last build is FAIL:** the post-build checks failed even if
  ninja finished. Fix the reported cause and run `build` again; an unchanged tree
  rebuilds quickly.
