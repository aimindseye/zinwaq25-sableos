#!/usr/bin/env bash
# DESIGN-KF-A static gates for Sable Hub (source half; the policy half is the Hub unit tests).
#
#   NOTIFICATION_POLICY_ANDROID_OWNED / HUB_DELIVERY_POLICY_OWNERSHIP=PASS_ABSENT
#   HUB_DND_BYPASS=PASS_ABSENT / SHADE_IS_NOTIFICATION_CENTER
#     Hub never cancels, snoozes, posts or re-ranks notifications, never edits channels or
#     interruption filters, and holds no permission that would let it.
#   HUB_HISTORY_BOUNDED_DERIVED: the history store prunes through HubHistoryBounds and no
#     unbounded retention option exists.
#   HUB_CONNECTED_APPS_PARITY: the hidden-apps provider goes through ConnectedAppsParity.
#   HUB_REMOTEINPUT_PROVIDER_SAFE: reply actions are chosen by ReplyEligibility.
#   KEYBOARD_NOTIFICATION_COMMANDS_CONTEXTUAL / TEXT_INPUT_ALWAYS_WINS: the SystemUI patch routes
#     keys through the router, which passes text-editor focus through first.
#
# Prints PASS/FAIL lines; exits non-zero on any failure. No network, no Android tree.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HUB="$ROOT/sable-src/apps/r8/android/hub/src/main"
fails=0
pass() { printf 'PASS  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*"; fails=$((fails + 1)); }

if [[ ! -d "$HUB" ]]; then
    fail "KF-A: Hub sources missing at $HUB"
    exit 1
fi

# Code lines only: drop KDoc/line comments so documentation may name the forbidden APIs.
code() { grep -rhv -E '^\s*(\*|//|/\*)' "$HUB/java" --include='*.kt' --include='*.java'; }

forbidden_calls='cancelNotification|cancelAllNotifications|snoozeNotification|requestInterruptionFilter|setInterruptionFilter|setNotificationPolicy|requestListenerHints|createNotificationChannel|updateNotificationChannel|NotificationChannel\(|setBypassDnd|setNotificationListenerAccessGranted|NotificationManagerCompat|\.notify\(|startForeground\('
if hits="$(code | grep -E "$forbidden_calls" || true)" && [[ -z "$hits" ]]; then
    pass "KF-A: Hub owns no notification delivery, DND or channel policy"
else
    fail "KF-A: Hub calls Android delivery/DND APIs: $hits"
fi

forbidden_perms='ACCESS_NOTIFICATION_POLICY|POST_NOTIFICATIONS|MANAGE_NOTIFICATIONS|STATUS_BAR_SERVICE|MANAGE_NOTIFICATION_LISTENERS'
if grep -Eq "$forbidden_perms" "$HUB/AndroidManifest.xml"; then
    fail "KF-A: Hub manifest requests a notification-policy permission"
else
    pass "KF-A: Hub manifest has no notification-policy permission"
fi

if grep -q 'HubHistoryBounds.retain' "$HUB/java/org/sableos/hub/ConnectedNotificationHistoryStore.kt" &&
    ! code | grep -q 'UntilDeleted('; then
    pass "KF-A: Hub history is bounded and derived"
else
    fail "KF-A: Hub history store bypasses HubHistoryBounds or keeps an unbounded option"
fi

if grep -q 'ConnectedAppsParity.effectiveHiddenKeys' "$HUB/java/org/sableos/hub/ConnectedAppsPolicyProvider.kt"; then
    pass "KF-A: hidden-apps provider follows notification access"
else
    fail "KF-A: hidden-apps provider ignores notification access parity"
fi

if grep -q 'ReplyEligibility.chooseReplyAction' "$HUB/java/org/sableos/hub/notifications/SableNotificationListenerService.kt"; then
    pass "KF-A: reply candidates are source-owned RemoteInputs"
else
    fail "KF-A: listener picks reply actions without ReplyEligibility"
fi

shade="$ROOT/patches/framework/frameworks/base"
if compgen -G "$shade/03*-*.patch" >/dev/null &&
    grep -q 'SableNotificationKeyRouter' "$shade"/03*-*.patch &&
    grep -q 'textInputFocused' "$shade"/03*-*.patch; then
    pass "KF-A: shade key commands are contextual and yield to text input"
else
    fail "KF-A: SystemUI notification key-command patch missing"
fi

settings="$ROOT/patches/framework/packages/apps/Settings"
if compgen -G "$settings/03*-*.patch" >/dev/null &&
    grep -q 'org.sableos.hub.action.ATTENTION_SETTINGS' "$settings"/03*-*.patch &&
    grep -q 'org.sableos.hub.action.APP_NOTIFICATION_SETTINGS' "$settings"/03*-*.patch; then
    pass "KF-A: Settings links to Sable Attention and per-app Hub settings"
else
    fail "KF-A: Settings IA patch missing"
fi

((fails == 0))
