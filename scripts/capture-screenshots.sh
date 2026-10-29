#!/usr/bin/env bash
# Take the user-guide screenshots from a connected phone over adb.
#
# For each line of docs/screenshots.tsv the script opens the screen (Home, an app, or a Settings
# intent), waits for it to draw, and saves a PNG with `adb exec-out screencap -p`. Screens it cannot
# open by itself ("manual") ask you to open them and press Enter. It then writes docs/SCREENSHOTS.md
# listing every image that exists, so the user guide never links a picture that was not taken.
#
# On the phone it only presses Home, starts activities and takes screenshots: no install, no clear,
# no root, no reboot, no settings changes.
#
# A screenshot shows whatever is on screen. Use a phone with no personal contacts, messages or
# accounts, and look at every image before you commit it.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

usage() {
    cat >&2 <<'EOF'
usage: scripts/capture-screenshots.sh [--serial SERIAL] [--only NAME[,NAME...]] [--auto] [--delay SEC] [--out DIR]

  --serial SERIAL  the Q25's adb serial (required; default: $SABLE_Q25_SERIAL, then $ANDROID_SERIAL)
  --only NAMES     take only these shots (names from docs/screenshots.tsv)
  --auto           skip the shots that need you to open the screen
  --delay SEC      seconds to wait after opening a screen (default 2)
  --out DIR        where the PNGs go (default docs/images/q25)
EOF
}

SERIAL="${SABLE_Q25_SERIAL:-${ANDROID_SERIAL:-}}"
ONLY=""
AUTO=NO
DELAY=2
OUT="$SABLE_REPO_ROOT/docs/images/q25"
while (($#)); do
    case "$1" in
        --serial) SERIAL="${2:-}"; shift 2 ;;
        --only) ONLY="${2:-}"; shift 2 ;;
        --auto) AUTO=YES; shift ;;
        --delay) DELAY="${2:-}"; shift 2 ;;
        --out) OUT="${2:-}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) usage; exit 64 ;;
    esac
done

[[ "$DELAY" =~ ^[0-9]+$ ]] || sable_fail "--delay must be a whole number of seconds"
[[ -n "$SERIAL" ]] || sable_fail "name the Q25 with --serial (see: adb devices); screenshots are never taken from a guessed device"
command -v "$SABLE_ADB" >/dev/null 2>&1 || sable_fail "adb not found ('$SABLE_ADB'); set SABLE_ADB"
adb=("$SABLE_ADB" -s "$SERIAL")
state="$("${adb[@]}" get-state 2>/dev/null || true)"
[[ "$state" == device ]] || sable_fail "adb device state is '${state:-none}', need 'device' (USB debugging authorized?)"

LIST="$SABLE_REPO_ROOT/docs/screenshots.tsv"
[[ -f "$LIST" ]] || sable_fail "missing $LIST"
mkdir -p "$OUT"

# Resolve app:WORD to an installed org.sableos.* package.
find_package() {
    "${adb[@]}" shell pm list packages 2>/dev/null | tr -d '\r' | sed 's/^package://' |
        grep -i '^org\.sableos\.' | grep -i -- "$1" | head -n1 || true
}

open_screen() {
    local how="$1" pkg
    case "$how" in
        home) "${adb[@]}" shell input keyevent KEYCODE_HOME ;;
        app:*)
            pkg="$(find_package "${how#app:}")"
            [[ -n "$pkg" ]] || return 1
            "${adb[@]}" shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
            ;;
        intent:*) "${adb[@]}" shell am start -a "${how#intent:}" >/dev/null 2>&1 ;;
        *) return 1 ;;
    esac
}

wanted() {
    [[ -z "$ONLY" ]] && return 0
    [[ ",$ONLY," == *",$1,"* ]]
}

taken=0
skipped=0
while IFS=$'\t' read -r name section how caption; do
    [[ -z "$name" || "$name" == \#* ]] && continue
    [[ "$name" =~ ^[a-z0-9-]+$ ]] || sable_fail "bad shot name '$name' in $LIST"
    wanted "$name" || continue
    if [[ "$how" == manual ]]; then
        if [[ "$AUTO" == YES ]]; then
            skipped=$((skipped + 1))
            continue
        fi
        printf 'Open: %s\nThen press Enter to capture (s + Enter to skip): ' "$caption" >&2
        read -r answer </dev/tty || answer=s
        if [[ "$answer" == s ]]; then
            skipped=$((skipped + 1))
            continue
        fi
    elif ! open_screen "$how"; then
        printf 'skip  %s (could not open %s)\n' "$name" "$how" >&2
        skipped=$((skipped + 1))
        continue
    else
        sleep "$DELAY"
    fi
    "${adb[@]}" exec-out screencap -p > "$OUT/$name.png.part"
    if [[ ! -s "$OUT/$name.png.part" ]]; then
        rm -f "$OUT/$name.png.part"
        printf 'skip  %s (empty screenshot)\n' "$name" >&2
        skipped=$((skipped + 1))
        continue
    fi
    mv "$OUT/$name.png.part" "$OUT/$name.png"
    taken=$((taken + 1))
    printf 'ok    %s\n' "$name" >&2
done < "$LIST"
"${adb[@]}" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true

# The gallery lists every image that exists in the default folder.
if [[ "$(realpath -m "$OUT")" == "$(realpath -m "$SABLE_REPO_ROOT/docs/images/q25")" ]]; then
    gallery="$SABLE_REPO_ROOT/docs/SCREENSHOTS.md"
    {
        echo "# Screenshots"
        echo
        echo "Taken from a Zinwa Q25 running SableOS with \`scripts/capture-screenshots.sh\`."
        echo "Back to the [User Guide](USER_GUIDE.md)."
        echo
        echo "To retake them: plug in a Q25 with USB debugging on, then run"
        echo "\`bash scripts/capture-screenshots.sh --serial \$(adb get-serialno)\`. Shots listed in"
        echo "[screenshots.tsv](screenshots.tsv) open by themselves or ask you to open the screen."
        while IFS=$'\t' read -r name section how caption; do
            [[ -z "$name" || "$name" == \#* ]] && continue
            [[ -f "$OUT/$name.png" ]] || continue
            printf '\n## %s\n\n%s (%s)\n\n![%s](images/q25/%s.png)\n' "$caption" "$section" "$name" "$caption" "$name"
        done < "$LIST"
    } > "$gallery"
fi

echo "SCREENSHOTS=DONE taken=$taken skipped=$skipped out=$OUT"
