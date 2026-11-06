package org.sableos.start

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.sable.start.model.StartText
import com.sable.start.model.StartText.t
import org.sableos.design.DesignText
import org.sableos.design.SableGlobalTheme
import org.sableos.design.sableFocusRing
import org.sableos.design.sableVerticalScroll
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
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)
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
                    .sableVerticalScroll()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(t("home_screen_style"), style = MaterialTheme.typography.titleLarge)
            Text(
                t("sable_start_can_look_like"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HomeStyle.entries.forEachIndexed { i, style ->
                Choice(
                    title = style.title + if (style == current) t("current") else "",
                    detail = style.summary,
                    active = style == current,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { onPick(style) },
                )
            }
            Choice(
                title = t("another_launcher"),
                detail = t("pick_any_installed_home_app"),
                active = false,
                onClick = onOtherLauncher,
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** A flat BlackBerry 10 list row: title and detail on black, a thin line below, the current choice in the blue. */
@Composable
private fun Choice(
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().sableFocusRing(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
