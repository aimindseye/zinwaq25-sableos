package org.sableos.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sableos.design.sableFocusRing
import org.sableos.media.MediaText.t

/*
 * Sable Media on the Q25, design A "Hub list" (owner pick, 2026-11-13): Hub's type and layout on the
 * square screen. One 40 dp title bar (22 sp) with search and a menu, one 30 dp strip of 13 sp tabs for
 * the library and the sources, Hub's rows, a 40 dp line for what is playing, and a 50 dp action bar with
 * 11 sp labels. Taller windows keep the hero header layout.
 */

/** The Q25 tab strip: the library views, then the sources. */
internal enum class MediaHubTab(
    private val labelKey: String,
    val destination: MediaDestination,
    val pivot: LibraryPivot? = null,
) {
    Songs("hub_tab_songs", MediaDestination.Collection, LibraryPivot.Songs),
    Artists("hub_tab_artists", MediaDestination.Collection, LibraryPivot.Artists),
    Albums("hub_tab_albums", MediaDestination.Collection, LibraryPivot.Albums),
    Playlists("hub_tab_playlists", MediaDestination.Collection, LibraryPivot.Playlists),
    Favorites("hub_tab_favourites", MediaDestination.Collection, LibraryPivot.Favorites),
    Podcasts("hub_tab_podcasts", MediaDestination.Podcasts),
    Radio("hub_tab_radio", MediaDestination.Radio),
    Fm("hub_tab_fm", MediaDestination.Fm),
    ;

    val label: String get() = t(labelKey)

    companion object {
        fun of(
            destination: MediaDestination,
            pivot: LibraryPivot,
        ): MediaHubTab? =
            when (destination) {
                MediaDestination.Collection -> entries.first { it.pivot == pivot }
                MediaDestination.Podcasts -> Podcasts
                MediaDestination.Radio, MediaDestination.ManageStations, MediaDestination.AddStation -> Radio
                MediaDestination.Fm -> Fm
                else -> null
            }
    }
}

/** Destinations drawn inside the Q25 Hub frame; the rest (settings, editors, queue) keep their own. */
internal fun MediaDestination.inHubFrame(): Boolean =
    this == MediaDestination.Collection ||
        this == MediaDestination.Podcasts ||
        this == MediaDestination.Radio ||
        this == MediaDestination.Fm ||
        this == MediaDestination.NowPlaying

internal class MediaMenuItem(
    val label: String,
    val onClick: () -> Unit,
)

/** The 40 dp title bar: an optional back mark, the title, a note, search and the ⋮ menu. */
@Composable
internal fun MediaHubTitleBar(
    title: String,
    onBack: (() -> Unit)? = null,
    note: String? = null,
    onSearch: (() -> Unit)? = null,
    menu: List<MediaMenuItem> = emptyList(),
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HubTitleHeight)
                .padding(start = if (onBack == null) 12.dp else 0.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            MediaIconButton(MediaIcons.Back, t("hub_back"), MaterialTheme.colorScheme.onSurface, onBack)
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (note != null) {
            Text(note, fontSize = 11.sp, color = muted, modifier = Modifier.padding(horizontal = 8.dp), maxLines = 1)
        }
        if (onSearch != null) MediaIconButton(MediaIcons.Search, t("hub_search"), muted, onSearch)
        if (menu.isNotEmpty()) {
            Box {
                var open by remember { mutableStateOf(false) }
                MediaIconButton(MediaIcons.More, t("hub_more"), muted) { open = true }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            onClick = {
                                open = false
                                item.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The 30 dp tab strip, scrolling sideways when the tabs do not fit; the chosen tab is underlined. */
@Composable
internal fun MediaHubTabs(
    selected: MediaHubTab?,
    songCount: Int,
    newEpisodes: Int,
    fmAvailable: Boolean,
    onSelect: (MediaHubTab) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(HubTabsHeight)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaHubTab.entries
                .filter { it != MediaHubTab.Fm || fmAvailable }
                .forEach { tab ->
                    val chosen = tab == selected
                    val count =
                        when (tab) {
                            MediaHubTab.Songs -> songCount.takeIf { it > 0 }
                            MediaHubTab.Podcasts -> newEpisodes.takeIf { it > 0 }
                            else -> null
                        }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .sableFocusRing()
                                .clickable(role = Role.Tab) { onSelect(tab) }
                                .semantics { this.selected = chosen }
                                .padding(horizontal = 6.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (count == null) tab.label else "${tab.label} $count",
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (chosen) accent else muted,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Box(
                            Modifier
                                .height(2.dp)
                                .width(TabUnderlineWidth)
                                .background(if (chosen) accent else Color.Transparent),
                        )
                    }
                }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The 40 dp line for what is playing; opens Now playing. */
@Composable
internal fun MediaNowLine(
    playback: PlaybackUiState,
    art: ArtworkSource?,
    onOpen: () -> Unit,
) {
    if (playback.currentIndex < 0 && !playback.isPlaying) return
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(HubNowLineHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .sableFocusRing()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MediaArt(source = art, name = playback.title, fallback = accent, size = 24.dp)
            Text(
                text = listOf(playback.title, playback.source).filter { it.isNotBlank() }.joinToString(" · "),
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (playback.isPlaying) t("hub_playing") else t("hub_paused"),
                fontSize = 11.sp,
                color = if (playback.isPlaying) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal class MediaBarAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val active: Boolean = false,
    /** When set, the button opens this menu instead of [onClick]. */
    val menu: List<MediaMenuItem> = emptyList(),
)

/** The 50 dp action bar: up to five buttons, each a 20 dp icon over an 11 sp label. */
@Composable
internal fun MediaActionBar(actions: List<MediaBarAction>) {
    if (actions.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(HubActionBarHeight)
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            actions.forEach { action ->
                Box(Modifier.weight(1f).fillMaxHeight()) { MediaActionButton(action) }
            }
        }
    }
}

@Composable
private fun MediaActionButton(action: MediaBarAction) {
    var open by remember { mutableStateOf(false) }
    val color =
        when {
            !action.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
            action.active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .sableFocusRing()
                .clickable(enabled = action.enabled, role = Role.Button) {
                    if (action.menu.isEmpty()) action.onClick() else open = true
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(action.icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            text = action.label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
        )
    }
    if (action.menu.isNotEmpty()) {
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            action.menu.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.5f

@Composable
internal fun MediaIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .sableFocusRing()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Line icons on a 24-unit grid, drawn the way Hub's are (2-unit strokes, or filled shapes). */
internal object MediaIcons {
    val Play = filled("M7 5v14l12-7z")
    val Pause = filled("M6 5h4v14H6zM14 5h4v14h-4z")
    val Next = filled("M16 5h2v14h-2zM4 5v14l11-7z")
    val Previous = filled("M6 5h2v14H6zM20 5v14L9 12z")
    val Stop = filled("M6 6h12v12H6z")
    val More =
        filled(
            "M12 3a2 2 0 1 0 0 4a2 2 0 1 0 0-4zM12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4zM12 17a2 2 0 1 0 0 4a2 2 0 1 0 0-4z",
        )
    val UpNext = stroked("M4 6h16M4 12h10M4 18h7M17 15v6M14 18h6")
    val Add = stroked("M12 5v14M5 12h14")
    val Settings =
        stroked(
            "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6zM12 2v3M12 19v3M2 12h3M19 12h3" +
                "M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1",
        )
    val Refresh = stroked("M20 12a8 8 0 1 1-3-6.2M20 4v5h-5")
    val Search = stroked("M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14zM20 20l-4-4")
    val Back = stroked("M15 5l-7 7 7 7")
    val Manage = stroked("M4 6h16M4 12h16M4 18h16")
    val Shuffle = stroked("M4 7h11l-3-3M20 17H9l3 3")

    private fun filled(path: String) = icon(path, stroke = false)

    private fun stroked(path: String) = icon(path, stroke = true)

    private fun icon(
        path: String,
        stroke: Boolean,
    ): ImageVector =
        ImageVector
            .Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = if (stroke) null else SolidColor(Color.Black),
                stroke = if (stroke) SolidColor(Color.Black) else null,
                strokeLineWidth = if (stroke) 2f else 0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
}

private val HubTitleHeight = 40.dp
private val HubTabsHeight = 30.dp
private val HubNowLineHeight = 40.dp
private val HubActionBarHeight = 50.dp
private val TabUnderlineWidth = 24.dp
