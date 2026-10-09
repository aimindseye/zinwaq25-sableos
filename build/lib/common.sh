#!/usr/bin/env bash
# Shared helpers for the Q25 build scripts. Source, don't execute.

SABLE_REPO_ROOT="${SABLE_REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
export SABLE_REPO_ROOT

# shellcheck source=build/config/storage.env
source "$SABLE_REPO_ROOT/build/config/storage.env"
# shellcheck source=build/config/host-tools.env
source "$SABLE_REPO_ROOT/build/config/host-tools.env"
# shellcheck source=build/config/q25.env
source "$SABLE_REPO_ROOT/build/config/q25.env"

sable_log() {
    printf '[sable-q25] %s\n' "$*" >&2
}

sable_fail() {
    printf '[sable-q25] FAIL %s\n' "$*" >&2
    exit 1
}

# Releases: Q1 is the plain LineageOS control image, Q2 and later add the
# Sable layer.
sable_release_has_sable_layer() {
    case "$1" in
        Q1) return 1 ;;
        *) return 0 ;;
    esac
}

# Q4 and later add the framework integration (phase Q4): Sable Start as HOME,
# Sable Keyboard as the only IME, and patches/framework applied to LineageOS.
sable_release_level() {
    local digits="${1#Q}"
    digits="${digits%%[!0-9]*}"
    printf '%s\n' "${digits:-0}"
}

sable_release_has_framework_layer() {
    (( $(sable_release_level "$1") >= 4 ))
}

sable_require_release() {
    [[ "${1:-}" =~ ^Q[0-9]+[A-Za-z0-9._-]*$ ]] ||
        sable_fail "release must look like Q1, Q2, Q2b...: got '${1:-}'"
}

sable_require_android_root() {
    [[ -f "$SABLE_ANDROID_ROOT/build/envsetup.sh" ]] ||
        sable_fail "no Android tree at SABLE_ANDROID_ROOT=$SABLE_ANDROID_ROOT (run bootstrap first)"
}

# Release config (bp4a for lineage-23.2) from the synced LineageOS tree.
sable_target_release() {
    if [[ -n "${SABLE_Q25_TARGET_RELEASE:-}" ]]; then
        printf '%s\n' "$SABLE_Q25_TARGET_RELEASE"
        return
    fi
    local vars="$SABLE_ANDROID_ROOT/vendor/lineage/vars/aosp_target_release"
    [[ -f "$vars" ]] || sable_fail "missing $vars; set SABLE_Q25_TARGET_RELEASE"
    local value
    value="$(sed -n 's/^aosp_target_release=\([a-z0-9]*\).*/\1/p' "$vars" | head -n1)"
    [[ -n "$value" ]] || sable_fail "could not read aosp_target_release from $vars"
    printf '%s\n' "$value"
}

sable_lunch_target() {
    printf '%s-%s-%s\n' "$SABLE_Q25_LUNCH_PRODUCT" "$(sable_target_release)" "$SABLE_Q25_BUILD_VARIANT"
}

sable_utc_stamp() {
    date -u +%Y%m%dT%H%M%SZ
}
