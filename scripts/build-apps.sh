#!/usr/bin/env bash
# Build the Sable APKs listed in product/q25/apps.tsv from sable-src/.
#
# Same flow as the SableOS Titan 2 product-app build: Gradle builds each app,
# the APK's package id is checked, and the APKs are collected with a manifest
# and SHA256SUMS so the image build consumes exact, hashed inputs.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

ALL=NO
while (($#)); do
    case "$1" in
        --all) ALL=YES; shift ;;
        -h|--help) echo "usage: scripts/build-apps.sh [--all]   (--all also builds rows with enabled=no)"; exit 0 ;;
        *) sable_fail "unknown argument $1" ;;
    esac
done

SRC="$SABLE_REPO_ROOT/sable-src"
TSV="$SABLE_REPO_ROOT/product/q25/apps.tsv"
[[ -d "$SRC" ]] || sable_fail "no sable-src/; import Sable sources first (apps/README.md)"
[[ -d "$ANDROID_HOME" ]] || sable_fail "ANDROID_HOME=$ANDROID_HOME missing"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
[[ -z "$SABLE_JDK" ]] || { export JAVA_HOME="$SABLE_JDK"; export PATH="$JAVA_HOME/bin:$PATH"; }

aapt2="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name aapt2 -type f 2>/dev/null | LC_ALL=C sort | tail -n1)"
[[ -x "$aapt2" ]] || sable_fail "aapt2 not found under $ANDROID_HOME/build-tools"

# Rows whose gradle_root is "flavor:NAME" are built from a pinned upstream
# checkout with the Sable flavor applied (sable-src/apps/r8/NAME: upstream.env,
# apply_sable_flavor.py, verify_sable_*.py), the same way SableOS builds them.
# The upstream is cloned once into SABLE_UPSTREAM_CACHE (needs
# NETWORK_FETCH_AUTHORIZED=YES) and copied to a fresh SABLE_FLAVOR_WORK tree.
declare -A flavor_dir=()
prepare_flavor() {
    local name="$1" spec="$SRC/apps/r8/$1" url commit cache work verify
    [[ -f "$spec/upstream.env" && -f "$spec/apply_sable_flavor.py" ]] || sable_fail "no Sable flavor at $spec"
    url="$(sed -n 's/^[A-Z_]*_REPOSITORY=//p' "$spec/upstream.env" | head -n1)"
    commit="$(sed -n 's/^[A-Z_]*_COMMIT=//p' "$spec/upstream.env" | head -n1)"
    [[ -n "$url" && "$commit" =~ ^[0-9a-f]{40}$ ]] || sable_fail "$spec/upstream.env lacks *_REPOSITORY or a full *_COMMIT"
    cache="$SABLE_UPSTREAM_CACHE/$name"
    if ! git -C "$cache" cat-file -e "$commit^{commit}" 2>/dev/null; then
        [[ "${NETWORK_FETCH_AUTHORIZED:-NO}" == YES ]] || sable_fail "$name upstream $commit not cached; set NETWORK_FETCH_AUTHORIZED=YES to fetch $url"
        [[ -d "$cache/.git" ]] || git clone --no-checkout "$url" "$cache"
        git -C "$cache" fetch origin "$commit"
    fi
    work="$SABLE_FLAVOR_WORK/$name"
    rm -rf "$work"
    mkdir -p "$SABLE_FLAVOR_WORK"
    git clone --quiet --no-hardlinks --no-checkout "$cache" "$work"
    git -C "$work" checkout --quiet --detach "$commit"
    python3 "$spec/apply_sable_flavor.py" "$work"
    for verify in "$spec"/verify_sable_*.py; do
        [[ -f "$verify" ]] && python3 "$verify" "$work"
    done
    chmod +x "$work/gradlew"
    flavor_dir[$name]="$work"
}
root_dir() {
    case "$1" in
        flavor:*) echo "${flavor_dir[${1#flavor:}]}" ;;
        *) echo "$SRC/$1" ;;
    esac
}

# Collect selected rows and group Gradle tasks per project.
declare -A tasks_by_root=()
rows=()
while IFS=$'\t' read -r module group root task apk package signing overrides enabled; do
    [[ -z "$module" || "$module" == \#* || "$module" == module ]] && continue
    [[ "$enabled" == yes || "$ALL" == YES ]] || continue
    rows+=("$module"$'\t'"$group"$'\t'"$root"$'\t'"$apk"$'\t'"$package"$'\t'"$signing"$'\t'"$overrides")
    tasks_by_root[$root]+=" $task"
done < "$TSV"
((${#rows[@]})) || sable_fail "no apps selected"

for root in "${!tasks_by_root[@]}"; do
    if [[ "$root" == flavor:* ]]; then
        sable_log "preparing Sable flavor ${root#flavor:}"
        prepare_flavor "${root#flavor:}"
    fi
done

for root in "${!tasks_by_root[@]}"; do
    dir="$(root_dir "$root")"
    [[ -x "$dir/gradlew" ]] || sable_fail "missing $dir/gradlew"
    sable_log "gradle ($root):${tasks_by_root[$root]}"
    # shellcheck disable=SC2086 # task list is intentionally word-split
    (cd "$dir" && ./gradlew --no-daemon --console=plain ${tasks_by_root[$root]})
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
head -n2 "$SRC/SOURCE_IMPORT.txt" > "$OUT/SOURCE_IMPORT.txt" 2>/dev/null || true
ln -sfn "$OUT" "$SABLE_APPS_OUT/latest"

echo "APPS=PASS"
echo "APPS_OUT=$OUT"
echo "APP_COUNT=${#rows[@]}"
