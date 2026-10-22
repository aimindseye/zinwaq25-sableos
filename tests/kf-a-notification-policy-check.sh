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

if compgen -G "$shade/0302-*.patch" >/dev/null && compgen -G "$shade/0303-*.patch" >/dev/null &&
    grep -q 'SableShadeKeys.systemKeyFor' "$shade"/0302-*.patch &&
    grep -q 'KEYCODE_DPAD_UP && event.hasModifiers(KeyEvent.META_ALT_ON)' "$shade"/0302-*.patch &&
    grep -q 'SableNotificationCommand.RowMenu' "$shade"/0302-*.patch &&
    grep -q 'SableTileKeys.actionFor' "$shade"/0303-*.patch; then
    pass "Shade: Alt replaces Fn (Alt+Down, Alt+Up, Alt+Enter) and tiles take Space and Alt+Enter"
else
    fail "Shade: Alt shortcuts or Quick Settings tile keys missing"
fi

lock="$(compgen -G "$shade/0304-*.patch" || true)"
if [[ -n "$lock" ]] && grep -q 'sable_keyguard_hint_pin">Type PIN · Enter unlocks' "$lock" &&
    grep -q 'SableKeyguardTypeAhead.get().take' "$lock" &&
    ! grep -E '^\+' "$lock" | grep -q -E 'verifyCredential|checkCredential|LockPatternChecker|verifyPasswordAndUnlock|dismissWithAction|keyguardDone'; then
    pass "Lock screen: keyboard hint and type-ahead fill the field and never check or submit a credential"
else
    fail "Lock screen keyboard patch missing, or it reaches credential checks"
fi

if [[ -n "$lock" ]] && grep -q 'public boolean dispatchKeyEvent(KeyEvent event)' "$lock" &&
    grep -q 'mOkButton.performClick();' "$lock" &&
    grep -q 'mView.setSableNumPadFocusable(' "$lock" &&
    grep -q 'handleSableKeyEvent(event)) {' "$lock" &&
    grep -q 'SableKeyguardKeys.pinDigitFor(' "$lock" &&
    grep -q 'R.string.sable_keyguard_hint_pin' "$lock" &&
    ! grep -q '^+        final int sableDigit = SableKeyguardKeys.digitFor(' "$lock"; then
    pass "Lock screen: PIN keys work wherever focus is (container and view); Enter presses OK; keypad unfocusable; bouncer hint"
else
    fail "Lock screen: PIN view does not take physical keys in dispatchKeyEvent"
fi

look="$(compgen -G "$shade/0305-*.patch" || true)"
overlay="$ROOT/product/common/overlay/SableSystemUIOverlay/res/values"
if [[ -n "$look" ]] && grep -q 'name="sable_keyguard_clock_margin_start">28dp' "$look" &&
    grep -q 'setHorizontalBias(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, 0f)' "$look" &&
    ! grep -E '^\+' "$look" | grep -q -E 'verifyCredential|checkCredential|LockPatternChecker|verifyPasswordAndUnlock|dismissWithAction|keyguardDone|LockscreenCredential' &&
    grep -q 'name="keyguard_clock_top_margin">28dp' "$overlay/dimens.xml" &&
    grep -q 'name="keyguard_indication_margin_bottom">36dp' "$overlay/dimens.xml" &&
    grep -q 'name="keyguard_unlock">Swipe up to unlock' "$overlay/strings.xml" &&
    grep -q 'name="config_sableKeyguardClockStartAligned"' "$overlay/config.xml"; then
    pass "Lock screen look: clock and unlock text are resources and the overlay; no credential code"
else
    fail "Lock screen look patch 0305 or its overlay values missing, or it reaches credential code"
fi

clock="$(compgen -G "$shade/0306-*.patch" || true)"
if [[ -n "$clock" ]] && grep -q 'mKeyguardUpdateMonitor.dispatchTimeUpdateNow();' "$clock" &&
    grep -q 'refreshTimeZoneAndFormat()' "$clock"; then
    pass "Lock screen clock: the doze tick, time zone and 12/24h format reach the keyguard clock"
else
    fail "Lock screen clock patch 0306 missing"
fi

glance="$(compgen -G "$shade/0307-*.patch" || true)"
if [[ -n "$glance" ]] && grep -q 'sableLockGlanceSection,' "$glance" &&
    grep -q 'name="sable_keyguard_hint_secure">Enter unlock · Alt+Space emergency · ↑↓ notifications' "$glance" &&
    grep -q 'name="sable_keyguard_private_text">Counts only until unlock' "$glance" &&
    grep -q 'name="sable_keyguard_requires_unlock">Open/reply requires unlock' "$glance" &&
    grep -q 'new · content hidden' "$glance" &&
    grep -q 'Physical keyboard active' "$glance" &&
    grep -q 'Software keyboard fallback' "$glance" &&
    grep -q 'SableKeyguardKeys.isEmergencyChord' "$glance" &&
    grep -q 'createLaunchEmergencyDialerIntent' "$glance" &&
    ! grep -E '^\+' "$glance" | grep -q -E 'verifyCredential|checkCredential|LockPatternChecker|verifyPasswordAndUnlock|dismissWithAction|keyguardDone|LockscreenCredential|getText\(\)|EXTRA_TEXT|EXTRA_TITLE'; then
    pass "Lock screen glance: counts, private card, emergency chord and bouncer cues; no credential code, no notification content read"
else
    fail "Lock screen glance patch 0307 missing, or it reaches credential code or notification content"
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
