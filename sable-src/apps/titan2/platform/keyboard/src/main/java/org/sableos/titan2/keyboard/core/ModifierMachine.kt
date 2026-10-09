package org.sableos.titan2.keyboard.core

enum class ModState { Off, OneShot, Lock, Held }

/**
 * Shift/Alt/Sym behaviour for a physical keyboard:
 *  - tap        -> one-shot (applies to the next character, then clears)
 *  - double tap -> lock (until tapped again)
 *  - hold + key -> chord (applies while held; clears on release, never leaves a stray one-shot)
 *  - long hold with no key -> cancelled (so resting a thumb on Shift does not arm it)
 */
class ModifierMachine(var cfg: KeyboardConfig = KeyboardConfig()) {
    private class S {
        var state = ModState.Off
        var downAt = 0L
        var lastTapAt = -1L
        var used = false
        var wasLock = false
        var armedAt = 0L
    }
    private val s = Mod.entries.associateWith { S() }

    fun state(m: Mod): ModState = s.getValue(m).state
    fun active(m: Mod): Boolean = s.getValue(m).state != ModState.Off

    fun down(m: Mod, t: Long) {
        val x = s.getValue(m)
        if (x.state == ModState.Held) return // key repeat
        x.wasLock = x.state == ModState.Lock
        x.downAt = t
        x.used = false
        if (!x.wasLock) x.state = ModState.Held
    }

    fun up(m: Mod, t: Long) {
        val x = s.getValue(m)
        if (x.wasLock) {
            x.state = ModState.Off
            x.wasLock = false
            x.lastTapAt = -1L
            return
        }
        if (x.state != ModState.Held) return
        val heldFor = t - x.downAt
        x.state = when {
            x.used -> ModState.Off

            // chord finished
            heldFor > cfg.holdCancelMs -> ModState.Off

            // long press, no key: cancel
            x.lastTapAt >= 0 && t - x.lastTapAt <= cfg.doubleTapMs -> ModState.Lock

            else -> ModState.OneShot
        }
        x.lastTapAt = if (x.state == ModState.OneShot) t else -1L
        if (x.state == ModState.OneShot) x.armedAt = t
    }

    /** Another key was pressed while modifiers are held: those modifiers are being used as a chord. */
    fun markChordUse() {
        s.values.forEach { if (it.state == ModState.Held) it.used = true }
    }

    /** Call after a character has been produced: one-shots are spent; held and locked modifiers stay. */
    fun consumeOneShots() {
        s.values.forEach {
            if (it.state ==
                ModState.OneShot
            ) {
                it.state = ModState.Off
                it.lastTapAt = -1L
            }
        }
    }

    fun expire(t: Long) {
        if (cfg.oneShotTimeoutMs <= 0) return
        s.values.forEach {
            if (it.state == ModState.OneShot &&
                t - it.armedAt > cfg.oneShotTimeoutMs
            ) {
                it.state = ModState.Off
            }
        }
    }

    fun reset() {
        s.values.forEach {
            it.state = ModState.Off
            it.lastTapAt = -1L
            it.used = false
            it.wasLock =
                false
        }
    }
}
