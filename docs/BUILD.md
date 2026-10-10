# Build SableOS for the Zinwa Q25

Status: **the Sable apps build and pass their quality gates in GitHub Actions
(section 7). A LineageOS image build (Q1, Q2 or Q4) has not been run yet.**
Report what you see; the first successful image build closes gate Q1-BUILD.

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
(`latest` points at the newest).

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

## 5. Outputs

`~/sable-q25/artifacts/q25/<release>/<stamp>/`:

```text
sableos-q25-Q2-YYYYMMDD-userdebug.zip   recovery-sideloadable package
boot.img  dtbo.img  vbmeta.img  vendor_boot.img
super_empty.img (if produced)
props/   build.prop files
pinned-manifest.xml  apps-manifest.tsv  BUILD_INFO.txt  SHA256SUMS.txt
```

Keep the whole directory for anything you flash.

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
its own CI moved to a local machine (`build/local-ci/run.sh` in sableos). All
four workflows are green for PR #9, the change that added them.

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
None of these checks is required on `main`. If one is made a required status
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
