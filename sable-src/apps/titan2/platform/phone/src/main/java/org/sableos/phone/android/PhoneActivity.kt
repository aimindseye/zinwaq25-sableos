package org.sableos.phone.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.concurrent.Executors
import org.sableos.phone.core.CallHistory
import org.sableos.phone.core.DialKeys
import org.sableos.phone.core.PhoneSearch
import org.sableos.phone.core.PhoneText.t
import org.sableos.titan2.theme.SablePalette

/**
 * Sable Phone's main screen, BlackBerry 10 style: Calls (by day), Contacts and Dial pad tabs on a 720x720 display,
 * all driven from the keyboard and trackpad. Letters find contacts and calls, digits open the dial pad, holding a
 * letter speed dials it, the Call key calls the focused row or what is typed (an empty dial pad fills in the last
 * number called), and Menu opens speed dial and settings.
 */
class PhoneActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var palette: SablePalette
    private lateinit var settings: PhoneSettings
    private lateinit var tabs: List<TextView>
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var adapter: PhoneListAdapter
    private lateinit var dialPad: DialPadView
    private lateinit var banner: TextView
    private lateinit var hint: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var calls: List<CallHistory.Call> = emptyList()
    private var people: List<PhoneSearch.Person> = emptyList()
    private var hideNames = false
    private var tab = TAB_CALLS
    private var pendingLetter: Char? = null
    private var speedDialed = false
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = SablePalette.read(this).also { it.applyTo(this) }
        ui = Ui(this, palette)
        settings = PhoneSettings(this)
        actionBar?.hide()
        setContentView(build().also(ui::fitSystemBars))
        askPermissions()
        handle(intent, savedInstanceState == null)
    }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.window)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(Ui.PAD / 2), ui.dp(Ui.PAD / 2), ui.dp(Ui.PAD / 2), 0)
        }
        tabs = listOf(t("tab_calls"), t("tab_contacts"), t("tab_dial_pad")).mapIndexed { i, label ->
            ui.text(Ui.BODY_SP, bold = true, s = label).apply {
                gravity = android.view.Gravity.CENTER
                isFocusable = true
                isClickable = true
                background = ui.focusBackground()
                setPadding(0, ui.dp(TAB_PAD), 0, ui.dp(TAB_PAD))
                setOnClickListener { showTab(i) }
            }.also {
                bar.addView(
                    it,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
        }
        // An on-screen Menu as well as the Menu key (hold the BlackBerry key on the Q25).
        bar.addView(
            ui.text(Ui.BODY_SP, palette.accent, bold = true, s = t("menu")).apply {
                gravity = android.view.Gravity.CENTER
                isFocusable = true
                isClickable = true
                background = ui.focusBackground()
                setPadding(ui.dp(Ui.PAD), ui.dp(TAB_PAD), ui.dp(Ui.PAD), ui.dp(TAB_PAD))
                setOnClickListener { menu() }
            }
        )
        root.addView(bar)
        banner = ui.text(Ui.SMALL_SP, palette.onAccent, s = t("make_default")).apply {
            isFocusable = true
            isClickable = true
            background = ui.box(palette.accent, palette.accent)
            setPadding(ui.dp(Ui.PAD), ui.dp(TAB_PAD), ui.dp(Ui.PAD), ui.dp(TAB_PAD))
            setOnClickListener { askRole() }
        }
        root.addView(banner, margins())
        search = searchField()
        root.addView(search, margins())
        adapter = PhoneListAdapter(this, ui)
        list = listView()
        root.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        dialPad = DialPadView(this, ui, dialListener)
        root.addView(dialPad, LinearLayout.LayoutParams(MATCH, 0, 1f))
        hint = ui.text(Ui.SMALL_SP, palette.muted)
        hint.setPadding(ui.dp(Ui.PAD), 0, ui.dp(Ui.PAD), ui.dp(Ui.PAD / 2))
        root.addView(hint)
        return root
    }

    private fun searchField(): EditText = EditText(this).apply {
        hint = t("search_hint")
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setTextColor(palette.text)
        setHintTextColor(palette.muted)
        background = ui.focusBackground(palette.surface)
        setPadding(ui.dp(Ui.PAD), ui.dp(TAB_PAD), ui.dp(Ui.PAD), ui.dp(TAB_PAD))
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) = refresh()
        })
    }

    private fun listView(): ListView = ListView(this).apply {
        this.adapter = this@PhoneActivity.adapter
        divider = null
        itemsCanFocus = false
        selector = ui.focusBackground()
        setOnItemClickListener { _, _, position, _ ->
            callItem(this@PhoneActivity.adapter.getItem(position))
        }
        setOnItemLongClickListener { _, _, position, _ ->
            actions(this@PhoneActivity.adapter.getItem(position))
            true
        }
    }

    private fun margins() = LinearLayout.LayoutParams(MATCH, WRAP).apply {
        setMargins(ui.dp(Ui.PAD), ui.dp(Ui.PAD / 2), ui.dp(Ui.PAD), 0)
    }

    private val dialListener = object : DialPadView.Listener {
        override fun onCall(number: String) {
            if (number.isEmpty()) {
                val last = calls.firstOrNull {
                    it.kind == CallHistory.Kind.OUTGOING &&
                        it.number.isNotEmpty()
                }
                last?.let { dialPad.setNumber(it.number) }
            } else if (Dialing.call(this@PhoneActivity, number)) {
                dialPad.setNumber("")
            }
        }

        override fun onNumberChanged(number: String) {
            if (Dialing.special(this@PhoneActivity, number)) {
                dialPad.setNumber("")
                return
            }
            dialPad.showSuggestions(
                if (number.isEmpty()) emptyList() else PhoneSearch.find(people, number)
            )
        }

        override fun onVoicemail() = Dialing.voicemail(this@PhoneActivity)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent, true)
    }

    /** Dial intents (tel: links) open the dial pad with the number; nothing another app sends places a call. */
    private fun handle(intent: Intent, fresh: Boolean) {
        val number = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        when (intent.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW, ACTION_ADD_CALL -> {
                showTab(TAB_DIAL)
                dialPad.setNumber(number.orEmpty())
            }

            else -> if (fresh) showTab(settings.tab.coerceIn(TAB_CALLS, TAB_DIAL)) else showTab(tab)
        }
    }

    override fun onResume() {
        super.onResume()
        banner.visibility = if (isDefaultPhone()) View.GONE else View.VISIBLE
        contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer
        )
        load()
    }

    override fun onPause() {
        contentResolver.unregisterContentObserver(observer)
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun load() {
        val data = PhoneData(this)
        io.execute {
            val c = if (granted(Manifest.permission.READ_CALL_LOG)) data.calls() else emptyList()
            val p = if (granted(Manifest.permission.READ_CONTACTS)) data.people() else emptyList()
            val hidden = data.privateMode()
            handler.post {
                calls = c
                people = p
                hideNames = hidden
                refresh()
            }
        }
    }

    private fun showTab(index: Int) {
        tab = index
        settings.tab = index
        tabs.forEachIndexed { i, v ->
            v.setTextColor(
                if (i ==
                    index
                ) {
                    palette.accent
                } else {
                    palette.muted
                }
            )
        }
        val dial = index == TAB_DIAL
        dialPad.visibility = if (dial) View.VISIBLE else View.GONE
        list.visibility = if (dial) View.GONE else View.VISIBLE
        search.visibility = if (dial) View.GONE else View.VISIBLE
        hint.text = when (index) {
            TAB_CALLS -> t("hint_calls")
            TAB_CONTACTS -> t("hint_contacts")
            else -> t("hint_dial_pad")
        }
        if (index == TAB_CALLS) clearMissed()
        refresh()
        if (dial) dialPad.requestFocus() else list.requestFocus()
    }

    private fun refresh() {
        if (!::adapter.isInitialized) return
        val query = search.text.toString().trim()
        val items = when {
            query.isNotEmpty() -> searchItems(query)

            tab == TAB_CONTACTS -> people.sortedBy {
                it.name.lowercase()
            }.map { PhoneListAdapter.Item.PersonRow(it) }

            else -> callItems(calls)
        }
        val empty = if (tab == TAB_CONTACTS) t("no_contacts") else t("no_calls")
        adapter.items = items.ifEmpty { listOf(PhoneListAdapter.Item.Header(empty)) }
    }

    private fun callItems(source: List<CallHistory.Call>): List<PhoneListAdapter.Item> {
        val today = java.time.LocalDate.now().toEpochDay()
        return CallHistory.group(source, today).flatMap { section ->
            val title = PhoneListAdapter.dayTitle(
                this,
                section,
                section.rows.first().latest.timeMillis
            )
            val rows = section.rows.map { PhoneListAdapter.Item.CallRow(it, hideNames) }
            listOf(PhoneListAdapter.Item.Header(title)) + rows
        }
    }

    /** Contacts that match, then the calls that match, each under its header. */
    private fun searchItems(query: String): List<PhoneListAdapter.Item> {
        val matches = PhoneSearch.find(people, query).take(MAX_PEOPLE).map {
            PhoneListAdapter.Item.PersonRow(it)
        }
        val matchingCalls = if (tab == TAB_CONTACTS) {
            emptyList()
        } else {
            calls.filter { c ->
                if (PhoneSearch.isNumberQuery(query)) {
                    PhoneSearch.numberMatches(c.number, query)
                } else {
                    !hideNames && c.name?.contains(query, ignoreCase = true) == true
                }
            }
        }
        val out = mutableListOf<PhoneListAdapter.Item>()
        if (matches.isNotEmpty()) {
            out +=
                listOf(PhoneListAdapter.Item.Header(t("tab_contacts"))) + matches
        }
        if (matchingCalls.isNotEmpty()) out += callItems(matchingCalls)
        if (out.isEmpty()) out += PhoneListAdapter.Item.Header(t("no_matches"))
        return out
    }

    private fun numberOf(item: PhoneListAdapter.Item?): String? = when (item) {
        is PhoneListAdapter.Item.CallRow -> item.row.latest.number.takeIf {
            it.isNotEmpty() &&
                !item.row.latest.hidden
        }

        is PhoneListAdapter.Item.PersonRow -> item.person.numbers.first()

        else -> null
    }

    private fun callItem(item: PhoneListAdapter.Item?) {
        if (item is PhoneListAdapter.Item.PersonRow && item.person.numbers.size > 1) {
            AlertDialog.Builder(this)
                .setTitle(item.person.name)
                .setItems(
                    item.person.numbers.map {
                        CallInfo.formatted(this, it)
                    }.toTypedArray()
                ) { _, i ->
                    Dialing.call(this, item.person.numbers[i])
                }
                .show()
            return
        }
        numberOf(item)?.let { Dialing.call(this, it) }
    }

    /** Call, message, add to contacts, copy, and for calls, remove from the list. */
    private fun actions(item: PhoneListAdapter.Item?) {
        val number = numberOf(item) ?: return
        val options = mutableListOf<Pair<String, () -> Unit>>(
            t("call") to { Dialing.call(this, number) },
            t("send_message") to
                { open(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))) },
            t("edit_before_call") to {
                showTab(TAB_DIAL)
                dialPad.setNumber(number)
            },
            t("copy_number") to {
                getSystemService(
                    ClipboardManager::class.java
                ).setPrimaryClip(ClipData.newPlainText(number, number))
            }
        )
        if (item is PhoneListAdapter.Item.CallRow) {
            if (item.row.latest.name == null) {
                options += t("add_contact") to {
                    open(
                        Intent(ContactsContract.Intents.Insert.ACTION)
                            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                            .putExtra(ContactsContract.Intents.Insert.PHONE, number)
                    )
                }
            }
            options += t("remove_from_calls") to {
                val ids = item.row.calls.map { it.id }
                io.execute { PhoneData(this).deleteCalls(ids) }
            }
        }
        AlertDialog.Builder(this)
            .setTitle(CallInfo.formatted(this, number))
            .setItems(options.map { it.first }.toTypedArray()) { _, i -> options[i].second() }
            .show()
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }
    }

    private fun menu() {
        val telecom = getSystemService(TelecomManager::class.java)
        val options = listOf<Pair<String, () -> Unit>>(
            t("speed_dial") to { open(Intent(this, SpeedDialActivity::class.java)) },
            (if (settings.spaceAnswers) t("space_answers_on") else t("space_answers_off")) to {
                settings.spaceAnswers = !settings.spaceAnswers
            },
            t("blocked_numbers") to { open(telecom.createManageBlockedNumbersIntent()) },
            t("call_settings") to { open(Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS)) },
            t("sim_settings") to { open(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)) }
        )
        AlertDialog.Builder(this)
            .setTitle(t("app_title"))
            .setItems(options.map { it.first }.toTypedArray()) { _, i -> options[i].second() }
            .show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = when (event.action) {
            KeyEvent.ACTION_DOWN -> keyDown(event)
            KeyEvent.ACTION_UP -> keyUp(event)
            else -> false
        }
        return handled || super.dispatchKeyEvent(event)
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun keyDown(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (code == KeyEvent.KEYCODE_MENU && e.repeatCount == 0) {
            menu()
            return true
        }
        if (code == DialKeys.KEYCODE_CALL) {
            if (e.repeatCount == 0) callKey()
            return true
        }
        if (tab == TAB_DIAL) return dialKey(e)
        // Holding a letter speed dials it: the first press waits, the first repeat (the long-press) dials.
        val searching = search.text.isNotEmpty() || search.hasFocus()
        val letter = DialKeys.letterFor(code)?.takeIf {
            e.metaState and DialKeys.CHORD_META == 0 &&
                !searching
        }
        if (letter != null) {
            if (e.repeatCount == 0) {
                pendingLetter = letter
                speedDialed = false
            } else if (DialKeys.heldLetter(code, e.metaState, e.repeatCount) == letter &&
                pendingLetter == letter
            ) {
                speedDialed = true
                speedDial(letter)
            }
            return true
        }
        if (!search.hasFocus() &&
            DialKeys.opensDialPad(code, e.unicodeChar, e.metaState, e.repeatCount)
        ) {
            DialKeys.dialChar(
                code,
                e.unicodeChar,
                e.number.code,
                e.metaState,
                dialScreen = false
            )?.let {
                showTab(TAB_DIAL)
                dialPad.setNumber(it.toString())
            }
            return true
        }
        return false
    }

    private fun keyUp(e: KeyEvent): Boolean {
        val letter = pendingLetter?.takeIf { DialKeys.letterFor(e.keyCode) == it } ?: return false
        pendingLetter = null
        if (!speedDialed) {
            // A short press: the letter starts a search.
            search.requestFocus()
            search.append(letter.toString())
        }
        return true
    }

    /** Delete (hold to clear), Enter calls, and every dial key types; holding 0 makes it +. */
    private fun dialKey(e: KeyEvent): Boolean {
        val c = DialKeys.dialChar(
            e.keyCode,
            e.unicodeChar,
            e.number.code,
            e.metaState,
            dialScreen = true
        )
        val onKey = dialPad.findFocus() !is TextView
        val enterCalls = e.keyCode == KeyEvent.KEYCODE_ENTER && dialPad.number.isNotEmpty() && onKey
        when {
            e.keyCode == KeyEvent.KEYCODE_DEL -> when (e.repeatCount) {
                0 -> dialPad.backspace()
                1 -> dialPad.setNumber("")
            }

            enterCalls -> dialListener.onCall(dialPad.number)

            c == null -> return false

            e.repeatCount == 0 -> dialPad.type(c)

            e.repeatCount == 1 && c == '0' -> dialPad.setNumber(dialPad.number.dropLast(1) + '+')
        }
        return true
    }

    /** The green key calls the focused row, else what the dial pad holds (an empty one fills in the last number). */
    private fun callKey() {
        if (tab == TAB_DIAL) {
            dialListener.onCall(dialPad.number)
            return
        }
        val position = list.selectedItemPosition
        if (position >= 0 && position < adapter.count) {
            callItem(adapter.getItem(position))
        } else {
            showTab(TAB_DIAL)
        }
    }

    private fun speedDial(letter: Char) {
        val entry = SpeedDialStore(this).get(letter)
        if (entry !=
            null
        ) {
            Dialing.call(this, entry.number)
        } else {
            startActivity(SpeedDialActivity.assign(this, letter))
        }
    }

    // The default Phone app may clear its missed call notification without MODIFY_PHONE_STATE, which lint
    // cannot know; a refusal is caught and logged.
    @SuppressLint("MissingPermission")
    private fun clearMissed() {
        try {
            getSystemService(TelecomManager::class.java).cancelMissedCallsNotification()
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot clear the missed call notification", e)
        }
        io.execute { PhoneData(this).markMissedSeen() }
    }

    private fun isDefaultPhone(): Boolean =
        getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true

    private fun askRole() {
        val roles = getSystemService(RoleManager::class.java) ?: return
        if (roles.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            @Suppress("DEPRECATION")
            startActivityForResult(
                roles.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                REQUEST_ROLE
            )
        }
    }

    private fun askPermissions() {
        val missing = PERMISSIONS.filterNot(::granted)
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        load()
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_ADD_CALL = "org.sableos.phone.action.ADD_CALL"
        private const val TAG = "SablePhone"
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        private const val TAB_CALLS = 0
        private const val TAB_CONTACTS = 1
        private const val TAB_DIAL = 2
        private const val TAB_PAD = 8
        private const val MAX_PEOPLE = 20
        private const val REQUEST_ROLE = 1
        private const val REQUEST_PERMISSIONS = 2
        private val PERMISSIONS = listOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
}
