package org.sableos.titan2.keyboard.core

/**
 * Turns raw key events into outputs. Rules (from the Sable keyboard-first docs):
 *  - text input always wins: Nav mode suspends in password and numeric fields;
 *  - a missing Alt/Sym mapping falls back to the plain character instead of swallowing the key;
 *  - non-editable focus passes everything through, so launcher/app navigation is untouched.
 */
class Resolver(
    val layout: KeyLayout = KeyLayout.SableProvisional,
    cfg: KeyboardConfig = KeyboardConfig()
) {
    var cfg: KeyboardConfig = cfg
        set(v) {
            field = v
            mods.cfg = v
        }
    val mods = ModifierMachine(cfg)
    var navActive: Boolean = false
        private set

    /** Active typing language; supplies accent variants for Sym+letter. */
    var language: Language = Languages.English

    // (base letter, index of the variant currently on screen)
    private var accentCycle: Pair<Char, Int>? = null
    private var accentOptions: List<String> = emptyList()
    private var accentAt = 0L

    /**
     * Read-only view of the Sym+letter accent cycle currently on screen, for the status strip. It is
     * the existing cycle state, not a new input path; null when no cycle is active.
     */
    fun variation(): VariationInfo? = accentCycle?.let { (base, idx) ->
        VariationInfo(base, accentOptions, idx, accentAt)
    }

    /** Forget the accent cycle (field change). */
    fun clearVariation() {
        accentCycle = null
        accentOptions = emptyList()
    }

    // letter whose long-press replacement was already committed
    private var longPressed: Char? = null

    /** Modifier and Nav state captured once per key press. */
    private class Held(val shift: Boolean, val alt: Boolean, val sym: Boolean, val nav: Boolean)

    fun setNav(on: Boolean) {
        navActive = on && cfg.navModeEnabled
    }

    fun onKey(ev: KeyEv, ctx: FieldCtx): List<Out> {
        val k = ev.key
        if (k is Key.Modifier) return onModifier(k, ev, ctx)
        mods.expire(ev.time)
        return if (ev.down) onKeyDown(k, ev, ctx) else onKeyUp(k)
    }

    private fun onModifier(k: Key.Modifier, ev: KeyEv, ctx: FieldCtx): List<Out> {
        // never eat modifiers outside text fields: app shortcuts keep working
        if (!ctx.editable) return listOf(Out.Pass)
        if (ev.down) mods.down(k.m, ev.time) else mods.up(k.m, ev.time)
        return listOf(Out.Consume)
    }

    // glue consumes the up of any key whose down it handled
    private fun onKeyUp(k: Key): List<Out> {
        if (k is Key.Letter && longPressed == k.c) longPressed = null
        return listOf(Out.Pass)
    }

    private fun onKeyDown(k: Key, ev: KeyEv, ctx: FieldCtx): List<Out> {
        if (ev.ctrlHeld || k is Key.Ctrl || !ctx.editable) return listOf(Out.Pass)

        mods.markChordUse()
        if (!(k is Key.Letter && mods.active(Mod.Sym))) clearVariation()
        val shiftOn = mods.active(Mod.Shift)
        val altOn = mods.active(Mod.Alt)
        val symOn = mods.active(Mod.Sym)

        return when (k) {
            is Key.Letter -> letter(k.c, ev, ctx, Held(shiftOn, altOn, symOn, navAllowed(ctx)))
            is Key.Space -> space(ev, ctx, altOn)
            is Key.Digit -> commit(k.c.toString(), shiftOn)
            is Key.Punct -> punct(k, shiftOn)
            is Key.Backspace -> backspace(ev, shiftOn)
            else -> listOf(Out.Pass)
        }
    }

    private fun navAllowed(ctx: FieldCtx): Boolean = navEffective(ctx.inputClass)

    /** True when Nav mode is on and not suspended by the field class (read-only, for the status strip). */
    fun navEffective(cls: InputClass): Boolean =
        navActive && cfg.navModeEnabled && cls !in NO_NAV_CLASSES

    private fun punct(k: Key.Punct, shiftOn: Boolean): List<Out> = commit(
        (if (shiftOn) layout.shiftPunct[k.c] ?: k.c else k.c).toString(),
        false,
        spend = true
    )

    private fun backspace(ev: KeyEv, shiftOn: Boolean): List<Out> = if (shiftOn && !ev.isRepeat()) {
        mods.consumeOneShots()
        listOf(Out.Send(Key.Delete))
    } else {
        listOf(Out.Pass)
    }

    private fun KeyEv.isRepeat() = repeat > 0

    private fun letter(c: Char, ev: KeyEv, ctx: FieldCtx, held: Held): List<Out> = when {
        held.nav -> navKey(c, held.shift)
        ev.repeat >= 1 -> longPress(c, ev, held)
        else -> shortPress(c, ev, ctx, held)
    }

    private fun navKey(c: Char, shift: Boolean): List<Out> =
        layout.navMap[c]?.let { listOf(Out.Send(it, shift = shift)) } ?: listOf(Out.Consume)

    /** Long press: replace the base char just committed with its Alt character. */
    private fun longPress(c: Char, ev: KeyEv, held: Held): List<Out> {
        val altChar = if (language.engine != Engine.Direct || longPressed == c) {
            null
        } else {
            ev.nativeAlt?.toString() ?: layout.alt[c]
        }
        return if (altChar != null && canLongPressReplace(ev, held)) {
            longPressed = c
            listOf(Out.DeleteBefore(1), Out.Commit(altChar))
        } else {
            listOf(Out.Consume)
        }
    }

    private fun canLongPressReplace(ev: KeyEv, held: Held): Boolean =
        cfg.longPressAlt && ev.repeat == 1 && !held.alt && !held.sym

    private fun shortPress(c: Char, ev: KeyEv, ctx: FieldCtx, held: Held): List<Out> {
        val accented = if (held.sym && ev.nativeSym == null) cycleAccent(c, ev.time) else null
        return accented ?: replaceOrType(c, ev, ctx, held)
    }

    /** Sym+letter accent cycling; null when the language has no variants for [c]. */
    private fun cycleAccent(c: Char, time: Long): List<Out>? {
        val vs = language.variants(c)
        if (vs.isEmpty()) return null
        val base = c.lowercaseChar()
        val prevIndex = accentCycle?.takeIf { it.first == base }?.second
        val idx = if (prevIndex != null) (prevIndex + 1) % vs.size else 0
        accentCycle = base to idx
        accentOptions = vs
        accentAt = time
        mods.consumeOneShots()
        return if (prevIndex != null) {
            listOf(
                Out.DeleteBefore(1),
                Out.Commit(vs[idx])
            )
        } else {
            listOf(Out.Commit(vs[idx]))
        }
    }

    private fun replaceOrType(c: Char, ev: KeyEv, ctx: FieldCtx, held: Held): List<Out> {
        val numeric = cfg.numericAutoAlt && ctx.inputClass in NUMERIC_CLASSES
        val replacement = when {
            held.sym -> ev.nativeSym?.toString() ?: layout.sym[c]
            held.alt || numeric -> ev.nativeAlt?.toString() ?: layout.alt[c]
            else -> null
        }
        return when {
            replacement != null -> {
                mods.consumeOneShots()
                listOf(Out.Commit(replacement))
            }

            language.engine != Engine.Direct && ctx.inputClass !in FieldPolicy.SECRET_CLASSES -> {
                mods.consumeOneShots()
                listOf(Out.Compose(c, held.shift))
            }

            else -> directLetter(c, ctx, held.shift)
        }
    }

    private fun directLetter(c: Char, ctx: FieldCtx, shift: Boolean): List<Out> {
        val capsLock = mods.state(Mod.Shift) == ModState.Lock
        val auto = cfg.autoCap && ctx.autoCapAllowed && ctx.inputClass == InputClass.Text &&
            atSentenceStart(ctx.before)
        val upper = shift || (auto && !capsLock)
        mods.consumeOneShots()
        return listOf(Out.Commit(if (upper) c.uppercaseChar().toString() else c.toString()))
    }

    private fun commit(s: String, shift: Boolean, spend: Boolean = true): List<Out> {
        if (spend) mods.consumeOneShots()
        return listOf(Out.Commit(if (shift) s.uppercase() else s))
    }

    private fun space(ev: KeyEv, ctx: FieldCtx, alt: Boolean): List<Out> = when {
        ev.repeat > 0 -> listOf(Out.Consume)

        mods.active(Mod.Sym) -> {
            mods.consumeOneShots()
            listOf(Out.NextLanguage)
        }

        alt && cfg.altSpaceTogglesNav && cfg.navModeEnabled -> {
            mods.consumeOneShots()
            navActive = !navActive
            listOf(Out.NavMode(navActive))
        }

        else -> {
            mods.consumeOneShots()
            plainSpace(ctx)
        }
    }

    private fun plainSpace(ctx: FieldCtx): List<Out> =
        if (cfg.doubleSpacePeriod && ctx.inputClass == InputClass.Text &&
            endsWordSpace(ctx.before)
        ) {
            listOf(Out.DeleteBefore(1), Out.Commit(". "))
        } else {
            listOf(Out.Commit(" "))
        }

    private fun endsWordSpace(b: String): Boolean =
        b.length >= 2 && b.last() == ' ' && b[b.length - 2].isLetterOrDigit()

    companion object {
        private const val SENTENCE_WINDOW = 3
        private val NO_NAV_CLASSES =
            setOf(
                InputClass.Password,
                InputClass.NumberPassword,
                InputClass.Number,
                InputClass.Phone
            )
        private val NUMERIC_CLASSES = FieldPolicy.DIGIT_ENTRY_CLASSES

        /** True at the start of the field, after a newline, or after sentence punctuation + space. */
        fun atSentenceStart(before: String): Boolean {
            val t = before.takeLast(SENTENCE_WINDOW)
            return before.isEmpty() || t.last() == '\n' || endsSentenceSpace(t)
        }

        private fun endsSentenceSpace(t: String): Boolean =
            t.length >= 2 && t.last() == ' ' && t[t.length - 2] in ".!?"
    }
}
