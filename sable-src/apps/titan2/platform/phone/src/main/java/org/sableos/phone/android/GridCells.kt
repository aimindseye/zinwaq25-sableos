package org.sableos.phone.android

import android.widget.GridLayout

/** An equal-width cell of a key grid, with [gap] dp between cells. */
fun gridCell(ui: Ui, gap: Int): GridLayout.LayoutParams = GridLayout.LayoutParams(
    GridLayout.spec(GridLayout.UNDEFINED, 1f),
    GridLayout.spec(GridLayout.UNDEFINED, 1f)
).apply {
    width = 0
    val half = ui.dp(gap) / 2
    setMargins(half, half, half, half)
}

/** Like [gridCell], and the row shares the grid's height too, so a weighted grid fits any screen. */
fun fittedGridCell(ui: Ui, gap: Int): GridLayout.LayoutParams = gridCell(ui, gap).apply {
    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
    height = 0
}
