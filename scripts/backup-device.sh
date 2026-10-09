#!/usr/bin/env bash
# Read-only full backup of a Zinwa Q25 with mtkclient, before any flashing.
#
# Saves the partition table (both GPT copies) and every partition except
# userdata, then hashes everything. Nothing is written to the phone.
#
# The output contains device identity (IMEI, serial, calibration). Keep it
# private and never commit it.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

usage() {
    cat >&2 <<'EOF'
usage: scripts/backup-device.sh --out DIR [--mtk PATH] [--skip LIST]

  --out DIR     new directory for the backup (must not exist)
  --mtk PATH    mtkclient entry point (default: mtk on PATH)
  --skip LIST   comma-separated partitions not to read (default: userdata)

When it waits for the device: power the phone off, then plug in USB. If it is
not detected, unplug, hold Volume Up + Volume Down and plug in again.
EOF
}

OUT=""
MTK="${SABLE_MTK:-mtk}"
SKIP=userdata
while (($#)); do
    case "$1" in
        --out) OUT="${2:-}"; shift 2 ;;
        --mtk) MTK="${2:-}"; shift 2 ;;
        --skip) SKIP="${2:-}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) usage; exit 64 ;;
    esac
done

[[ -n "$OUT" ]] || { usage; exit 64; }
[[ ! -e "$OUT" ]] || sable_fail "$OUT already exists; pick a new directory so no backup is overwritten"
command -v "$MTK" >/dev/null 2>&1 || sable_fail "mtkclient not found ('$MTK'); see https://github.com/bkerler/mtkclient"
case "$(realpath -m "$OUT")/" in
    "$SABLE_REPO_ROOT"/*) sable_fail "backup must not live inside this repository" ;;
esac

mkdir -p "$OUT/gpt" "$OUT/partitions"
chmod 700 "$OUT"

sable_log "reading partition table (connect the powered-off phone now)"
"$MTK" printgpt | tee "$OUT/printgpt.txt"
"$MTK" gpt "$OUT/gpt"

sable_log "reading all partitions except: $SKIP"
"$MTK" rl "$OUT/partitions" --skip="$SKIP"

files="$(cd "$OUT" && find . -type f ! -name SHA256SUMS.txt -print0 | LC_ALL=C sort -z | xargs -0 sha256sum)"
printf '%s\n' "$files" > "$OUT/SHA256SUMS.txt"
chmod -R go-rwx "$OUT"

for must in nvram nvdata proinfo persist protect1 protect2; do
    if ! compgen -G "$OUT/partitions/$must*" >/dev/null; then
        sable_log "WARNING: no image for '$must' in the backup; check printgpt.txt before flashing anything"
    fi
done

echo "BACKUP=PASS"
echo "OUT=$OUT"
echo "NEXT=copy $OUT to two places off this machine; never commit it"
