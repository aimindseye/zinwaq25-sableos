package org.sableos.titan2.keyboard.core

/** State of the Sym grid: the open page, and whether the current Sym press is a lone tap. Pure, no Android. */
class SymGridSession {
    /** The page showing, or null while the grid is closed. */
    var page: Int? = null
        private set
    private var downAt = -1L
    private var chorded = false
    private var swallowUp = false

    fun close() {
        page = null
    }

    /**
     * A key-down. While the grid is open this returns what the key does (and applies page turns and closes); while
     * it is closed it only tracks whether Sym is being used as a chord, and returns null.
     */
    fun onDown(keyCode: Int, repeat: Int, eventTime: Long): SymGrid.Action? {
        val p = page
        if (p == null) {
            track(keyCode, repeat, eventTime)
            return null
        }
        val action = SymGrid.action(keyCode, p)
        when (action) {
            is SymGrid.Action.Pick, SymGrid.Action.Close, SymGrid.Action.ClosePass -> page = null

            SymGrid.Action.NextPage -> {
                swallowUp = true
                if (repeat == 0) page = SymGrid.nextPage(p)
            }

            SymGrid.Action.Ignore -> Unit
        }
        return action
    }

    private fun track(keyCode: Int, repeat: Int, eventTime: Long) {
        if (SymGrid.isSymKey(keyCode) && repeat == 0) {
            downAt = eventTime
            chorded = false
        } else if (downAt >= 0) {
            chorded = true
        }
    }

    /** A key-up: opens the grid after a lone Sym tap in an editable field. True when it opened. */
    fun onUp(keyCode: Int, eventTime: Long, editable: Boolean): Boolean {
        val tap = SymGrid.isSymKey(keyCode) && downAt >= 0 &&
            SymGrid.isTap(eventTime - downAt, chorded)
        downAt = -1L
        val open = tap && editable && page == null
        if (open) page = 0
        return open
    }

    /** True once for the release of a Sym press whose press turned a page: the resolver never saw its press. */
    fun swallowsUp(keyCode: Int): Boolean {
        val swallow = SymGrid.isSymKey(keyCode) && swallowUp
        if (swallow) swallowUp = false
        return swallow
    }
}
