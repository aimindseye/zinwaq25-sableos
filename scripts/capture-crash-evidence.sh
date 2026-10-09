#!/usr/bin/env bash
# Read-only capture of crash evidence from a connected phone over adb (#84).
#
# Run it right after a crash, an ANR or a bad first boot, BEFORE anything
# clears the evidence (a reboot, `logcat -c`, a factory reset, reinstalling an
# app). It saves the DropBox crash/ANR/tombstone entries, `logcat -b crash`,
# the other log buffers, `dumpsys activity exit-info`, the tombstone and ANR
# lists (pulled when the shell may read them), `getprop`, and the versions of
# the Sable apps, then writes SHA256SUMS.txt.
#
# Nothing on the phone is changed: only `adb shell` reads, `logcat -d` (dump,
# never -c), `dumpsys` reads and `adb pull` are used. No `adb root`, no
# reboot, no install, no clear.
#
# The output contains device identity (serial, build fingerprint, possibly
# IMEI-adjacent properties and app data in logs). Keep it private and never
# commit it; the script refuses an output directory inside this repository.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

usage() {
    cat >&2 <<'EOF'
usage: scripts/capture-crash-evidence.sh [--out DIR] [--serial SERIAL] [--label TEXT]

  --out DIR        parent directory (default: $HOME/sable-q25-evidence); a new
                   q25-crash-evidence-<UTC stamp>[-label] folder is made in it.
                   Must be outside this repository.
  --serial SERIAL  adb serial, when more than one device is attached
  --label TEXT     short tag added to the folder name (letters, digits, - _)

Environment: SABLE_ADB selects the adb binary (default: adb on PATH).
EOF
}

OUT="${SABLE_EVIDENCE_DIR:-$HOME/sable-q25-evidence}"
SERIAL=""
LABEL=""
while (($#)); do
    case "$1" in
        --out) OUT="${2:-}"; shift 2 ;;
        --serial) SERIAL="${2:-}"; shift 2 ;;
        --label) LABEL="${2:-}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) usage; exit 64 ;;
    esac
done

[[ -n "$OUT" ]] || { usage; exit 64; }
[[ -z "$LABEL" || "$LABEL" =~ ^[A-Za-z0-9_-]{1,40}$ ]] || sable_fail "label must be 1-40 of A-Z a-z 0-9 - _"
case "$(realpath -m "$OUT")/" in
    "$SABLE_REPO_ROOT"/*) sable_fail "evidence must not live inside this repository ($SABLE_REPO_ROOT)" ;;
esac
command -v "$SABLE_ADB" >/dev/null 2>&1 || sable_fail "adb not found ('$SABLE_ADB'); set SABLE_ADB"

adb=("$SABLE_ADB")
[[ -n "$SERIAL" ]] && adb+=(-s "$SERIAL")

if [[ -z "$SERIAL" ]]; then
    count="$("$SABLE_ADB" devices | awk 'NR > 1 && $2 == "device"' | grep -c . || true)"
    [[ "$count" == 1 ]] || sable_fail "expected exactly one adb device in 'device' state, found $count (use --serial)"
fi
state="$("${adb[@]}" get-state 2>/dev/null || true)"
[[ "$state" == device ]] || sable_fail "adb device state is '${state:-none}', need 'device' (USB debugging authorized?)"

name="q25-crash-evidence-$(sable_utc_stamp)"
[[ -n "$LABEL" ]] && name="$name-$LABEL"
DIR="$OUT/$name"
[[ ! -e "$DIR" ]] || sable_fail "$DIR already exists"
mkdir -p "$DIR/dropbox" "$DIR/exit-info" "$DIR/tombstones" "$DIR/anr" "$DIR/packages"
chmod 700 "$DIR"

INDEX="$DIR/INDEX.tsv"
printf 'file\texit\tcommand\n' > "$INDEX"

# cap FILE ARGS...: run adb ARGS, save stdout+stderr to FILE, record status. Never aborts.
cap() {
    local file="$1"; shift
    local rc=0
    "${adb[@]}" "$@" > "$DIR/$file" 2>&1 || rc=$?
    printf '%s\t%s\tadb %s\n' "$file" "$rc" "$*" >> "$INDEX"
    return 0
}

# Shell-side reads only. Every command here is listed in docs/CRASH_EVIDENCE.md.
sh_cap() {
    local file="$1" cmd="$2"
    cap "$file" shell "$cmd"
}

sable_log "capturing to $DIR"

# 1. Volatile first: logs and DropBox can rotate, so they go before anything slow.
cap logcat-crash.txt logcat -d -b crash -v threadtime
cap logcat-all.txt logcat -d -b main,system,events,crash -v threadtime
cap logcat-kernel.txt logcat -d -b kernel -v threadtime

sh_cap dropbox/list.txt "dumpsys dropbox"
for tag in data_app_crash system_app_crash system_server_crash \
    data_app_anr system_app_anr system_server_anr \
    data_app_native_crash system_app_native_crash SYSTEM_TOMBSTONE \
    system_server_watchdog SYSTEM_BOOT SYSTEM_LAST_KMSG SYSTEM_RESTART \
    data_app_wtf system_app_wtf system_server_wtf; do
    sh_cap "dropbox/$tag.txt" "dumpsys dropbox --print $tag"
done

sh_cap exit-info/all.txt "dumpsys activity exit-info"

# 2. Device and build identity.
sh_cap getprop.txt "getprop"
sh_cap uptime.txt "uptime; date -u"
cap devices.txt devices -l

# 3. Tombstones and ANR traces: list always, pull when the shell may read them.
pull_dir() {
    local remote="$1" local_dir="$2"
    sh_cap "$local_dir/ls.txt" "ls -la $remote"
    local files f rc
    files="$("${adb[@]}" shell "ls $remote 2>/dev/null" 2>/dev/null | tr -d '\r' || true)"
    while IFS= read -r f; do
        [[ "$f" =~ ^[A-Za-z0-9._-]+$ ]] || continue
        rc=0
        "${adb[@]}" pull "$remote/$f" "$DIR/$local_dir/$f" > /dev/null 2>&1 || rc=$?
        printf '%s\t%s\tadb pull %s\n' "$local_dir/$f" "$rc" "$remote/$f" >> "$INDEX"
        [[ "$rc" == 0 ]] || rm -f "$DIR/$local_dir/$f"
    done <<<"$files"
}
pull_dir /data/tombstones tombstones
pull_dir /data/anr anr

# 4. Sable app versions: every package in product/q25/apps.tsv plus any org.sableos.* on the phone.
mapfile -t expected < <(awk -F'\t' '!/^#/ && NF >= 6 && $1 != "module" { print $6 }' "$SABLE_REPO_ROOT/product/q25/apps.tsv")
sh_cap packages/versions.txt "pm list packages --show-versioncode -U"
mapfile -t ondevice < <("${adb[@]}" shell "pm list packages" 2>/dev/null | tr -d '\r' | sed -n 's/^package:\(org\.sableos\.[A-Za-z0-9._]*\)$/\1/p')
mapfile -t pkgs < <(printf '%s\n' "${expected[@]}" org.sableos.launcher "${ondevice[@]}" | grep -E '^[A-Za-z0-9._]+$' | LC_ALL=C sort -u)
{
    printf 'package\tinstalled\tversionCode\tversionName\tlastUpdateTime\n'
    for p in "${pkgs[@]}"; do
        info="$("${adb[@]}" shell "dumpsys package $p" 2>/dev/null | tr -d '\r' || true)"
        if grep -q "Package \[$p\]" <<<"$info"; then
            vc="$(sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' <<<"$info" | head -n1)"
            vn="$(sed -n 's/.*versionName=\(.*\)$/\1/p' <<<"$info" | head -n1)"
            lu="$(sed -n 's/.*lastUpdateTime=\(.*\)$/\1/p' <<<"$info" | head -n1)"
            printf '%s\tyes\t%s\t%s\t%s\n' "$p" "${vc:--}" "${vn:--}" "${lu:--}"
        else
            printf '%s\tno\t-\t-\t-\n' "$p"
        fi
    done
} > "$DIR/packages/sable-apps.tsv"
printf 'packages/sable-apps.tsv\t0\tadb shell dumpsys package <each Sable package>\n' >> "$INDEX"
for p in "${pkgs[@]}"; do
    sh_cap "exit-info/$p.txt" "dumpsys activity exit-info $p"
done

# 5. Summary and hashes.
crash_lines="$(grep -c 'FATAL EXCEPTION\|Fatal signal\|ANR in' "$DIR/logcat-all.txt" || true)"
tombstones="$(find "$DIR/tombstones" -type f ! -name ls.txt | grep -c . || true)"
failed="$(awk -F'\t' 'NR > 1 && $2 != 0' "$INDEX" | grep -c . || true)"
{
    echo "CAPTURED_UTC=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "SERIAL=${SERIAL:-auto}"
    echo "FINGERPRINT=$(sed -n 's/^\[ro.build.fingerprint\]: \[\(.*\)\]$/\1/p' "$DIR/getprop.txt" | head -n1)"
    echo "SABLE_RELEASE=$(sed -n 's/^\[ro.sable.release\]: \[\(.*\)\]$/\1/p' "$DIR/getprop.txt" | head -n1)"
    echo "CRASH_MARKERS=$crash_lines"
    echo "TOMBSTONES_PULLED=$tombstones"
    echo "COMMANDS_NONZERO=$failed (see INDEX.tsv; permission denied is normal on user builds)"
    echo "DEVICE_MODIFIED=NO"
} > "$DIR/SUMMARY.txt"

(cd "$DIR" && find . -type f ! -name SHA256SUMS.txt -print0 | LC_ALL=C sort -z | xargs -0 sha256sum) > "$DIR/SHA256SUMS.txt"
chmod -R go-rwx "$DIR"

cat "$DIR/SUMMARY.txt"
echo "EVIDENCE=CAPTURED"
echo "OUT=$DIR"
echo "NEXT=keep $DIR private; attach SUMMARY.txt and the relevant files to the issue"
