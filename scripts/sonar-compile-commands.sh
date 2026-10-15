#!/usr/bin/env bash
# Writes out/sonar/compile_commands.json for SonarQube Cloud's C analysis of the camera
# 50 MP remosaic shim. The Android-only headers are replaced by the same two-line stubs the
# host test uses (tests/test_camera_50mp.py), so the file parses on a Linux runner.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="$root/out/sonar"
stubs="$out/stubs"
shim_dir="$root/product/q25/camera-50mp"
shim="remosaic_shim_v4_rc1.c"

cc="$(command -v clang || command -v gcc || true)"
if [[ -z "$cc" ]]; then
    echo "sonar-compile-commands: no clang or gcc on PATH" >&2
    exit 1
fi

mkdir -p "$stubs/android" "$stubs/sys" "$out/empty"
printf '%s\n' '#define ANDROID_LOG_INFO 4' '#define ANDROID_LOG_ERROR 6' \
    'int __android_log_print(int, const char *, const char *, ...);' >"$stubs/android/log.h"
printf '%s\n' '#define PROP_VALUE_MAX 92' \
    'int __system_property_get(const char *, char *);' >"$stubs/sys/system_properties.h"

# The Android.bp flags, plus the stub include path.
"$cc" -O3 -Wall -Werror -Wno-unused-function -pthread -I"$stubs" -fsyntax-only "$shim_dir/$shim" 2>/dev/null ||
    echo "sonar-compile-commands: note: $shim did not compile cleanly with $cc; Sonar may report parse errors" >&2

python3 - "$out/compile_commands.json" "$shim_dir" "$shim" "$cc" "$stubs" <<'PY'
import json, sys
path, directory, source, cc, stubs = sys.argv[1:]
entry = {
    "directory": directory,
    "file": source,
    "arguments": [cc, "-O3", "-Wall", "-Wno-unused-function", "-pthread", "-I" + stubs, "-c", source],
}
with open(path, "w") as fh:
    json.dump([entry], fh, indent=2)
PY
echo "sonar-compile-commands: wrote $out/compile_commands.json"
