#!/usr/bin/env bash
# Package a collected build as a GitHub pre-release: one zip with the images,
# the install package, checksums, flashing steps and release notes.
#
#   bash build/sable.sh q25 full artifacts
#   bash build/sable.sh q25 full release [--tag alpha-YYYYMMDD] [--notes FILE]
#                                      [--artifacts DIR] [--boot-tested] [--publish]
#
# Without --publish it only builds the zip and prints the gh command. With
# --publish it creates the pre-release with gh (gh auth login first). The tag
# is created on the commit the build was made from, which must be pushed.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

RELEASE="${1:-}"
sable_require_release "$RELEASE"
shift

TAG="alpha-$(date -u +%Y%m%d)"
NOTES=""
ARTIFACTS=""
PUBLISH=NO
BOOT_TESTED=""
REPO="${SABLE_RELEASE_REPO:-aimindseye/zinwaq25-sableos}"
while (($#)); do
    case "$1" in
        --tag) TAG="${2:?--tag needs a value}"; shift 2 ;;
        --notes) NOTES="${2:?--notes needs a file}"; shift 2 ;;
        --artifacts) ARTIFACTS="${2:?--artifacts needs a directory}"; shift 2 ;;
        --repo) REPO="${2:?--repo needs owner/name}"; shift 2 ;;
        --publish) PUBLISH=YES; shift ;;
        --boot-tested) BOOT_TESTED=YES; shift ;;
        *) sable_fail "unknown option $1" ;;
    esac
done
[[ "$TAG" =~ ^[A-Za-z0-9._-]+$ ]] || sable_fail "tag '$TAG' may only hold letters, digits, '.', '_' and '-'"
command -v zip >/dev/null || sable_fail "zip is not installed (sudo apt install zip)"

# The newest collected build of this release, unless one was named.
if [[ -z "$ARTIFACTS" ]]; then
    ARTIFACTS="$(find "$SABLE_ARTIFACTS_ROOT/q25/$RELEASE" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort | tail -n1)"
    [[ -n "$ARTIFACTS" ]] || sable_fail "no artifacts for $RELEASE; run: bash build/sable.sh q25 $RELEASE artifacts"
fi
[[ -f "$ARTIFACTS/SHA256SUMS.txt" && -f "$ARTIFACTS/BUILD_INFO.txt" ]] ||
    sable_fail "$ARTIFACTS is not a collected build (no SHA256SUMS.txt or BUILD_INFO.txt)"
(cd "$ARTIFACTS" && sha256sum --quiet -c SHA256SUMS.txt) || sable_fail "checksums in $ARTIFACTS do not match"

info() {
    local key="$1"
    sed -n "s/^$key=//p" "$ARTIFACTS/BUILD_INFO.txt"
}
commit="$(info tool_commit)"
mapfile -t packages < <(find "$ARTIFACTS" -maxdepth 1 -name '*.zip' -printf '%f\n')
((${#packages[@]} == 1)) || sable_fail "expected one install package in $ARTIFACTS, found ${#packages[@]}"
package="${packages[0]}"
name="sableos-q25-$RELEASE-$TAG"

stage="$ARTIFACTS/release/$name"
rm -rf "$ARTIFACTS/release"
mkdir -p "$stage"
# Everything collected, so SHA256SUMS.txt checks inside the zip too.
find "$ARTIFACTS" -mindepth 1 -maxdepth 1 ! -name release -exec cp -a -t "$stage" {} +
install -m 0644 "$SABLE_REPO_ROOT/docs/FLASHING_RELEASE.md" "$stage/FLASHING.md"

# Release notes: the hand-written notes, then what this build is.
# releases/<tag>.md for this release, else the current alpha notes.
if [[ -z "$NOTES" ]]; then
    for f in "$SABLE_REPO_ROOT/releases/$TAG.md" "$SABLE_REPO_ROOT/releases/ALPHA_NOTES.md"; do
        [[ -f "$f" ]] && { NOTES="$f"; break; }
    done
fi
# The LineageOS version the image is built on: from the build's own props, else the release branch.
lineage_version="$(sed -n 's/^ro\.lineage\.version=//p' "$ARTIFACTS/props/system/build.prop" 2>/dev/null | head -n1)"
lineage_base="LineageOS 23.2 (Android 16 QPR2)"
[[ -n "$lineage_version" ]] && lineage_base="$lineage_base, build $lineage_version"
previous="$(git -C "$SABLE_REPO_ROOT" describe --tags --abbrev=0 "$commit^" 2>/dev/null || true)"
{
    echo "# SableOS for the Zinwa Q25, $TAG ($RELEASE)"
    echo
    echo "> [!WARNING]"
    echo "> **Alpha software: you flash it entirely at your own risk.** SableOS is an unofficial"
    echo "> community build, not made or supported by Zinwa, provided \"as is\" without warranty of"
    echo "> any kind (Apache License 2.0). The repository owner and contributors are not responsible"
    echo "> for anything that happens to your phone or your data, including a phone that no longer"
    echo "> starts, lost data, lost IMEI or radio calibration, or warranty problems."
    echo ">"
    echo "> Installing unlocks the bootloader and erases the phone. Back up first and know the way"
    echo "> back: both are in FLASHING.md, inside the zip."
    echo
    echo "**Built on $lineage_base**, the official \`lineage_Q25\` device tree."
    echo
    if [[ -n "$NOTES" ]]; then
        cat "$NOTES"
        echo
    fi
    echo "## Download"
    echo
    echo "\`$name.zip\` holds the install package \`$package\`, the boot images, \`SHA256SUMS.txt\`"
    echo "and \`FLASHING.md\` (the steps below). Check it after downloading:"
    echo
    echo '```bash'
    echo "sha256sum $name.zip   # compare with the value under Build"
    echo '```'
    echo
    echo "## Flashing, in short"
    echo
    sed -n '/^<!-- short:start -->$/,/^<!-- short:end -->$/p' "$SABLE_REPO_ROOT/docs/FLASHING_RELEASE.md" | sed '1d;$d'
    echo
    echo "## Back up first, and the way back"
    echo
    echo "Before unlocking, while the phone still runs stock Android:"
    echo
    echo "1. Copy your photos, messages and other files off the phone: unlocking erases them."
    echo "2. Save every partition except your data with [mtkclient](https://github.com/bkerler/mtkclient)"
    echo "   (phone off, then plug in USB). This keeps your IMEI and radio calibration, which can"
    echo "   never be downloaded again:"
    echo
    echo '   ```bash'
    echo '   mkdir -p ~/q25-backup && cd ~/q25-backup'
    echo '   mtk printgpt | tee printgpt.txt && mtk gpt gpt && mtk rl partitions --skip=userdata'
    echo '   sha256sum printgpt.txt gpt/* partitions/* > SHA256SUMS.txt'
    echo '   ```'
    echo
    echo "3. Download the full stock firmware package for your phone and keep it with the backup."
    echo
    echo "To go back to stock: flash the stock \`boot\`, \`dtbo\`, \`vendor_boot\`, \`vbmeta\`,"
    echo "\`vbmeta_system\` and \`vbmeta_vendor\` images to both slots and \`super.img\` from fastboot,"
    echo "then \`fastboot -w\`. If that is not enough, write your own backup back with \`mtk wl\`, and if"
    echo "the phone does not reach fastboot use Zinwa's SP Flash Tool method. FLASHING.md (\"Going"
    echo "back to stock\") has every command, and"
    echo "[RETURN_TO_STOCK.md](https://github.com/$REPO/blob/main/docs/RETURN_TO_STOCK.md) the details."
    echo
    echo "## Build"
    echo
    echo "- Base: $lineage_base"
    echo "- Release: $RELEASE, lunch \`$(info lunch)\`"
    echo "- Source: [\`${commit:0:12}\`](https://github.com/$REPO/commit/$commit)"
    echo "- Booted on a Q25 before upload: ${BOOT_TESTED:-$(info boot_tested)}"
    if [[ -n "$previous" ]]; then
        echo
        echo "## Changes since $previous"
        echo
        git -C "$SABLE_REPO_ROOT" log --no-merges --format='- %s' "$previous..$commit" | head -n 80
    fi
} > "$stage/RELEASE_NOTES.md"

out="$ARTIFACTS/release"
(cd "$out" && zip -q -r "$name.zip" "$name")
# GitHub refuses release files over 2 GiB: then the install package goes up on its own.
assets=("$out/$name.zip")
limit=$((2 * 1024 * 1024 * 1024 - 1))
if (($(stat -c %s "$out/$name.zip") > limit)); then
    rm "$out/$name.zip"
    (cd "$out" && zip -q -r "$name.zip" "$name" -x "$name/$package")
    install -m 0644 "$stage/$package" "$out/$package"
    assets+=("$out/$package")
    echo "NOTE: over 2 GiB in one zip; $package is a separate download"
fi
zip_sum="$(sha256sum "$out/$name.zip" | awk '{print $1}')"
notes="$out/RELEASE_NOTES.md"
sed "s|^- Source: |- \`$name.zip\` SHA-256: \`$zip_sum\`\n- Source: |" "$stage/RELEASE_NOTES.md" > "$notes"

echo "RELEASE_ZIP=$out/$name.zip"
echo "RELEASE_NOTES=$notes"
gh_cmd=(gh release create "$TAG" "${assets[@]}" --repo "$REPO" --target "$commit" --prerelease
    --title "SableOS Q25 $TAG" --notes-file "$notes")
if [[ "$PUBLISH" != YES ]]; then
    echo "Not published. To publish:"
    printf ' %q' "${gh_cmd[@]}"
    echo
    exit 0
fi
command -v gh >/dev/null || sable_fail "gh is not installed (https://cli.github.com), or rerun without --publish and upload by hand"
gh auth status >/dev/null 2>&1 || sable_fail "gh is not signed in: gh auth login"
[[ "$commit" =~ ^[0-9a-f]{40}$ ]] || sable_fail "the build was made from uncommitted sources; commit, push and rebuild"
git -C "$SABLE_REPO_ROOT" branch -r --contains "$commit" 2>/dev/null | grep -q . ||
    sable_fail "commit ${commit:0:12} is not pushed; push it so the tag can point at it"
"${gh_cmd[@]}"
echo "RELEASE=PASS tag=$TAG"
