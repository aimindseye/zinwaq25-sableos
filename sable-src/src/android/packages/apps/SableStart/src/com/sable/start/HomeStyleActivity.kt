package org.sableos.start

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.sableos.design.SableGlobalTheme
import org.sableos.design.sableFocusRing
import org.sableos.start.model.HomeStyle
import org.sableos.start.platform.StartStateRepository

/**
 * Chooses Sable Start's home style: BlackBerry 10 or BlackBerry 7, or another launcher (Android's
 * Home app screen). Opened from Sable Setup (org.sableos.start.action.CHOOSE_HOME_STYLE) and from
 * Start's own menu. The choice is Start's own setting; nothing else is changed.
 */
class HomeStyleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = StartStateRepository(this)
        setContent {
            SableGlobalTheme(window = window) {
                HomeStyleChooser(
                    current = state.loadHomeStyle(),
                    onPick = { style ->
                        state.saveHomeStyle(style)
                        // Show the result: Home brings Start forward in the new style.
                        runCatching {
                            startActivity(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                                    .setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        finish()
                    },
                    onOtherLauncher = {
                        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeStyleChooser(
    current: HomeStyle,
    onPick: (HomeStyle) -> Unit,
    onOtherLauncher: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Home screen style", style = MaterialTheme.typography.titleLarge)
            Text(
                "Sable Start can look like BlackBerry 10 or BlackBerry 7. Your apps, favourites and Hub stay the same.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HomeStyle.entries.forEachIndexed { i, style ->
                Choice(
                    title = style.title + if (style == current) " · current" else "",
                    detail = style.summary,
                    active = style == current,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { onPick(style) },
                )
            }
            Choice(
                title = "Another launcher…",
                detail = "Pick any installed home app in Android's settings",
                active = false,
                onClick = onOtherLauncher,
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun Choice(
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().sableFocusRing(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border =
            BorderStroke(
                if (active) 2.dp else 1.dp,
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
