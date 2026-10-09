#!/usr/bin/env bash
# Apply, revert or check the SableOS framework patches (phase Q4) in the
# LineageOS tree.
#
# patches/framework/<project path>/*.patch are `git format-patch` files for the
# LineageOS project at <project path> (for example packages/apps/Launcher3),
# applied in name order with `git apply`, so the projects stay on their synced
# commits and `repo sync` still works. What was applied is recorded in
# $SABLE_ANDROID_ROOT/.sable-q25-framework-patches so `revert` undoes exactly
# that, in reverse order.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

usage() {
    echo "usage: scripts/apply-framework-patches.sh apply|revert|check" >&2
}

ACTION="${1:-}"
case "$ACTION" in apply|revert|check) ;; *) usage; exit 64 ;; esac

PATCH_ROOT="${SABLE_FRAMEWORK_PATCHES:-$SABLE_REPO_ROOT/patches/framework}"
STAMP="$SABLE_ANDROID_ROOT/.sable-q25-framework-patches"
[[ -d "$SABLE_ANDROID_ROOT" ]] || sable_fail "no Android tree at SABLE_ANDROID_ROOT=$SABLE_ANDROID_ROOT"

# Lines of "<project>\t<patch path>", in apply order.
list_patches() {
    local patch_file rel
    while IFS= read -r -d '' patch_file; do
        rel="${patch_file#"$PATCH_ROOT"/}"
        printf '%s\t%s\n' "$(dirname "$rel")" "$patch_file"
    done < <(find "$PATCH_ROOT" -type f -name '*.patch' -print0 2>/dev/null | LC_ALL=C sort -z)
}

project_dir() {
    local dir="$SABLE_ANDROID_ROOT/$1"
    git -C "$dir" rev-parse --git-dir >/dev/null 2>&1 || sable_fail "LineageOS project $1 missing or not a git checkout"
    printf '%s\n' "$dir"
}

revert_stamped() {
    [[ -f "$STAMP" ]] || return 0
    local -a lines=()
    mapfile -t lines < "$STAMP"
    local i project patch_file dir
    for ((i = ${#lines[@]} - 1; i >= 0; i--)); do
        IFS=$'\t' read -r project patch_file <<<"${lines[$i]}"
        [[ -n "$project" ]] || continue
        dir="$(project_dir "$project")"
        [[ -f "$patch_file" ]] || sable_fail "recorded patch is gone: $patch_file (restore it or run 'repo sync $project')"
        if git -C "$dir" apply --reverse --check "$patch_file" 2>/dev/null; then
            git -C "$dir" apply --reverse "$patch_file"
            sable_log "reverted $(basename "$patch_file") in $project"
        else
            sable_fail "can't revert $(basename "$patch_file") in $project; run 'repo sync -d $project'"
        fi
    done
    rm -f "$STAMP"
}

case "$ACTION" in
    check)
        n=0
        while IFS=$'\t' read -r project patch_file; do
            dir="$(project_dir "$project")"
            if git -C "$dir" apply --check "$patch_file" 2>/dev/null; then
                echo "APPLIES  $project $(basename "$patch_file")"
            elif git -C "$dir" apply --reverse --check "$patch_file" 2>/dev/null; then
                echo "APPLIED  $project $(basename "$patch_file")"
            else
                echo "CONFLICT $project $(basename "$patch_file")"
                n=$((n + 1))
            fi
        done < <(list_patches)
        ((n == 0)) || sable_fail "$n framework patch(es) don't apply to this tree"
        echo "FRAMEWORK_PATCHES=CHECK_PASS"
        ;;
    revert)
        revert_stamped
        echo "FRAMEWORK_PATCHES=REVERTED"
        ;;
    apply)
        # Start from a clean state so re-staging is idempotent.
        revert_stamped
        mapfile -t entries < <(list_patches)
        for entry in "${entries[@]}"; do
            IFS=$'\t' read -r project patch_file <<<"$entry"
            dir="$(project_dir "$project")"
            git -C "$dir" apply --check "$patch_file" 2>/dev/null \
                || sable_fail "$(basename "$patch_file") doesn't apply to $project (LineageOS moved? see patches/README.md)"
        done
        : > "$STAMP"
        for entry in "${entries[@]}"; do
            IFS=$'\t' read -r project patch_file <<<"$entry"
            git -C "$(project_dir "$project")" apply "$patch_file"
            printf '%s\t%s\n' "$project" "$patch_file" >> "$STAMP"
            sable_log "applied $(basename "$patch_file") in $project"
        done
        echo "FRAMEWORK_PATCHES=APPLIED"
        echo "FRAMEWORK_PATCH_COUNT=${#entries[@]}"
        ;;
esac
