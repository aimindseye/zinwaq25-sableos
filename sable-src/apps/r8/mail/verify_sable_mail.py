#!/usr/bin/env python3
from __future__ import annotations

import os
import pathlib
import subprocess
import sys

EXPECTED_COMMIT = "c733317d933c22bcd790a4790306f30ccf2026b8"
EXPECTED_PACKAGE = "org.sableos.mail"


def fail(marker: str, detail: str = "") -> None:
    suffix = f" {detail}" if detail else ""
    raise SystemExit(f"{marker}=FAIL{suffix}")


def require_text(path: pathlib.Path, needle: str, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    if needle not in text:
        fail(marker, f"path={path} missing={needle!r}")


def reject_text(path: pathlib.Path, needle: str, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    if needle in text:
        fail(marker, f"path={path} forbidden={needle!r}")


def main() -> None:
    if len(sys.argv) not in (2, 3):
        fail("SABLE_MAIL_VERIFY_USAGE", "verify_sable_mail.py <root> [apk]")

    root = pathlib.Path(sys.argv[1]).resolve()
    app = root / "app-thunderbird"

    actual = subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "HEAD"],
        text=True,
    ).strip()
    if actual != EXPECTED_COMMIT:
        fail(
            "SABLE_MAIL_UPSTREAM_COMMIT",
            f"expected={EXPECTED_COMMIT} actual={actual}",
        )
    print(f"SABLE_MAIL_UPSTREAM_COMMIT=PASS value={actual}")

    build_logic_diff = subprocess.check_output(
        [
            "git",
            "-C",
            str(root),
            "diff",
            "--name-only",
            "HEAD",
            "--",
            "build.gradle.kts",
            "build-plugin",
        ],
        text=True,
    ).splitlines()
    if build_logic_diff:
        fail(
            "SABLE_MAIL_UPSTREAM_BUILD_LOGIC_MUTATION",
            f"paths={build_logic_diff!r}",
        )
    print("SABLE_MAIL_UPSTREAM_BUILD_LOGIC_MUTATION=PASS_ABSENT")

    build = app / "build.gradle.kts"
    require_text(
        build,
        'applicationId = "org.sableos.mail"',
        "SABLE_MAIL_APPLICATION_ID_SOURCE",
    )
    require_text(
        build,
        '"fossImplementation"(projects.feature.funding.noop)',
        "SABLE_MAIL_FUNDING_NOOP",
    )
    require_text(
        build,
        "implementation(projects.feature.onboarding.migration.noop)",
        "SABLE_MAIL_ONBOARDING_MIGRATION_NOOP",
    )
    require_text(
        build,
        "implementation(projects.feature.migration.launcher.noop)",
        "SABLE_MAIL_LAUNCHER_MIGRATION_NOOP",
    )
    reject_text(
        build,
        "projects.feature.onboarding.migration.thunderbird",
        "SABLE_MAIL_STALE_THUNDERBIRD_MIGRATION",
    )
    reject_text(
        build,
        "projects.feature.migration.launcher.thunderbird",
        "SABLE_MAIL_STALE_THUNDERBIRD_MIGRATION",
    )
    reject_text(
        build,
        "projects.feature.thundermail",
        "SABLE_MAIL_THUNDERMAIL_DEPENDENCY",
    )
    require_text(
        build,
        'signingConfig = signingConfigs.getByName("debug")',
        "SABLE_MAIL_DEVELOPMENT_SIGNING",
    )
    require_text(
        build,
        'isMinifyEnabled = true',
        "SABLE_MAIL_MINIFY",
    )
    require_text(
        build,
        'isShrinkResources = true',
        "SABLE_MAIL_RESOURCE_SHRINK",
    )
    funding_settings = (
        app
        / "src/main/kotlin/net/thunderbird/android/feature/TbFundingSettings.kt"
    )
    if funding_settings.exists():
        fail(
            "SABLE_MAIL_FUNDING_SETTINGS_SOURCE",
            f"unexpected={funding_settings}",
        )
    print("SABLE_MAIL_FUNDING_SETTINGS_SOURCE=PASS_ABSENT")
    print("SABLE_MAIL_BUILD_CONFIGURATION=PASS")

    for path in (
        root / "app-common/build.gradle.kts",
        root / "feature/account/setup/build.gradle.kts",
        root / "feature/launcher/build.gradle.kts",
        root / "feature/onboarding/main/build.gradle.kts",
    ):
        reject_text(
            path,
            "projects.feature.thundermail",
            "SABLE_MAIL_TRANSITIVE_THUNDERMAIL_DEPENDENCY",
        )

    reject_text(
        root / "feature/account/setup/build.gradle.kts",
        "projects.feature.autodiscovery.demo",
        "SABLE_MAIL_AUTODISCOVERY_DEMO_DEPENDENCY",
    )

    for path in (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "domain/AutoDiscoveryMapper.kt",
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "domain/usecase/GetAutoDiscovery.kt",
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "ui/autodiscovery/AccountAutoDiscoveryViewModel.kt",
    ):
        reject_text(
            path,
            "DemoServerSettings",
            "SABLE_MAIL_AUTODISCOVERY_DEMO_SOURCE",
        )
        reject_text(
            path,
            "autodiscovery.demo",
            "SABLE_MAIL_AUTODISCOVERY_DEMO_SOURCE",
        )

    app_common_feature = (
        root
        / "app-common/src/main/kotlin/net/thunderbird/app/common/feature/"
        "AppCommonFeatureModule.kt"
    )
    reject_text(
        app_common_feature,
        "featureThundermailCommonModule",
        "SABLE_MAIL_TRANSITIVE_THUNDERMAIL_DI",
    )

    auto_content = (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "ui/autodiscovery/AccountAutoDiscoveryContent.kt"
    )
    for needle in (
        "ThundermailButtonPanel",
        "onThundermailClick",
        "onScanQrCodeClick",
        "thundermail_panel",
    ):
        reject_text(
            auto_content,
            needle,
            "SABLE_MAIL_ACCOUNT_SETUP_THUNDERMAIL_UI",
        )

    auto_screen = (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "ui/autodiscovery/AccountAutoDiscoveryScreen.kt"
    )
    reject_text(
        auto_screen,
        "onThundermailClick",
        "SABLE_MAIL_ACCOUNT_SETUP_THUNDERMAIL_CALLBACK",
    )
    reject_text(
        auto_screen,
        "onScanQrCodeClick",
        "SABLE_MAIL_ACCOUNT_SETUP_THUNDERMAIL_CALLBACK",
    )

    account_setup_nav = (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "navigation/AccountSetupNavHost.kt"
    )
    reject_text(
        account_setup_nav,
        "onThundermailClick",
        "SABLE_MAIL_ACCOUNT_SETUP_THUNDERMAIL_NAV",
    )
    reject_text(
        account_setup_nav,
        "onScanQrCodeClick",
        "SABLE_MAIL_ACCOUNT_SETUP_THUNDERMAIL_NAV",
    )

    onboarding_nav = (
        root
        / "feature/onboarding/main/src/main/kotlin/app/k9mail/feature/onboarding/main/"
        "navigation/OnboardingNavHost.kt"
    )
    require_text(
        onboarding_nav,
        "onStartClick = { navController.navigateToAccountSetup() }",
        "SABLE_MAIL_STANDARD_ACCOUNT_ENTRY",
    )
    reject_text(
        onboarding_nav,
        "onFinish(OnboardingRoute.Thundermail",
        "SABLE_MAIL_ONBOARDING_THUNDERMAIL_ROUTE",
    )

    for route_path in (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "navigation/AccountSetupRoute.kt",
        root
        / "feature/onboarding/main/src/main/kotlin/app/k9mail/feature/onboarding/main/"
        "navigation/OnboardingRoute.kt",
    ):
        reject_text(
            route_path,
            "Thundermail",
            "SABLE_MAIL_THUNDERMAIL_ROUTE_DECLARATION",
        )

    launcher_nav = (
        root
        / "feature/launcher/src/main/kotlin/app/k9mail/feature/launcher/navigation/"
        "FeatureLauncherNavHost.kt"
    )
    for needle in (
        "ThundermailNavigation",
        "ThundermailRoute",
        "registerThundermailNavigation",
    ):
        reject_text(
            launcher_nav,
            needle,
            "SABLE_MAIL_LAUNCHER_THUNDERMAIL_NAV",
        )

    print("SABLE_MAIL_TRANSITIVE_THUNDERMAIL=PASS_ABSENT")
    print("SABLE_MAIL_AUTODISCOVERY_DEMO=PASS_ABSENT")
    print("SABLE_MAIL_AUTODISCOVERY_DEMO_SOURCE=PASS_ABSENT")
    print("SABLE_MAIL_STANDARD_ACCOUNT_ENTRY=PASS")
    print("SABLE_MAIL_THUNDERMAIL_ROUTES=PASS_ABSENT")

    strings = app / "src/main/res/values/strings.xml"
    require_text(strings, ">Sable Mail</string>", "SABLE_MAIL_BRANDING")
    reject_text(strings, ">Thunderbird</string>", "SABLE_MAIL_BRANDING")
    print("SABLE_MAIL_BRANDING=PASS")

    feature_module = (
        app
        / "src/main/kotlin/net/thunderbird/android/feature/FeatureModule.kt"
    )
    reject_text(
        feature_module,
        "featureThundermailModule",
        "SABLE_MAIL_THUNDERMAIL_MODULE",
    )
    print("SABLE_MAIL_THUNDERMAIL=PASS_ABSENT")

    for variant in ("debug", "beta", "daily", "release"):
        feature_flags = (
            app
            / f"src/{variant}/kotlin/net/thunderbird/android/featureflag/"
            "TbFeatureFlagFactory.kt"
        )
        reject_text(
            feature_flags,
            "ThundermailFeatureFlags",
            "SABLE_MAIL_THUNDERMAIL_FEATURE_FLAG",
        )

    for variant in ("debug", "beta", "daily", "release"):
        oauth = (
            app
            / f"src/{variant}/kotlin/net/thunderbird/android/auth/"
            "TbOAuthConfigurationFactory.kt"
        )
        require_text(oauth, "emptyMap()", "SABLE_MAIL_OAUTH_DISABLED")
        reject_text(oauth, "clientId =", "SABLE_MAIL_INHERITED_OAUTH_CLIENT")
    print("SABLE_MAIL_INHERITED_OAUTH_CLIENTS=PASS_ABSENT")

    appearance = (
        app
        / "src/main/kotlin/net/thunderbird/android/SableMailAppearance.kt"
    )
    for needle in (
        "org.sableos.appearance",
        '"follow-system"',
        '"light"',
        '"dark"',
        '"green"',
        '"purple"',
        '"orange"',
        '"slate"',
        "resumedActivity.get()?.recreate()",
    ):
        require_text(
            appearance,
            needle,
            "SABLE_MAIL_GLOBAL_APPEARANCE_SOURCE",
        )

    provider = (
        app
        / "src/main/kotlin/net/thunderbird/android/provider/TbThemeProvider.kt"
    )
    require_text(
        provider,
        "SableMailThemeResources.main(context)",
        "SABLE_MAIL_GLOBAL_THEME_PROVIDER",
    )

    themes = app / "src/main/res/values/sable_mail_themes.xml"
    for accent in ("Blue", "Green", "Purple", "Orange", "Slate"):
        require_text(
            themes,
            f"Theme.SableMail.Light.{accent}",
            "SABLE_MAIL_THEME_SET",
        )
        require_text(
            themes,
            f"Theme.SableMail.Dark.{accent}",
            "SABLE_MAIL_THEME_SET",
        )
    reject_text(
        appearance,
        "org.sableos.start.appearance",
        "SABLE_MAIL_RETIRED_APPEARANCE_AUTHORITY",
    )
    print("SABLE_MAIL_GLOBAL_APPEARANCE_SOURCE=PASS")

    manifest = app / "src/main/AndroidManifest.xml"
    require_text(
        manifest,
        'android:icon="@drawable/ic_sable_mail"',
        "SABLE_MAIL_ICON",
    )
    require_text(
        manifest,
        'android:theme="@style/Theme.SableMail.Startup"',
        "SABLE_MAIL_STARTUP_THEME",
    )
    require_text(
        manifest,
        'android:authorities="org.sableos.appearance"',
        "SABLE_MAIL_APPEARANCE_PROVIDER_VISIBILITY",
    )
    reject_text(
        manifest,
        'android:authorities="org.sableos.start.appearance"',
        "SABLE_MAIL_RETIRED_APPEARANCE_AUTHORITY",
    )
    print("SABLE_MAIL_RETIRED_APPEARANCE_AUTHORITY=PASS_ABSENT")
    require_text(
        manifest,
        'android:authorities="org.sableos.mail.snapshot"',
        "SABLE_MAIL_SNAPSHOT_PROVIDER",
    )
    snapshot_provider = (
        app
        / "src/main/kotlin/net/thunderbird/android/SableMailSnapshotProvider.kt"
    )
    for needle in (
        "NotificationManager::class.java",
        "Notification.CATEGORY_EMAIL",
        "Notification.CATEGORY_MESSAGE",
        '"no mail alerts"',
        '" mail alerts"',
        "content://org.sableos.mail.snapshot/current",
        '"org.sableos.launcher"',
        '"org.sableos.hub"',
        "packages.intersect(authorized).isEmpty()",
        "Sable mail snapshot is restricted to SableLauncher and SableHub.",
        "SecurityException(",
        "Binder.getCallingUid()",
        "UnsupportedOperationException",
    ):
        require_text(
            snapshot_provider,
            needle,
            "SABLE_MAIL_PRIVACY_SAFE_LIVE_SNAPSHOT",
        )
    reject_text(
        snapshot_provider,
        'if ("com.android.launcher3" !in packages)',
        "SABLE_MAIL_RETIRED_LAUNCHER3_SNAPSHOT_CALLER",
    )
    print("SABLE_MAIL_RETIRED_LAUNCHER3_SNAPSHOT_CALLER=PASS_ABSENT")
    print("SABLE_MAIL_PRIVACY_SAFE_LIVE_SNAPSHOT=PASS_NOTIFICATION_COUNT_ONLY")
    print("SABLE_MAIL_HUB_SNAPSHOT_CALLER=PASS_BOUNDED_READ_ONLY")
    app_source = (
        app
        / "src/main/kotlin/net/thunderbird/android/ThunderbirdApp.kt"
    )
    require_text(
        app_source,
        "uploadEnabled = false",
        "SABLE_MAIL_TELEMETRY_UPLOAD",
    )
    print("SABLE_MAIL_TELEMETRY_UPLOAD=PASS_DISABLED")

    message_container = (
        root
        / "legacy/ui/legacy/src/main/java/com/fsck/k9/ui/messageview/MessageContainerView.kt"
    )
    require_text(
        message_container,
        "messageContentView.blockNetworkData(!enable)",
        "SABLE_MAIL_REMOTE_CONTENT_PRIVACY",
    )
    message_webview = (
        root
        / "legacy/ui/legacy/src/main/java/com/fsck/k9/view/MessageWebView.kt"
    )
    require_text(
        message_webview,
        "settings.blockNetworkLoads = shouldBlockNetworkData",
        "SABLE_MAIL_REMOTE_CONTENT_PRIVACY",
    )
    print("SABLE_MAIL_REMOTE_CONTENT_PRIVACY=PASS_UPSTREAM_PRESERVED")
    print("SABLE_MAIL_PRODUCT_BRANDING_SOURCE=PASS")

    if len(sys.argv) == 3:
        apk = pathlib.Path(sys.argv[2]).resolve()
        if not apk.is_file():
            fail("SABLE_MAIL_APK", f"path={apk}")

        apkanalyzer = (
            os.environ.get("APKANALYZER")
            or os.environ.get("SABLE_APKANALYZER")
            or "apkanalyzer"
        )
        apkanalyzer_path = pathlib.Path(apkanalyzer)
        if "/" in apkanalyzer and not apkanalyzer_path.is_file():
            fail(
                "SABLE_MAIL_APKANALYZER",
                f"path={apkanalyzer_path}",
            )

        package = subprocess.check_output(
            [apkanalyzer, "manifest", "application-id", str(apk)],
            text=True,
        ).strip()
        debuggable = subprocess.check_output(
            [apkanalyzer, "manifest", "debuggable", str(apk)],
            text=True,
        ).strip().lower()
        if debuggable != "false":
            fail(
                "SABLE_MAIL_APK_DEBUGGABLE",
                f"expected=false actual={debuggable}",
            )
        if package != EXPECTED_PACKAGE:
            fail(
                "SABLE_MAIL_APK_PACKAGE",
                f"expected={EXPECTED_PACKAGE} actual={package}",
            )

        digest = subprocess.check_output(
            ["sha256sum", str(apk)],
            text=True,
        ).split()[0]
        print(f"SABLE_MAIL_APK_PACKAGE=PASS value={package}")
        print("SABLE_MAIL_APK_DEBUGGABLE=PASS_FALSE")
        print(f"SABLE_MAIL_APK_SHA256={digest}")

    print("SABLE_MAIL_POLICY=PASS")


if __name__ == "__main__":
    main()
