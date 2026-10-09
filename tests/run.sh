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
if command -v xmllint >/dev/null 2>&1; then
    xmllint --noout manifests/local_manifests/sable_q25.xml && pass "manifest XML" || fail "manifest XML"
else
    python3 -c 'import sys,xml.dom.minidom as m; m.parse(sys.argv[1])' manifests/local_manifests/sable_q25.xml &&
        pass "manifest XML" || fail "manifest XML"
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
    $7 != "unsigned" && $7 != "presigned" { print "bad signing line " NR; bad = 1 }
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
printf 'home=launcher3\n' > "$proj/config.txt"
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
    grep -q 'home=launcher3' "$proj/config.txt" && [[ ! -e "$stamp" ]]; then
    pass "framework patches apply, re-apply, check and revert"
else
    fail "framework patches: $(cat "$tmp/err")"
fi
printf 'home=vendor\n' > "$proj/config.txt"
if fwrun apply >/dev/null || [[ -e "$stamp" ]]; then fail "framework patch conflict not refused"; else pass "framework patch conflict refused"; fi
git -C "$proj" checkout -q -- config.txt

stagerun() { SABLE_ANDROID_ROOT="$tmp/android" SABLE_FRAMEWORK_PATCHES="$fw" bash scripts/stage-product.sh "$@" 2>"$tmp/err"; }
if out="$(stagerun Q4 --apps-dir "$tmp/apps/run")"; then
    v="$tmp/android/vendor/sable/q25"
    ok=yes
    grep -q '^FRAMEWORK_LAYER=YES' <<<"$out" || ok=no
    grep -q 'SableLauncher' "$v/sable-q25-framework.mk" || ok=no
    grep -q 'name: "SableLauncher"' "$v/src/SableStart/Android.bp" || ok=no
    grep -q 'name: "sable_design_shared_srcs"' "$v/src/sabledesign/Android.bp" || ok=no
    grep -q 'overrides: \["LatinIME"\]' "$v/Android.bp" || ok=no
    grep -q 'home=sable' "$proj/config.txt" || ok=no
    [[ "$ok" == yes ]] && pass "stage Q4 adds framework layer" || fail "stage Q4: generated files wrong: $out"
else
    fail "stage Q4: $(cat "$tmp/err")"
fi
if out="$(stagerun Q2 --apps-dir "$tmp/apps/run")" && grep -q '^FRAMEWORK_LAYER=NO' <<<"$out" &&
    grep -q 'home=launcher3' "$proj/config.txt" && ! grep -q 'SableLauncher' "$tmp/android/vendor/sable/q25/sable-q25-framework.mk" &&
    [[ ! -e "$tmp/android/vendor/sable/q25/src" ]]; then
    pass "stage Q2 reverts framework layer"
else
    fail "stage Q2 after Q4: $(cat "$tmp/err")"
fi
if stagerun Q1 >/dev/null && grep -q 'home=launcher3' "$proj/config.txt"; then pass "stage Q1 after Q4"; else fail "stage Q1 after Q4: $(cat "$tmp/err")"; fi

# 6c. The real framework patches are well-formed git patches.
bad=""
while IFS= read -r -d '' p; do
    git apply --stat "$p" >/dev/null 2>&1 || bad="$bad $p"
done < <(find patches/framework -name '*.patch' -print0)
if [[ -z "$bad" ]]; then pass "framework patches parse"; else fail "malformed framework patches:$bad"; fi

# 7. Entry point refuses flashing and unknown devices.
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
    grep -q 'flash --slot=all vendor_boot ' <<<"$out" || ok=no
    grep -q 'flash super ' <<<"$out" || ok=no
    grep -q 'flash --slot=all lk ' <<<"$out" || ok=no
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
        grep -q "flash --slot=all $p $gms/$p.img" <<<"$out" || ok=no
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
case "$*" in
    devices) echo "FAKE0001 fastboot" ;;
    "getvar product") echo "product: q20_v1_factory" >&2 ;;
    "getvar unlocked") echo "unlocked: yes" >&2 ;;
    *) echo "unexpected fastboot $*" >&2; exit 99 ;;
esac
FB
chmod +x "$fakefb"
if SABLE_FASTBOOT="$fakefb" bash scripts/restore-stock.sh --images "$gms" --scope full --execute --yes >/dev/null 2>"$tmp/err"; then
    fail "full restore accepted a q20_v12 package on a q20_v1 phone"
elif grep -q "needs a package for q20_v1_factory" "$tmp/err"; then
    pass "restore-stock refuses other-board package in full scope"
else
    fail "board check: $(cat "$tmp/err")"
fi

echo
if ((fails)); then echo "CI=FAIL ($fails)"; exit 1; fi
echo "CI=PASS"
