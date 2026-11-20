#!/usr/bin/env bash
# Build the Sable APKs listed in product/q25/apps.tsv from a sableos-android checkout
# (SABLE_APPS_REPO, the normal way; see docs/BUILD.md 4a). Without it only the rows that
# are plain Gradle projects build, from the older sable-src/ copy.
#
# Same flow as the SableOS Titan 2 product-app build: Gradle builds each app,
# the APK's package id is checked, and the APKs are collected with a manifest
# and SHA256SUMS so the image build consumes exact, hashed inputs.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

ALL=NO
ONLY_ROOT=""
while (($#)); do
    case "$1" in
        --all) ALL=YES; shift ;;
        --root) [[ $# -ge 2 ]] || sable_fail "--root needs a gradle_root from apps.tsv"; ONLY_ROOT="$2"; shift 2 ;;
        -h|--help)
            echo "usage: scripts/build-apps.sh [--all] [--root GRADLE_ROOT]"
            echo "  --all   also build rows with enabled=no"
            echo "  --root  build only the rows of one gradle_root (as written in apps.tsv)"
            echo "  SABLE_APPS_REPO=/path/to/sableos-android builds every app from that checkout instead"
            echo "  SABLE_EXTRA_GRADLE_TASKS adds Gradle tasks (unit tests, lint, detekt)"
            echo "  to the selected Gradle project; use it with --root"
            echo "  SABLE_GRADLE_ARGS adds Gradle options, for example"
            echo "  --write-verification-metadata sha256"
            exit 0 ;;
        *) sable_fail "unknown argument $1" ;;
    esac
done

SRC="$SABLE_REPO_ROOT/sable-src"
TSV="$SABLE_REPO_ROOT/product/q25/apps.tsv"
if [[ -n "${SABLE_APPS_REPO:-}" ]]; then
    [[ -d "$SABLE_APPS_REPO/apps" ]] || sable_fail "SABLE_APPS_REPO=$SABLE_APPS_REPO has no apps/"
    SRC="$SABLE_APPS_REPO"
else
    [[ -d "$SRC" ]] || sable_fail "no sable-src/; import Sable sources first (apps/README.md)"
fi
[[ -d "$ANDROID_HOME" ]] || sable_fail "ANDROID_HOME=$ANDROID_HOME missing"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
[[ -z "$SABLE_JDK" ]] || { export JAVA_HOME="$SABLE_JDK"; export PATH="$JAVA_HOME/bin:$PATH"; }

aapt2="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name aapt2 -type f 2>/dev/null | LC_ALL=C sort | tail -n1)"
[[ -x "$aapt2" ]] || sable_fail "aapt2 not found under $ANDROID_HOME/build-tools"

# SABLE_APPS_REPO: path to a checkout of sableos-project/sableos-android. Every app is built straight
# from that tree, so the repo is the one source of truth for the code that ships. The old flavor
# scripts (upstream clone plus apply_sable_flavor.py) are gone.
repo_dir() {
    case "$1" in
        flavor:espeak) echo "$SABLE_APPS_REPO/apps/espeak/android" ;; # its Gradle project is in android/
        flavor:*) echo "$SABLE_APPS_REPO/apps/${1#flavor:}" ;;
        apps/titan2/platform) echo "$SABLE_APPS_REPO/apps/titan2-platform" ;;
        apps/r8/android) echo "$SABLE_APPS_REPO/apps/android" ;;
        apps/common/reader) echo "$SABLE_APPS_REPO/apps/reader" ;;
        *) sable_fail "no sableos-android folder for gradle_root $1" ;;
    esac
}
root_dir() {
    local root="$1"
    if [[ -n "${SABLE_APPS_REPO:-}" ]]; then repo_dir "$root"; return; fi
    case "$root" in
        flavor:*) sable_fail "$root needs SABLE_APPS_REPO (a sableos-android checkout); the flavor scripts were removed" ;;
        *) echo "$SRC/$root" ;;
    esac
}

# Collect selected rows and group Gradle tasks per project.
declare -A tasks_by_root=()
rows=()
while IFS=$'\t' read -r module group root task apk package signing overrides enabled; do
    [[ -z "$module" || "$module" == \#* || "$module" == module ]] && continue
    [[ "$enabled" == yes || "$ALL" == YES ]] || continue
    [[ -z "$ONLY_ROOT" || "$root" == "$ONLY_ROOT" ]] || continue
    rows+=("$module"$'\t'"$group"$'\t'"$root"$'\t'"$apk"$'\t'"$package"$'\t'"$signing"$'\t'"$overrides")
    tasks_by_root[$root]+=" $task"
done < "$TSV"
((${#rows[@]})) || sable_fail "no apps selected"

for root in "${!tasks_by_root[@]}"; do
    dir="$(root_dir "$root")"
    [[ -x "$dir/gradlew" ]] || sable_fail "missing $dir/gradlew"
    tasks="${tasks_by_root[$root]} ${SABLE_EXTRA_GRADLE_TASKS:-}"
    sable_log "gradle ($root):$tasks"
    # shellcheck disable=SC2086 # task list is intentionally word-split
    # shellcheck disable=SC2086 # SABLE_GRADLE_ARGS is intentionally word-split
    (cd "$dir" && ./gradlew --no-daemon --console=plain ${SABLE_GRADLE_ARGS:-} $tasks)
done

OUT="$SABLE_APPS_OUT/$(sable_utc_stamp)"
mkdir -p "$OUT/prebuilt"
manifest="$OUT/manifest.tsv"
printf 'module\tgroup\tpackage\tsigning\toverrides\tfilename\tsha256\n' > "$manifest"

for row in "${rows[@]}"; do
    IFS=$'\t' read -r module group root apk package signing overrides <<<"$row"
    mapfile -t found < <(compgen -G "$(root_dir "$root")/$apk" | LC_ALL=C sort)
    [[ "${#found[@]}" == 1 ]] || sable_fail "$module: expected one APK for $root/$apk, found ${#found[@]}"
    actual="$("$aapt2" dump badging "${found[0]}" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n1)"
    [[ "$actual" == "$package" ]] || sable_fail "$module: package $actual, expected $package"
    install -m 0644 "${found[0]}" "$OUT/prebuilt/$module.apk"
    sha="$(sha256sum "$OUT/prebuilt/$module.apk" | awk '{print $1}')"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$module" "$group" "$package" "$signing" "$overrides" "$module.apk" "$sha" >> "$manifest"
done

(cd "$OUT/prebuilt" && sha256sum ./*.apk > ../SHA256SUMS.txt)
if [[ -n "${SABLE_APPS_REPO:-}" ]]; then git -C "$SABLE_APPS_REPO" rev-parse HEAD > "$OUT/SOURCE_IMPORT.txt" 2>/dev/null || true; else head -n2 "$SRC/SOURCE_IMPORT.txt" > "$OUT/SOURCE_IMPORT.txt" 2>/dev/null || true; fi
ln -sfn "$OUT" "$SABLE_APPS_OUT/latest"

echo "APPS=PASS"
echo "APPS_OUT=$OUT"
echo "APP_COUNT=${#rows[@]}"
