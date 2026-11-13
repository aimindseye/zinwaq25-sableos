package org.sableos.games.console

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The console's key handler for whichever screen is showing (down and up, so held keys steer). */
class ConsoleKeyHost {
    var handler: ((KeyEvent) -> Boolean)? = null
}

val LocalConsoleKeys = staticCompositionLocalOf { ConsoleKeyHost() }

/** Takes key events while the calling screen is shown; returning false leaves a key to the system. */
@Composable
fun ConsoleKeyHandler(onKey: (KeyEvent) -> Boolean) {
    val host = LocalConsoleKeys.current
    val latest by rememberUpdatedState(onKey)
    DisposableEffect(host) {
        host.handler = { latest(it) }
        onDispose { host.handler = null }
    }
}

/** The colours the games name, matched to the light or dark Sable theme. */
object ConsolePalette {
    val Tilt = Color(0xFFFFB74D)
    private val SHARED =
        mapOf(
            "player" to Color(0xFFFFB74D),
            "goal" to Color(0xFF2F8F5B),
            "good" to Color(0xFF2F8F5B),
            "box" to Color(0xFF9C7440),
            "warn" to Color(0xFFE5493A),
        )

    @Composable
    fun colors(): Map<String, Color> {
        val scheme = MaterialTheme.colorScheme
        val dark = scheme.background.luminance() < HALF
        return SHARED +
            mapOf(
                "bg" to scheme.background,
                "text" to scheme.onBackground,
                "dim" to scheme.onSurfaceVariant,
                "accent" to scheme.primary,
                "wall" to scheme.primary,
                "floor" to if (dark) Color(0xFF0E0E0E) else Color(0xFFECECEC),
            )
    }

    fun resolve(
        name: String,
        named: Map<String, Color>,
    ): Color {
        named[name]?.let { return it }
        val hex = name.removePrefix("#")
        val rgb = if (name.startsWith("#") && hex.length == HEX_RGB) hex.toLongOrNull(HEX_RADIX) else null
        return rgb?.let { Color(OPAQUE or it) } ?: named.getValue("text")
    }

    private const val HALF = 0.5f
    private const val HEX_RGB = 6
    private const val HEX_RADIX = 16
    private const val OPAQUE = 0xFF000000L
}

/** A game's frame: the shapes from `draw()`, scaled so the grid fills the square. */
@Composable
fun GameCanvas(
    ops: List<DrawOp>,
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier,
) {
    val named = ConsolePalette.colors()
    val paint =
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    Canvas(modifier) {
        val cell = minOf(size.width / cols, size.height / rows)
        val left = (size.width - cell * cols) / 2
        val top = (size.height - cell * rows) / 2
        for (op in ops) {
            val color = ConsolePalette.resolve(op.color, named)
            when (op) {
                is DrawOp.Rect -> {
                    drawRoundRect(
                        color,
                        Offset(left + op.x.toFloat() * cell, top + op.y.toFloat() * cell),
                        Size(op.w.toFloat() * cell, op.h.toFloat() * cell),
                        CornerRadius(cell * CORNER),
                    )
                }

                is DrawOp.Circle -> {
                    drawCircle(
                        color,
                        op.r.toFloat() * cell,
                        Offset(
                            left + op.x.toFloat() * cell,
                            top + op.y.toFloat() * cell,
                        ),
                    )
                }

                is DrawOp.Text -> {
                    paint.color = color.toArgb()
                    paint.textSize = op.size.toFloat() * cell
                    val baseline = top + op.y.toFloat() * cell - (paint.descent() + paint.ascent()) / 2
                    drawContext.canvas.nativeCanvas.drawText(op.text, left + op.x.toFloat() * cell, baseline, paint)
                }
            }
        }
    }
}

private const val CORNER = 0.08f

/** The Hub title line: a lower-case title and, on the right, a short note. */
@Composable
fun ConsoleTitle(
    title: String,
    note: String = "",
    back: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (back != null) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = back),
            )
        }
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (note.isNotEmpty()) {
            Text(
                note,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun ConsoleTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Column(modifier = Modifier.clickable { onSelect(i) }) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .height(2.dp)
                        .width(if (on) 24.dp else 0.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** A section label above rows, like "CONTROLS". */
@Composable
fun ConsoleSection(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** A Hub row: [lead] on the left, a title and a line under it, [trail] on the right. */
@Composable
fun ConsoleRow(
    title: String,
    line: String = "",
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    lead: @Composable (() -> Unit)? = null,
    trail: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(if (line.isEmpty()) 44.dp else 52.dp)
                    .background(if (selected) scheme.surfaceVariant else Color.Transparent)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            lead?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line.isNotEmpty()) {
                    Text(
                        line,
                        fontSize = 14.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trail()
        }
        HorizontalDivider(color = scheme.surfaceVariant)
    }
}

/** The small TILT or KEYS tag on a row. */
@Composable
fun ConsoleBadge(
    text: String,
    tilt: Boolean,
) {
    val color = if (tilt) ConsolePalette.Tilt else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text,
        fontSize = 11.sp,
        color = color,
        modifier =
            Modifier
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** A game's tile: its colour and first letter. */
@Composable
fun GameTile(
    game: GameManifest,
    side: Int = 36,
) {
    Box(
        modifier = Modifier.size(side.dp).clip(RoundedCornerShape(4.dp)).background(tileColor(game.id)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            game.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (side * TILE_LETTER).sp,
        )
    }
}

private const val TILE_LETTER = 0.45f

private val TILE_COLORS =
    listOf(
        Color(0xFF2E9BD6),
        Color(0xFFE07A2E),
        Color(0xFF6B5BB5),
        Color(0xFF2F8F5B),
        Color(0xFFB5465B),
        Color(0xFF3D7A8C),
    )

private val KNOWN_TILES = mapOf("tilt_maze" to 0, "motion_2048" to 1, "sokoban_tilt" to 2, "tilt_racer" to 3)

fun tileColor(id: String): Color = TILE_COLORS[KNOWN_TILES[id] ?: Math.floorMod(id.hashCode(), TILE_COLORS.size)]

/** One action in the bottom bar. */
data class ConsoleAction(
    val label: String,
    val primary: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ConsoleActionBar(actions: List<ConsoleAction>) {
    val scheme = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = scheme.surfaceVariant)
        Row(modifier = Modifier.fillMaxWidth().height(50.dp).background(scheme.surface)) {
            actions.forEach { action ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize().clickable(onClick = action.onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        action.label,
                        fontSize = 13.sp,
                        fontWeight = if (action.primary) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (action.primary) scheme.primary else scheme.onSurface,
                    )
                }
            }
        }
    }
}

/** A thin line of hints at the bottom of a screen. */
@Composable
fun ConsoleHint(text: String) {
    val scheme = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = scheme.surfaceVariant)
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
