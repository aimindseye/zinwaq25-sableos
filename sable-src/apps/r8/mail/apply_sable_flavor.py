#!/usr/bin/env python3
from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

EXPECTED_COMMIT = "c733317d933c22bcd790a4790306f30ccf2026b8"
SABLE_APPLICATION_ID = "org.sableos.mail"
SABLE_LABEL = "Sable Mail"
THUNDERMAIL_API_DEPENDENCY = "    implementation(projects.feature.thundermail.api)\n"
DEMO_SERVER_SETTINGS_IMPORT = "import app.k9mail.autodiscovery.demo.DemoServerSettings\n"

# Gmail sign-in uses the owner's own Google Cloud OAuth client (Android type, package
# org.sableos.mail, custom URI scheme enabled), passed by the build host and never stored in
# the repository. Without it Sable Mail ships with no OAuth providers (app password only).
GOOGLE_CLIENT_ID_ENV = "SABLE_MAIL_GOOGLE_CLIENT_ID"
GOOGLE_CLIENT_ID_PATTERN = re.compile(r"[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com")
# Google Cloud projects of the upstream Thunderbird/K-9 Gmail clients (registered to other
# packages and signing keys).
INHERITED_GOOGLE_PROJECTS = ("560629489500-", "262622259280-")


def google_client_id() -> str | None:
    value = os.environ.get(GOOGLE_CLIENT_ID_ENV, "").strip()
    if not value:
        return None
    if not GOOGLE_CLIENT_ID_PATTERN.fullmatch(value):
        fail(f"{GOOGLE_CLIENT_ID_ENV} is not a Google OAuth client id (NNN-xxx.apps.googleusercontent.com)")
    if value.startswith(INHERITED_GOOGLE_PROJECTS):
        fail(f"{GOOGLE_CLIENT_ID_ENV} is an inherited Thunderbird/K-9 client; register your own")
    return value


def oauth_factory_source(client_id: str | None) -> str:
    if client_id is None:
        return '''package net.thunderbird.android.auth

import net.thunderbird.core.common.oauth.OAuthConfiguration
import net.thunderbird.core.common.oauth.OAuthConfigurationFactory

/**
 * Sable Mail deliberately ships without inherited Thunderbird OAuth client IDs.
 *
 * Generic IMAP/SMTP remains available. Gmail/Microsoft OAuth must stay hidden
 * until Sable-owned provider registrations and redirect URIs are qualified.
 */
class TbOAuthConfigurationFactory : OAuthConfigurationFactory {
    override fun createConfigurations(): Map<List<String>, OAuthConfiguration> =
        emptyMap()
}
'''
    return f'''package net.thunderbird.android.auth

import net.thunderbird.android.BuildConfig
import net.thunderbird.core.common.oauth.OAuthConfiguration
import net.thunderbird.core.common.oauth.OAuthConfigurationFactory

/**
 * Sable Mail ships no inherited Thunderbird OAuth client IDs. Gmail sign-in uses the Sable
 * owner's own Google OAuth client ({GOOGLE_CLIENT_ID_ENV} at build time). Every other
 * provider stays on IMAP/SMTP with a password.
 */
class TbOAuthConfigurationFactory : OAuthConfigurationFactory {{
    override fun createConfigurations(): Map<List<String>, OAuthConfiguration> =
        mapOf(
            listOf(
                "imap.gmail.com",
                "imap.googlemail.com",
                "smtp.gmail.com",
                "smtp.googlemail.com",
            ) to OAuthConfiguration(
                clientId = "{client_id}",
                scopes = listOf("https://mail.google.com/"),
                authorizationEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
                tokenEndpoint = "https://oauth2.googleapis.com/token",
                redirectUri = "${{BuildConfig.APPLICATION_ID}}:/oauth2redirect",
            ),
        )
}}
'''

# The upstream checkout being flavored; every file read or written must resolve inside it.
_checkout: Path | None = None


def fail(message: str) -> None:
    raise SystemExit(message)


def in_checkout(path: Path) -> Path:
    """path, resolved, refused unless it stays inside the checkout (no ../ or symlink escape)."""
    resolved = path.resolve()
    if _checkout is None or not resolved.is_relative_to(_checkout):
        fail(f"SABLE_MAIL_PATH=FAIL_OUTSIDE_CHECKOUT path={path}")
    return resolved


def replace_once(path: Path, old: str, new: str) -> None:
    path = in_checkout(path)
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        fail(f"SABLE_MAIL_PATCH_ANCHOR=FAIL path={path} count={count} anchor={old!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def remove_once(path: Path, old: str) -> None:
    replace_once(path, old, "")


def remove_exact_count(path: Path, old: str, expected: int) -> None:
    path = in_checkout(path)
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        fail(
            "SABLE_MAIL_PATCH_ANCHOR=FAIL "
            f"path={path} count={count} expected={expected} anchor={old!r}"
        )
    # The checkout path is the operator's own command-line argument, and in_checkout() has already refused any
    # path that resolves outside it, so this write cannot leave the checkout.
    path.write_text(text.replace(old, ""), encoding="utf-8")  # NOSONAR(S2083)


def write(path: Path, content: str) -> None:
    path = in_checkout(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def copy_icon_resources(dest_res: Path) -> None:
    """Copy the generated launcher icon (sable-src/tools/gen_first_party_icons.py, row mail) into the app.

    res/ next to this script holds the legacy vector (drawable/ic_sable_mail.xml, also the in-app logo and the
    pre-26 icon via mipmap/), the adaptive layers and mipmap-anydpi-v26/ic_sable_mail{,_round}.xml.
    """
    source = Path(__file__).resolve().parent / "res"
    files = sorted(source.rglob("*.xml"))
    if not any(f.name == "ic_sable_mail.xml" and f.parent.name == "mipmap-anydpi-v26" for f in files):
        fail(f"SABLE_MAIL_ICON=FAIL_MISSING_ADAPTIVE path={source}")
    for f in files:
        write(dest_res / f.relative_to(source), f.read_text(encoding="utf-8"))


SABLE_KEYMAP_SOURCES = (
    "main/java/com/fsck/k9/activity/SableMailKeymap.kt",
    "test/java/com/fsck/k9/activity/SableMailKeymapTest.kt",
)


def apply_keyboard_model(root: Path) -> None:
    """Sable Mail keyboard model (MAIL_CALENDAR_PRODUCTIVITY_UX.md, shared keyboard contract).

    The pure keymap and its unit test (src/ next to this script) go into the legacy UI module;
    MessageHomeActivity routes every key-down through it instead of the upstream K-9 hotkeys,
    MessageCompose gains Ctrl+Enter / Alt+Enter send and Esc cancel, and delete asks for
    confirmation by default. On a square keyboard screen the split pane is forced and carries a
    Bottom Command Dock; the reader collapses quoted text until E expands it (SableMailQuotes).
    """
    source = Path(__file__).resolve().parent / "src"
    module = root / "legacy/ui/legacy/src"
    for relative in SABLE_KEYMAP_SOURCES:
        write(module / relative, (source / relative).read_text(encoding="utf-8"))

    home = module / "main/java/com/fsck/k9/activity/MessageHomeActivity.kt"
    replace_once(
        home,
        "import android.widget.ProgressBar\n",
        "import android.graphics.Rect\n"
        "import android.view.ViewGroup\n"
        "import android.widget.EditText\n"
        "import android.widget.ProgressBar\n"
        "import android.widget.ScrollView\n"
        "import androidx.recyclerview.widget.LinearLayoutManager\n"
        "import androidx.recyclerview.widget.RecyclerView\n",
    )
    replace_once(
        home,
        """        if (event.action == KeyEvent.ACTION_DOWN && isSearchViewCollapsed()) {
            eventHandled = onCustomKeyDown(event)
        }
""",
        """        if (event.action == KeyEvent.ACTION_DOWN) {
            eventHandled = onSableKeyDown(event)
        }
""",
    )
    replace_once(
        home,
        "    private fun onDeleteHotKey() {\n",
        '''    /**
     * Sable Mail keyboard model. [SableMailKeymap] owns every single-key command; only volume-key
     * and Left/Right reader navigation still reach the upstream [onCustomKeyDown].
     * TEXT_INPUT_ALWAYS_WINS: while the search field (or any text field) has focus only Esc is taken.
     */
    private fun onSableKeyDown(event: KeyEvent): Boolean {
        val press = SableMailKeyPress.from(event)
        val textInputActive = !isSearchViewCollapsed() || currentFocus is EditText
        val command = SableMailKeymap.command(press, textInputActive)
        return when {
            command != null -> runSableMailCommand(command)
            SableMailKeymap.upstreamFallbackAllowed(press, textInputActive) -> onCustomKeyDown(event)
            else -> false
        }
    }

    private val hasOpenSableMessage: Boolean
        get() = messageViewContainerFragment != null && displayMode != DisplayMode.MESSAGE_LIST

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun runSableMailCommand(command: SableMailCommand): Boolean {
        val list = messageListFragment ?: return false
        val message = messageViewContainerFragment.takeIf { hasOpenSableMessage }
        when (command) {
            SableMailCommand.COMPOSE -> list.onCompose()
            SableMailCommand.TOP -> {
                if (displayMode == DisplayMode.MESSAGE_VIEW) showMessageList()
                moveSableListFocus(SableListMove.TOP)
            }
            SableMailCommand.NEXT -> {
                if (message != null) showNextMessage() else moveSableListFocus(SableListMove.NEXT)
            }
            SableMailCommand.PREVIOUS -> {
                if (message != null) showPreviousMessage() else moveSableListFocus(SableListMove.PREVIOUS)
            }
            SableMailCommand.REPLY -> message?.onReply()
            SableMailCommand.REPLY_ALL -> message?.onReplyAll()
            SableMailCommand.FORWARD -> message?.onForward()
            SableMailCommand.EXPAND_QUOTE -> if (message != null) toggleSableQuotes()
            SableMailCommand.ATTACHMENTS -> if (message != null) focusSableAttachments()
            SableMailCommand.DELETE -> onDeleteHotKey()
            SableMailCommand.SEARCH -> {
                if (displayMode == DisplayMode.MESSAGE_VIEW) showMessageList()
                list.expandSearchView()
            }
            SableMailCommand.VIEW_MODE -> list.onCycleSort()
            SableMailCommand.PAGE_DOWN -> pageSable(down = true)
            SableMailCommand.PAGE_UP -> pageSable(down = false)
            SableMailCommand.BACK -> onBackPressedDispatcher.onBackPressed()
            SableMailCommand.ARCHIVE -> if (message != null) message.onArchive() else list.onArchive()
            SableMailCommand.SELECT -> list.toggleMessageSelect()
            SableMailCommand.TOGGLE_FLAG -> if (message != null) message.onToggleFlagged() else list.onToggleFlagged()
            SableMailCommand.MOVE -> if (message != null) message.onMove() else list.onMove()
            SableMailCommand.COPY -> if (message != null) message.onCopy() else list.onCopy()
            SableMailCommand.TOGGLE_READ -> if (message != null) message.onToggleRead() else list.onToggleRead()
            SableMailCommand.REVERSE_SORT -> list.onReverseSort()
        }
        return true
    }

    /** Moves keyboard focus in the message list: next/previous row, a page, or the newest message. */
    private fun moveSableListFocus(move: SableListMove) {
        val recycler = findViewById<RecyclerView>(R.id.message_list) ?: return
        val layoutManager = recycler.layoutManager as? LinearLayoutManager ?: return
        val focused = recycler.focusedChild?.let { recycler.getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION
        val target =
            SableMailKeymap.listTarget(
                move = move,
                focused = focused,
                firstVisible = layoutManager.findFirstVisibleItemPosition(),
                lastVisible = layoutManager.findLastVisibleItemPosition(),
                itemCount = layoutManager.itemCount,
            ) ?: return
        layoutManager.scrollToPosition(target)
        recycler.post { recycler.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus() }
    }

    /** Space / Shift+Space: page the open message body, else page the message list. */
    private fun pageSable(down: Boolean) {
        val body = if (hasOpenSableMessage) findVisibleMessageBody(messageViewContainerFragment?.view) else null
        if (body != null) {
            body.pageScroll(if (down) View.FOCUS_DOWN else View.FOCUS_UP)
        } else {
            moveSableListFocus(if (down) SableListMove.PAGE_DOWN else SableListMove.PAGE_UP)
        }
    }

    /** E: show or collapse the quoted text of the open message (MAIL_QUOTED_TEXT_COLLAPSED_BY_DEFAULT). */
    private fun toggleSableQuotes() {
        val body = findVisibleMessageBody(messageViewContainerFragment?.view) ?: return
        findSableView(body) { it is com.fsck.k9.view.MessageWebView }
            ?.let { (it as com.fsck.k9.view.MessageWebView).toggleSableQuotes() }
    }

    /** A: move focus to the first attachment of the open message; the reader scrolls to it. */
    private fun focusSableAttachments() {
        val body = findVisibleMessageBody(messageViewContainerFragment?.view) ?: return
        val attachments = findSableView(body) { it.id == R.id.attachments_container } as? ViewGroup ?: return
        val first = (0 until attachments.childCount).map { attachments.getChildAt(it) }.firstOrNull { it.isShown }
        if (first == null) {
            android.widget.Toast.makeText(this, "No attachments", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (!first.requestFocus()) {
            body.smoothScrollTo(0, attachments.top)
        }
    }

    private fun findSableView(
        root: View,
        match: (View) -> Boolean,
    ): View? =
        when {
            match(root) -> root
            root is ViewGroup -> (0 until root.childCount).firstNotNullOfOrNull { findSableView(root.getChildAt(it), match) }
            else -> null
        }

    /** The on-screen message body scroller; the view pager also keeps neighbouring messages laid out. */
    private fun findVisibleMessageBody(root: View?): ScrollView? =
        when {
            root == null || !root.isShown -> null
            root is ScrollView && root.id == R.id.message_scrollview && root.getGlobalVisibleRect(Rect()) -> root
            root is ViewGroup ->
                (0 until root.childCount).firstNotNullOfOrNull { findVisibleMessageBody(root.getChildAt(it)) }
            else -> null
        }

    private fun onDeleteHotKey() {
''',
    )

    # MAIL_SPLIT_PANE: forced on a square keyboard screen (SableMailLayout), with the Bottom Command Dock.
    split_layout = module / "main/res/layout/split_message_list.xml"
    replace_once(
        home,
        """    private fun useSplitView(): Boolean {
        val splitViewMode = generalSettingsManager.getConfig().display.coreSettings.splitViewMode
""",
        """    private fun useSplitView(): Boolean {
        val sableConfig = resources.configuration
        if (SableMailLayout.forceSplitView(sableConfig.screenWidthDp, sableConfig.screenHeightDp)) return true
        val splitViewMode = generalSettingsManager.getConfig().display.coreSettings.splitViewMode
""",
    )
    replace_once(
        home,
        """        if (useSplitView()) {
            setLayout(R.layout.split_message_list)
        } else {""",
        """        if (useSplitView()) {
            setLayout(R.layout.split_message_list)
            setUpSableMailDock()
        } else {""",
    )
    replace_once(
        home,
        "    private fun useSplitView(): Boolean {\n",
        """    /** Bottom Command Dock (MAIL_BOTTOM_COMMAND_DOCK): the prompt opens search; the hints name real keys. */
    private fun setUpSableMailDock() {
        findViewById<android.widget.TextView>(R.id.sable_mail_dock_prompt)?.apply {
            text = SableMailLayout.DOCK_PROMPT
            setOnClickListener {
                if (displayMode == DisplayMode.MESSAGE_VIEW) showMessageList()
                messageListFragment?.expandSearchView()
            }
        }
        findViewById<android.widget.TextView>(R.id.sable_mail_dock_hint)?.text =
            SableMailLayout.DOCK_LIST_HINT + "\\n" + SableMailLayout.DOCK_READER_HINT
    }

    private fun useSplitView(): Boolean {
""",
    )
    replace_once(
        split_layout,
        """            android:layout_below="@id/app_bar_layout"
            >
""",
        """            android:layout_below="@id/app_bar_layout"
            android:layout_above="@+id/sable_mail_dock"
            >
""",
    )
    replace_once(
        split_layout,
        """        </FrameLayout>

    </RelativeLayout>
""",
        """        </FrameLayout>

        <LinearLayout
            android:id="@+id/sable_mail_dock"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_alignParentBottom="true"
            android:background="?attr/colorSurfaceContainer"
            android:orientation="vertical"
            android:paddingHorizontal="12dp"
            android:paddingVertical="6dp"
            >

            <TextView
                android:id="@+id/sable_mail_dock_prompt"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:clickable="true"
                android:focusable="true"
                android:foreground="@drawable/sable_mail_list_item_foreground"
                android:paddingVertical="6dp"
                android:textAppearance="?attr/textAppearanceBodyLarge"
                android:textColor="?attr/colorPrimary"
                />

            <TextView
                android:id="@+id/sable_mail_dock_hint"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:maxLines="2"
                android:textAppearance="?attr/textAppearanceLabelMedium"
                android:textColor="?attr/colorOnSurfaceVariant"
                />

        </LinearLayout>

    </RelativeLayout>
""",
    )

    # MAIL_QUOTED_TEXT_COLLAPSED_BY_DEFAULT: the reader WebView adds the collapse style until E.
    web = module / "main/java/com/fsck/k9/view/MessageWebView.kt"
    replace_once(
        web,
        """    private fun setHtmlContent(htmlText: String) {
        loadDataWithBaseURL("about:blank", htmlText, "text/html", "utf-8", null)
        resumeTimers()
    }
""",
        """    private var sableHtml: String? = null
    private var sableQuotesExpanded = false

    private fun setHtmlContent(htmlText: String) {
        sableHtml = htmlText
        sableQuotesExpanded = false
        loadDataWithBaseURL(
            "about:blank",
            com.fsck.k9.activity.SableMailQuotes.html(htmlText, expanded = false),
            "text/html",
            "utf-8",
            null,
        )
        resumeTimers()
    }

    /** Sable Mail E key: show or collapse the quoted text; the same content is loaded again. */
    fun toggleSableQuotes() {
        val html = sableHtml ?: return
        sableQuotesExpanded = !sableQuotesExpanded
        loadDataWithBaseURL(
            "about:blank",
            com.fsck.k9.activity.SableMailQuotes.html(html, sableQuotesExpanded),
            "text/html",
            "utf-8",
            null,
        )
    }
""",
    )

    compose = module / "main/java/com/fsck/k9/activity/MessageCompose.java"
    replace_once(
        compose,
        "    @Override\n    public boolean onOptionsItemSelected(MenuItem item) {\n",
        """    /** Sable Mail: Ctrl+Enter (Alt+Enter on the Q25, which has no Ctrl) sends; Esc cancels like Back. */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            SableComposeCommand command = SableMailKeymap.INSTANCE.composeCommand(SableMailKeyPress.Companion.from(event));
            if (command == SableComposeCommand.SEND) {
                checkToSendMessage();
                return true;
            }
            if (command == SableComposeCommand.CANCEL) {
                getOnBackPressedDispatcher().onBackPressed();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
""",
    )

    # VISIBLE_KEYBOARD_FOCUS: a 2 dp accent ring on the focused message row, over the upstream ripple.
    res = module / "main/res"
    write(
        res / "drawable/sable_mail_list_item_foreground.xml",
        """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="?attr/selectableItemBackground" />
    <item>
        <selector>
            <item android:state_focused="true">
                <shape android:shape="rectangle">
                    <stroke
                        android:width="2dp"
                        android:color="?attr/colorPrimary" />
                </shape>
            </item>
        </selector>
    </item>
</layer-list>
""",
    )
    replace_once(
        res / "layout/message_list_item.xml",
        'android:foreground="?attr/selectableItemBackground"',
        'android:foreground="@drawable/sable_mail_list_item_foreground"',
    )

    replace_once(
        root / "core/preference/api/src/commonMain/kotlin/net/thunderbird/core/preference/interaction/InteractionSettings.kt",
        "const val INTERACTION_SETTINGS_DEFAULT_CONFIRM_DELETE = false",
        "const val INTERACTION_SETTINGS_DEFAULT_CONFIRM_DELETE = true",
    )


def git_head(root: Path) -> str:
    return subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "HEAD"],
        text=True,
    ).strip()


def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: apply_sable_flavor.py <thunderbird-android-root>")

    global _checkout
    root = Path(sys.argv[1]).resolve()
    if not (root / ".git").exists():
        fail(f"SABLE_MAIL_UPSTREAM=FAIL_NOT_GIT path={root}")
    _checkout = root

    actual = git_head(root)
    if actual != EXPECTED_COMMIT:
        fail(
            "SABLE_MAIL_UPSTREAM=FAIL_COMMIT "
            f"expected={EXPECTED_COMMIT} actual={actual}"
        )

    app = root / "app-thunderbird"
    build = app / "build.gradle.kts"

    replace_once(
        build,
        'applicationId = "net.thunderbird.android"',
        f'applicationId = "{SABLE_APPLICATION_ID}"',
    )
    replace_once(
        build,
        'testApplicationId = "net.thunderbird.android.tests"',
        f'testApplicationId = "{SABLE_APPLICATION_ID}.tests"',
    )
    replace_once(
        build,
        'versionName = "23.0"',
        'versionName = "23.0-sable-r8"',
    )
    replace_once(
        build,
        'signingConfig = signingConfigs.getByType(SigningType.TB_RELEASE)',
        'signingConfig = signingConfigs.getByName("debug")',
    )
    replace_once(
        build,
        'isMinifyEnabled = !isCI.get()',
        'isMinifyEnabled = true',
    )
    replace_once(
        build,
        'isShrinkResources = !isCI.get()',
        'isShrinkResources = true',
    )
    replace_once(
        build,
        r'buildConfigField("String", "CLIENT_INFO_APP_NAME", "\"Thunderbird for Android\"")',
        r'buildConfigField("String", "CLIENT_INFO_APP_NAME", "\"Sable Mail\"")',
    )
    replace_once(
        build,
        '"fossImplementation"(projects.feature.funding.link)',
        '"fossImplementation"(projects.feature.funding.noop)',
    )
    replace_once(
        build,
        "    implementation(projects.feature.onboarding.migration.thunderbird)\n",
        "    implementation(projects.feature.onboarding.migration.noop)\n",
    )
    replace_once(
        build,
        "    implementation(projects.feature.migration.launcher.thunderbird)\n",
        "    implementation(projects.feature.migration.launcher.noop)\n",
    )

    for line in (
        THUNDERMAIL_API_DEPENDENCY,
        "    implementation(projects.feature.thundermail.thunderbird)\n",
        "    testImplementation(projects.feature.thundermail.internal.common)\n",
    ):
        remove_once(build, line)

    app_common_build = root / "app-common/build.gradle.kts"
    remove_once(
        app_common_build,
        "    implementation(projects.feature.thundermail.internal.common)\n",
    )

    app_common_feature = (
        root
        / "app-common/src/main/kotlin/net/thunderbird/app/common/feature/"
        "AppCommonFeatureModule.kt"
    )
    remove_once(
        app_common_feature,
        "import net.thunderbird.feature.thundermail.internal.common.inject.featureThundermailCommonModule\n",
    )
    remove_once(
        app_common_feature,
        "    includes(featureThundermailCommonModule)\n",
    )

    account_setup_build = root / "feature/account/setup/build.gradle.kts"
    remove_once(
        account_setup_build,
        "    implementation(projects.feature.autodiscovery.demo)\n",
    )
    remove_exact_count(
        account_setup_build,
        THUNDERMAIL_API_DEPENDENCY,
        2,
    )

    auto_mapper = (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "domain/AutoDiscoveryMapper.kt"
    )
    remove_once(
        auto_mapper,
        DEMO_SERVER_SETTINGS_IMPORT,
    )
    remove_exact_count(
        auto_mapper,
        "        is DemoServerSettings -> this.serverSettings\n",
        2,
    )

    get_auto_discovery = (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "domain/usecase/GetAutoDiscovery.kt"
    )
    remove_once(
        get_auto_discovery,
        DEMO_SERVER_SETTINGS_IMPORT,
    )
    replace_once(
        get_auto_discovery,
        '''        return if (result is AutoDiscoveryResult.Settings) {
            if (result.incomingServerSettings is DemoServerSettings) {
                return result
            } else {
                validateOAuthSupport(result)
            }
        } else {
            result
        }
''',
        '''        return if (result is AutoDiscoveryResult.Settings) {
            validateOAuthSupport(result)
        } else {
            result
        }
''',
    )

    auto_view_model = (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "ui/autodiscovery/AccountAutoDiscoveryViewModel.kt"
    )
    remove_once(
        auto_view_model,
        DEMO_SERVER_SETTINGS_IMPORT,
    )
    remove_once(
        auto_view_model,
        '''        if (settings.incomingServerSettings is DemoServerSettings) {
            updateState {
                it.copy(
                    isLoading = false,
                    autoDiscoverySettings = settings,
                    configStep = ConfigStep.PASSWORD,
                    isNextButtonVisible = true,
                )
            }
            return
        }

''',
    )

    launcher_build = root / "feature/launcher/build.gradle.kts"
    remove_once(
        launcher_build,
        THUNDERMAIL_API_DEPENDENCY,
    )

    onboarding_build = root / "feature/onboarding/main/build.gradle.kts"
    remove_once(
        onboarding_build,
        THUNDERMAIL_API_DEPENDENCY,
    )

    auto_content = (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "ui/autodiscovery/AccountAutoDiscoveryContent.kt"
    )
    remove_once(auto_content, "import androidx.compose.animation.AnimatedVisibility\n")
    remove_once(
        auto_content,
        "import net.thunderbird.feature.thundermail.ui.component.ThundermailButtonPanel\n",
    )
    remove_exact_count(auto_content, "    onThundermailClick: () -> Unit,\n", 3)
    remove_exact_count(auto_content, "    onScanQrCodeClick: () -> Unit,\n", 3)
    remove_once(
        auto_content,
        '''        AnimatedVisibility(state.emailAddress.value.isBlank()) {
            ThundermailButtonPanel(
                onThundermailClick = onThundermailClick,
                onScanQrCodeClick = onScanQrCodeClick,
                modifier = Modifier
                    .testTag("thundermail_panel")
                    .padding(bottom = BoltTheme.spacings.quadruple),
            )
        }

''',
    )
    remove_once(
        auto_content,
        "                    onThundermailClick = onThundermailClick,\n",
    )
    remove_once(
        auto_content,
        "                    onScanQrCodeClick = onScanQrCodeClick,\n",
    )
    remove_once(
        auto_content,
        "                onThundermailClick = onThundermailClick,\n",
    )
    remove_once(
        auto_content,
        "                onScanQrCodeClick = onScanQrCodeClick,\n",
    )

    auto_screen = (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "ui/autodiscovery/AccountAutoDiscoveryScreen.kt"
    )
    remove_once(auto_screen, "    onThundermailClick: () -> Unit,\n")
    remove_once(auto_screen, "    onScanQrCodeClick: () -> Unit,\n")
    remove_once(
        auto_screen,
        "            onThundermailClick = onThundermailClick,\n",
    )
    remove_once(
        auto_screen,
        "            onScanQrCodeClick = onScanQrCodeClick,\n",
    )

    # The debug-only previews call the same composables; they compile in the debug unit-test build.
    previews = (
        root
        / "feature/account/setup/src/debug/kotlin/app/k9mail/feature/account/setup/"
        "ui/autodiscovery"
    )
    for preview, count in (
        ("AccountAutoDiscoveryContentPreview.kt", 6),
        ("AccountAutoDiscoveryScreenPreview.kt", 1),
    ):
        remove_exact_count(previews / preview, "            onThundermailClick = {},\n", count)
        remove_exact_count(previews / preview, "            onScanQrCodeClick = {},\n", count)

    account_setup_nav = (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "navigation/AccountSetupNavHost.kt"
    )
    remove_once(
        account_setup_nav,
        "                onThundermailClick = { onFinish(AccountSetupRoute.ThundermailSignIn) },\n",
    )
    remove_once(
        account_setup_nav,
        "                onScanQrCodeClick = { onFinish(AccountSetupRoute.ThundermailScanQrCode) },\n",
    )

    account_setup_route = (
        root
        / "feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/"
        "navigation/AccountSetupRoute.kt"
    )
    remove_once(
        account_setup_route,
        '''    data object ThundermailSignIn : AccountSetupRoute {
        override val basePath: String = ACCOUNT_SETUP_BASE_PATH

        override fun route(): String = "$basePath/thundermail-sign-in"
    }

''',
    )
    remove_once(
        account_setup_route,
        '''    data object ThundermailScanQrCode : AccountSetupRoute {
        override val basePath: String = ACCOUNT_SETUP_BASE_PATH

        override fun route(): String = "$basePath/thundermail-scan-qr-code"
    }

''',
    )

    onboarding_nav = (
        root
        / "feature/onboarding/main/src/main/kotlin/app/k9mail/feature/onboarding/main/"
        "navigation/OnboardingNavHost.kt"
    )
    replace_once(
        onboarding_nav,
        "                onStartClick = { onFinish(OnboardingRoute.ThundermailAddAccount) },",
        "                onStartClick = { navController.navigateToAccountSetup() },",
    )
    replace_once(
        onboarding_nav,
        "                onThundermailClick = { onFinish(OnboardingRoute.ThundermailSignIn) },",
        "                onThundermailClick = { navController.navigateToAccountSetup() },",
    )

    replace_once(
        onboarding_nav,
        '''                    when (route) {
                        is AccountSetupRoute.AccountSetup -> {
                            navController.navigateToPermissions()
                        }

                        AccountSetupRoute.ThundermailScanQrCode -> onFinish(OnboardingRoute.ThundermailScanQrCode)

                        AccountSetupRoute.ThundermailSignIn -> onFinish(OnboardingRoute.ThundermailSignIn)
                    }
''',
        '''                    when (route) {
                        is AccountSetupRoute.AccountSetup -> {
                            navController.navigateToPermissions()
                        }
                    }
''',
    )

    onboarding_route = (
        root
        / "feature/onboarding/main/src/main/kotlin/app/k9mail/feature/onboarding/main/"
        "navigation/OnboardingRoute.kt"
    )
    remove_once(
        onboarding_route,
        '''    data object ThundermailSignIn : OnboardingRoute {
        override val basePath: String = ONBOARDING_BASE_PATH

        override fun route(): String = "$basePath/thundermail-sign-in"
    }

''',
    )
    remove_once(
        onboarding_route,
        '''    data object ThundermailScanQrCode : OnboardingRoute {
        override val basePath: String = ONBOARDING_BASE_PATH

        override fun route(): String = "$basePath/thundermail-scan-qr-code"
    }

''',
    )
    remove_once(
        onboarding_route,
        '''    data object ThundermailAddAccount : OnboardingRoute {
        override val basePath: String = ONBOARDING_BASE_PATH

        override fun route(): String = "${ThundermailScanQrCode.basePath}/thundermail-add-account"
    }

''',
    )

    launcher_nav = (
        root
        / "feature/launcher/src/main/kotlin/app/k9mail/feature/launcher/navigation/"
        "FeatureLauncherNavHost.kt"
    )
    remove_once(launcher_nav, "import androidx.navigation.NavGraphBuilder\n")
    remove_once(
        launcher_nav,
        "import net.thunderbird.feature.thundermail.navigation.ThundermailNavigation\n",
    )
    remove_once(
        launcher_nav,
        "import net.thunderbird.feature.thundermail.navigation.ThundermailRoute\n",
    )
    remove_once(
        launcher_nav,
        "    thundermailNavigation: ThundermailNavigation = koinInject(),\n",
    )
    replace_once(
        launcher_nav,
        '''                when (it) {
                    is OnboardingRoute.Onboarding -> {
                        messageListLauncher.launch(it.accountId)
                        activity.finish()
                    }

                    is OnboardingRoute.ThundermailScanQrCode ->
                        navController.navigate(ThundermailRoute.ScanQrCode)

                    is OnboardingRoute.ThundermailSignIn ->
                        navController.navigate(ThundermailRoute.SignInWithThundermail)

                    is OnboardingRoute.ThundermailAddAccount ->
                        navController.navigate(ThundermailRoute.AddAccount)
                }
''',
        '''                when (it) {
                    is OnboardingRoute.Onboarding -> {
                        messageListLauncher.launch(it.accountId)
                        activity.finish()
                    }

                    else -> Unit
                }
''',
    )
    remove_once(
        launcher_nav,
        '''        registerThundermailNavigation(
            thundermailNavigation = thundermailNavigation,
            navController = navController,
            messageListLauncher = messageListLauncher,
            activity = activity,
        )

''',
    )
    replace_once(
        launcher_nav,
        '''                when (it) {
                    is AccountSetupRoute.AccountSetup -> {
                        messageListLauncher.launch(it.accountId)
                    }

                    is AccountSetupRoute.ThundermailScanQrCode ->
                        navController.navigate(ThundermailRoute.ScanQrCode)

                    is AccountSetupRoute.ThundermailSignIn ->
                        navController.navigate(ThundermailRoute.SignInWithThundermail)
                }
''',
        '''                when (it) {
                    is AccountSetupRoute.AccountSetup -> {
                        messageListLauncher.launch(it.accountId)
                    }

                    else -> Unit
                }
''',
    )
    launcher_text = in_checkout(launcher_nav).read_text(encoding="utf-8")
    private_marker = "\nprivate fun NavGraphBuilder.registerThundermailNavigation("
    private_index = launcher_text.find(private_marker)
    if private_index < 0:
        fail("SABLE_MAIL_PATCH_ANCHOR=FAIL launcher thundermail navigation function")
    in_checkout(launcher_nav).write_text(launcher_text[:private_index] + "\n", encoding="utf-8")

    for variant in ("debug", "beta", "daily", "release"):
        feature_flags = (
            app
            / f"src/{variant}/kotlin/net/thunderbird/android/featureflag/"
            "TbFeatureFlagFactory.kt"
        )
        remove_once(
            feature_flags,
            "import net.thunderbird.feature.thundermail.featureflag.ThundermailFeatureFlags\n",
        )
        remove_once(
            feature_flags,
            "                FeatureFlag(ThundermailFeatureFlags.ThundermailOnboardingEnabled, enabled = true),\n",
        )

    strings = app / "src/main/res/values/strings.xml"
    replace_once(
        strings,
        '<string name="app_name" translatable="false">Thunderbird</string>',
        '<string name="app_name" translatable="false">Sable Mail</string>',
    )
    replace_once(
        strings,
        '<string name="brand_name" translatable="false">Thunderbird</string>',
        '<string name="brand_name" translatable="false">Sable Mail</string>',
    )

    debug_strings = app / "src/debug/res/values/strings.xml"
    replace_once(
        debug_strings,
        ">Thunderbird Debug</string>",
        ">Sable Mail</string>",
    )

    name_provider = (
        app
        / "src/main/kotlin/net/thunderbird/android/provider/TbAppNameProvider.kt"
    )
    replace_once(
        name_provider,
        'override val filePrefix: String = "thunderbird"',
        'override val filePrefix: String = "sable-mail"',
    )

    funding_settings = (
        app
        / "src/main/kotlin/net/thunderbird/android/feature/TbFundingSettings.kt"
    )
    if not funding_settings.is_file():
        fail(f"SABLE_MAIL_FUNDING_SOURCE=FAIL_MISSING path={funding_settings}")
    funding_settings.unlink()

    feature_module = (
        app
        / "src/main/kotlin/net/thunderbird/android/feature/FeatureModule.kt"
    )
    remove_once(
        feature_module,
        "import net.thunderbird.feature.thundermail.thunderbird.inject.featureThundermailModule\n",
    )
    remove_once(
        feature_module,
        "    includes(featureThundermailModule)\n",
    )
    remove_once(
        feature_module,
        "import net.thunderbird.feature.funding.api.FundingSettings\n",
    )
    remove_once(
        feature_module,
        "    single<FundingSettings> { TbFundingSettings() }\n",
    )

    di_test = app / "src/test/kotlin/net/thunderbird/android/DependencyInjectionTest.kt"
    remove_once(
        di_test,
        "import net.thunderbird.feature.thundermail.internal.common.ui.ThundermailContract\n",
    )
    remove_once(
        di_test,
        "                definition<ThundermailContract.ViewModel>(ThundermailContract.State::class),\n",
    )

    oauth_source = oauth_factory_source(google_client_id())

    for variant in ("debug", "beta", "daily", "release"):
        write(
            app
            / f"src/{variant}/kotlin/net/thunderbird/android/auth/TbOAuthConfigurationFactory.kt",
            oauth_source,
        )

    provider_module = (
        app
        / "src/main/kotlin/net/thunderbird/android/provider/ProviderModule.kt"
    )
    replace_once(
        provider_module,
        "    single<ThemeProvider> { TbThemeProvider() }",
        "    single<ThemeProvider> { TbThemeProvider(androidContext()) }",
    )

    theme_provider = (
        app
        / "src/main/kotlin/net/thunderbird/android/provider/TbThemeProvider.kt"
    )
    write(
        theme_provider,
        '''package net.thunderbird.android.provider

import android.content.Context
import net.thunderbird.android.SableMailAppearanceReader
import net.thunderbird.android.SableMailThemeResources
import net.thunderbird.core.ui.theme.api.ThemeProvider

internal class TbThemeProvider(
    private val context: Context,
) : ThemeProvider {
    override val appThemeResourceId: Int
        get() = SableMailThemeResources.main(context)

    override val appLightThemeResourceId: Int
        get() = SableMailThemeResources.main(context, forceDark = false)

    override val appDarkThemeResourceId: Int
        get() = SableMailThemeResources.main(context, forceDark = true)

    override val dialogThemeResourceId: Int
        get() = SableMailThemeResources.dialog(context)

    override val translucentDialogThemeResourceId: Int
        get() = SableMailThemeResources.translucentDialog(context)
}
''',
    )

    icon_provider = (
        app
        / "src/main/kotlin/net/thunderbird/android/provider/TbAppIconNotificationProvider.kt"
    )
    write(
        icon_provider,
        '''package net.thunderbird.android.provider

import app.k9mail.core.android.common.provider.NotificationIconResourceProvider
import net.thunderbird.android.R

class TbAppIconNotificationProvider : NotificationIconResourceProvider {
    override val pushNotificationIcon: Int
        get() = R.drawable.ic_sable_mail_notification
}
''',
    )

    thunderbird_app = (
        app
        / "src/main/kotlin/net/thunderbird/android/ThunderbirdApp.kt"
    )
    write(
        thunderbird_app,
        '''package net.thunderbird.android

import app.k9mail.feature.telemetry.api.TelemetryManager
import net.thunderbird.app.common.BaseApplication
import net.thunderbird.core.preference.GeneralSettingsManager
import org.koin.android.ext.android.inject
import org.koin.core.module.Module

class ThunderbirdApp : BaseApplication() {
    private val telemetryManager: TelemetryManager by inject()
    private val generalSettingsManager: GeneralSettingsManager by inject()

    override fun provideAppModule(): Module = appModule

    override fun onCreate() {
        super.onCreate()

        SableMailAppearanceSync.install(
            application = this,
            generalSettingsManager = generalSettingsManager,
        )
        initializeTelemetry()
    }

    private fun initializeTelemetry() {
        telemetryManager.init(
            uploadEnabled = false,
            releaseChannel = BuildConfig.GLEAN_RELEASE_CHANNEL,
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
        )
    }
}
''',
    )

    appearance_bridge = (
        app
        / "src/main/kotlin/net/thunderbird/android/SableMailAppearance.kt"
    )
    write(
        appearance_bridge,
        '''package net.thunderbird.android

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import net.thunderbird.core.preference.AppTheme
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.core.preference.update

internal data class SableMailAppearance(
    val mode: String = "follow-system",
    val accent: String = "blue",
)

internal object SableMailAppearanceReader {
    private const val AUTHORITY = "org.sableos.appearance"
    private val URI = Uri.parse("content://$AUTHORITY/appearance")

    fun read(context: Context): SableMailAppearance =
        runCatching {
            context.contentResolver.query(
                URI,
                arrayOf("mode", "accent"),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use SableMailAppearance()
                }

                SableMailAppearance(
                    mode = cursor.getString(cursor.getColumnIndexOrThrow("mode")),
                    accent = cursor.getString(cursor.getColumnIndexOrThrow("accent")),
                )
            } ?: SableMailAppearance()
        }.getOrDefault(SableMailAppearance())

    fun uri(): Uri = URI

    fun resolveDark(
        context: Context,
        appearance: SableMailAppearance = read(context),
    ): Boolean =
        when (appearance.mode) {
            "light" -> false
            "dark" -> true
            else ->
                (
                    context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                ) == Configuration.UI_MODE_NIGHT_YES
        }
}

internal object SableMailThemeResources {
    fun main(
        context: Context,
        forceDark: Boolean? = null,
    ): Int =
        resolveStyle(
            appearance = SableMailAppearanceReader.read(context),
            dark =
                forceDark
                    ?: SableMailAppearanceReader.resolveDark(context),
            kind = Kind.MAIN,
        )

    fun dialog(context: Context): Int =
        resolveStyle(
            appearance = SableMailAppearanceReader.read(context),
            dark = SableMailAppearanceReader.resolveDark(context),
            kind = Kind.DIALOG,
        )

    fun translucentDialog(context: Context): Int =
        resolveStyle(
            appearance = SableMailAppearanceReader.read(context),
            dark = SableMailAppearanceReader.resolveDark(context),
            kind = Kind.TRANSLUCENT,
        )

    private enum class Kind {
        MAIN,
        DIALOG,
        TRANSLUCENT,
    }

    private fun resolveStyle(
        appearance: SableMailAppearance,
        dark: Boolean,
        kind: Kind,
    ): Int =
        when (kind) {
            Kind.MAIN ->
                if (dark) {
                    when (appearance.accent) {
                        "green" -> R.style.Theme_SableMail_Dark_Green
                        "purple" -> R.style.Theme_SableMail_Dark_Purple
                        "orange" -> R.style.Theme_SableMail_Dark_Orange
                        "slate" -> R.style.Theme_SableMail_Dark_Slate
                        else -> R.style.Theme_SableMail_Dark_Blue
                    }
                } else {
                    when (appearance.accent) {
                        "green" -> R.style.Theme_SableMail_Light_Green
                        "purple" -> R.style.Theme_SableMail_Light_Purple
                        "orange" -> R.style.Theme_SableMail_Light_Orange
                        "slate" -> R.style.Theme_SableMail_Light_Slate
                        else -> R.style.Theme_SableMail_Light_Blue
                    }
                }

            Kind.DIALOG ->
                if (dark) {
                    when (appearance.accent) {
                        "green" -> R.style.Theme_SableMail_Dark_Green_Dialog
                        "purple" -> R.style.Theme_SableMail_Dark_Purple_Dialog
                        "orange" -> R.style.Theme_SableMail_Dark_Orange_Dialog
                        "slate" -> R.style.Theme_SableMail_Dark_Slate_Dialog
                        else -> R.style.Theme_SableMail_Dark_Blue_Dialog
                    }
                } else {
                    when (appearance.accent) {
                        "green" -> R.style.Theme_SableMail_Light_Green_Dialog
                        "purple" -> R.style.Theme_SableMail_Light_Purple_Dialog
                        "orange" -> R.style.Theme_SableMail_Light_Orange_Dialog
                        "slate" -> R.style.Theme_SableMail_Light_Slate_Dialog
                        else -> R.style.Theme_SableMail_Light_Blue_Dialog
                    }
                }

            Kind.TRANSLUCENT ->
                if (dark) {
                    when (appearance.accent) {
                        "green" -> R.style.Theme_SableMail_Dark_Green_Dialog_Translucent
                        "purple" -> R.style.Theme_SableMail_Dark_Purple_Dialog_Translucent
                        "orange" -> R.style.Theme_SableMail_Dark_Orange_Dialog_Translucent
                        "slate" -> R.style.Theme_SableMail_Dark_Slate_Dialog_Translucent
                        else -> R.style.Theme_SableMail_Dark_Blue_Dialog_Translucent
                    }
                } else {
                    when (appearance.accent) {
                        "green" -> R.style.Theme_SableMail_Light_Green_Dialog_Translucent
                        "purple" -> R.style.Theme_SableMail_Light_Purple_Dialog_Translucent
                        "orange" -> R.style.Theme_SableMail_Light_Orange_Dialog_Translucent
                        "slate" -> R.style.Theme_SableMail_Light_Slate_Dialog_Translucent
                        else -> R.style.Theme_SableMail_Light_Blue_Dialog_Translucent
                    }
                }
        }
}

internal object SableMailAppearanceSync {
    fun install(
        application: Application,
        generalSettingsManager: GeneralSettingsManager,
    ) {
        var current = SableMailAppearanceReader.read(application)
        var resumedActivity = WeakReference<Activity>(null)

        fun applyMode(appearance: SableMailAppearance) {
            val desired =
                when (appearance.mode) {
                    "light" -> AppTheme.LIGHT
                    "dark" -> AppTheme.DARK
                    else -> AppTheme.FOLLOW_SYSTEM
                }

            val existing =
                generalSettingsManager
                    .getConfig()
                    .display
                    .coreSettings
                    .appTheme

            if (existing != desired) {
                generalSettingsManager.update { settings ->
                    settings.copy(
                        display =
                            settings.display.copy(
                                coreSettings =
                                    settings.display.coreSettings.copy(
                                        appTheme = desired,
                                    ),
                            ),
                    )
                }
            }
        }

        applyMode(current)

        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumedActivity = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    if (resumedActivity.get() === activity) {
                        resumedActivity.clear()
                    }
                }
            },
        )

        // The appearance provider lives in Settings (Q4 framework layer). On an
        // image without it, Android throws SecurityException here; Mail keeps
        // the defaults instead of failing to start.
        runCatching {
            application.contentResolver.registerContentObserver(
                SableMailAppearanceReader.uri(),
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        val next = SableMailAppearanceReader.read(application)
                        val modeChanged = next.mode != current.mode
                        val accentChanged = next.accent != current.accent
                        current = next

                        if (modeChanged) {
                            applyMode(next)
                        }
                        if (accentChanged) {
                            resumedActivity.get()?.recreate()
                        }
                    }
                },
            )
        }
    }
}
''',
    )

    mail_snapshot_provider = (
        app
        / "src/main/kotlin/net/thunderbird/android/SableMailSnapshotProvider.kt"
    )
    write(
        mail_snapshot_provider,
        '''package net.thunderbird.android

import android.app.Notification
import android.app.NotificationManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process

class SableMailSnapshotProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val appContext = checkNotNull(context)
        enforceSableSnapshotCaller(appContext)
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val count =
            runCatching {
                manager?.activeNotifications
                    .orEmpty()
                    .count { item ->
                        item.notification.category == Notification.CATEGORY_EMAIL ||
                            item.notification.category == Notification.CATEGORY_MESSAGE
                    }
            }.getOrDefault(0)

        val detail =
            when (count) {
                0 -> "no mail alerts"
                1 -> "1 mail alert"
                else -> count.toString() + " mail alerts"
            }

        return MatrixCursor(COLUMNS).apply {
            addRow(
                arrayOf<Any?>(
                    "Mail",
                    detail,
                    if (count == 0) "EMPTY" else "LIVE",
                    System.currentTimeMillis(),
                ),
            )
            setNotificationUri(appContext.contentResolver, SNAPSHOT_URI)
        }
    }

    override fun getType(uri: Uri): String =
        "vnd.android.cursor.item/vnd.org.sableos.mail.snapshot"

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read-only provider")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    private fun enforceSableSnapshotCaller(context: android.content.Context) {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return
        val packages =
            context.packageManager.getPackagesForUid(callingUid)
                ?.toSet()
                .orEmpty()
        val authorized =
            setOf(
                "org.sableos.launcher",
                "org.sableos.hub",
            )
        if (packages.intersect(authorized).isEmpty()) {
            throw SecurityException(
                "Sable mail snapshot is restricted to SableLauncher and SableHub.",
            )
        }
    }

    companion object {
        val SNAPSHOT_URI: Uri =
            Uri.parse("content://org.sableos.mail.snapshot/current")
        private val COLUMNS =
            arrayOf("title", "detail", "availability", "observed_at")
    }
}
''',
    )

    core_theme_manager = (
        root
        / "core/ui/theme/manager/src/main/java/net/thunderbird/core/ui/theme/manager/ThemeManager.kt"
    )
    text = in_checkout(core_theme_manager).read_text(encoding="utf-8")
    text = text.replace(
        "override val appThemeResourceId: Int = themeProvider.appThemeResourceId",
        """override val appThemeResourceId: Int
        get() = themeProvider.appThemeResourceId""",
        1,
    )
    text = text.replace(
        "override val dialogThemeResourceId: Int = themeProvider.dialogThemeResourceId",
        """override val dialogThemeResourceId: Int
        get() = themeProvider.dialogThemeResourceId""",
        1,
    )
    text = text.replace(
        "override val translucentDialogThemeResourceId: Int = themeProvider.translucentDialogThemeResourceId",
        """override val translucentDialogThemeResourceId: Int
        get() = themeProvider.translucentDialogThemeResourceId""",
        1,
    )
    if "get() = themeProvider.appThemeResourceId" not in text:
        fail("SABLE_MAIL_THEME_MANAGER=FAIL_APP_THEME_GETTER")
    in_checkout(core_theme_manager).write_text(text, encoding="utf-8")

    manifest = app / "src/main/AndroidManifest.xml"
    manifest_text = in_checkout(manifest).read_text(encoding="utf-8")
    application_anchor = "\n    <application"
    if manifest_text.count(application_anchor) != 1:
        fail("SABLE_MAIL_MANIFEST=FAIL_APPLICATION_ANCHOR")
    manifest_text = manifest_text.replace(
        application_anchor,
        """
    <queries>
        <provider android:authorities="org.sableos.appearance" />
    </queries>

    <application""",
        1,
    )
    in_checkout(manifest).write_text(manifest_text, encoding="utf-8")

    replace_once(
        manifest,
        'android:icon="@mipmap/ic_launcher"',
        'android:icon="@mipmap/ic_sable_mail"\n        android:roundIcon="@mipmap/ic_sable_mail_round"',
    )
    replace_once(
        manifest,
        'android:theme="@style/Theme.Thunderbird.Startup"',
        'android:theme="@style/Theme.SableMail.Startup"',
    )
    manifest_text = in_checkout(manifest).read_text(encoding="utf-8")
    manifest_text = manifest_text.replace(
        "@style/Theme.Thunderbird.DayNight.Dialog.Translucent",
        "@style/Theme.SableMail.DayNight.Dialog.Translucent",
    )
    provider_anchor = "\n    </application>"
    if manifest_text.count(provider_anchor) != 1:
        fail("SABLE_MAIL_MANIFEST=FAIL_PROVIDER_ANCHOR")
    manifest_text = manifest_text.replace(
        provider_anchor,
        """
        <provider
            android:name="net.thunderbird.android.SableMailSnapshotProvider"
            android:authorities="org.sableos.mail.snapshot"
            android:exported="true"
            android:grantUriPermissions="false" />

    </application>""",
        1,
    )
    in_checkout(manifest).write_text(manifest_text, encoding="utf-8")

    copy_icon_resources(app / "src/main/res")
    apply_keyboard_model(root)
    write(
        app / "src/main/res/drawable/ic_sable_mail_notification.xml",
        '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M2,5h20v14H2zM4,7v1l8,6 8,-6V7l-8,6z" />
</vector>
''',
    )

    accents = {
        "Blue": ("#4D9CFF", "#06111F"),
        "Green": ("#35C66B", "#04140A"),
        "Purple": ("#7A42E8", "#FFFFFF"),
        "Orange": ("#F28C45", "#1D0C02"),
        "Slate": ("#64748B", "#FFFFFF"),
    }

    style_lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<resources>",
        '    <style name="Theme.SableMail.Startup" parent="Theme.Thunderbird.DayNight" />',
        '    <style name="Theme.SableMail.DayNight.Dialog.Translucent" parent="Theme.Thunderbird.DayNight.Dialog.Translucent" />',
        "",
    ]

    def style(name: str, parent: str, color: str, on_color: str) -> None:
        style_lines.extend(
            [
                f'    <style name="{name}" parent="{parent}">',
                f'        <item name="colorPrimary">{color}</item>',
                f'        <item name="colorOnPrimary">{on_color}</item>',
                f'        <item name="colorPrimaryContainer">{color}</item>',
                f'        <item name="colorOnPrimaryContainer">{on_color}</item>',
                f'        <item name="colorSecondary">{color}</item>',
                f'        <item name="colorOnSecondary">{on_color}</item>',
                f'        <item name="colorSecondaryContainer">{color}</item>',
                f'        <item name="colorOnSecondaryContainer">{on_color}</item>',
                '        <item name="appLogo">@drawable/ic_sable_mail</item>',
                "    </style>",
                "",
            ],
        )

    for accent, (color, on_color) in accents.items():
        for tone in ("Light", "Dark"):
            base = f"Theme.Thunderbird.{tone}"
            style(f"Theme.SableMail.{tone}.{accent}", base, color, on_color)
            style(
                f"Theme.SableMail.{tone}.{accent}.Dialog",
                f"Theme.Thunderbird.{tone}.Dialog",
                color,
                on_color,
            )
            style(
                f"Theme.SableMail.{tone}.{accent}.Dialog.Translucent",
                f"Theme.Thunderbird.{tone}.Dialog.Translucent",
                color,
                on_color,
            )

    style_lines.append("</resources>")
    write(
        app / "src/main/res/values/sable_mail_themes.xml",
        "\n".join(style_lines) + "\n",
    )

    print(f"SABLE_MAIL_UPSTREAM_COMMIT={actual}")
    print(f"SABLE_MAIL_APPLICATION_ID={SABLE_APPLICATION_ID}")
    print("SABLE_MAIL_BRANDING=SABLE_MAIL")
    print("SABLE_MAIL_BUILD_TYPE=FOSS_RELEASE_NON_DEBUGGABLE_MINIFIED")
    print("SABLE_MAIL_DEVELOPMENT_SIGNING=DEBUG_KEY_ONLY")
    print("SABLE_MAIL_TELEMETRY=NOOP_UPLOAD_DISABLED")
    print("SABLE_MAIL_FUNDING=NOOP")
    print("SABLE_MAIL_MIGRATION_UI=NOOP")
    print("SABLE_MAIL_FUNDING_SETTINGS_SOURCE=REMOVED_FROM_PRODUCT_APP")
    print("SABLE_MAIL_THUNDERMAIL=REMOVED_FROM_PRODUCT_RUNTIME_GRAPH")
    print("SABLE_MAIL_AUTODISCOVERY_DEMO=REMOVED_FROM_PRODUCT_RUNTIME_GRAPH")
    print("SABLE_MAIL_AUTODISCOVERY_DEMO_SOURCE_SPECIAL_CASES=REMOVED")
    print("SABLE_MAIL_ACCOUNT_ENTRY=STANDARD_IMAP_SMTP_SETUP")
    print("SABLE_MAIL_THUNDERMAIL_ROUTES=REMOVED_FROM_COMPOSED_SOURCE")
    if google_client_id() is None:
        print("SABLE_MAIL_OAUTH=DISABLED_UNTIL_SABLE_CLIENT_REGISTRATION")
    else:
        print("SABLE_MAIL_OAUTH=GMAIL_OWNER_CLIENT")
    print("SABLE_MAIL_GENERIC_IMAP_SMTP=UPSTREAM")
    print("SABLE_MAIL_GLOBAL_APPEARANCE=FOLLOW_SYSTEM_LIGHT_DARK_PLUS_5_ACCENTS")
    print("SABLE_MAIL_KEYMAP=SABLE_SHARED_CONTRACT_INPUT_GUARDED")
    print("SABLE_MAIL_DELETE_CONFIRMATION=DEFAULT_ON")
    print("SABLE_MAIL_PATCH=PASS")


if __name__ == "__main__":
    main()
