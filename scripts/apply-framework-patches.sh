#!/usr/bin/env bash
# Apply, revert or check the SableOS framework patches (phase Q4) in the
# LineageOS tree.
#
# patches/framework/<project path>/*.patch are `git format-patch` files for the
# LineageOS project at <project path> (for example packages/apps/Launcher3),
# applied in name order with `git apply` (all patches of one project in one
# call, so later ones may build on earlier ones), so the projects stay on their synced
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

# Patches of one project are checked and applied together, in name order, so a
# later patch may build on an earlier one in the same project.
patches_of() {
    local want="$1" project patch_file
    while IFS=$'\t' read -r project patch_file; do
        [[ "$project" == "$want" ]] && printf '%s\n' "$patch_file"
    done
}

projects_in() {
    cut -f1 | awk '!seen[$0]++'
}

reverse_lines() {
    awk '{ l[NR] = $0 } END { for (i = NR; i >= 1; i--) print l[i] }'
}

# seq_check DIR [--reverse] PATCH...: would the patches apply one after another
# to the working tree? `git apply --check` with several patches checks each one
# against the unpatched tree, so try them in order on a throwaway index holding
# the working tree instead. The working tree and the real index are untouched.
seq_check() {
    local dir="$1"; shift
    local -a flags=()
    if [[ "${1:-}" == --reverse ]]; then flags=(--reverse); shift; fi
    local idx rc=0 f
    idx="$(mktemp)"
    if GIT_INDEX_FILE="$idx" git -C "$dir" read-tree HEAD 2>/dev/null &&
        GIT_INDEX_FILE="$idx" git -C "$dir" add -A . 2>/dev/null; then
        for f in "$@"; do
            GIT_INDEX_FILE="$idx" git -C "$dir" apply --cached "${flags[@]}" "$f" 2>/dev/null || { rc=1; break; }
        done
    else
        rc=1
    fi
    rm -f "$idx"
    return "$rc"
}

revert_stamped() {
    [[ -f "$STAMP" ]] || return 0
    local project dir
    local -a files=()
    while IFS= read -r project; do
        [[ -n "$project" ]] || continue
        dir="$(project_dir "$project")"
        mapfile -t files < <(patches_of "$project" < "$STAMP" | reverse_lines)
        for f in "${files[@]}"; do
            [[ -f "$f" ]] || sable_fail "recorded patch is gone: $f (restore it or run 'repo sync $project')"
        done
        if seq_check "$dir" --reverse "${files[@]}"; then
            for f in "${files[@]}"; do git -C "$dir" apply --reverse "$f"; done
            sable_log "reverted ${#files[@]} patch(es) in $project"
        else
            sable_fail "can't revert the Sable patches in $project; run 'repo sync -d $project'"
        fi
    done < <(projects_in < "$STAMP" | reverse_lines)
    rm -f "$STAMP"
}

ALL="$(list_patches)"
PROJECTS=()
[[ -z "$ALL" ]] || mapfile -t PROJECTS < <(projects_in <<<"$ALL")

case "$ACTION" in
    check)
        n=0
        for project in "${PROJECTS[@]}"; do
            dir="$(project_dir "$project")"
            mapfile -t files < <(patches_of "$project" <<<"$ALL")
            names="$(for f in "${files[@]}"; do basename "$f"; done | paste -sd' ')"
            mapfile -t rev < <(printf '%s\n' "${files[@]}" | reverse_lines)
            if seq_check "$dir" "${files[@]}"; then
                echo "APPLIES  $project $names"
            elif seq_check "$dir" --reverse "${rev[@]}"; then
                echo "APPLIED  $project $names"
            else
                echo "CONFLICT $project $names"
                n=$((n + 1))
            fi
        done
        ((n == 0)) || sable_fail "framework patches don't apply to $n project(s) in this tree"
        echo "FRAMEWORK_PATCHES=CHECK_PASS"
        ;;
    revert)
        revert_stamped
        echo "FRAMEWORK_PATCHES=REVERTED"
        ;;
    apply)
        # Start from a clean state so re-staging is idempotent.
        revert_stamped
        for project in "${PROJECTS[@]}"; do
            dir="$(project_dir "$project")"
            mapfile -t files < <(patches_of "$project" <<<"$ALL")
            seq_check "$dir" "${files[@]}" \
                || sable_fail "the Sable patches don't apply to $project (LineageOS moved? see patches/README.md)"
        done
        : > "$STAMP"
        count=0
        for project in "${PROJECTS[@]}"; do
            dir="$(project_dir "$project")"
            mapfile -t files < <(patches_of "$project" <<<"$ALL")
            for f in "${files[@]}"; do
                git -C "$dir" apply "$f"
                printf '%s\t%s\n' "$project" "$f" >> "$STAMP"
                sable_log "applied $(basename "$f") in $project"
                count=$((count + 1))
            done
        done
        echo "FRAMEWORK_PATCHES=APPLIED"
        echo "FRAMEWORK_PATCH_COUNT=$count"
        ;;
esac
