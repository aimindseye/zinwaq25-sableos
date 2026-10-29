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
import org.sableos.titan2.keyboard.core.ClipAction
import org.sableos.titan2.keyboard.core.ClipPicker
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
import org.sableos.titan2.keyboard.core.Mod
import org.sableos.titan2.keyboard.core.ModState
import org.sableos.titan2.keyboard.core.Out
import org.sableos.titan2.keyboard.core.PinyinDict
import org.sableos.titan2.keyboard.core.Resolver
import org.sableos.titan2.keyboard.core.StripInputs
import org.sableos.titan2.keyboard.core.StripMode

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
        ClipboardWatcher.start(this) { prefs.getBool(CLIP_HISTORY, false) }
    }

    override fun onDestroy() {
        ClipboardWatcher.stop()
        super.onDestroy()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resolver.cfg = prefs.load()
        resolver.autoText = AutoText(prefs.autoTextEntries)
        if (!prefs.getBool(CLIP_HISTORY, false)) ClipboardWatcher.history.clear()
        if (!restarting) {
            resolver.mods.reset()
            handledDown.clear()
            softToggled = false
        }
        fieldClass = classify(attribute)
        resolver.clearVariation()
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
                softView = it
            }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // currentInputMethodSubtype is an InputMethodManager property, not an InputMethodService one.
        val subtype = getSystemService(InputMethodManager::class.java)?.currentInputMethodSubtype
        applyLanguage(subtype?.locale, subtype?.extraValue, announce = false)
        softView?.refreshAutoCap()
        refreshStrip()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        applyLanguage(newSubtype?.locale, newSubtype?.extraValue, announce = true)
    }

    // ---- composing languages (Hangul, kana, pinyin)
    private var composer: Composer? = null
    private var strip: CompactStripView? = null
    private var stripMode = StripMode.Hidden

    @Volatile private var pinyinDict: PinyinDict? = null
    private var pinyinLoading = false
    private var lastComposeAt = 0L
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreateCandidatesView(): View = CompactStripView(
        this,
        { i -> composer?.let { applyComposition(it.pick(i)) } },
        { s ->
            resolver.mods.consumeOneShots()
            commitOut(s)
            refreshStrip()
        },
        { chip -> clipSelected?.let { ClipPicker.entryForChip(chip, it) }?.let(::pasteClip) }
    ).also {
        strip = it
        refreshStrip()
    }

    private fun loadPinyinOnce() {
        if (pinyinDict != null || pinyinLoading) return
        pinyinLoading = true
        Thread {
            try {
                val d = assets.open("pinyin_zh.tsv").bufferedReader(Charsets.UTF_8).use {
                    PinyinDict.parse(it.lineSequence())
                }
                pinyinDict = d
                ui.post { refreshStrip() }
            } catch (_: Exception) {
                pinyinLoading = false
            }
        }.start()
    }

    /**
     * Commits [commit] (which replaces the old composing region), then shows the composer's new composing text and
     * candidates.
     */
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
        candidates = composer?.candidates ?: emptyList(),
        variation = resolver.variation(),
        layout = resolver.layout,
        nowMs = android.os.SystemClock.uptimeMillis(),
        clips = clipSelected?.let { ClipPicker.chips(ClipboardWatcher.history.entries, it) }
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
        clipSelected = null
        fieldClass = InputClass.None
        resolver.clearVariation()
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
            composer = Composers.create(l.engine, { pinyinDict }, l.phonetic)
            if (l.engine == Engine.Pinyin) loadPinyinOnce()
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

    private fun softKey(e: SoftKeyboardView.Event) {
        val ic = currentInputConnection ?: return
        // Secret fields (passwords, PINs) never go through composition: no candidates, no IME-held text.
        val comp = composer.takeUnless { fieldClass in FieldPolicy.SECRET_CLASSES }
        when (e) {
            is SoftKeyboardView.Event.Text -> when {
                comp == null -> ic.commitText(e.s, 1)

                e.s == " " -> {
                    val r = comp.space()
                    applyComposition(r.commit)
                    if (r.addSpace) ic.commitText(" ", 1)
                }

                else -> applyComposition(comp.type(e.s))
            }

            SoftKeyboardView.Event.Backspace -> if (comp != null &&
                comp.backspace()
            ) {
                applyComposition("")
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }

            SoftKeyboardView.Event.Enter -> {
                if (comp !=
                    null
                ) {
                    applyComposition(comp.flush())
                }
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }

            SoftKeyboardView.Event.NextLanguage -> switchToNextInputMethod(true)

            SoftKeyboardView.Event.Hide -> {
                softToggled = false
                requestHideSelf(0)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleShortcut(keyCode, event)) {
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
    private fun handleShortcut(keyCode: Int, event: KeyEvent): Boolean = clipKey(keyCode, event) ||
        pickCandidate(keyCode, event) ||
        sendWithEnter(keyCode, event) ||
        openClips(keyCode, event)

    // ---- clipboard history picker: null while closed, else the selected entry
    private var clipSelected: Int? = null

    private fun openClips(keyCode: Int, event: KeyEvent): Boolean {
        val opens = event.repeatCount == 0 && ClipPicker.opens(
            keyCode,
            event.isAltPressed,
            event.isShiftPressed,
            event.isCtrlPressed || event.isMetaPressed
        )
        if (!opens) return false
        // The held Alt and Shift were a chord: their release must not leave a one-shot.
        resolver.mods.markChordUse()
        when {
            !prefs.getBool(CLIP_HISTORY, false) ->
                toast("Turn on clipboard history in Sable Keyboard settings")

            getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true ->
                toast("Unlock to use clipboard history")

            else -> clipSelected = 0
        }
        return true
    }

    /** A key while the picker is open; false when the picker is closed or the key should type as usual. */
    private fun clipKey(keyCode: Int, event: KeyEvent): Boolean {
        val selected = clipSelected ?: return false
        val history = ClipboardWatcher.history
        val count = history.entries.size
        val action = ClipPicker.action(keyCode, event.isShiftPressed)
        when (action) {
            ClipAction.Next -> clipSelected = ClipPicker.move(selected, 1, count)

            ClipAction.Previous -> clipSelected = ClipPicker.move(selected, -1, count)

            ClipAction.Paste -> pasteClip(selected)

            ClipAction.Remove -> {
                history.remove(selected)
                clipSelected = selected.coerceAtMost((count - 2).coerceAtLeast(0))
            }

            ClipAction.Close, ClipAction.CloseAndPass -> clipSelected = null

            ClipAction.Ignore -> Unit
        }
        if (action == ClipAction.CloseAndPass) refreshStrip()
        return action != ClipAction.Ignore && action != ClipAction.CloseAndPass
    }

    private fun pasteClip(index: Int) {
        val text = ClipboardWatcher.history.entries.getOrNull(index)
        clipSelected = null
        if (text != null) {
            composer?.let { applyComposition(it.flush()) }
            currentInputConnection?.commitText(text, 1)
        }
        refreshStrip()
    }

    private fun pickCandidate(keyCode: Int, event: KeyEvent): Boolean {
        val comp = composer
        val pick = KeyShortcuts.candidateIndex(
            keyCode,
            event.isCtrlPressed,
            event.isAltPressed || event.isMetaPressed || event.isShiftPressed,
            comp?.candidates?.size ?: 0
        )
        if (comp == null || pick == null) return false
        applyComposition(comp.pick(pick))
        return true
    }

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
        if (key !is Key.Other) resolver.onKey(KeyEv(key, false, event.eventTime), fieldCtx())
        if (key !is Key.Other) refreshStrip()
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

            is Out.NavMode -> toast(if (o.on) "Nav mode on (Alt+Space to exit)" else "Nav mode off")

            Out.NextLanguage -> if (!switchToNextInputMethod(true)) {
                toast("Enable more languages in Settings > Keyboard > Sable")
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
