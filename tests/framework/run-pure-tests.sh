#!/usr/bin/env bash
# Pure (off-device) unit tests for the Java classes that framework patches add.
#
# For each project under tests/framework/<LineageOS project path>/, the new
# files under a "sable/" package in patches/framework/<same path>/*.patch are
# extracted into a temporary directory, the ones without android.* imports are
# compiled with javac, and the Kotlin
# JUnit tests next to this script are run against them.
#
# Needs: javac/java, SABLE_KOTLINC (kotlinc binary) and SABLE_JUNIT (a Kotlin
# file or jar providing org.junit.Test/Assert and an org.junit.Run main, or a
# real junit jar on SABLE_JUNIT_CP). Prints SKIP and exits 0 when missing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ -z "${SABLE_KOTLINC:-}" || -z "${SABLE_JUNIT:-}" ]] || ! command -v javac >/dev/null 2>&1; then
    echo "SKIP  framework pure tests (set SABLE_KOTLINC and SABLE_JUNIT; needs javac)"
    exit 0
fi
stdlib="${SABLE_KOTLIN_STDLIB:-$(dirname "$SABLE_KOTLINC")/../lib/kotlin-stdlib.jar}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
fails=0
while IFS= read -r -d '' testdir; do
    project="${testdir#"$ROOT/tests/framework/"}"
    src="$work/$project/src"; out="$work/$project/out"
    mkdir -p "$src" "$out"
    for patch in "$ROOT/patches/framework/$project"/*.patch; do
        (cd "$src" && git apply --include='*/sable/*.java' "$patch")
    done
    # Only the pure classes (no android.* imports) can compile off-device.
    mapfile -t java < <(find "$src" -name '*.java' -exec grep -L '^import android\.' {} +)
    mapfile -t tests < <(find "$testdir" -maxdepth 1 -name '*Test.kt')
    ((${#java[@]} && ${#tests[@]})) || continue
    javac -d "$out" "${java[@]}" 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
    "$SABLE_KOTLINC" -cp "$out" "${tests[@]}" "$SABLE_JUNIT" -d "$out" 2>&1 | grep -v -e '^warning' -e JAVA_TOOL_OPTIONS || true
    mapfile -t classes < <(sed -n 's/^package \(.*\)$/\1/p' "${tests[0]}" | head -n1 | while read -r pkg; do
        for t in "${tests[@]}"; do printf '%s.%s\n' "$pkg" "$(basename "$t" .kt)"; done
    done)
    if result="$(java -cp "$out:$stdlib" org.junit.Run "${classes[@]}" 2>&1 | grep -v JAVA_TOOL_OPTIONS)" &&
        grep -q 'FAILED=0' <<<"$result"; then
        echo "PASS  framework pure tests $project: $(tail -n1 <<<"$result")"
    else
        echo "FAIL  framework pure tests $project: $result"
        fails=$((fails + 1))
    fi
done < <(find "$ROOT/tests/framework" -mindepth 1 -type d -print0 | while IFS= read -r -d '' d; do
    compgen -G "$d/*Test.kt" >/dev/null && printf '%s\0' "$d"; done)
((fails == 0))
