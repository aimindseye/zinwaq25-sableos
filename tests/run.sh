#!/usr/bin/env bash
# Static checks for this repository. No network, no Android tree, no device.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
fails=0
pass() { printf 'PASS  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*"; fails=$((fails + 1)); }

mapfile -t shells < <(git ls-files '*.sh' 2>/dev/null || find . -name '*.sh' -not -path './sable-src/*')

# 1. Shell syntax and shellcheck.
for f in "${shells[@]}"; do
    bash -n "$f" || fail "bash -n $f"
done
if command -v shellcheck >/dev/null 2>&1; then
    if shellcheck -x -P SCRIPTDIR -P "$ROOT" "${shells[@]}"; then pass "shellcheck (${#shells[@]} files)"; else fail shellcheck; fi
else
    printf 'SKIP  shellcheck not installed\n'
fi

# 2. Local manifest is well-formed XML.
manifest_check="manifest XML"
if command -v xmllint >/dev/null 2>&1; then
    xmllint --noout manifests/local_manifests/sable_q25.xml && pass "$manifest_check" || fail "$manifest_check"
else
    python3 -c 'import sys,xml.dom.minidom as m; m.parse(sys.argv[1])' manifests/local_manifests/sable_q25.xml &&
        pass "$manifest_check" || fail "$manifest_check"
fi

# 3. Pins in q25.env match the local manifest.
# shellcheck source=build/config/q25.env
source build/config/q25.env
check_pin() {
    local path="$1" rev="$2"
    if grep -A4 "path=\"$path\"" manifests/local_manifests/sable_q25.xml | grep -q "revision=\"$rev\""; then
        pass "pin $path"
    else
        fail "pin $path: manifest does not carry $rev"
    fi
}
check_pin device/xelex/Q25 "$SABLE_Q25_DEVICE_TREE_REV"
check_pin kernel/xelex/mt6789 "$SABLE_Q25_KERNEL_REV"
check_pin hardware/mediatek "$SABLE_Q25_HARDWARE_MEDIATEK_REV"
check_pin device/mediatek/sepolicy_vndr "$SABLE_Q25_SEPOLICY_VNDR_REV"
check_pin vendor/xelex/Q25 "$SABLE_Q25_VENDOR_BLOBS_REV"

# 4. apps.tsv: 9 columns, unique module names, known enums.
if awk -F'\t' '
    /^#/ || NF == 0 { next }
    $1 == "module" { next }
    NF != 9 { print "bad column count line " NR; bad = 1 }
    $2 != "platform" && $2 != "common" { print "bad group line " NR; bad = 1 }
    $7 != "unsigned" && $7 != "presigned" && $7 != "platform" && $7 != "privileged" { print "bad signing line " NR; bad = 1 }
    $9 != "yes" && $9 != "no" { print "bad enabled line " NR; bad = 1 }
    seen[$1]++ { print "duplicate module " $1; bad = 1 }
    END { exit bad }
' product/q25/apps.tsv; then pass "apps.tsv"; else fail "apps.tsv"; fi

# 5. Nothing that must never be committed.
forbidden="$(git ls-files 2>/dev/null | grep -E '\.(img|zip|apk|jks|keystore|pk8|bin)$|(^|/)proprietary/' | grep -v -e '^\.github/' -e '^sable-src/third_party/pinyin_zh/inputs/' || true)"
if [[ -z "$forbidden" ]]; then pass "no images, archives, APKs or keys tracked"; else fail "forbidden files tracked: $forbidden"; fi

# 6. Stage dry run against a fake Android tree.
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/android/build" "$tmp/apps/run/prebuilt"
touch "$tmp/android/build/envsetup.sh"
printf 'fake' > "$tmp/apps/run/prebuilt/SableKeyboard.apk"
printf 'fake' > "$tmp/apps/run/prebuilt/SableCalculator.apk"
(cd "$tmp/apps/run/prebuilt" && sha256sum ./*.apk > ../SHA256SUMS.txt)
{
    printf 'module\tgroup\tpackage\tsigning\toverrides\tfilename\tsha256\n'
    printf 'SableKeyboard\tplatform\torg.sableos.titan2.keyboard\tunsigned\t-\tSableKeyboard.apk\tx\n'
    printf 'SableCalculator\tcommon\torg.sableos.calculator\tpresigned\tExactCalculator\tSableCalculator.apk\tx\n'
} > "$tmp/apps/run/manifest.tsv"

if SABLE_ANDROID_ROOT="$tmp/android" bash scripts/stage-product.sh Q2 --apps-dir "$tmp/apps/run" >/dev/null 2>"$tmp/err"; then
    v="$tmp/android/vendor/sable/q25"
    ok=yes
    grep -q 'name: "SableKeyboard"' "$v/Android.bp" || ok=no
    grep -q 'default_dev_cert: true' "$v/Android.bp" || ok=no
    grep -A4 'name: "SableCalculator"' "$v/Android.bp" | grep -q 'preprocessed: true' || ok=no
    grep -q 'overrides: \["ExactCalculator"\]' "$v/Android.bp" || ok=no
    grep -q 'SableCalculator' "$v/sable-q25-apps.mk" || ok=no
    grep -q 'ro.sable.release=Q2' "$v/release.mk" || ok=no
    grep -q 'vendor/sable/q25/sable-q25.mk' "$tmp/android/vendor/extra/product.mk" || ok=no
    [[ "$ok" == yes ]] && pass "stage Q2 dry run" || fail "stage Q2 dry run: generated files wrong"
else
    fail "stage Q2 dry run: $(cat "$tmp/err")"
fi

if SABLE_ANDROID_ROOT="$tmp/android" bash scripts/stage-product.sh Q1 >/dev/null 2>"$tmp/err" &&
    [[ ! -e "$tmp/android/vendor/extra/product.mk" && ! -e "$tmp/android/vendor/sable/q25" ]]; then
    pass "stage Q1 removes Sable layer"
else
    fail "stage Q1: $(cat "$tmp/err")"
fi

# 6b. Framework patches (phase Q4): apply, record, check, revert, and staging.
fw="$tmp/fw"; proj="$tmp/android/packages/apps/Demo"
mkdir -p "$proj" "$fw/packages/apps/Demo"
git -C "$proj" init -q
stock_home='home=launcher3'
printf '%s\n' "$stock_home" > "$proj/config.txt"
git -C "$proj" -c user.name=t -c user.email=t@t add config.txt
git -C "$proj" -c user.name=t -c user.email=t@t commit -qm base
printf 'home=sable\n' > "$proj/config.txt"
git -C "$proj" diff > "$fw/packages/apps/Demo/0001-demo.patch"
git -C "$proj" checkout -q -- config.txt
fwrun() { SABLE_ANDROID_ROOT="$tmp/android" SABLE_FRAMEWORK_PATCHES="$fw" bash scripts/apply-framework-patches.sh "$@" 2>"$tmp/err"; }
stamp="$tmp/android/.sable-q25-framework-patches"
if fwrun check | grep -q '^APPLIES  packages/apps/Demo 0001-demo.patch' &&
    fwrun apply | grep -q '^FRAMEWORK_PATCH_COUNT=1' &&
    grep -q 'home=sable' "$proj/config.txt" && grep -q 'packages/apps/Demo' "$stamp" &&
    fwrun apply >/dev/null && grep -q 'home=sable' "$proj/config.txt" &&
    fwrun check | grep -q '^APPLIED  packages/apps/Demo' &&
    fwrun revert | grep -q '^FRAMEWORK_PATCHES=REVERTED' &&
    grep -q "$stock_home" "$proj/config.txt" && [[ ! -e "$stamp" ]]; then
    pass "framework patches apply, re-apply, check and revert"
else
    fail "framework patches: $(cat "$tmp/err")"
fi
# A second patch in the same project may build on the first.
git -C "$proj" apply "$fw/packages/apps/Demo/0001-demo.patch" && git -C "$proj" add config.txt
printf 'home=sable\nrecents=quickstep\n' > "$proj/config.txt"
git -C "$proj" diff > "$fw/packages/apps/Demo/0002-demo.patch"
git -C "$proj" reset -q --hard
if fwrun check | grep -q '^APPLIES  packages/apps/Demo 0001-demo.patch 0002-demo.patch' &&
    fwrun apply | grep -q '^FRAMEWORK_PATCH_COUNT=2' && grep -q 'recents=quickstep' "$proj/config.txt" &&
    fwrun check | grep -q '^APPLIED  packages/apps/Demo' &&
    fwrun revert >/dev/null && grep -qx "$stock_home" "$proj/config.txt"; then
    pass "framework patches stack within a project"
else
    fail "stacked framework patches: $(cat "$tmp/err")"
fi
# A revert after `git pull` changed a patch file undoes what was applied.
if fwrun apply >/dev/null && printf 'stale\n' > "$fw/packages/apps/Demo/0002-demo.patch" &&
    fwrun revert >/dev/null && grep -qx "$stock_home" "$proj/config.txt" && [[ ! -e "$stamp.d" ]]; then
    pass "framework patches revert after the patch files change"
else
    fail "revert after patch change: $(cat "$tmp/err")"
fi
rm "$fw/packages/apps/Demo/0002-demo.patch"
printf 'home=vendor\n' > "$proj/config.txt"
if fwrun apply >/dev/null || [[ -e "$stamp" ]]; then fail "framework patch conflict not refused"; else pass "framework patch conflict refused"; fi
git -C "$proj" checkout -q -- config.txt

# microG (SABLE_Q25_MICROG, default YES at Q4): fake APKs pinned in a test prebuilts.tsv.
mg="$tmp/microg"; mkdir -p "$mg/cache"
printf 'module\tpackage\tversion\tsha256\turl\n' > "$mg/prebuilts.tsv"
for m in GmsCore FakeStore AuroraStore; do
    printf 'fake %s' "$m" > "$mg/cache/$m.apk"
    printf '%s\tpkg.%s\t1\t%s\thttps://example.invalid/%s.apk\n' "$m" "$m" "$(sha256sum "$mg/cache/$m.apk" | awk '{print $1}')" "$m" >> "$mg/prebuilts.tsv"
done
export SABLE_MICROG_CACHE="$mg/cache" SABLE_MICROG_PREBUILTS="$mg/prebuilts.tsv"
stagerun() { SABLE_ANDROID_ROOT="$tmp/android" SABLE_FRAMEWORK_PATCHES="$fw" bash scripts/stage-product.sh "$@" 2>"$tmp/err"; }
if out="$(stagerun Q4 --apps-dir "$tmp/apps/run")"; then
    v="$tmp/android/vendor/sable/q25"
    ok=yes
    grep -q '^FRAMEWORK_LAYER=YES' <<<"$out" || ok=no
    grep -q 'SableLauncher' "$v/sable-q25-framework.mk" || ok=no
    grep -q 'etc/sable/battery/zinwa-q25.conf' "$v/sable-q25-framework.mk" || ok=no
    [[ -f "$v/etc/battery/zinwa-q25.conf" ]] || ok=no
    [[ -f "$v/etc/init/sable-keyboard-backlight.rc" ]] || ok=no
    grep -q 'etc/init/sable-keyboard-backlight.rc' "$v/sable-q25-framework.mk" || ok=no
    [[ -f "$v/etc/init/sable-power.rc" ]] || ok=no
    grep -q "etc/init/sable-power.rc:[$](TARGET_COPY_OUT_PRODUCT)/etc/init/sable-power.rc" "$v/sable-q25-framework.mk" || ok=no
    grep -q 'ro.sable.keyboard_backlight=true' "$v/sable-q25-framework.mk" || ok=no
    grep -q 'name: "SableLauncher"' "$v/src/SableStart/Android.bp" || ok=no
    grep -q 'name: "sable_design_shared_srcs"' "$v/src/sabledesign/Android.bp" || ok=no
    grep -q 'overrides: \["LatinIME"\]' "$v/Android.bp" || ok=no
    grep -q 'home=sable' "$proj/config.txt" || ok=no
    for o in SableFrameworkOverlay SableSystemUIOverlay SableSetupWizardOverlay SableGlimpseOverlay SableGallery2Overlay SableSettingsProviderOverlay SableLedOverlay; do
        grep -q "$o" "$v/sable-q25-framework.mk" || ok=no
        grep -q "name: \"$o\"" "$v/overlay/Android.bp" || ok=no
    done
    [[ -f "$v/overlay/SableSystemUIOverlay/res/values/sable_quick_settings.xml" ]] || ok=no
    grep -q 'SableNoUpdater' "$v/sable-q25-framework.mk" || ok=no
    grep -q '^SABLE_Q25_NO_UPDATER := true' "$v/sable-q25-framework.mk" || ok=no
    grep -q 'overrides: \["Updater"\]' "$v/updater/Android.bp" || ok=no
    grep -qF "ifneq (\$(SABLE_Q25_NO_UPDATER),true)" "$v/sable-q25.mk" || ok=no
    [[ "$ok" == yes ]] && pass "stage Q4 adds framework layer" || fail "stage Q4: generated files wrong: $out"
else
    fail "stage Q4: $(cat "$tmp/err")"
fi
if out="$(stagerun Q2 --apps-dir "$tmp/apps/run")" && grep -q '^FRAMEWORK_LAYER=NO' <<<"$out" &&
    grep -q "$stock_home" "$proj/config.txt" && ! grep -q 'SableLauncher' "$tmp/android/vendor/sable/q25/sable-q25-framework.mk" &&
    [[ ! -e "$tmp/android/vendor/sable/q25/src" && ! -e "$tmp/android/vendor/sable/q25/overlay" ]] &&
    ! grep -q 'SableSystemUIOverlay' "$tmp/android/vendor/sable/q25/sable-q25-framework.mk" &&
    ! grep -q 'SABLE_Q25_NO_UPDATER' "$tmp/android/vendor/sable/q25/sable-q25-framework.mk" &&
    [[ ! -e "$tmp/android/vendor/sable/q25/updater" ]]; then
    pass "stage Q2 reverts framework layer"
else
    fail "stage Q2 after Q4: $(cat "$tmp/err")"
fi
if stagerun Q1 >/dev/null && grep -q "$stock_home" "$proj/config.txt"; then pass "stage Q1 after Q4"; else fail "stage Q1 after Q4: $(cat "$tmp/err")"; fi

# 6b1. Privileged apps (App display compatibility, Secure Vault): "platform" rows ship from Q4 only, with the
#      platform certificate; "privileged" rows are privileged from Q4; the allowlist matches the manifests.
pa="$tmp/apps/priv"
mkdir -p "$pa/prebuilt"
printf 'fake' > "$pa/prebuilt/SableDisplayCompat.apk"
printf 'fake' > "$pa/prebuilt/SableVault.apk"
(cd "$pa/prebuilt" && sha256sum ./*.apk > ../SHA256SUMS.txt)
{
    printf 'module\tgroup\tpackage\tsigning\toverrides\tfilename\tsha256\n'
    printf 'SableDisplayCompat\tplatform\torg.sableos.titan2.displaycompat\tplatform\t-\tSableDisplayCompat.apk\tx\n'
    printf 'SableVault\tplatform\torg.sableos.titan2.vault\tprivileged\t-\tSableVault.apk\tx\n'
} > "$pa/manifest.tsv"
v="$tmp/android/vendor/sable/q25"
if stagerun Q4 --apps-dir "$pa" >/dev/null; then
    ok=yes
    grep -A6 'name: "SableDisplayCompat"' "$v/Android.bp" | grep -q 'certificate: "platform"' || ok=no
    grep -A6 'name: "SableDisplayCompat"' "$v/Android.bp" | grep -q 'privileged: true' || ok=no
    grep -A6 'name: "SableVault"' "$v/Android.bp" | grep -q 'default_dev_cert: true' || ok=no
    grep -A6 'name: "SableVault"' "$v/Android.bp" | grep -q 'privileged: true' || ok=no
    grep -q 'persist.sable.displaycompat.backend=validated' "$v/sable-q25-framework.mk" || ok=no
    grep -q 'etc/permissions/privapp-permissions-sable-q25.xml' "$v/sable-q25-framework.mk" || ok=no
    [[ -f "$v/etc/permissions/privapp-permissions-sable-q25.xml" ]] || ok=no
    grep -q 'etc/sysconfig/preinstalled-packages-sable-vault.xml' "$v/sable-q25-apps.mk" || ok=no
    [[ -f "$v/overlay/SableFrameworkOverlay/res/values/sable_displaycompat.xml" ]] || ok=no
    [[ "$ok" == yes ]] && pass "stage Q4 privileged apps (platform cert, allowlist, display compat gate)" ||
        fail "stage Q4 privileged apps: generated files wrong"
else
    fail "stage Q4 privileged apps: $(cat "$tmp/err")"
fi
if stagerun Q2 --apps-dir "$pa" >/dev/null && ! grep -q 'SableDisplayCompat' "$v/Android.bp" &&
    grep -A6 'name: "SableVault"' "$v/Android.bp" | grep -q 'privileged: false' &&
    ! grep -q 'certificate: "platform"' "$v/Android.bp" && ! grep -q 'privapp' "$v/sable-q25-framework.mk" &&
    ! grep -q 'displaycompat.backend' "$v/sable-q25-framework.mk" && grep -q 'SKIPPED_BEFORE_Q4=SableDisplayCompat' "$tmp/err"; then
    pass "stage Q2 keeps every app unprivileged (system-UID display compat waits for Q4)"
else
    fail "stage Q2 privileged apps: $(cat "$tmp/err")"
fi
stagerun Q1 >/dev/null || fail "stage Q1 after privileged apps: $(cat "$tmp/err")"
# Each privileged app requests exactly the permissions the allowlist grants it.
privxml=product/q25/permissions/privapp-permissions-sable-q25.xml
for pair in displaycompat:org.sableos.titan2.displaycompat vault:org.sableos.titan2.vault; do
    mod="${pair%%:*}" pkg="${pair#*:}"
    want="$(grep -o 'android.permission.[A-Z_]*' "sable-src/apps/titan2/platform/$mod/src/main/AndroidManifest.xml" | grep -v '^$' | LC_ALL=C sort -u)"
    have="$(awk -v p="$pkg" '$0 ~ "package=\""p"\"" {on=1; next} /<\/privapp-permissions>/ {on=0} on' "$privxml" |
        grep -o 'android.permission.[A-Z_]*' | LC_ALL=C sort -u)"
    if [[ -n "$want" && "$want" == "$have" ]]; then pass "privapp allowlist matches $mod"; else fail "privapp allowlist $mod: manifest [$want] allowlist [$have]"; fi
done
dcm=sable-src/apps/titan2/platform/displaycompat/src/main/AndroidManifest.xml
if grep -q 'android:sharedUserId="android.uid.system"' "$dcm" &&
    grep -q 'org.sableos.titan2.displaycompat	platform	' product/q25/apps.tsv &&
    grep -q 'config_appCompatUserAppAspectRatioSettingsIsEnabled">true' product/common/overlay/SableFrameworkOverlay/res/values/sable_displaycompat.xml &&
    grep -q '"setUserMinAspectRatio",' sable-src/apps/titan2/platform/displaycompat/src/main/java/org/sableos/titan2/displaycompat/android/GatedPlatformBackend.kt &&
    grep -q 'Class.forName("android.content.pm.IPackageManager")' sable-src/apps/titan2/platform/displaycompat/src/main/java/org/sableos/titan2/displaycompat/android/GatedPlatformBackend.kt; then
    pass "display compat: system UID, platform cert, IPackageManager(pkg, userId, value), WM overlay"
else
    fail "display compat platform wiring incomplete"
fi
# Secure Vault: Private Space only, no network or storage access, no backup, secure window.
vm=sable-src/apps/titan2/platform/vault/src/main/AndroidManifest.xml
va=sable-src/apps/titan2/platform/vault/src/main/java/org/sableos/titan2/vault/android/VaultActivity.kt
if grep -q 'user-type="android.os.usertype.profile.PRIVATE"' product/q25/sysconfig/preinstalled-packages-sable-vault.xml &&
    ! grep -q 'install-in user-type="FULL"\|SYSTEM' product/q25/sysconfig/preinstalled-packages-sable-vault.xml &&
    ! grep -qE 'INTERNET|READ_EXTERNAL|READ_MEDIA|MANAGE_EXTERNAL|WRITE_EXTERNAL' "$vm" &&
    grep -q 'android:allowBackup="false"' "$vm" && grep -q 'android:exported="false"' "$vm" &&
    grep -q 'FLAG_SECURE' "$va" && grep -q 'setRecentsScreenshotEnabled(false)' "$va" &&
    ! grep -q 'ACTION_SEND' "$va"; then
    pass "Secure Vault: Private Space only, no network/storage/backup, secure window, no share sheet"
else
    fail "Secure Vault manifest or window policy"
fi
# 6b1b. microG and Aurora Store (SABLE_Q25_MICROG, docs/MICROG.md): on by default at Q4, off with NO,
# never before Q4, refused when an APK does not match its pin; the fetcher never keeps a bad file.
v="$tmp/android/vendor/sable/q25"
if out="$(stagerun Q4 --apps-dir "$tmp/apps/run")" && grep -q '^MICROG=YES' <<<"$out" &&
    grep -qx -- '-include vendor/sable/q25/microg/microg.mk' "$v/sable-q25-framework.mk" &&
    (for m in GmsCore FakeStore AuroraStore; do cmp -s "$mg/cache/$m.apk" "$v/microg/prebuilt/$m.apk" || exit 1; done) &&
    grep -q 'ro.sable.microg.spoofing=true' "$v/microg/microg.mk" &&
    [[ -f "$v/microg/etc/com.google.android.gms/settings.xml" && -f "$v/microg/Android.bp" ]]; then
    pass "stage Q4 adds microG and Aurora Store by default"
else
    fail "stage Q4 microG: $(cat "$tmp/err")"
fi
if out="$(SABLE_Q25_MICROG=NO stagerun Q4 --apps-dir "$tmp/apps/run")" && grep -q '^MICROG=NO' <<<"$out" &&
    [[ ! -e "$v/microg" ]] && ! grep -q microg "$v/sable-q25-framework.mk"; then
    pass "SABLE_Q25_MICROG=NO leaves microG out"
else
    fail "stage Q4 without microG: $(cat "$tmp/err")"
fi
if out="$(stagerun Q2 --apps-dir "$tmp/apps/run")" && grep -q '^MICROG=NO' <<<"$out" && [[ ! -e "$v/microg" ]]; then
    pass "microG is not staged before Q4"
else
    fail "stage Q2 microG: $(cat "$tmp/err")"
fi
cp "$mg/cache/AuroraStore.apk" "$mg/aurora.bak"; printf 'tampered' > "$mg/cache/AuroraStore.apk"
if stagerun Q4 --apps-dir "$tmp/apps/run" >/dev/null; then
    fail "stage Q4 accepted a microG APK that does not match its pin"
elif grep -q 'AuroraStore' "$tmp/err"; then
    pass "stage refuses a microG APK that does not match its pin"
else
    fail "microG pin check: $(cat "$tmp/err")"
fi
mkdir -p "$mg/from"; printf 'wrong' > "$mg/from/AuroraStore.apk"
if bash scripts/fetch-microg.sh --from "$mg/from" >/dev/null 2>&1 || [[ -e "$mg/cache/AuroraStore.apk" ]]; then
    fail "fetch-microg kept a file that does not match its pin"
else
    cp "$mg/aurora.bak" "$mg/from/AuroraStore.apk"
    if bash scripts/fetch-microg.sh --from "$mg/from" 2>"$tmp/err" | grep -q '^MICROG_PREBUILTS=PASS' &&
        bash scripts/fetch-microg.sh --check | grep -q '^MICROG_APK_COUNT=3'; then
        pass "fetch-microg takes only files that match their pins"
    else
        fail "fetch-microg --from: $(cat "$tmp/err")"
    fi
fi
stagerun Q1 >/dev/null || fail "stage Q1 after the microG checks: $(cat "$tmp/err")"
unset SABLE_MICROG_CACHE SABLE_MICROG_PREBUILTS

# 6b1c. microG product files (static): pins, battery and location defaults, permissions, spoofing gate.
mgp=product/q25/microg; mg_bad=""
awk -F'\t' '!/^#/ && NF && $1 != "module" { n++; if (NF != 5 || $4 !~ /^[0-9a-f]{64}$/ || $5 !~ /^https:\/\//) bad = 1 }
    END { exit (bad || n != 3) }' "$mgp/prebuilts.tsv" || mg_bad="$mg_bad pins"
for f in "$mgp"/etc/*/*.xml; do
    python3 -I -c 'import sys,xml.dom.minidom as m; m.parse(sys.argv[1])' "$f" 2>/dev/null || mg_bad="$mg_bad xml:$f"
done
for k in location_wifi_mls location_wifi_moving location_wifi_learning location_cell_mls location_cell_learning \
    location_ichnaea_contribute safetynet_enabled droidguard_enabled exposure_scanner_enabled; do
    grep -q "<boolean name=\"$k\" value=\"false\" />" "$mgp/etc/com.google.android.gms/settings.xml" || mg_bad="$mg_bad default:$k"
done
grep -q 'value="true"' "$mgp/etc/com.google.android.gms/settings.xml" && mg_bad="$mg_bad default-true"
grep -q 'ACCESS_BACKGROUND_LOCATION"' "$mgp/etc/default-permissions/default-permissions-sable-microg.xml" && mg_bad="$mg_bad background-location"
grep -q 'fixed="true"' "$mgp/etc/default-permissions/default-permissions-sable-microg.xml" && mg_bad="$mg_bad fixed-grant"
[[ "$(grep -o '<allow-[a-z-]*' "$mgp/etc/sysconfig/sable-microg.xml" | sort -u)" == '<allow-in-power-save' ]] || mg_bad="$mg_bad sysconfig-scope"
[[ "$(grep -o 'package="[^"]*"' "$mgp/etc/sysconfig/sable-microg.xml" | sort -u)" == 'package="com.google.android.gms"' ]] || mg_bad="$mg_bad sysconfig-package"
[[ "$(grep -o '<privapp-permissions package="[^"]*"' "$mgp/etc/permissions/privapp-permissions-sable-microg.xml")" == '<privapp-permissions package="com.google.android.gms"' ]] || mg_bad="$mg_bad privapp-scope"
grep -A12 'name: "GmsCore"' "$mgp/Android.bp" | grep -q 'privileged: true' || mg_bad="$mg_bad gms-priv"
grep -A16 'name: "GmsCore"' "$mgp/Android.bp" | grep -q 'skip_preprocessed_apk_checks: true' || mg_bad="$mg_bad gms-dex-check"
[[ "$(grep -c 'presigned: true' "$mgp/Android.bp")" == 3 ]] || mg_bad="$mg_bad presigned"
for m in FakeStore AuroraStore; do grep -A8 "name: \"$m\"" "$mgp/Android.bp" | grep -q 'privileged: false' || mg_bad="$mg_bad $m-priv"; done
for f in privapp-permissions-sable-microg.xml default-permissions-sable-microg.xml sysconfig-sable-microg.xml sable-microg-settings.xml; do
    grep -q "name: \"$f\"" "$mgp/Android.bp" || mg_bad="$mg_bad bp:$f"
done
sp=(patches/framework/frameworks/base/0550-*.patch)
grep -qF '+    private static final String SABLE_MICROG_SPOOFING_PROP = "ro.sable.microg.spoofing";' "${sp[0]}" || mg_bad="$mg_bad 0550:prop"
grep -qF '+        return isDebuggable() || SystemProperties.getBoolean(SABLE_MICROG_SPOOFING_PROP, false);' "${sp[0]}" || mg_bad="$mg_bad 0550:gate"
grep -E '^[-+]' "${sp[0]}" | grep -v '^+++\|^---' | grep -q 'MICROG_REAL_SIGNATURE\|MICROG_FAKE_SIGNATURE\|getPackageName' && mg_bad="$mg_bad 0550:touches-checks"
if [[ -z "$mg_bad" ]]; then pass "microG pins, battery and location defaults, permissions and spoofing gate"; else fail "microG product files:$mg_bad"; fi

# 6b1. Q4 device review fixes: Sable apps replace their LineageOS twins, Start hides AudioFX, Weather
# refreshes in the background, and display compatibility rows open on a tap.
q4_bad=""
awk -F'\t' '$1 == "SableCalendar" && $8 == "Etar"' product/q25/apps.tsv | grep -q . || q4_bad="$q4_bad etar"
awk -F'\t' '$1 == "SableMedia" && $8 == "Twelve"' product/q25/apps.tsv | grep -q . || q4_bad="$q4_bad twelve"
grep -q '"org.lineageos.audiofx"' sable-src/src/android/packages/apps/SableStart/src/com/sable/start/platform/LauncherAppsRepository.kt || q4_bad="$q4_bad audiofx"
wx=sable-src/apps/r8/android/weather/src/main
grep -A3 'name=".WeatherRefreshJob"' "$wx/AndroidManifest.xml" | grep -q 'BIND_JOB_SERVICE' || q4_bad="$q4_bad weather-job"
grep -q 'WeatherRefreshJob.ensureScheduled' "$wx/java/org/sableos/weather/WeatherSnapshotProvider.kt" || q4_bad="$q4_bad weather-provider"
grep -q '\.clickable { onClick() }' sable-src/apps/titan2/platform/displaycompat/src/main/java/org/sableos/titan2/displaycompat/ui/AppListScreen.kt || q4_bad="$q4_bad displaycompat-tap"
gm=sable-src/apps/r8/android/games/src/main/java/org/sableos/games
grep -q 'GameLayout.isSquare(width.value, height.value)' "$gm/GameFrame.kt" || q4_bad="$q4_bad games-square"
for g in SudokuScreen Game2048Screen MinesweeperScreen; do grep -q '^    GameFrame($' "$gm/$g.kt" || q4_bad="$q4_bad games-frame-$g"; done
grep -q 'ro.sable.keyboard.compact_strip=false' product/q25/sable-q25.mk || q4_bad="$q4_bad strip-prop"
grep -q 'COMPACT_STRIP_PROP = "ro.sable.keyboard.compact_strip"' sable-src/apps/titan2/platform/keyboard/src/main/java/org/sableos/titan2/keyboard/android/KeyboardPrefs.kt || q4_bad="$q4_bad strip-default"
grep -q 'ro.config.wallpaper=/product/media/wallpaper/sable_default_wallpaper.png' scripts/stage-product.sh || q4_bad="$q4_bad wallpaper-prop"
python3 -I -c 'import struct,sys; d=open(sys.argv[1],"rb").read(24); sys.exit(0 if d[:8]==b"\x89PNG\r\n\x1a\n" and struct.unpack(">II",d[16:24])==(720,720) else 1)' product/q25/wallpaper/sable_default_wallpaper.png || q4_bad="$q4_bad wallpaper-png"
grep -qF '+            SableActiveFrames.open(touchInteractionService)' patches/framework/packages/apps/Launcher3/0003-*.patch || q4_bad="$q4_bad recents-frames"
grep -q 'METHOD_SELECT = "setup_select_city"' sable-src/apps/r8/android/weather/src/main/java/org/sableos/weather/WeatherSetupAccess.kt || q4_bad="$q4_bad weather-setup-call"
grep -q 'android:authorities="org.sableos.weather.snapshot"' sable-src/apps/titan2/platform/setup/src/main/AndroidManifest.xml || q4_bad="$q4_bad setup-queries"
grep -q 'org.sableos.start.action.SHOW_ACTIVE_FRAMES' sable-src/src/android/packages/apps/SableStart/AndroidManifest.xml || q4_bad="$q4_bad start-recents-filter"
grep -q 'const val COLUMNS = 5' sable-src/src/android/packages/apps/SableStart/src/com/sable/start/model/Bb10Home.kt || q4_bad="$q4_bad start-5-columns"
for g in DENSITY GAMES KEYBOARD-STRIP WALLPAPER APPS WEATHER WEATHER-CITY DISPLAYCOMPAT RECENTS START-ICONS; do grep -q "^| Q4-REVIEW-$g |" docs/QUALIFICATION.md || q4_bad="$q4_bad gate:$g"; done
# Sable boot animation instead of LineageOS's: staged, wired through TARGET_BOOTANIMATION, a stored
# (uncompressed) zip whose desc.txt fills the 720x720 panel and names both parts.
grep -q 'TARGET_BOOTANIMATION := vendor/sable/q25/media/bootanimation.zip' scripts/stage-product.sh || q4_bad="$q4_bad bootanimation-wiring"
ba_zip="$(mktemp)"
python3 -I product/q25/bootanimation/pack-bootanimation.py product/q25/bootanimation/frames "$ba_zip" || q4_bad="$q4_bad bootanimation-pack"
python3 -I - "$ba_zip" <<'PY' || q4_bad="$q4_bad bootanimation-zip"
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
assert z.namelist()[0] == "desc.txt"
assert all(i.compress_type == zipfile.ZIP_STORED for i in z.infolist())
desc = z.read("desc.txt").decode().split("\n")
assert desc[0].split()[:2] == ["720", "720"]
for part in ("part0", "part1"):
    assert any(line.split()[3:4] == [part] for line in desc[1:] if line)
    assert any(n.startswith(part + "/") and n.endswith(".png") for n in z.namelist())
PY
rm -f "$ba_zip"
if [[ -z "$q4_bad" ]]; then pass "Q4 review fixes (overrides, AudioFX, weather, display compat, games, keyboard strip, wallpaper, boot animation)"; else fail "Q4 review fixes:$q4_bad"; fi

# 6b1a. One Hub: Start's Hub page has the Contacts tab and the unread bar; Sable Hub has no launcher entry.
hub_bad=""
python3 -I -c 'import re,sys; s=open(sys.argv[1]).read(); sys.exit(1 if any("category.LAUNCHER" in f for f in re.findall(r"<intent-filter.*?</intent-filter>", s, re.S)) else 0)' sable-src/apps/r8/android/hub/src/main/AndroidManifest.xml || hub_bad="$hub_bad hub-launcher"
grep -q 'HubAppCounts.rows' sable-src/apps/r8/android/hub/src/main/java/org/sableos/hub/HubSnapshotProvider.kt || hub_bad="$hub_bad no-app-counts"
grep -q 'org.sableos.start.action.SHOW_HUB' sable-src/src/android/packages/apps/SableStart/AndroidManifest.xml || hub_bad="$hub_bad no-show-hub"
grep -q 'Contacts("contacts")' sable-src/src/android/packages/apps/SableStart/src/com/sable/start/model/HubBar.kt || hub_bad="$hub_bad no-contacts-tab"
grep -q 'package="org.sableos.launcher"' product/q25/permissions/default-permissions-sable-q25.xml || hub_bad="$hub_bad no-start-contacts-grant"
grep -q 'fixed="true"' product/q25/permissions/default-permissions-sable-q25.xml && hub_bad="$hub_bad fixed-grant"
grep -q "^| Q4-HUB-ONE |" docs/QUALIFICATION.md || hub_bad="$hub_bad gate"
if [[ -z "$hub_bad" ]]; then pass "one Hub: Start's Hub page (bar, Contacts tab), Hub headless"; else fail "one Hub:$hub_bad"; fi

# 6b1b. Sable Messages is the default SMS app: shipped, set as the SMS role default, granted, and
# declares every component the SMS role requires.
msg_bad=""
grep -qP '^SableMessages\tcommon\tapps/r8/android\t:messages:assembleRelease\tmessages/build/outputs/apk/release/\*\.apk\torg\.sableos\.messages\tunsigned\tmessaging\tyes$' product/q25/apps.tsv || msg_bad="$msg_bad apps.tsv"
grep -q '<string name="config_defaultSms"[^>]*>org.sableos.messages<' product/common/overlay/SableFrameworkOverlay/res/values/sable_messages.xml || msg_bad="$msg_bad default-sms"
grep -A10 'package="org.sableos.messages"' product/q25/permissions/default-permissions-sable-q25.xml | grep -q 'android.permission.SEND_SMS' || msg_bad="$msg_bad grants"
msg_m=sable-src/apps/r8/android/messages/src/main/AndroidManifest.xml
for s in android.provider.Telephony.SMS_DELIVER android.provider.Telephony.WAP_PUSH_DELIVER android.intent.action.RESPOND_VIA_MESSAGE android.permission.BROADCAST_SMS android.permission.BROADCAST_WAP_PUSH android.permission.SEND_RESPOND_VIA_MESSAGE android.intent.action.SENDTO smsto; do
    grep -q "$s" "$msg_m" || msg_bad="$msg_bad manifest:$s"
done
if [[ -z "$msg_bad" ]]; then pass "Sable Messages is the default SMS app (role default, grants, SMS/MMS components)"; else fail "Sable Messages default SMS:$msg_bad"; fi

# 6b1c. Framework Hub links open Start's Hub page, Hub settings or Sable Messages, never the old Hub UI.
hl_bad=""
grep -q 'org.sableos.start.action.SHOW_HUB' patches/framework/frameworks/base/0301-*.patch || hl_bad="$hl_bad 0301:show-hub"
grep -q 'OPEN_NOTIFICATION' patches/framework/frameworks/base/0301-*.patch && hl_bad="$hl_bad 0301:old-hub"
grep -q 'SableHubRoute' patches/framework/frameworks/base/0311-*.patch || hl_bad="$hl_bad 0311"
grep -q 'org.sableos.hub.action.CONNECTED_APPS' patches/framework/packages/apps/Settings/1001-*.patch || hl_bad="$hl_bad 1001"
grep -q 'org.sableos.messages' patches/framework/packages/apps/Contacts/0754-*.patch || hl_bad="$hl_bad 0754:messages"
grep -q '"smsto"' patches/framework/packages/apps/Contacts/0754-*.patch || hl_bad="$hl_bad 0754:smsto"
grep -q "^| Q4-HUB-LINKS |" docs/QUALIFICATION.md || hl_bad="$hl_bad gate"
if [[ -z "$hl_bad" ]]; then pass "Framework Hub links open Start's Hub page, Hub settings or Sable Messages"; else fail "framework Hub links:$hl_bad"; fi

# 6b1d. Display tools: Tall canvas through forced size, task listener, boot restore, Alt+Shift+R (0313).
dcs=sable-src/apps/titan2/platform/displaycompat/src/main
dt_bad=""
grep -q '"setForcedDisplaySize"' "$dcs/java/org/sableos/titan2/displaycompat/android/WmDisplayPort.kt" || dt_bad="$dt_bad forced-size"
grep -q 'registerTaskStackListener' "$dcs/java/org/sableos/titan2/displaycompat/android/DisplayToolsService.kt" || dt_bad="$dt_bad task-listener"
grep -q 'android.intent.action.LOCKED_BOOT_COMPLETED' "$dcs/AndroidManifest.xml" || dt_bad="$dt_bad boot-restore"
grep -A3 'CanvasHotkeyReceiver' "$dcs/AndroidManifest.xml" | grep -q 'android:exported="false"' || dt_bad="$dt_bad receiver-exported"
grep -q 'SableDisplayKeys.isCanvasCycleChord' patches/framework/frameworks/base/0313-*.patch || dt_bad="$dt_bad 0313-chord"
grep -q 'org.sableos.titan2.displaycompat.action.CYCLE_CANVAS' patches/framework/frameworks/base/0313-*.patch || dt_bad="$dt_bad 0313-action"
for g in TALL-CANVAS HOTKEY ROTATION; do grep -q "^| Q4-DISPLAY-$g |" docs/QUALIFICATION.md || dt_bad="$dt_bad gate:$g"; done
if [[ -z "$dt_bad" ]]; then pass "display tools: Tall canvas via forced size, task listener, boot restore, Alt+Shift+R (0313)"; else fail "display tools:$dt_bad"; fi

# 6b2. Optional 50 MP camera: the patcher on a synthetic vendor tree, and off by default.
if python3 -I tests/test_camera_50mp.py >"$tmp/cam50" 2>&1; then
    pass "camera 50 MP patcher ($(grep -o 'Ran [0-9]* tests' "$tmp/cam50"))"
else
    fail "camera 50 MP patcher: $(tail -5 "$tmp/cam50")"
fi
if out="$(stagerun Q2 --apps-dir "$tmp/apps/run")" && grep -q '^CAMERA_50MP=NO' <<<"$out" &&
    [[ ! -e "$tmp/android/vendor/sable/q25/camera-50mp" ]]; then
    pass "stage leaves the 50 MP camera off by default"
else
    fail "stage 50 MP default: $(cat "$tmp/err")"
fi
if SABLE_Q25_CAMERA_50MP=YES stagerun Q2 --apps-dir "$tmp/apps/run" >/dev/null; then
    fail "stage with SABLE_Q25_CAMERA_50MP=YES did not refuse a tree without the Q25 kernel and blobs"
else
    pass "stage refuses the 50 MP camera without its kernel and blobs"
fi
stagerun Q1 >/dev/null || fail "stage Q1 after the 50 MP checks: $(cat "$tmp/err")"

# 6b3. Sable lock screen needs the legacy keyguard: the build fails when SystemUI's
# scene_container flag is enabled (or missing) in the built aconfig store.
(
    # shellcheck source=build/lib/common.sh
    source build/lib/common.sh
    printf 'com.android.systemui.foo=ENABLED\ncom.android.systemui.scene_container=DISABLED\n' |
        sable_check_scene_container_off 2>/dev/null || exit 1
    printf 'com.android.systemui.scene_container=ENABLED\n' |
        sable_check_scene_container_off 2>/dev/null && exit 1
    printf 'com.android.systemui.scene_container=true\n' |
        sable_check_scene_container_off 2>/dev/null && exit 1
    printf 'com.android.systemui.other=DISABLED\n' |
        sable_check_scene_container_off 2>/dev/null && exit 1
    grep -q 'sable_check_scene_container_off' scripts/build.sh || exit 1
    exit 0
) && pass "scene_container build check" || fail "scene_container build check"

# 6c. The real framework patches are well-formed git patches.
bad=""
while IFS= read -r -d '' p; do
    git apply --stat "$p" >/dev/null 2>&1 || bad="$bad $p"
done < <(find patches/framework -name '*.patch' -print0)
if [[ -z "$bad" ]]; then pass "framework patches parse"; else fail "malformed framework patches:$bad"; fi

# 6d. DESIGN-KF-B tokens, overlays, branding and density (static).
if python3 tests/check-sable-design.py >"$tmp/design" 2>&1; then
    pass "Sable design tokens, overlays and branding ($(grep -c '^PASS' "$tmp/design") checks)"
else
    fail "Sable design check: $(grep '^FAIL' "$tmp/design" | head -5)"
fi

# 6d2. R9 daily-driver presentation overlays, Glimpse patch and Sable Start roles (static).
if python3 tests/check-daily-driver.py >"$tmp/daily" 2>&1; then
    pass "daily-driver overlays and patch ($(grep -c '^PASS' "$tmp/daily") checks)"
else
    fail "daily-driver check: $(grep '^FAIL' "$tmp/daily" | head -5)"
fi

# 6d3. Phone/Contacts Sable look patches (Dialer 0707-0709, Contacts 0755-0756), static.
if python3 -I tests/check-phone-contacts-style.py >"$tmp/phonestyle" 2>&1; then
    pass "Phone/Contacts Sable look ($(grep -c '^PASS' "$tmp/phonestyle") checks)"
else
    fail "Phone/Contacts look check: $(grep '^FAIL' "$tmp/phonestyle" | head -5)"
fi

# 6e. Pure Settings classes from the framework patches, compiled on the host.
if command -v javac >/dev/null 2>&1; then
    st="$tmp/settings-policy"
    mkdir -p "$st"
    for p in patches/framework/packages/apps/Settings/010[123]-*.patch; do
        (cd "$st" && git apply --include='src/com/android/settings/sable/SableAppearancePolicy.java' \
            --include='src/com/android/settings/sable/SableBuildInfo.java' "$ROOT/$p")
    done
    if javac -d "$st/out" "$st"/src/com/android/settings/sable/*.java \
        tests/java/settings/com/android/settings/sable/SableSettingsPolicyTest.java tests/java/junit-shim/org/junit/*.java 2>"$tmp/err" &&
        java -cp "$st/out" org.junit.Run com.android.settings.sable.SableSettingsPolicyTest >"$tmp/out" 2>&1; then
        pass "Settings appearance policy and About summary ($(grep -o 'CHECKS=[0-9]*' "$tmp/out"))"
    else
        fail "Settings policy test: $(cat "$tmp/err" "$tmp/out" | grep -v JAVA_TOOL | head -5)"
    fi
else
    printf 'SKIP  javac not installed (Settings policy test)\n'
fi

# 6f. Sable Applications model (Settings patches 0601, 1101 and 12xx) host tests, and the Sable
# Tools Settings actions the Settings patches must export (settings-ui5).
if command -v javac >/dev/null 2>&1; then
    sa="$tmp/settings-apps"
    mkdir -p "$sa/out"
    for p in "$ROOT"/patches/framework/packages/apps/Settings/0601-*.patch \
        "$ROOT"/patches/framework/packages/apps/Settings/1101-*.patch \
        "$ROOT"/patches/framework/packages/apps/Settings/12[0-9][0-9]-*.patch; do
        (cd "$sa" && git apply --include='src/com/android/settings/applications/sable/model/*' \
            --include='tests/sable-apps/src/*' "$p")
    done
    mapfile -t sa_src < <(find "$sa/src" "$sa/tests" tests/java/junit-shim -name '*.java')
    mapfile -t sa_tests < <(cd "$sa/tests/sable-apps/src" && find . -name '*Test.java' | sed 's#^\./##; s#\.java$##; s#/#.#g' | sort)
    if javac -encoding UTF-8 -d "$sa/out" "${sa_src[@]}" 2>"$tmp/err" &&
        java -cp "$sa/out" org.junit.Run "${sa_tests[@]}" >"$tmp/out" 2>&1; then
        pass "Settings Applications model host tests ($(grep -o 'RAN=[0-9]*' "$tmp/out"))"
    else
        fail "Settings Applications model tests: $(cat "$tmp/err" "$tmp/out" | grep -v JAVA_TOOL | head -5)"
    fi
else
    printf 'SKIP  javac not installed (Settings Applications model tests)\n'
fi
# 6f2. Battery health model (Settings patches 0201 and 1301) host tests, with the shipped Q25
# profile checked under the same parser (ProfileFileCheckTest), and kernel 0201 reporting the
# gauge's learned values instead of the fixed MediaTek ones.
if command -v javac >/dev/null 2>&1; then
    sb="$tmp/settings-battery"
    mkdir -p "$sb/out"
    for p in "$ROOT"/patches/framework/packages/apps/Settings/0201-*.patch \
        "$ROOT"/patches/framework/packages/apps/Settings/1301-*.patch; do
        (cd "$sb" && git apply --include='src/com/android/settings/fuelgauge/sable/model/*' \
            --include='tests/sable-battery/src/*' "$p")
    done
    mapfile -t sb_src < <(find "$sb/src" "$sb/tests" tests/java/junit-shim -name '*.java')
    mapfile -t sb_tests < <(cd "$sb/tests/sable-battery/src" && find . -name '*Test.java' | sed 's#^\./##; s#\.java$##; s#/#.#g' | sort)
    if javac -encoding UTF-8 -d "$sb/out" "${sb_src[@]}" 2>"$tmp/err" &&
        java -Dsable.battery.profile="$ROOT/device-profile/battery/zinwa-q25.conf" -cp "$sb/out" \
            org.junit.Run "${sb_tests[@]}" >"$tmp/out" 2>&1; then
        pass "Settings battery health model host tests and Q25 profile ($(grep -o 'RAN=[0-9]*' "$tmp/out"))"
    else
        fail "Settings battery health model tests: $(cat "$tmp/err" "$tmp/out" | grep -v JAVA_TOOL | head -5)"
    fi
else
    printf 'SKIP  javac not installed (Settings battery health model tests)\n'
fi
bh_k=(patches/framework/kernel/xelex/mt6789/0201-*.patch)
bh_bad=""
[[ -f "${bh_k[0]}" ]] || bh_bad="$bh_bad file:kernel/0201"
if [[ -f "${bh_k[0]}" ]]; then
    grep -qF -- '-		val->intval = 1;' "${bh_k[0]}" || bh_bad="$bh_bad cycle-fixed-1"
    grep -qF '+			val->intval = gm->bat_cycle;' "${bh_k[0]}" || bh_bad="$bh_bad cycle-learned"
    grep -qF '+			ret = -ENODATA;' "${bh_k[0]}" || bh_bad="$bh_bad enodata"
    grep -qF '+	int af = gm->algo.active ? gm->algo.aging_factor : gm->aging_factor;' "${bh_k[0]}" || bh_bad="$bh_bad aging"
fi
grep -q '^health_cycle_count_supported=YES$' device-profile/battery/zinwa-q25.conf || bh_bad="$bh_bad profile:cycles"
grep -q 'Q4-BATTERY-HEALTH-Q25' docs/QUALIFICATION.md || bh_bad="$bh_bad qualification-row"
if [[ -z "$bh_bad" ]]; then pass "Q25 battery health: kernel 0201 learned values, profile, qualification row"; else fail "Q25 battery health:$bh_bad"; fi

# 6g. Keyboard light. The Q25 keyboard firmware ignores host writes to its
# backlight register, so Settings patch 0901 adds only a read-only info row
# (no switch, slider or property writes), gated on ro.sable.keyboard_backlight.
# The kernel 0901 parameters and the init rule stay harmless; their names must
# still agree.
kbd_set=$(ls patches/framework/packages/apps/Settings/0901-*.patch)
kbd_bad=""
grep -q '"ro.sable.keyboard_backlight"' "$kbd_set" || kbd_bad="$kbd_bad no-gate"
grep -q 'Your keyboard controls its own light. Press Sym+0 to turn it on or off. With the PaBaLF keyboard firmware, Sym+Shift+1 to 9 sets the level.' "$kbd_set" || kbd_bad="$kbd_bad no-summary"
grep -q 'keyboard light, backlight, kb light' "$kbd_set" || kbd_bad="$kbd_bad no-keywords"
grep -q '^+        android:selectable="false"' "$kbd_set" || kbd_bad="$kbd_bad selectable"
grep -q 'persist\.sys\.sable\.kbd_backlight' "$kbd_set" && kbd_bad="$kbd_bad writes-property"
grep -Eq '^\+.*(SwitchPreference|SliderPreference|TogglePreferenceController|SliderPreferenceController|SystemProperties\.set)' "$kbd_set" && kbd_bad="$kbd_bad interactive"
if [[ -z "$kbd_bad" ]]; then pass "Settings keyboard light is a read-only info row"; else fail "Settings keyboard light:$kbd_bad"; fi
kbd_rc=product/q25/init/sable-keyboard-backlight.rc
kbd_missing=""
for pair in kbd_backlight:persist.sys.sable.kbd_backlight kbd_backlight_level:persist.sys.sable.kbd_backlight_level; do
    param="${pair%%:*}"; prop="${pair#*:}"
    grep -q "^+module_param_cb($param, " patches/framework/kernel/xelex/mt6789/0901-*.patch || kbd_missing="$kbd_missing kernel:$param"
    grep -q "write /sys/module/bbqX0kbd/parameters/$param \${$prop}\$" "$kbd_rc" || kbd_missing="$kbd_missing rc:$param"
    grep -q "on property:$prop=\*" "$kbd_rc" || kbd_missing="$kbd_missing rc:$prop"
done
if [[ -z "$kbd_missing" ]]; then pass "keyboard backlight names agree (init, driver)"; else fail "keyboard backlight names:$kbd_missing"; fi

# 6g2. BlackBerry-style notification LED (Q4-LED-BB): the overlay's red
# default, and the three patches that make every alerting channel light it
# in that default color unless the user turned the channel's light off.
led_missing=""
led_ov=product/common/overlay/SableLedOverlay/res/values/config.xml
grep -q 'name="config_defaultNotificationColor">#ff0000ff<' "$led_ov" || led_missing="$led_missing overlay:color"
grep -q 'name="config_defaultNotificationLedOn">250<' "$led_ov" || led_missing="$led_missing overlay:on"
grep -q 'name="config_defaultNotificationLedOff">2750<' "$led_ov" || led_missing="$led_missing overlay:off"
led_fwb=(patches/framework/frameworks/base/0501-*.patch)
led_sdk=(patches/framework/lineage-sdk/0502-*.patch)
led_parts=(patches/framework/packages/apps/LineageParts/0503-*.patch)
for p in "${led_fwb[0]}" "${led_sdk[0]}" "${led_parts[0]}"; do [[ -f "$p" ]] || led_missing="$led_missing file:$p"; done
if [[ -f "${led_fwb[0]}" ]]; then
    grep -qF '+        Light light = (getChannel().shouldShowLights() || shouldShowDefaultLights())' "${led_fwb[0]}" || led_missing="$led_missing fwb:calculateLights"
    grep -qF '+                com.android.internal.R.bool.config_intrusiveNotificationLed)) {' "${led_fwb[0]}" || led_missing="$led_missing fwb:intrusive-led-gate"
    grep -qF '+        return (getChannel().getUserLockedFields() & NotificationChannel.USER_LOCKED_LIGHTS) == 0' "${led_fwb[0]}" || led_missing="$led_missing fwb:user-locked"
    grep -qF '+                && getImportance() >= IMPORTANCE_DEFAULT;' "${led_fwb[0]}" || led_missing="$led_missing fwb:importance"
fi
if [[ -f "${led_sdk[0]}" ]]; then
    grep -qF '+                    0, UserHandle.USER_CURRENT) != 0;' "${led_sdk[0]}" || led_missing="$led_missing sdk:color-auto-default"
fi
if [[ -f "${led_parts[0]}" ]]; then
    grep -qF '+            android:defaultValue="false" />' "${led_parts[0]}" || led_missing="$led_missing parts:xml-default"
    [[ "$(grep -cF 'NOTIFICATION_LIGHT_COLOR_AUTO, 0) == 1' "${led_parts[0]}")" == 2 ]] || led_missing="$led_missing parts:java-default"
fi
# Battery light: blinking amber while charging, solid green only at 100 % and kept on.
led_sdk2=(patches/framework/lineage-sdk/0504-*.patch)
led_parts2=(patches/framework/packages/apps/LineageParts/0504-*.patch)
if [[ -f "${led_sdk2[0]}" ]]; then
    grep -qF '+        return level >= FULL_LEVEL' "${led_sdk2[0]}" || led_missing="$led_missing sdk:full-at-100"
    grep -qF -- '-            if (isCharged || level >= 90) {' "${led_sdk2[0]}" || led_missing="$led_missing sdk:no-90"
    grep -qF '+                    0, UserHandle.USER_CURRENT) != 0;' "${led_sdk2[0]}" || led_missing="$led_missing sdk:full-stays-on"
    grep -qF '+    <integer name="config_batteryChargingLedOn">1000</integer>' "${led_sdk2[0]}" || led_missing="$led_missing sdk:charging-blink"
else led_missing="$led_missing file:lineage-sdk/0504"; fi
if [[ -f "${led_parts2[0]}" ]]; then
    grep -qF '+    private static final boolean DEFAULT_LIGHT_FULL_CHARGE_DISABLED_PREF = false;' "${led_parts2[0]}" || led_missing="$led_missing parts:full-default"
else led_missing="$led_missing file:LineageParts/0504"; fi
grep -q 'name="config_notificationsBatteryFullARGB">0xFF00FF00<' "$led_ov" || led_missing="$led_missing overlay:full-green"
if [[ -z "$led_missing" ]]; then pass "notification LED: red default, lit alerting channels, blinking charge light and green only at 100 % (frameworks/base 0501, lineage-sdk 0502/0504, LineageParts 0503/0504)"; else fail "notification LED:$led_missing"; fi

# 6g2b. Pure black by default (Q4-ALPHA-PURE-BLACK): the Lineage provider writes
# BERRY_BLACK_THEME=1 on first boot; SystemUI enables the black overlay from it.
pb=(patches/framework/lineage-sdk/0150-*.patch)
if [[ -f "${pb[0]}" ]] &&
    grep -qF '+    <bool name="def_berry_black_theme">true</bool>' "${pb[0]}" &&
    grep -qF '+            loadBooleanSetting(stmt, LineageSettings.Secure.BERRY_BLACK_THEME,' "${pb[0]}"; then
    pass "pure black dark theme is the first-boot default (lineage-sdk 0150)"
    pb2=(patches/framework/frameworks/base/0103-*.patch)
    if [[ -f "${pb2[0]}" ]] && grep -qF '+            reevaluateSystemTheme(isSableBlackThemeWanted() /* forceReload */);' "${pb2[0]}"; then
        pass "pure black overlay applied at boot and after setup (frameworks/base 0103)"
    else fail "pure black overlay: frameworks/base 0103 missing or changed"; fi
else fail "pure black default: lineage-sdk 0150 missing or changed"; fi

# 6g3. Battery drain guards (Q4-POWER-IDLE): no Doze location prefetch, and
# MediaTek's userdebug pr_debug switched back off from /product (after vendor).
pw_missing=""
grep -q 'name="config_autoPowerModePrefetchLocation">false<' product/common/overlay/SableFrameworkOverlay/res/values/sable_power.xml || pw_missing="$pw_missing overlay:prefetch"
grep -q '^on property:sys.boot_completed=1$' product/q25/init/sable-power.rc || pw_missing="$pw_missing rc:trigger"
grep -qF 'write /proc/dynamic_debug/control "file *mediatek* -p"' product/q25/init/sable-power.rc || pw_missing="$pw_missing rc:dyndbg"
grep -q 'Q4-POWER-IDLE' docs/QUALIFICATION.md || pw_missing="$pw_missing qualification"
kq=(patches/framework/kernel/xelex/mt6789/0202-*.patch)
if [[ -f "${kq[0]}" ]]; then
    grep -qF '+#define BBQX0KBD_KEYBOARD_LOG(fmt, args...)    pr_debug(' "${kq[0]}" || pw_missing="$pw_missing kernel:pr_debug"
    grep -q '^diff --git a/drivers/input/keyboard/bbqX0kbd/debug_levels.h' "${kq[0]}" || pw_missing="$pw_missing kernel:file"
else pw_missing="$pw_missing file:kernel/0202"; fi
if [[ -z "$pw_missing" ]]; then pass "battery drain guards: no Doze location prefetch, MediaTek and keyboard debug logging off"; else fail "battery drain guards:$pw_missing"; fi

tools_link=sable-src/apps/titan2/platform/tools/src/main/java/org/sableos/tools/core/SettingsLink.kt
missing=""
for action in org.sableos.settings.NETWORK_MANAGER org.sableos.settings.APP_SECURITY; do
    grep -q "\"$action\"" "$tools_link" || missing="$missing tools:$action"
    grep -q "<action android:name=\"$action\" />" patches/framework/packages/apps/Settings/060*.patch ||
        missing="$missing settings:$action"
done
if [[ -z "$missing" ]]; then pass "Sable Tools Settings links land on exported Settings actions"; else fail "Settings actions missing:$missing"; fi

# Mobile Manager (Settings patches 1101/1102): exported actions, the suspended-app details
# handler, the Focus receiver, and App lock never claimed as enforced.
mm=(patches/framework/packages/apps/Settings/1102-*.patch)
missing=""
for action in org.sableos.settings.MOBILE_MANAGER org.sableos.settings.FOCUS_MODE \
    android.intent.action.SHOW_SUSPENDED_APP_DETAILS android.app.action.AUTOMATIC_ZEN_RULE_STATUS_CHANGED; do
    grep -q "<action android:name=\"$action\" />" "${mm[@]}" || missing="$missing $action"
done
grep -q 'PLATFORM_ENFORCEMENT = Enforcement.NOT_ENFORCED' patches/framework/packages/apps/Settings/1101-*.patch ||
    missing="$missing app-lock-not-enforced"
if [[ -z "$missing" ]]; then pass "Mobile Manager actions, Focus receiver and App lock status"; else fail "Mobile Manager:$missing"; fi

# Display and App info (Settings patch 1001) open the action App display compatibility declares;
# Settings > Sable (1103) opens Sable Setup's launcher activity and nothing else.
sable_links=(patches/framework/packages/apps/Settings/1001-*.patch)
missing=""
action=org.sableos.titan2.displaycompat.action.APP_PROFILE
grep -q "\"$action\"" "${sable_links[@]}" || missing="$missing settings:$action"
grep -q "<action android:name=\"$action\" />" sable-src/apps/titan2/platform/displaycompat/src/main/AndroidManifest.xml ||
    missing="$missing app:$action"
setup_page=(patches/framework/packages/apps/Settings/1103-*.patch)
grep -q '+    public static final String SETUP_PACKAGE = "org.sableos.titan2.setup";' "${setup_page[@]}" ||
    missing="$missing settings:setup-package"
grep -q 'org.sableos.titan2.setup' product/q25/apps.tsv || missing="$missing apps:SableSetup"
grep -q '<category android:name="android.intent.category.LAUNCHER" />' \
    sable-src/apps/titan2/platform/setup/src/main/AndroidManifest.xml || missing="$missing app:setup-launcher"
grep -q '^+        android:key="sable_setup"' "${setup_page[@]}" &&
    grep -q '^-        android:key="sable_hub"' "${setup_page[@]}" || missing="$missing settings:setup-row-only"
if [[ -z "$missing" ]]; then pass "Settings Sable entries land on actions the Sable apps declare"; else fail "Sable entry actions missing:$missing"; fi

# 6h. Stock app keyboard model (Files, Photos, Browser patches 1001-1099): the pure sable/
# classes taken from the patches, compiled with their host tests (tests/java/stock-apps).
if command -v javac >/dev/null 2>&1; then
    sk="$tmp/stock-keys"
    mkdir -p "$sk/out"
    for app in DocumentsUI Glimpse Jelly FMRadio; do
        mkdir -p "$sk/$app"
        for p in patches/framework/packages/apps/"$app"/10[0-9][0-9]-*.patch; do
            (cd "$sk/$app" && git apply --include='*/sable/*.java' "$ROOT/$p")
        done
    done
    mapfile -t sk_src < <(find "$sk/DocumentsUI" "$sk/Glimpse" "$sk/Jelly" "$sk/FMRadio" tests/java/stock-apps tests/java/junit-shim -name '*.java')
    mapfile -t sk_tests < <(cd tests/java/stock-apps && find . -name '*Test.java' | sed 's#^\./[^/]*/##; s#\.java$##; s#/#.#g' | sort)
    sk_android="$(find "$sk/DocumentsUI" "$sk/Glimpse" "$sk/Jelly" "$sk/FMRadio" -name '*.java' -exec grep -l '^import android\.' {} + || true)"
    if [[ -n "$sk_android" ]]; then
        fail "stock app key models must be pure Java: $sk_android"
    elif javac -encoding UTF-8 -d "$sk/out" "${sk_src[@]}" 2>"$tmp/err" &&
        java -cp "$sk/out" org.junit.Run "${sk_tests[@]}" >"$tmp/out" 2>&1; then
        pass "stock app keyboard models (Files, Photos, Browser) and FM bridge protocol host tests ($(grep -o 'RAN=[0-9]*' "$tmp/out"))"
    else
        fail "stock app keyboard model tests: $(cat "$tmp/err" "$tmp/out" | grep -v JAVA_TOOL | head -5)"
    fi
else
    printf 'SKIP  javac not installed (stock app keyboard model tests)\n'
fi

# 6i. FM in Sable Media: the bridge protocol numbers agree on both sides (FMRadio patch 1061,
# Sable Media FmModel.kt), the bridge checks every caller, and FM Radio has no launcher entry.
fm_java=$(ls patches/framework/packages/apps/FMRadio/1061-*.patch)
fm_bridge=$(ls patches/framework/packages/apps/FMRadio/1062-*.patch)
fm_kt=sable-src/apps/r8/android/media/src/main/java/org/sableos/media/FmModel.kt
fm_bad=""
while read -r name value; do
    grep -Eq "^\+    public static final (int|String) $name = $value;" "$fm_java" || fm_bad="$fm_bad $name"
done < <(sed -n 's/^    const val \(MSG_[A-Z_]*\|KEY_[A-Z_]*\|ACTION_CONTROL\|LOWEST_STATION\|HIGHEST_STATION\) = \(.*\)$/\1 \2/p' "$fm_kt")
[[ "$(grep -c '^    const val \(MSG_\|KEY_\)' "$fm_kt")" -ge 25 ]] || fm_bad="$fm_bad few-constants"
grep -q 'callerAllowed(msg.sendingUid)' "$fm_bridge" || fm_bad="$fm_bad no-caller-check"
grep -q '^-                <category android:name="android.intent.category.LAUNCHER" />' "$fm_bridge" || fm_bad="$fm_bad launcher-entry"
grep -q '<action android:name="org.sableos.media.action.FM_RADIO" />' sable-src/apps/r8/android/media/src/main/AndroidManifest.xml || fm_bad="$fm_bad no-media-fm-action"
if [[ -z "$fm_bad" ]]; then pass "Sable Media FM and the FM Radio bridge agree"; else fail "FM bridge protocol:$fm_bad"; fi

# 7. Entry point refuses flashing and unknown devices.
# The complete image is called "full" (Q4 is its old name): it has the Sable and framework layers.
if (source build/lib/common.sh; sable_require_release full && sable_release_has_sable_layer full && sable_release_has_framework_layer full && ! sable_release_has_framework_layer Q2 && sable_release_has_framework_layer Q4) >/dev/null 2>&1 &&
    [[ "$(bash build/sable.sh q25 Q4 flash 2>&1 || true)" == *RELEASE=full* ]]; then
    pass "release name full (Q4 still accepted)"
else
    fail "release name full"
fi
[[ "$(bash build/sable.sh q25 doctor 2>&1 || true)" == *RELEASE=full* ]] && pass "release defaults to full" || fail "release default"
all_out="$(env -u NETWORK_FETCH_AUTHORIZED SABLE_BUILD_ROOT=/nonexistent/sable bash build/sable.sh q25 all 2>&1 || true)"
[[ "$all_out" == *"add --yes"* ]] && pass "all explains --yes" || fail "all without --yes"
tree_t="$(mktemp -d)"
mkdir -p "$tree_t/.repo" "$tree_t/build" "$tree_t/device/xelex/Q25" "$tree_t/kernel/xelex/mt6789" "$tree_t/hardware/mediatek" "$tree_t/device/mediatek/sepolicy_vndr" "$tree_t/vendor/xelex/Q25"
touch "$tree_t/build/envsetup.sh"
boot_out="$(env -u NETWORK_FETCH_AUTHORIZED SABLE_ANDROID_ROOT="$tree_t" bash build/sable.sh q25 bootstrap 2>&1 || true)"
[[ "$boot_out" == *"not downloading again"* ]] && pass "bootstrap skips a finished download" || fail "bootstrap re-downloads"
rm -rf "$tree_t"
if bash build/sable.sh q25 Q2 flash >/dev/null 2>&1; then fail "flash should be blocked"; else pass "flash blocked"; fi
if bash build/sable.sh titan2 Q2 doctor >/dev/null 2>&1; then fail "non-q25 device accepted"; else pass "only q25 accepted"; fi

# 8. Stock restore: plan only by default, never writes identity/user partitions.
img="$tmp/stock"; mkdir -p "$img"
for n in boot_a dtbo vendor_boot vbmeta vbmeta_system vbmeta_vendor super lk nvram nvdata proinfo userdata; do
    printf x > "$img/$n.img"
done
printf x > "$img/preloader_q20_v12_factory.bin"
(cd "$img" && sha256sum ./*.img ./*.bin > SHA256SUMS.txt)
if out="$(bash scripts/restore-stock.sh --images "$img" --scope full 2>/dev/null)"; then
    ok=yes
    grep -q '^RESTORE=PLAN_ONLY' <<<"$out" || ok=no
    grep -q 'flash vendor_boot_a ' <<<"$out" && grep -q 'flash vendor_boot_b ' <<<"$out" || ok=no
    grep -q 'slot=all' <<<"$out" && ok=no
    grep -q 'flash super ' <<<"$out" || ok=no
    grep -q 'flash lk_a ' <<<"$out" || ok=no
    grep -Eq 'flash [^ ]* ?(nvram|nvdata|proinfo|userdata|preloader) ' <<<"$out" && ok=no
    grep -q -- "fastboot -w" <<<"$out" && ok=no
    [[ "$ok" == yes ]] && pass "restore-stock plan" || fail "restore-stock plan wrong: $out"
else
    fail "restore-stock plan run failed"
fi
rm "$img/vendor_boot.img"
if bash scripts/restore-stock.sh --images "$img" >/dev/null 2>&1; then fail "restore-stock should need vendor_boot"; else pass "restore-stock requires boot set"; fi
if bash scripts/backup-device.sh --out "$PWD/backup-test" --mtk true >/dev/null 2>&1; then fail "backup inside repo accepted"; else pass "backup refuses repo path"; fi
rm -rf "$PWD/backup-test"

# 9. Restore plan for the real "With-GMS" stock package layout.
gms="$tmp/gms"
while IFS= read -r f; do mkdir -p "$gms/$(dirname "$f")"; printf x > "$gms/$f"; done < tests/fixtures/stock-gms-sp1a.files
if out="$(bash scripts/restore-stock.sh --images "$gms" --scope full 2>/dev/null)"; then
    ok=yes
    for p in boot dtbo vendor_boot vbmeta vbmeta_system vbmeta_vendor lk tee md1img; do
        grep -q "flash ${p}_a $gms/$p.img" <<<"$out" && grep -q "flash ${p}_b $gms/$p.img" <<<"$out" || ok=no
    done
    grep -q "flash super $gms/super.img" <<<"$out" || ok=no
    grep -q "flash logo $gms/logo.bin" <<<"$out" || ok=no
    grep -Eq 'debug|userdata|preloader|DA_BR' <<<"$out" && ok=no
    [[ "$ok" == yes ]] && pass "restore-stock plan for With-GMS layout" || fail "With-GMS plan wrong: $out"
else
    fail "restore-stock failed on With-GMS layout"
fi

# 10. Full-scope restore refuses a package built for the other Q25 board.
fakefb="$tmp/fastboot"
cat > "$fakefb" <<'FB'
#!/usr/bin/env bash
[[ "${1:-}" == -s ]] && shift 2
case "$*" in
    devices) echo "FAKE0001 fastboot" ;;
    "getvar product") echo "product: q20_v1_factory" >&2 ;;
    "getvar unlocked") echo "unlocked: yes" >&2 ;;
    *) echo "unexpected fastboot $*" >&2; exit 99 ;;
esac
FB
chmod +x "$fakefb"
unset ANDROID_SERIAL SABLE_Q25_SERIAL
if SABLE_FASTBOOT="$fakefb" bash scripts/restore-stock.sh --images "$gms" --execute --yes >/dev/null 2>&1; then
    fail "restore-stock flashed without naming the Q25's serial"
else
    pass "restore-stock --execute needs --serial"
fi
if SABLE_FASTBOOT="$fakefb" bash scripts/restore-stock.sh --images "$gms" --serial OTHER --execute --yes >/dev/null 2>&1; then
    fail "restore-stock accepted a serial that is not attached"
else
    pass "restore-stock refuses an unattached serial"
fi
if SABLE_FASTBOOT="$fakefb" bash scripts/restore-stock.sh --images "$gms" --scope full --serial FAKE0001 --execute --yes >/dev/null 2>"$tmp/err"; then
    fail "full restore accepted a q20_v12 package on a q20_v1 phone"
elif grep -q "needs a package for q20_v1_factory" "$tmp/err"; then
    pass "restore-stock refuses other-board package in full scope"
else
    fail "board check: $(cat "$tmp/err")"
fi

# 11. Crash evidence capture (#84): read-only adb use, hashed output, refuses repo paths.
fakeadb="$tmp/adb"; adblog="$tmp/adb.log"
cat > "$fakeadb" <<'ADB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_ADB_LOG"
[[ "${1:-}" == -s ]] && shift 2
case "${1:-}" in
    devices) printf 'List of devices attached\nFAKE0001\tdevice\n'; [[ -n "${FAKE_ADB_TWO:-}" ]] && printf 'FAKE0002\tdevice\n'; exit 0 ;;
    get-state) echo device ;;
    logcat) case " $* " in *" -c "*) exit 99 ;; esac; echo "E AndroidRuntime: FATAL EXCEPTION: main" ;;
    pull) printf 'tombstone\n' > "$3" ;;
    shell)
        case "$2" in
            "ls /data/tombstones 2>/dev/null") echo tombstone_00 ;;
            "ls /data/anr 2>/dev/null") exit 1 ;;
            "pm list packages") printf 'package:org.sableos.weather\npackage:com.android.phone\n' ;;
            "dumpsys package org.sableos.weather") printf 'Packages:\n  Package [org.sableos.weather] (abc):\n    versionCode=7 minSdk=30\n    versionName=0.1.0\n    lastUpdateTime=2026-10-09 10:00:00\n' ;;
            "dumpsys package "*) echo "Unable to find package" ;;
            getprop) printf '[ro.build.fingerprint]: [fake/q25/fp]\n[ro.sable.release]: [Q4]\n' ;;
            *) echo "ok: $2" ;;
        esac ;;
    *) echo "unexpected adb $*" >&2; exit 99 ;;
esac
ADB
chmod +x "$fakeadb"
evout="$tmp/evidence"
if out="$(FAKE_ADB_LOG="$adblog" SABLE_ADB="$fakeadb" bash scripts/capture-crash-evidence.sh --out "$evout" --serial FAKE0001 --label t 2>"$tmp/err")"; then
    d="$(sed -n 's/^OUT=//p' <<<"$out")"
    ok=yes
    grep -q '^EVIDENCE=CAPTURED' <<<"$out" || ok=no
    [[ "$d" == "$evout"/q25-crash-evidence-*-t ]] || ok=no
    (cd "$d" && sha256sum -c --quiet SHA256SUMS.txt) || ok=no
    grep -q 'FATAL EXCEPTION' "$d/logcat-crash.txt" || ok=no
    grep -q '^org.sableos.weather	yes	7	0.1.0' "$d/packages/sable-apps.tsv" || ok=no
    grep -q '^org.sableos.titan2.keyboard	no' "$d/packages/sable-apps.tsv" || ok=no
    [[ -f "$d/tombstones/tombstone_00" && -f "$d/dropbox/data_app_crash.txt" && -f "$d/exit-info/all.txt" ]] || ok=no
    grep -q '^FINGERPRINT=fake/q25/fp' "$d/SUMMARY.txt" || ok=no
    grep -Eq -- '(^| )(-c|-w|root|unroot|reboot|install|uninstall|clear|rm|wipe|disable|push)( |$)' "$adblog" && ok=no
    grep -q 'logcat -d -b crash' "$adblog" || ok=no
    [[ "$ok" == yes ]] && pass "crash evidence capture (fake adb)" || fail "crash evidence capture: $out"
else
    fail "crash evidence capture: $(cat "$tmp/err")"
fi
if FAKE_ADB_LOG="$adblog" SABLE_ADB="$fakeadb" bash scripts/capture-crash-evidence.sh --out "$PWD/evidence-test" >/dev/null 2>&1; then
    fail "crash evidence inside repo accepted"
else
    pass "crash evidence refuses repo path"
fi
rm -rf "$PWD/evidence-test"
if FAKE_ADB_LOG="$adblog" SABLE_ADB="$fakeadb" bash scripts/capture-crash-evidence.sh --out "$evout" >/dev/null 2>&1; then
    fail "crash evidence ran without naming the Q25's serial"
else
    pass "crash evidence needs --serial"
fi

# 12. Static icon/launch audit (#83): launcher entry, label, Sable icon, round icon, adaptive icon and its
#     monochrome layer are enforced for every enabled app (docs/implementation/t3.md, icon section).
enabled_apps="$(awk -F'\t' '!/^#/ && $1 != "module" && $9 == "yes"' product/q25/apps.tsv | grep -c .)"
if out="$(python3 -I scripts/audit-app-icons.py --enforce launcher,label,icon,round,adaptive,mono 2>&1)" &&
    grep -q '^AUDIT=PASS (enforced: launcher,label,icon,round,adaptive,mono)' <<<"$out" &&
    grep -q "^AUDIT_APPS=$enabled_apps\$" <<<"$out" && ! grep -q 'extra launcher entries' <<<"$out"; then
    pass "icon/launch audit ($enabled_apps apps, adaptive + monochrome + round enforced)"
else
    fail "icon/launch audit: $out"
fi

# 12b. The launcher icons are exactly what the Phosphor icon generator produces from the pinned glyphs.
if out="$(python3 -I sable-src/tools/gen_first_party_icons.py --check 2>&1)"; then
    pass "icon generator check ($(grep -o 'files=[0-9]*' <<<"$out"))"
else
    fail "icon generator drift: $(head -5 <<<"$out")"
fi

# 13. Pure unit tests of framework patch classes (SKIP unless SABLE_KOTLINC and SABLE_JUNIT are set).
if ! bash tests/framework/run-pure-tests.sh; then fail "framework pure tests"; fi

# 14. DESIGN-KF-A static gates for Sable Hub and the notification framework patches.
if bash tests/kf-a-notification-policy-check.sh; then pass "KF-A notification policy gates"; else fail "KF-A notification policy gates"; fi

# 15. Default still camera and the camera/calendar design patches (sable-src 0040-0041, Aperture 0801).
cam_bad=""
cam_manifest=sable-src/apps/titan2/platform/camera/src/main/AndroidManifest.xml
for action in STILL_IMAGE_CAMERA IMAGE_CAPTURE; do
    grep -q "<action android:name=\"android.media.action.$action\" />" "$cam_manifest" || cam_bad="$cam_bad camera:$action"
    grep -q "^-                <action android:name=\"android.media.action.$action\" />" \
        patches/framework/packages/apps/Aperture/0801-*.patch || cam_bad="$cam_bad aperture-keeps:$action"
done
grep -q '^+.*android.media.action.\(VIDEO\|STILL_IMAGE_CAMERA_SECURE\|IMAGE_CAPTURE_SECURE\)' \
    patches/framework/packages/apps/Aperture/0801-*.patch && cam_bad="$cam_bad aperture-adds"
grep -q '^-.*android.media.action.\(VIDEO\|STILL_IMAGE_CAMERA_SECURE\|IMAGE_CAPTURE_SECURE\)' \
    patches/framework/packages/apps/Aperture/0801-*.patch && cam_bad="$cam_bad aperture-drops-video-or-secure"
grep -q 'trackpadArrows = true' sable-src/apps/titan2/platform/camera/src/main/java/org/sableos/titan2/camera/core/DeviceProfile.kt ||
    cam_bad="$cam_bad no-trackpad-guard"
for p in patches/sable-src/0040-*.patch patches/sable-src/0041-*.patch; do
    [[ -s "$p" ]] || cam_bad="$cam_bad missing:$p"
done
if [[ -z "$cam_bad" ]]; then pass "Sable Camera is the default still camera; Aperture keeps video and secure"; else fail "default camera:$cam_bad"; fi
cal_keys=sable-src/apps/r8/android/calendar/src/main/java/org/sableos/calendar/CalendarWeek.kt
cal_bad=""
for k in C T V J K R; do grep -q "KeyEvent.KEYCODE_$k -> CalendarScreenKey" "$cal_keys" || cal_bad="$cal_bad key:$k"; done
grep -q 'override fun onKeyDown' sable-src/apps/r8/android/calendar/src/main/java/org/sableos/calendar/MainActivity.kt || cal_bad="$cal_bad no-activity-keys"
grep -q 'fun nowLine' "$cal_keys" || cal_bad="$cal_bad no-now-line"
if [[ -z "$cal_bad" ]]; then pass "Sable Calendar week view, NOW line and C/T/V/J/K/R keys"; else fail "calendar:$cal_bad"; fi

# 16. Sable Notes (sable-src 0081): local-first notes and checklists, no permissions, Menu-key editor.
notes=sable-src/apps/r8/android/notes
notes_bad=""
grep -qP '^SableNotes\tcommon\tapps/r8/android\t:notes:assembleRelease\tnotes/build/outputs/apk/release/\*\.apk\torg\.sableos\.notes\tunsigned\t-\tyes$' product/q25/apps.tsv || notes_bad="$notes_bad apps.tsv"
grep -q 'uses-permission' "$notes/src/main/AndroidManifest.xml" && notes_bad="$notes_bad permission"
grep -q 'android:allowBackup="false"' "$notes/src/main/AndroidManifest.xml" || notes_bad="$notes_bad backup"
grep -q 'KEYCODE_MENU -> EditorKey.Menu' "$notes/src/main/java/org/sableos/notes/core/NotesKeys.kt" || notes_bad="$notes_bad menu-key"
grep -q 'android.intent.action.SEND"' "$notes/src/main/AndroidManifest.xml" || notes_bad="$notes_bad share-in"
grep -q '"org.sableos.notes" to AppGlyph.NOTES' sable-src/src/android/packages/apps/SableStart/src/com/sable/start/model/AppGlyphs.kt || notes_bad="$notes_bad start-glyph"
grep -q ':notes:testDebugUnitTest' .github/workflows/android.yml || notes_bad="$notes_bad ci"
if [[ -z "$notes_bad" ]]; then pass "Sable Notes: shipped, no permissions, Menu-key editor, Start glyph, CI"; else fail "notes:$notes_bad"; fi

# 17. Clipboard history in Sable Keyboard (sable-src 0083): off by default, memory only, no sensitive clips.
kb=sable-src/apps/titan2/platform/keyboard/src/main/java/org/sableos/titan2/keyboard
clip_bad=""
grep -q 'prefs.getBool(CLIP_HISTORY, false)' "$kb/android/SableImeService.kt" || clip_bad="$clip_bad default-off"
grep -q 'isChecked = prefs.getBool(CLIP_HISTORY, false)' "$kb/android/KeyboardSettingsActivity.kt" || clip_bad="$clip_bad settings-default"
grep -q 'EXTRA_IS_SENSITIVE' "$kb/android/ClipboardWatcher.kt" || clip_bad="$clip_bad sensitive"
grep -q 'isKeyguardLocked' "$kb/android/SableImeService.kt" || clip_bad="$clip_bad keyguard"
grep -qi 'SharedPreferences\|openFileOutput\|filesDir' "$kb/core/ClipboardHistory.kt" "$kb/android/ClipboardWatcher.kt" && clip_bad="$clip_bad persisted"
grep -q 'uses-permission' sable-src/apps/titan2/platform/keyboard/src/main/AndroidManifest.xml && clip_bad="$clip_bad permission"
if [[ -z "$clip_bad" ]]; then pass "Sable Keyboard clipboard history: opt-in, memory only, skips sensitive copies, not on the lock screen"; else fail "clipboard history:$clip_bad"; fi

# 18. Sable Terminal (sable-src 0084): ConnectBot v1.10.9 flavor (no mosh), Menu = Ctrl, CI, Start glyph.
cb=sable-src/apps/r8/connectbot
term_bad=""
grep -qP '^SableTerminal\tcommon\tflavor:connectbot\t:app:assembleOssRelease\tapp/build/outputs/apk/oss/release/\*\.apk\torg\.sableos\.terminal\tunsigned\t-\tyes$' product/q25/apps.tsv || term_bad="$term_bad apps.tsv"
grep -qx 'CONNECTBOT_COMMIT=21ce6f47cf16785bf2314b4ea931900164c179c1' "$cb/upstream.env" || term_bad="$term_bad pin"
grep -q 'EXPECTED_COMMIT = "21ce6f47cf16785bf2314b4ea931900164c179c1"' "$cb/apply_sable_flavor.py" "$cb/verify_sable_terminal.py" || term_bad="$term_bad script-pin"
grep -q 'KEYCODE_MENU -> if (down && repeat == 0) SableTerminalKey.Ctrl' "$cb/src/main/kotlin/org/connectbot/sable/SableTerminalKeys.kt" || term_bad="$term_bad menu-ctrl"
[[ -f "$cb/res/mipmap-anydpi-v26/ic_sable_terminal.xml" ]] || term_bad="$term_bad icon"
grep -q '"org.sableos.terminal" to AppGlyph.TERMINAL' sable-src/src/android/packages/apps/SableStart/src/com/sable/start/model/AppGlyphs.kt || term_bad="$term_bad start-glyph"
grep -q 'root: flavor:connectbot' .github/workflows/android.yml || term_bad="$term_bad ci"
if [[ -z "$term_bad" ]]; then pass "Sable Terminal: pinned ConnectBot 1.10.9 flavor, Menu is Ctrl, Start glyph, CI"; else fail "terminal:$term_bad"; fi

# 19. Translatable Sable apps (sable-src 0085, docs/LANGUAGES.md): every app keeps its English in one
#     table, repeats it in values/strings.xml, binds the table to the resources and tests the two equal.
i18n_bad=""
while read -r table strings bind test; do
  [[ -f "$table" ]] || { i18n_bad="$i18n_bad missing:$table"; continue; }
  obj=$(basename "$table" .kt)
  [[ -f "$strings" ]] || i18n_bad="$i18n_bad strings:$strings"
  grep -q "$obj.t.bindTo(this)" "$bind" || i18n_bad="$i18n_bad bind:$obj"
  [[ -f "$test" ]] || i18n_bad="$i18n_bad test:$obj"
done <<EOF_I18N
sable-src/apps/r8/android/calculator/src/main/java/org/sableos/calculator/CalculatorText.kt sable-src/apps/r8/android/calculator/src/main/res/values/strings.xml sable-src/apps/r8/android/calculator/src/main/java/org/sableos/calculator/CalculatorApplication.kt sable-src/apps/r8/android/calculator/src/test/java/org/sableos/calculator/CalculatorTextTest.kt
sable-src/apps/r8/android/calendar/src/main/java/org/sableos/calendar/CalendarText.kt sable-src/apps/r8/android/calendar/src/main/res/values/strings.xml sable-src/apps/r8/android/calendar/src/main/java/org/sableos/calendar/CalendarApplication.kt sable-src/apps/r8/android/calendar/src/test/java/org/sableos/calendar/CalendarTextTest.kt
sable-src/apps/r8/android/convert/src/main/java/org/sableos/convert/ConvertText.kt sable-src/apps/r8/android/convert/src/main/res/values/strings.xml sable-src/apps/r8/android/calculator/src/main/java/org/sableos/calculator/CalculatorApplication.kt sable-src/apps/r8/android/calculator/src/test/java/org/sableos/calculator/ConvertTextTest.kt
sable-src/apps/r8/android/games/src/main/java/org/sableos/games/GamesText.kt sable-src/apps/r8/android/games/src/main/res/values/strings.xml sable-src/apps/r8/android/games/src/main/java/org/sableos/games/GamesApplication.kt sable-src/apps/r8/android/games/src/test/java/org/sableos/games/GamesTextTest.kt
sable-src/apps/r8/android/hub/src/main/java/org/sableos/hub/HubText.kt sable-src/apps/r8/android/hub/src/main/res/values/strings.xml sable-src/apps/r8/android/hub/src/main/java/org/sableos/hub/HubApplication.kt sable-src/apps/r8/android/hub/src/test/java/org/sableos/hub/HubTextTest.kt
sable-src/apps/r8/android/media/src/main/java/org/sableos/media/MediaText.kt sable-src/apps/r8/android/media/src/main/res/values/strings.xml sable-src/apps/r8/android/media/src/main/java/org/sableos/media/MediaApplication.kt sable-src/apps/r8/android/media/src/test/java/org/sableos/media/MediaTextTest.kt
sable-src/apps/r8/android/messages/src/main/java/org/sableos/messages/core/MessagesText.kt sable-src/apps/r8/android/messages/src/main/res/values/strings.xml sable-src/apps/r8/android/messages/src/main/java/org/sableos/messages/MessagesApplication.kt sable-src/apps/r8/android/messages/src/test/java/org/sableos/messages/MessagesTextTest.kt
sable-src/apps/r8/android/notes/src/main/java/org/sableos/notes/core/NotesText.kt sable-src/apps/r8/android/notes/src/main/res/values/strings.xml sable-src/apps/r8/android/notes/src/main/java/org/sableos/notes/NotesApplication.kt sable-src/apps/r8/android/notes/src/test/java/org/sableos/notes/NotesTextTest.kt
sable-src/apps/r8/android/weather/src/main/java/org/sableos/weather/WeatherText.kt sable-src/apps/r8/android/weather/src/main/res/values/strings.xml sable-src/apps/r8/android/weather/src/main/java/org/sableos/weather/WeatherApplication.kt sable-src/apps/r8/android/weather/src/test/java/org/sableos/weather/WeatherTextTest.kt
sable-src/apps/titan2/platform/camera/src/main/java/org/sableos/titan2/camera/core/CameraText.kt sable-src/apps/titan2/platform/camera/src/main/res/values/strings.xml sable-src/apps/titan2/platform/camera/src/main/java/org/sableos/titan2/camera/CameraApplication.kt sable-src/apps/titan2/platform/camera/src/test/java/org/sableos/titan2/camera/CameraTextTest.kt
sable-src/apps/titan2/platform/displaycompat/src/main/java/org/sableos/titan2/displaycompat/core/DisplayCompatText.kt sable-src/apps/titan2/platform/displaycompat/src/main/res/values/strings.xml sable-src/apps/titan2/platform/displaycompat/src/main/java/org/sableos/titan2/displaycompat/android/DisplayCompatApp.kt sable-src/apps/titan2/platform/displaycompat/src/test/java/org/sableos/titan2/displaycompat/DisplayCompatTextTest.kt
sable-src/apps/titan2/platform/keyboard/src/main/java/org/sableos/titan2/keyboard/core/KeyboardText.kt sable-src/apps/titan2/platform/keyboard/src/main/res/values/strings.xml sable-src/apps/titan2/platform/keyboard/src/main/java/org/sableos/titan2/keyboard/KeyboardApplication.kt sable-src/apps/titan2/platform/keyboard/src/test/java/org/sableos/titan2/keyboard/KeyboardTextTest.kt
sable-src/apps/titan2/platform/profiles/src/main/java/org/sableos/titan2/profiles/core/ProfilesText.kt sable-src/apps/titan2/platform/profiles/src/main/res/values/strings.xml sable-src/apps/titan2/platform/profiles/src/main/java/org/sableos/titan2/profiles/ProfilesApplication.kt sable-src/apps/titan2/platform/profiles/src/test/java/org/sableos/titan2/profiles/ProfilesTextTest.kt
sable-src/apps/titan2/platform/setup/src/main/java/org/sableos/titan2/setup/core/SetupText.kt sable-src/apps/titan2/platform/setup/src/main/res/values/strings.xml sable-src/apps/titan2/platform/setup/src/main/java/org/sableos/titan2/setup/android/SetupActivity.kt sable-src/apps/titan2/platform/setup/src/test/java/org/sableos/titan2/setup/SetupTextTest.kt
sable-src/apps/titan2/platform/tools/src/main/java/org/sableos/tools/core/ToolsText.kt sable-src/apps/titan2/platform/tools/src/main/res/values/strings.xml sable-src/apps/titan2/platform/tools/src/main/java/org/sableos/tools/ToolsApplication.kt sable-src/apps/titan2/platform/tools/src/test/java/org/sableos/tools/ToolsTextTest.kt
sable-src/apps/titan2/platform/vault/src/main/java/org/sableos/titan2/vault/core/VaultLines.kt sable-src/apps/titan2/platform/vault/src/main/res/values/strings.xml sable-src/apps/titan2/platform/vault/src/main/java/org/sableos/titan2/vault/VaultApplication.kt sable-src/apps/titan2/platform/vault/src/test/java/org/sableos/titan2/vault/VaultLinesTest.kt
sable-src/src/android/packages/apps/SableStart/src/com/sable/start/model/StartText.kt sable-src/src/android/packages/apps/SableStart/res/values/strings.xml sable-src/src/android/packages/apps/SableStart/src/com/sable/start/SableStartActivity.kt sable-src/apps/r8/android/sablestart-presentation-check/src/test/java/org/sableos/start/model/StartTextTest.kt
sable-src/src/android/shared/sabledesign/src/main/java/org/sableos/design/DesignText.kt sable-src/src/android/shared/sabledesign/src/main/res/values/strings.xml sable-src/src/android/packages/apps/SableStart/src/com/sable/start/SableStartActivity.kt sable-src/src/android/shared/sabledesign/src/test/java/org/sableos/design/DesignTextTest.kt
EOF_I18N
if [[ -z "$i18n_bad" ]]; then pass "Sable apps: English in one table per app, equal to values/strings.xml, bound to the resources"; else fail "translations:$i18n_bad"; fi

echo
if ((fails)); then echo "CI=FAIL ($fails)"; exit 1; fi
echo "CI=PASS"
