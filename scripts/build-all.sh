#!/usr/bin/env bash
# Build the complete SableOS image for the Zinwa Q25 with one command.
#
#   bash build/sable.sh q25 all --yes
#
# Steps, in order: check the computer, download LineageOS (first run only),
# build the Sable apps, fetch microG, add the Sable layer, build the image,
# collect the files. It stops at the first problem and says what to do. Run
# the same command again to carry on: finished downloads are reused.
#
#   --yes        allow the large downloads (about 200 GB of LineageOS source,
#                plus microG and Aurora Store). Anything already downloaded is
#                kept and not fetched again. Without --yes nothing is downloaded.
#   --release    also zip the result for a GitHub pre-release
#   --skip-apps  do not rebuild the Sable apps (use the ones already built)
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

RELEASE="${1:-}"
sable_require_release "$RELEASE"
shift

RELEASE_ZIP=NO
SKIP_APPS=NO
while (($#)); do
    case "$1" in
        --yes) export NETWORK_FETCH_AUTHORIZED=YES; shift ;;
        --release) RELEASE_ZIP=YES; shift ;;
        --skip-apps) SKIP_APPS=YES; shift ;;
        -h|--help) sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) sable_fail "unknown option $1 (see: bash build/sable.sh q25 all --help)" ;;
    esac
done

SABLE="$SABLE_REPO_ROOT/build/sable.sh"
TOTAL=7
n=0
step() {
    n=$((n + 1))
    echo
    echo "===== Step $n/$TOTAL: $1 ====="
}
run() {
    local what="$1"
    shift
    "$@" || sable_fail "$what failed (see the messages above). Fix that, then run the same command again."
}

tree_ready() { [[ -d "$SABLE_ANDROID_ROOT/.repo" && -f "$SABLE_ANDROID_ROOT/build/envsetup.sh" && -d "$SABLE_ANDROID_ROOT/device/xelex/Q25" ]]; }
if [[ "${NETWORK_FETCH_AUTHORIZED:-NO}" != YES ]] && ! tree_ready; then
    sable_fail "the LineageOS source is not downloaded yet. It is about 200 GB; add --yes to allow it:
  bash build/sable.sh q25 all --yes"
fi

step "check this computer"
run "the computer check" bash "$SABLE" q25 "$RELEASE" doctor

step "download LineageOS (first run only)"
# bootstrap itself skips a finished download
run "the download" bash "$SABLE" q25 "$RELEASE" bootstrap

step "build the Sable apps"
if [[ "$SKIP_APPS" == YES ]]; then
    echo "skipped (--skip-apps)"
else
    run "building the apps" bash "$SABLE" q25 "$RELEASE" apps
fi

step "fetch microG and Aurora Store"
if [[ "$RELEASE" == full && "$SABLE_Q25_MICROG" == YES ]]; then
    if bash "$SABLE" q25 "$RELEASE" microg --check >/dev/null 2>&1; then
        echo "microG and Aurora Store already downloaded and verified; not downloading again"
    else
        run "fetching microG" bash "$SABLE" q25 "$RELEASE" microg
    fi
else
    echo "not needed for this release"
fi

step "add the Sable layer to LineageOS"
run "staging" bash "$SABLE" q25 "$RELEASE" stage

step "build the image (hours on the first run)"
run "the image build" bash "$SABLE" q25 "$RELEASE" build

step "collect the finished files"
run "collecting the files" bash "$SABLE" q25 "$RELEASE" artifacts

if [[ "$RELEASE_ZIP" == YES ]]; then
    echo
    echo "===== Extra: zip for a GitHub pre-release ====="
    run "making the release zip" bash "$SABLE" q25 "$RELEASE" release
fi

echo
echo "BUILD_ALL=PASS release=$RELEASE"
echo "Your files are under: $SABLE_ARTIFACTS_ROOT/q25/$RELEASE/"
echo "Install them on the phone with docs/INSTALL.md."
