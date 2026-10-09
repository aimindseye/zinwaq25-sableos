#!/usr/bin/env bash
# SableOS Zinwa Q25 build entry point.
#
# Adapted from the SableOS canonical interface (bash build/sable.sh <device>
# <release> <function>). This repository has one device, q25.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
    cat >&2 <<'EOF'
SableOS for the Zinwa Q25

usage:
  bash build/sable.sh q25 <release> <function> [options]

releases:
  Q1    plain LineageOS lineage_Q25 control image (no Sable layer)
  Q2    LineageOS base + Sable product layer and apps
  Q4    Q2 + framework integration: Sable Start as HOME, Sable Keyboard as the
        only IME, patches/framework applied to LineageOS (needs gate Q3-TEXT)

functions:
  doctor                      check the build host
  bootstrap                   repo init + sync (needs NETWORK_FETCH_AUTHORIZED=YES)
  import --sableos PATH       copy Sable sources from a sableos checkout
                              (needs --authorize-public-copy; see apps/README.md)
  apps [--all]                build Sable APKs listed in product/q25/apps.tsv
  stage                       inject (Q2+) or remove (Q1) the Sable layer;
                              Q4+ also applies patches/framework, earlier
                              releases revert them
  build                       lunch + m bacon
  artifacts                   collect and hash build outputs
  ci                          static repository checks

example:
  bash build/sable.sh q25 Q2 doctor
EOF
}

DEVICE="${1:-}"
RELEASE="${2:-}"
FUNCTION="${3:-}"
shift "$(( $# >= 3 ? 3 : $# ))"

[[ -n "$DEVICE" && -n "$RELEASE" && -n "$FUNCTION" ]] || { usage; exit 64; }

case "$DEVICE" in
    q25|Q25|zinwa-q25) DEVICE=q25 ;;
    *) echo "SABLE_ENTRYPOINT=FAIL_DEVICE device=$DEVICE (only q25 is supported here)" >&2; exit 64 ;;
esac

# shellcheck source=build/lib/common.sh
source "$ROOT/build/lib/common.sh"
# shellcheck source=build/devices/q25.sh
source "$ROOT/build/devices/q25.sh"
sable_require_release "$RELEASE"

echo "===== SABLEOS Q25 ENTRY POINT ====="
echo "DEVICE=$DEVICE"
echo "RELEASE=$RELEASE"
echo "FUNCTION=$FUNCTION"
echo "TOOL_SOURCE_COMMIT=$(git -C "$ROOT" rev-parse --verify -q HEAD 2>/dev/null || echo uncommitted)"
echo "BASE=$SABLE_DEVICE_BASE"
echo "SABLE_LAYER=$(sable_release_has_sable_layer "$RELEASE" && echo YES || echo NO)"

case "$FUNCTION" in
    doctor)    exec bash "$ROOT/scripts/doctor.sh" "$@" ;;
    bootstrap) exec bash "$ROOT/scripts/bootstrap.sh" "$@" ;;
    import)    exec bash "$ROOT/scripts/import-sable-sources.sh" "$@" ;;
    apps)      exec bash "$ROOT/scripts/build-apps.sh" "$@" ;;
    stage)     exec bash "$ROOT/scripts/stage-product.sh" "$RELEASE" "$@" ;;
    build)
        [[ "$SABLE_DEVICE_BUILD_SUPPORTED" == YES ]] || { echo "SABLE_BUILD=BLOCKED" >&2; exit 78; }
        exec bash "$ROOT/scripts/build.sh" "$RELEASE" "$@"
        ;;
    artifacts) exec bash "$ROOT/scripts/collect-artifacts.sh" "$RELEASE" "$@" ;;
    ci)        exec bash "$ROOT/tests/run.sh" "$@" ;;
    flash)
        echo "SABLE_FLASH=BLOCKED_UNQUALIFIED device=q25: flashing is manual, see docs/INSTALL.md" >&2
        exit 78
        ;;
    *) usage; exit 64 ;;
esac
