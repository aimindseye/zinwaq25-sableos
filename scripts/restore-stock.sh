#!/usr/bin/env bash
# Restore Zinwa Q25 stock firmware over fastboot from an extracted stock "OS"
# archive. Prints the plan unless --execute is given.
#
# Scopes:
#   sable  (default) everything a SableOS/LineageOS install writes: boot, dtbo,
#          vendor_boot, vbmeta, vbmeta_system, vbmeta_vendor (both slots) and
#          super or its logical partitions.
#   full   every image in the archive that maps to a known partition, both
#          slots, except partitions that hold device identity or user data.
#
# Never written by this script: userdata, metadata, nvram, nvdata, nvcfg,
# proinfo, persist, protect1/2, frp, seccfg, otp, misc, para, expdb.
# preloader is skipped unless --include-preloader is given.
#
# Untested on hardware until gate R0 in docs/QUALIFICATION.md passes.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

usage() {
    cat >&2 <<'EOF'
usage: scripts/restore-stock.sh --images DIR [--scope sable|full] [--serial SERIAL]
                                [--wipe-data] [--include-preloader] [--execute [--yes]]

  --images DIR          extracted stock OS archive (not an OTA)
  --scope               sable (default) or full
  --wipe-data           also erase userdata and metadata (needed when coming
                        back from SableOS: stock can't read SableOS's encrypted data)
  --include-preloader   full scope only: also flash preloader (risky; only with
                        the exact matching stock archive)
  --execute             actually flash; without it the plan is only printed
  --yes                 skip the typed confirmation (for rehearsals you script)
EOF
}

IMAGES=""
SCOPE=sable
SERIAL=""
WIPE=NO
PRELOADER=NO
EXECUTE=NO
YES=NO
while (($#)); do
    case "$1" in
        --images) IMAGES="${2:-}"; shift 2 ;;
        --scope) SCOPE="${2:-}"; shift 2 ;;
        --serial) SERIAL="${2:-}"; shift 2 ;;
        --wipe-data) WIPE=YES; shift ;;
        --include-preloader) PRELOADER=YES; shift ;;
        --execute) EXECUTE=YES; shift ;;
        --yes) YES=YES; shift ;;
        -h|--help) usage; exit 0 ;;
        *) usage; exit 64 ;;
    esac
done

[[ -d "$IMAGES" ]] || { usage; exit 64; }
case "$SCOPE" in sable|full) ;; *) sable_fail "scope must be sable or full" ;; esac
[[ "$PRELOADER" == NO || "$SCOPE" == full ]] || sable_fail "--include-preloader needs --scope full"

NEVER=" userdata metadata nvram nvdata nvcfg proinfo persist protect1 protect2 frp seccfg otp misc para expdb "
BOOT_SET=(boot dtbo vendor_boot vbmeta vbmeta_system vbmeta_vendor)
LOGICAL=(system system_ext product vendor vendor_dlkm odm_dlkm)
# Physical firmware partitions seen in the Q25 fstab (device/xelex/Q25/rootdir/etc/fstab.mt6789).
FIRMWARE=(lk tee scp sspm dpm mcupm md1img md1dsp md1arm7 md3img gz ccu vcp gpueb mcf_ota
          mvpu_algo apusys spmfw pi_img boot_para odmdtbo logo)

# Map image files to partition names: "<name>.img"/"<name>.bin", optional _a/_b
# suffix; "preloader_*.bin" -> preloader.
declare -A image_for=()
while IFS= read -r -d '' f; do
    base="$(basename "$f")"
    name="${base%.*}"
    case "$name" in
        preloader*) name=preloader ;;
        *_a|*_b) name="${name%_?}" ;;
    esac
    if [[ -n "${image_for[$name]:-}" && "${image_for[$name]}" != "$f" ]]; then
        sable_fail "two images for '$name': ${image_for[$name]} and $f"
    fi
    image_for[$name]="$f"
done < <(find "$IMAGES" -maxdepth 2 -type f \( -name '*.img' -o -name '*.bin' \) -print0 | LC_ALL=C sort -z)
((${#image_for[@]})) || sable_fail "no .img/.bin files under $IMAGES (unzip the OS archive first)"

if [[ -f "$IMAGES/SHA256SUMS.txt" ]]; then
    (cd "$IMAGES" && sha256sum -c --quiet SHA256SUMS.txt) || sable_fail "image hashes don't match $IMAGES/SHA256SUMS.txt"
    sable_log "image hashes verified"
else
    sable_log "no SHA256SUMS.txt in $IMAGES: record hashes in docs/STOCK_BASIS.md before relying on these images"
fi

plan=()      # bootloader-mode commands, one per line
fbd_plan=()  # fastbootd commands for logical partitions
add() { plan+=("$*"); }

for p in "${BOOT_SET[@]}"; do
    [[ -n "${image_for[$p]:-}" ]] || sable_fail "stock archive has no $p image; it is required to undo a SableOS install"
    if [[ "$p" == vbmeta ]]; then
        add "flash --slot=all vbmeta ${image_for[$p]}"
    else
        add "flash --slot=all $p ${image_for[$p]}"
    fi
done

if [[ -n "${image_for[super]:-}" ]]; then
    add "flash super ${image_for[super]}"
else
    for p in "${LOGICAL[@]}"; do
        [[ -n "${image_for[$p]:-}" ]] || sable_fail "no super.img and no $p.img: can't restore the system partitions"
        fbd_plan+=("flash $p ${image_for[$p]}")
    done
fi

if [[ "$SCOPE" == full ]]; then
    for p in "${FIRMWARE[@]}"; do
        if [[ -n "${image_for[$p]:-}" ]]; then
            if [[ "$p" == logo ]]; then
                add "flash logo ${image_for[$p]}"
            else
                add "flash --slot=all $p ${image_for[$p]}"
            fi
        fi
    done
    if [[ -n "${image_for[preloader]:-}" ]]; then
        if [[ "$PRELOADER" == YES ]]; then
            add "flash --slot=all preloader ${image_for[preloader]}"
        else
            sable_log "skipping preloader (pass --include-preloader to flash it)"
        fi
    fi
fi

for name in "${!image_for[@]}"; do
    [[ "$NEVER" == *" $name "* ]] && sable_log "ignoring $name image: this script never writes $name"
done

# Wipe last, after every image is written.
final=()
[[ "$WIPE" == YES ]] && final+=("-w")

echo "===== Q25 STOCK RESTORE PLAN (scope=$SCOPE) ====="
echo "# in bootloader fastboot (Volume Up + Power, choose fastboot):"
printf '  fastboot %s\n' "${plan[@]}"
if ((${#fbd_plan[@]})); then
    echo "# then in userspace fastbootd:"
    echo "  fastboot reboot fastboot"
    echo "  fastboot snapshot-update cancel   # drop any pending Virtual A/B merge"
    printf '  fastboot %s\n' "${fbd_plan[@]}"
fi
((${#final[@]})) && printf '  fastboot %s\n' "${final[@]}"
echo "  fastboot reboot"

if [[ "$EXECUTE" != YES ]]; then
    echo "RESTORE=PLAN_ONLY (add --execute to flash)"
    exit 0
fi

fb=("$SABLE_FASTBOOT")
[[ -n "$SERIAL" ]] && fb+=(-s "$SERIAL")
command -v "$SABLE_FASTBOOT" >/dev/null 2>&1 || sable_fail "fastboot not found"

count="$("$SABLE_FASTBOOT" devices | grep -c . || true)"
if [[ -z "$SERIAL" && "$count" != 1 ]]; then
    sable_fail "expected exactly one fastboot device, found $count (use --serial)"
fi
product="$("${fb[@]}" getvar product 2>&1 | sed -n 's/^product: *//p' | head -n1)"
case "$product" in
    q20_v12_factory|q20_v1_factory|Q25) ;;
    *) sable_fail "fastboot product is '$product', not a Q25 board (q20_v12_factory, q20_v1_factory)" ;;
esac
# Q25 ships on two boards (q20_v1_factory, q20_v12_factory) and stock packages
# are built per board; the preloader file name says which one.
pkg_board=""
if [[ -n "${image_for[preloader]:-}" ]]; then
    pkg_board="$(basename "${image_for[preloader]}" .bin)"
    pkg_board="${pkg_board#preloader_}"
fi
if [[ -n "$pkg_board" && "$product" != Q25 && "$pkg_board" != "$product" ]]; then
    if [[ "$SCOPE" == full ]]; then
        sable_fail "package is for board $pkg_board but the phone is $product; full scope needs a package for $product"
    fi
    sable_log "WARNING: package is for board $pkg_board but the phone is $product (scope sable only writes boot images and super)"
fi
unlocked="$("${fb[@]}" getvar unlocked 2>&1 | sed -n 's/^unlocked: *//p' | head -n1)"
[[ "$unlocked" == yes ]] || sable_fail "bootloader reports unlocked='$unlocked'; fastboot flashing needs an unlocked bootloader"

if [[ "$YES" != YES ]]; then
    read -r -p "Type RESTORE to flash product $product: " answer
    [[ "$answer" == RESTORE ]] || sable_fail "not confirmed"
fi

for cmd in "${plan[@]}"; do
    # shellcheck disable=SC2086 # plan entries are fastboot argument lists
    "${fb[@]}" $cmd
done
if ((${#fbd_plan[@]})); then
    "${fb[@]}" reboot fastboot
    sleep 10
    "${fb[@]}" snapshot-update cancel || sable_log "snapshot-update cancel failed; continuing"
    for cmd in "${fbd_plan[@]}"; do
        # shellcheck disable=SC2086
        "${fb[@]}" $cmd
    done
fi
for cmd in "${final[@]}"; do
    # shellcheck disable=SC2086
    "${fb[@]}" $cmd
done
"${fb[@]}" reboot

echo "RESTORE=FLASHED"
echo "NEXT=wait for stock Android to boot; record the result as gate R0/RESTORE in docs/QUALIFICATION.md"
