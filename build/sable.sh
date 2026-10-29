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

quick start (builds everything; no background needed):
  export ANDROID_HOME=/path/to/android-sdk     # Android SDK, platform 36
  bash build/sable.sh q25 all --yes            # check, download, build, collect

  --yes allows the ~200 GB LineageOS download. Run the same command again to
  carry on after a problem. Add --release to also zip it for a GitHub release.
  Results are in ~/sable-q25/artifacts (change with SABLE_BUILD_ROOT).

options for every step (put them anywhere after the function):
  --version N     the SableOS release number, for example 1.0 (default 1.0).
                  It names the files and the GitHub release and is written
                  into the image as ro.sable.version.
  --variant V     user or userdebug (default userdebug). userdebug is
                  debuggable (adb root works); user is not.
  Use the same values for stage, build, artifacts and release.
  Example: bash build/sable.sh q25 all --yes --version 1.1 --variant user

step by step (advanced):
  bash build/sable.sh q25 [release] <function> [options]
  The release defaults to full.

releases:
  full  the complete SableOS image: LineageOS base + Sable apps + framework
        integration (Sable Start as HOME, Sable Keyboard as the only IME,
        patches/framework applied to LineageOS). Use this for releases.
        (Q4 is the old name of full and is still accepted.)
  Q2    LineageOS base + Sable product layer and apps, no framework patches
  Q1    plain LineageOS lineage_Q25 control image (no Sable layer)

functions:
  all [--yes] [--release]     every step below, in order (the quick start)
  doctor                      check the build host
  bootstrap                   repo init + sync (needs NETWORK_FETCH_AUTHORIZED=YES)
  import --sableos PATH       copy Sable sources from a sableos checkout
                              (needs --authorize-public-copy; see apps/README.md)
  apps [--all]                build Sable APKs listed in product/q25/apps.tsv
  microg [--check|--from DIR] fetch and sha256-check the pinned microG and
                              Aurora Store APKs (Q4+, SABLE_Q25_MICROG=YES;
                              downloads need NETWORK_FETCH_AUTHORIZED=YES)
  stage                       inject (Q2+) or remove (Q1) the Sable layer;
                              Q4+ also applies patches/framework, earlier
                              releases revert them
  build                       lunch + m bacon
  artifacts                   collect and hash build outputs
  release [--publish]         zip the collected build with flashing steps and
                              release notes as a GitHub release; the tag is
                              the --version (docs/FLASHING_RELEASE.md, releases/)
  ci                          static repository checks

example:
  bash build/sable.sh q25 all --yes
  bash build/sable.sh q25 full doctor
EOF
}

# --version and --variant apply to every step: take them out of the arguments and pass
# them on as environment variables, so the steps a function runs agree.
args=()
while (($#)); do
    case "$1" in
        --version)
            [[ -n "${2:-}" ]] || { echo "[sable-q25] FAIL --version needs a value, for example --version 1.0" >&2; exit 64; }
            export SABLE_VERSION="$2"
            shift 2
            ;;
        --version=*) export SABLE_VERSION="${1#--version=}"; shift ;;
        --variant)
            [[ -n "${2:-}" ]] || { echo "[sable-q25] FAIL --variant needs user or userdebug" >&2; exit 64; }
            export SABLE_Q25_BUILD_VARIANT="$2"
            shift 2
            ;;
        --variant=*) export SABLE_Q25_BUILD_VARIANT="${1#--variant=}"; shift ;;
        *) args+=("$1"); shift ;;
    esac
done
set -- "${args[@]+"${args[@]}"}"

# The release is optional: "q25 <function>" means "q25 full <function>".
if (($# >= 2)) && [[ "$2" =~ ^(all|doctor|bootstrap|import|apps|microg|stage|build|artifacts|release|ci|flash)$ ]]; then
    set -- "$1" full "${@:2}"
fi

DEVICE="${1:-}"
RELEASE="${2:-}"
FUNCTION="${3:-}"
shift "$(( $# >= 3 ? 3 : $# ))"

[[ -n "$DEVICE" && -n "$RELEASE" && -n "$FUNCTION" ]] || { usage; exit 64; }
[[ "$RELEASE" == Q4 ]] && RELEASE=full

case "$DEVICE" in
    q25|Q25|zinwa-q25) DEVICE=q25 ;;
    *) echo "SABLE_ENTRYPOINT=FAIL_DEVICE device=$DEVICE (only q25 is supported here)" >&2; exit 64 ;;
esac

# shellcheck source=build/lib/common.sh
source "$ROOT/build/lib/common.sh"
# shellcheck source=build/devices/q25.sh
source "$ROOT/build/devices/q25.sh"
sable_require_release "$RELEASE"
sable_require_build_options

echo "===== SABLEOS Q25 ENTRY POINT ====="
echo "DEVICE=$DEVICE"
echo "RELEASE=$RELEASE"
echo "VERSION=$SABLE_VERSION"
echo "VARIANT=$SABLE_Q25_BUILD_VARIANT"
echo "FUNCTION=$FUNCTION"
echo "TOOL_SOURCE_COMMIT=$(git -C "$ROOT" rev-parse --verify -q HEAD 2>/dev/null || echo uncommitted)"
echo "BASE=$SABLE_DEVICE_BASE"
echo "SABLE_LAYER=$(sable_release_has_sable_layer "$RELEASE" && echo YES || echo NO)"

case "$FUNCTION" in
    all)       exec bash "$ROOT/scripts/build-all.sh" "$RELEASE" "$@" ;;
    doctor)    exec bash "$ROOT/scripts/doctor.sh" "$@" ;;
    bootstrap) exec bash "$ROOT/scripts/bootstrap.sh" "$@" ;;
    import)    exec bash "$ROOT/scripts/import-sable-sources.sh" "$@" ;;
    apps)      exec bash "$ROOT/scripts/build-apps.sh" "$@" ;;
    microg)    exec bash "$ROOT/scripts/fetch-microg.sh" "$@" ;;
    stage)     exec bash "$ROOT/scripts/stage-product.sh" "$RELEASE" "$@" ;;
    build)
        [[ "$SABLE_DEVICE_BUILD_SUPPORTED" == YES ]] || { echo "SABLE_BUILD=BLOCKED" >&2; exit 78; }
        exec bash "$ROOT/scripts/build.sh" "$RELEASE" "$@"
        ;;
    artifacts) exec bash "$ROOT/scripts/collect-artifacts.sh" "$RELEASE" "$@" ;;
    release)   exec bash "$ROOT/scripts/make-release.sh" "$RELEASE" "$@" ;;
    ci)        exec bash "$ROOT/tests/run.sh" "$@" ;;
    flash)
        echo "SABLE_FLASH=BLOCKED_UNQUALIFIED device=q25: flashing is manual, see docs/INSTALL.md" >&2
        exit 78
        ;;
    *) usage; exit 64 ;;
esac
