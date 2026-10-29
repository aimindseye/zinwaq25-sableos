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
    return
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
    local release="$1"
    # "full" is the complete SableOS image; Q4 is its old name (same content).
    if [[ "$release" == full ]]; then
        printf '99\n'
        return
    fi
    local digits="${release#Q}"
    digits="${digits%%[!0-9]*}"
    printf '%s\n' "${digits:-0}"
    return
}

sable_release_has_framework_layer() {
    local release="$1" level
    level="$(sable_release_level "$release")"
    (( level >= 4 ))
    return
}

sable_require_release() {
    local release="${1:-}"
    [[ "$release" == full || "$release" =~ ^Q[0-9]+[A-Za-z0-9._-]*$ ]] ||
        sable_fail "release must be full, Q1 or Q2 (Q4 is the old name of full): got '$release'"
    return 0
}

sable_require_android_root() {
    [[ -f "$SABLE_ANDROID_ROOT/build/envsetup.sh" ]] ||
        sable_fail "no Android tree at SABLE_ANDROID_ROOT=$SABLE_ANDROID_ROOT (run bootstrap first)"
    return 0
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

# The release number and build type must be plain words: they end up in file names, the
# GitHub tag and the lunch target.
sable_require_build_options() {
    [[ "$SABLE_VERSION" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] ||
        sable_fail "version '$SABLE_VERSION' may only hold letters, digits, '.', '_' and '-' (example: --version 1.0)"
    case "$SABLE_Q25_BUILD_VARIANT" in
        user | userdebug | eng) ;;
        *) sable_fail "variant must be user or userdebug (eng for developers): got '$SABLE_Q25_BUILD_VARIANT'" ;;
    esac
    return 0
}

sable_lunch_target() {
    printf '%s-%s-%s\n' "$SABLE_Q25_LUNCH_PRODUCT" "$(sable_target_release)" "$SABLE_Q25_BUILD_VARIANT"
    return
}

sable_utc_stamp() {
    date -u +%Y%m%dT%H%M%SZ
    return
}

# The Sable lock screen (frameworks/base 0304-0307: type-ahead, PIN keys, the
# locked glance, the bouncer cues) is written for the legacy keyguard. With
# com.android.systemui.scene_container enabled SystemUI uses the scene
# container keyguard instead and those changes silently disappear (only the
# Alt+Space emergency shortcut works on both). Reads `aconfig dump` output
# ("name=ENABLED|DISABLED" or "name=true|false", one flag per line) on stdin
# and fails when the flag is enabled or missing.
sable_check_scene_container_off() {
    local line state
    line="$(grep -E '^com\.android\.systemui\.scene_container[=: ]' || true)"
    [[ -n "$line" ]] || {
        sable_log "scene_container flag not found in the aconfig dump"
        return 1
    }
    state="${line#*[=: ]}"
    case "$state" in
        *ENABLED* | *true*)
            sable_log "com.android.systemui.scene_container is enabled: the Sable lock screen needs the legacy keyguard"
            return 1
            ;;
    esac
    return 0
}
