package org.sableos.titan2.keyboard.android

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import org.sableos.titan2.keyboard.R
import org.sableos.titan2.keyboard.core.AutoText
import org.sableos.titan2.keyboard.core.Cand
import org.sableos.titan2.keyboard.core.CompactStrip
import org.sableos.titan2.keyboard.core.Composer
import org.sableos.titan2.keyboard.core.Composers
import org.sableos.titan2.keyboard.core.Dubeolsik
import org.sableos.titan2.keyboard.core.Engine
import org.sableos.titan2.keyboard.core.FieldCtx
import org.sableos.titan2.keyboard.core.FieldPolicy
import org.sableos.titan2.keyboard.core.InputClass
import org.sableos.titan2.keyboard.core.Key
import org.sableos.titan2.keyboard.core.KeyEv
import org.sableos.titan2.keyboard.core.KeyShortcuts
import org.sableos.titan2.keyboard.core.KeyboardText.t
import org.sableos.titan2.keyboard.core.Mod
import org.sableos.titan2.keyboard.core.ModState
import org.sableos.titan2.keyboard.core.Out
import org.sableos.titan2.keyboard.core.Resolver
import org.sableos.titan2.keyboard.core.StripInputs
import org.sableos.titan2.keyboard.core.StripMode
import org.sableos.titan2.keyboard.core.SymGrid
import org.sableos.titan2.keyboard.core.SymGridSession
import org.sableos.titan2.keyboard.core.WordEdit

/**
 * Physical-keyboard-first IME. Hardware keys are resolved by the pure-Kotlin [Resolver]; the on-screen view is only a
 * fallback (numeric/phone/PIN fields, or when the user forces it) and is never shown just because a field gained
 * focus.
 */
// reason: the platform InputMethodService lifecycle/key overrides alone number 11; they cannot be merged or removed
@Suppress("TooManyFunctions")
class SableImeService : InputMethodService() {
    private lateinit var prefs: KeyboardPrefs
    private lateinit var resolver: Resolver
    private val handledDown = HashSet<Int>()
    private var softToggled = false
    private var fieldClass = InputClass.None

    private companion object {
        // enough for an AutoText shortcut and the expansion Backspace undoes
        const val BEFORE_CURSOR_CHARS = 64
        const val COMPOSITION_LOST_MS = 400L
    }

    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPrefs(this)
        resolver =
            Resolver(
                layout = org.sableos.titan2.keyboard.core.KeyLayout.forProfile(
                    SysProps.get("ro.sable.profile.id").ifBlank {
                        null
                    }
                ),
                cfg = prefs.load()
            )
        resolver.autoTextVar = autoTextVar(this)
        ClipboardWatcher.start(this) { clipboardOn() }
    }

    override fun onDestroy() {
        ClipboardWatcher.stop()
        super.onDestroy()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resolver.cfg = prefs.load()
        resolver.autoText = AutoText(prefs.autoTextEntries)
        if (!clipboardOn()) ClipboardWatcher.history.clear()
        if (!restarting) {
            resolver.mods.reset()
            handledDown.clear()
            softToggled = false
        }
        fieldClass = classify(attribute)
        resolver.clearVariation()
        suggester.reset()
        corrector.clear()
        refreshStrip()
    }

    override fun onEvaluateInputViewShown(): Boolean = FieldPolicy.softKeyboardWanted(
        fieldClass,
        prefs.forceSoftKeyboard,
        softToggled,
        prefs.softForNumeric
    ) ||
        super.onEvaluateInputViewShown()

    private var softView: SoftKeyboardView? = null
    private var language = org.sableos.titan2.keyboard.core.Languages.English

    override fun onCreateInputView(): View =
        SoftKeyboardView(this, resolver.layout, autoCapQuery = {
            shouldAutoCap()
        }) { event -> softKey(event) }
            .also {
                it.setLanguage(language)
                applyNumericPad(it)
                softView = it
            }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // currentInputMethodSubtype is an InputMethodManager property, not an InputMethodService one.
        val subtype = getSystemService(InputMethodManager::class.java)?.currentInputMethodSubtype
        applyLanguage(subtype?.locale, subtype?.extraValue, announce = false)
        softView?.let { applyNumericPad(it) }
        softView?.refreshAutoCap()
        updateSuggestions()
        refreshStrip()
    }

    /** PIN, number and phone fields get a number-only pad, not the full keyboard. */
    private fun applyNumericPad(v: SoftKeyboardView) =
        v.setNumeric(fieldClass in FieldPolicy.DIGIT_ENTRY_CLASSES, fieldClass == InputClass.Phone)

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        applyLanguage(newSubtype?.locale, newSubtype?.extraValue, announce = true)
    }

    // ---- composing languages (Hangul, kana, pinyin)
    private var composer: Composer? = null
    private var strip: CompactStripView? = null
    private var stripMode = StripMode.Hidden

    private val pinyin = PinyinLoader(this) { ui.post { refreshStrip() } }
    private var lastComposeAt = 0L
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreateCandidatesView(): View = CompactStripView(
        this,
        { i -> pickAt(i) },
        { s ->
            resolver.mods.consumeOneShots()
            commitOut(s)
            refreshStrip()
        },
        { chip -> clips.chipTapped(chip) },
        { chip -> sym.page?.let { SymGrid.symbolForChip(chip, it) }?.let(::pickSymbol) }
    ).also {
        strip = it
        refreshStrip()
    }

    override fun onComputeInsets(outInsets: Insets) = super.onComputeInsets(outInsets)
        .also { StripInsets.raise(outInsets, strip, isInputViewShown) }

    private fun applyComposition(commit: String) {
        val ic = currentInputConnection ?: return
        val comp = composer ?: return
        ic.beginBatchEdit()
        if (commit.isNotEmpty()) ic.commitText(commit, 1)
        val c = comp.composing
        if (c.isEmpty()) ic.finishComposingText() else ic.setComposingText(c, 1)
        ic.endBatchEdit()
        lastComposeAt = android.os.SystemClock.uptimeMillis()
        refreshStrip()
    }

    /**
     * Rebuilds the status strip from state the IME already holds and shows or hides the candidates view. Never
     * throws into the key path: a failed refresh must not cost the user a keystroke.
     */
    private fun refreshStrip() {
        val inputs = stripInputs()
        refreshModifierIcon(inputs)
        val model = runCatching { CompactStrip.build(inputs) }.getOrNull() ?: return
        strip?.render(model)
        if (model.mode != stripMode) {
            stripMode = model.mode
            setCandidatesViewShown(model.visible)
        }
        if (model.content is org.sableos.titan2.keyboard.core.StripContent.Variation) {
            ui.removeCallbacks(stripRefresh)
            ui.postDelayed(
                stripRefresh,
                org.sableos.titan2.keyboard.core.StripPolicy.VARIATION_TTL_MS
            )
        }
    }

    private val stripRefresh = Runnable { refreshStrip() }

    /** Status bar icon showing the active modifier while neither the strip nor the soft keyboard shows it. */
    private var modifierIcon = 0

    private fun refreshModifierIcon(i: StripInputs) {
        val icon = when {
            i.enabled || i.softKeyboardShown -> 0
            i.sym != ModState.Off -> R.drawable.ic_stat_mod_sym
            i.alt != ModState.Off -> R.drawable.ic_stat_mod_alt
            i.shift != ModState.Off -> R.drawable.ic_stat_mod_shift
            i.navOn -> R.drawable.ic_stat_mod_nav
            else -> 0
        }
        if (icon == modifierIcon) return
        modifierIcon = icon
        if (icon == 0) hideStatusIcon() else showStatusIcon(icon)
    }

    private fun stripInputs(): StripInputs = StripInputs(
        field = fieldClass,
        softKeyboardShown = isInputViewShown,
        enabled = prefs.compactStrip,
        language = language,
        shift = resolver.mods.state(Mod.Shift),
        alt = resolver.mods.state(Mod.Alt),
        sym = resolver.mods.state(Mod.Sym),
        navOn = resolver.navActive,
        navEffective = resolver.navEffective(fieldClass),
        candidates = composer?.candidates?.takeIf { it.isNotEmpty() }
            ?: suggester.suggestions.map { Cand(it.text, it.replaces) },
        holdOpen = suggester.holdOpen,
        variation = resolver.variation(),
        layout = resolver.layout,
        nowMs = android.os.SystemClock.uptimeMillis(),
        clips = clips.chips(),
        symGrid = sym.page?.let { SymGrid.chips(it) }
    )

    private fun dropComposition() {
        composer?.reset()
        refreshStrip()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )
        noSelection = newSelStart == newSelEnd
        updateSuggestions()
        val comp = composer ?: return
        if (comp.composing.isEmpty()) return
        val moved =
            candidatesStart >= 0 && (newSelStart < candidatesStart || newSelStart > candidatesEnd)
        val lost =
            candidatesStart < 0 &&
                android.os.SystemClock.uptimeMillis() - lastComposeAt > COMPOSITION_LOST_MS
        if (moved || lost) dropComposition()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        clips.close()
        sym.close()
        fieldClass = InputClass.None
        resolver.clearVariation()
        suggester.reset()
        corrector.clear()
        dropComposition()
    }

    private fun applyLanguage(tag: String?, extra: String?, announce: Boolean) {
        val l = org.sableos.titan2.keyboard.core.Languages.forTag(
            tag,
            org.sableos.titan2.keyboard.core.Languages.layoutOf(extra)
        )
        val changed = l.tag != language.tag || l.layout != language.layout
        if (changed) {
            composer?.let { c -> applyComposition(c.flush()) }
        }
        language = l
        resolver.language = l
        softView?.setLanguage(l)
        if (changed || (composer == null && l.engine != Engine.Direct)) {
            composer = Composers.create(l.engine, { pinyin.dict }, l.phonetic)
            if (l.engine == Engine.Pinyin) pinyin.loadOnce()
            refreshStrip()
        }
        refreshStrip()
        if (changed && announce) Toast.makeText(this, l.label, Toast.LENGTH_SHORT).show()
    }

    /** Sentence-start capitalisation for the on-screen shift, only in text fields that ask for it. */
    private fun shouldAutoCap(): Boolean =
        FieldPolicy.wantsSentenceCaps(currentInputEditorInfo?.inputType) &&
            Resolver.atSentenceStart(
                currentInputConnection
                    ?.getTextBeforeCursor(BEFORE_CURSOR_CHARS, 0)
                    ?.toString()
                    ?: ""
            )

    private fun doubleSpaceOn() =
        fieldClass == InputClass.Text && prefs.getBool("doubleSpace", true)

    private fun softAutoCorrect(typed: String) {
        if (typed == " ") autoCorrectOnSpace() else corrector.clear()
    }

    private fun softText(
        ic: android.view.inputmethod.InputConnection,
        comp: Composer?,
        text: String
    ) {
        when {
            text == " " && doubleSpaceOn() && edits.doubleSpacePeriod() -> Unit

            comp == null -> ic.commitText(text, 1)

            text == " " -> {
                val r = comp.space()
                applyComposition(r.commit)
                if (r.addSpace) ic.commitText(" ", 1)
            }

            else -> applyComposition(comp.type(text))
        }
    }

    private fun softBackspace(comp: Composer?) = when {
        comp != null && comp.backspace() -> applyComposition("")
        undoAutoCorrect() -> Unit
        else -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    /** Commits what is being composed, then runs an on-screen text edit. */
    private fun softEdit(comp: Composer?, edit: () -> Unit) {
        comp?.let { applyComposition(it.flush()) }
        edit()
    }

    private fun softKey(e: SoftKeyboardView.Event) {
        val ic = currentInputConnection ?: return
        // Secret fields (passwords, PINs) never go through composition: no candidates, no IME-held text.
        val comp = composer.takeUnless { fieldClass in FieldPolicy.SECRET_CLASSES }
        when (e) {
            is SoftKeyboardView.Event.Text -> {
                if (comp == null) softAutoCorrect(e.s)
                softText(ic, comp, e.s)
            }

            SoftKeyboardView.Event.Backspace -> softBackspace(comp)

            SoftKeyboardView.Event.Enter -> {
                if (comp !=
                    null
                ) {
                    applyComposition(comp.flush())
                }
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }

            SoftKeyboardView.Event.DeleteWord -> softEdit(comp) { edits.deleteWord() }

            is SoftKeyboardView.Event.MoveCursor -> softEdit(comp) { edits.moveCursor(e.steps) }

            SoftKeyboardView.Event.NextLanguage -> switchToNextInputMethod(true)

            SoftKeyboardView.Event.Hide -> {
                softToggled = false
                requestHideSelf(0)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (autoCorrectKey(keyCode, event) || handleShortcut(keyCode, event)) {
            handledDown.add(keyCode)
            refreshStrip()
            return true
        }
        val key = KeyMapper.map(keyCode, event.getUnicodeChar(0))
        val ours = !event.isCtrlPressed && !event.isMetaPressed && key !is Key.Other
        val handled = ours && (handleComposingKey(keyCode) || resolveKeyDown(key, keyCode, event))
        if (ours) refreshStrip()
        return handled || super.onKeyDown(keyCode, event)
    }

    /** History picker keys, candidate picks (Ctrl+1/2/3, Ctrl+W/E/R), Enter-to-send, Alt+Shift+V. */
    private fun handleShortcut(keyCode: Int, event: KeyEvent): Boolean =
        symGridKey(keyCode, event) ||
            editChord(keyCode, event) ||
            toggleSoftKeyboard(keyCode, event) ||
            clips.key(keyCode, event) ||
            pickCandidate(keyCode, event) ||
            acceptWithTab(keyCode, event) ||
            sendWithEnter(keyCode, event) ||
            clips.open(keyCode, event)

    // ---- Alt+Backspace deletes a word; Alt+Shift+I toggles incognito mode
    private val edits = TextEdits { currentInputConnection }

    private fun incognito() = prefs.getBool(INCOGNITO, false)

    private fun clipboardOn() = prefs.getBool(CLIP_HISTORY, false) && !incognito()

    private fun editChord(keyCode: Int, event: KeyEvent): Boolean {
        val alt = event.isAltPressed
        val ctrl = event.isCtrlPressed || event.isMetaPressed
        val hit = when {
            WordEdit.deletesWord(keyCode, alt, event.isShiftPressed, ctrl) -> {
                composer?.let { applyComposition(it.flush()) }
                edits.deleteWord()
                true
            }

            event.repeatCount == 0 &&
                WordEdit.togglesIncognito(keyCode, alt, event.isShiftPressed, ctrl) -> {
                setIncognito(!incognito())
                true
            }

            else -> false
        }
        if (hit) resolver.mods.markChordUse()
        return hit
    }

    private fun setIncognito(on: Boolean) {
        prefs.setBool(INCOGNITO, on)
        if (on) {
            ClipboardWatcher.history.clear()
            suggester.reset()
        }
        toast(t(if (on) "incognito_mode_on" else "incognito_mode_off"))
    }

    // ---- Sym grid (letter-labelled symbol chart opened by a Sym tap)
    private val sym = SymGridSession()

    /** A key while the Sym grid is open (letter types its symbol, Sym turns the page, Back closes). */
    private fun symGridKey(keyCode: Int, event: KeyEvent): Boolean {
        val action = sym.onDown(keyCode, event.repeatCount, event.eventTime)
        when (action) {
            is SymGrid.Action.Pick -> pickSymbol(action.symbol)
            SymGrid.Action.ClosePass -> refreshStrip()
            else -> Unit
        }
        return action != null && action != SymGrid.Action.Ignore &&
            action != SymGrid.Action.ClosePass
    }

    private fun pickSymbol(symbol: String) {
        sym.close()
        resolver.mods.consumeOneShots()
        composer?.let { applyComposition(it.flush()) }
        commitOut(symbol)
        refreshStrip()
    }

    private fun toggleSoftKeyboard(keyCode: Int, event: KeyEvent): Boolean {
        val hit = event.repeatCount == 0 && SymGrid.togglesSoftKeyboard(
            keyCode,
            event.isAltPressed,
            event.isShiftPressed,
            event.isCtrlPressed || event.isMetaPressed
        )
        if (hit) {
            resolver.mods.markChordUse()
            apply(Out.ToggleSoftKeyboard)
        }
        return hit
    }

    // ---- clipboard history picker
    private val clips = ClipPickerController(
        this,
        { clipboardOn() },
        { text ->
            composer?.let { applyComposition(it.flush()) }
            currentInputConnection?.commitText(text, 1)
        },
        { refreshStrip() },
        { resolver.mods.markChordUse() },
        { toast(it) }
    )

    private fun pickCandidate(keyCode: Int, event: KeyEvent): Boolean {
        val pick = KeyShortcuts.candidateIndex(
            keyCode,
            event.isCtrlPressed,
            event.isAltPressed || event.isMetaPressed || event.isShiftPressed,
            composer?.candidates?.size?.takeIf { it > 0 } ?: suggester.suggestions.size
        )
        return pick != null && pickAt(pick)
    }

    /** Picks candidate [i]: the composer's while one is composing, else the word suggestion. */
    private fun pickAt(i: Int): Boolean {
        val comp = composer
        if (comp != null && comp.candidates.isNotEmpty()) {
            applyComposition(comp.pick(i))
            return true
        }
        return acceptSuggestion(i)
    }

    // ---- word suggestions, learning and auto-correct (see SuggestionController, AutoCorrector)
    private val suggester = SuggestionController(this, ui) { refreshStrip() }
    private val corrector = AutoCorrector(this)
    private var noSelection = true

    private fun suggestionsAllowed(): Boolean = noSelection &&
        prefs.getBool(WORD_SUGGEST, true) &&
        fieldClass == InputClass.Text &&
        composer == null &&
        FieldPolicy.autoTextAllowed(currentInputEditorInfo?.inputType)

    private fun suggestionField(): SuggestionField {
        val allowed = suggestionsAllowed() &&
            (language.latin || Dictionaries.languageOf(language.tag) != null)
        val optOut = (currentInputEditorInfo?.imeOptions ?: 0) and
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        return SuggestionField(
            allowed,
            language.tag,
            allowed && !optOut && prefs.getBool(LEARN_WORDS, true) && !incognito()
        )
    }

    private fun updateSuggestions() = suggester.update(currentInputConnection, suggestionField())

    private fun acceptSuggestion(i: Int): Boolean =
        suggester.accept(currentInputConnection, i, suggestionField()).also {
            if (it) resolver.mods.consumeOneShots()
        }

    /** Space is about to be typed: fixes the word before it when auto-correct is on and sure. */
    private fun autoCorrectOnSpace() {
        val ic = currentInputConnection ?: return
        if (!prefs.getBool(AUTO_CORRECT, false)) return
        val english = language.tag.startsWith("en")
        corrector.onSpace(ic, suggestionField()) { before ->
            resolver.autoText.match(before, english, resolver.autoTextVar) != null
        }
    }

    /**
     * Space types after the word before it is fixed (the key then goes on as usual); Backspace straight after a fix
     * puts the typed word back and is consumed; any other key ends the chance to undo.
     */
    private fun autoCorrectKey(keyCode: Int, event: KeyEvent): Boolean {
        val plain = !event.isCtrlPressed && !event.isMetaPressed && !event.isAltPressed
        val spaceTypes = plain && event.repeatCount == 0 && !resolver.mods.active(Mod.Sym) &&
            !resolver.navEffective(fieldClass)
        return when {
            keyCode == KeyEvent.KEYCODE_DEL && plain -> undoAutoCorrect()

            keyCode == KeyEvent.KEYCODE_SPACE && spaceTypes -> {
                autoCorrectOnSpace()
                false
            }

            else -> {
                if (keyCode != KeyEvent.KEYCODE_SPACE) corrector.clear()
                false
            }
        }
    }

    /** Backspace right after an auto-correction puts back what was typed; true when it did. */
    private fun undoAutoCorrect(): Boolean {
        val ic = currentInputConnection ?: return false
        return corrector.undo(ic, suggestionField())
    }

    /** Tab alone takes the first suggestion while any is showing; otherwise it stays Tab. */
    private fun acceptWithTab(keyCode: Int, event: KeyEvent): Boolean =
        keyCode == KeyEvent.KEYCODE_TAB &&
            suggester.suggestions.isNotEmpty() &&
            event.repeatCount == 0 &&
            !event.isShiftPressed && !event.isAltPressed && !event.isCtrlPressed &&
            !event.isMetaPressed &&
            acceptSuggestion(0)

    private fun sendWithEnter(keyCode: Int, event: KeyEvent): Boolean {
        val send = keyCode == KeyEvent.KEYCODE_ENTER && event.repeatCount == 0 &&
            KeyShortcuts.enterSends(
                prefs.getBool(ENTER_SENDS, false),
                currentInputEditorInfo?.imeOptions,
                event.isShiftPressed || resolver.mods.active(Mod.Shift),
                event.isAltPressed || resolver.mods.active(Mod.Alt),
                event.isCtrlPressed || event.isMetaPressed
            )
        if (!send) return false
        composer?.let { applyComposition(it.flush()) }
        return currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_SEND) == true
    }

    /** Composition-aware Backspace/Enter/Space; true when the key was fully handled here. */
    private fun handleComposingKey(keyCode: Int): Boolean {
        val comp = composer ?: return false
        val composingPlain = comp.composing.isNotEmpty() &&
            !resolver.mods.active(Mod.Sym) &&
            !resolver.mods.active(Mod.Alt)
        val consumed = composingPlain && when (keyCode) {
            KeyEvent.KEYCODE_DEL -> comp.backspace().also { if (it) applyComposition("") }

            // then the Enter itself goes through as usual
            KeyEvent.KEYCODE_ENTER -> {
                applyComposition(comp.flush())
                false
            }

            KeyEvent.KEYCODE_SPACE -> {
                val r = comp.space()
                applyComposition(r.commit)
                !r.addSpace
            }

            else -> false
        }
        if (consumed) handledDown.add(keyCode)
        return consumed
    }

    /** Runs the pure resolver for a key-down; false when it asks the platform to handle the key. */
    private fun resolveKeyDown(key: Key, keyCode: Int, event: KeyEvent): Boolean {
        val outs = resolver.onKey(
            KeyEv(
                key,
                true,
                event.eventTime,
                event.repeatCount,
                event.isCtrlPressed,
                nativeAlt = nativeChar(event, KeyEvent.META_ALT_ON),
                nativeSym = nativeChar(event, KeyEvent.META_SYM_ON)
            ),
            fieldCtx()
        )
        val pass = outs.singleOrNull() === Out.Pass
        if (!pass) {
            outs.forEach { apply(it) }
            handledDown.add(keyCode)
        }
        return !pass
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val key = KeyMapper.map(keyCode, event.getUnicodeChar(0))
        // Before the resolver sees the release, so the tap does not also leave a one-shot modifier.
        val opened = sym.onUp(keyCode, event.eventTime, fieldClass != InputClass.None)
        if (opened) resolver.mods.markChordUse()
        if (key !is Key.Other && !sym.swallowsUp(keyCode)) {
            resolver.onKey(KeyEv(key, false, event.eventTime), fieldCtx())
        }
        if (key !is Key.Other || opened) refreshStrip()
        return handledDown.remove(keyCode) || super.onKeyUp(keyCode, event)
    }

    /**
     * Character the device's own .kcm gives for this key with [meta], or null when it equals the plain character or
     * is not printable.
     */
    private fun nativeChar(event: KeyEvent, meta: Int): Char? {
        val base = event.getUnicodeChar(0)
        val withMeta = event.getUnicodeChar(meta)
        return if (withMeta != 0 && withMeta != base &&
            withMeta and KeyCharacterMap.COMBINING_ACCENT == 0
        ) {
            withMeta.toChar()
        } else {
            null
        }
    }

    private fun apply(o: Out) {
        when (o) {
            is Out.Commit -> commitOut(o.text)

            is Out.DeleteBefore -> {
                composer?.let { applyComposition(it.flush()) }
                currentInputConnection?.deleteSurroundingText(o.n, 0)
            }

            is Out.Compose -> composer?.let { comp -> applyComposition(comp.type(composeText(o))) }

            is Out.Send -> sendNav(o)

            is Out.NavMode -> toast(if (o.on) t("nav_mode_on_alt_space") else t("nav_mode_off"))

            Out.NextLanguage -> if (!switchToNextInputMethod(true)) {
                toast(t("enable_more_languages_in_settings"))
            }

            Out.ToggleSoftKeyboard -> {
                softToggled = !softToggled
                updateInputViewShown()
                ui.post { refreshStrip() }
            }

            Out.Consume, Out.Pass -> Unit
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun commitOut(text: String) {
        val comp = composer
        if (comp !=
            null
        ) {
            applyComposition(comp.type(text))
        } else {
            currentInputConnection?.commitText(text, 1)
        }
    }

    private fun composeText(o: Out.Compose): String = when (language.engine) {
        Engine.Hangul -> (Dubeolsik.jamo(o.c, o.shift) ?: o.c).toString()

        Engine.Phonetic -> (if (o.shift) o.c.uppercaseChar() else o.c).toString()

        // case selects T/D/N/L/Sh etc.
        else -> o.c.toString()
    }

    private fun sendNav(o: Out.Send) {
        val code = KeyMapper.toKeyCode(o.key) ?: return
        val ic = currentInputConnection
        if (o.shift) ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT))
        sendDownUpKeyEvents(code)
        if (o.shift) ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT))
    }

    private fun fieldCtx(): FieldCtx {
        val ei = currentInputEditorInfo
        val ic = currentInputConnection
        val cls = classify(ei)
        val before = if (ic != null &&
            cls != InputClass.None
        ) {
            ic.getTextBeforeCursor(BEFORE_CURSOR_CHARS, 0)?.toString().orEmpty()
        } else {
            ""
        }
        val capFlags = ei?.inputType?.and(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) ?: 0
        return FieldCtx(
            cls,
            before,
            autoCapAllowed = capFlags != 0,
            autoText = FieldPolicy.autoTextAllowed(ei?.inputType)
        )
    }

    private fun classify(ei: EditorInfo?): InputClass = FieldPolicy.classify(ei?.inputType)
}
