@file:Suppress("FunctionNaming")

package org.sableos.titan2.displaycompat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.sableos.titan2.displaycompat.android.TraitsResolver
import org.sableos.titan2.displaycompat.android.displayCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Root() }
    }

    @Composable
    private fun Root() {
        val app = displayCompat
        val resolver = remember { TraitsResolver(this) }
        val metrics = resources.displayMetrics
        val display = org.sableos.titan2.displaycompat.core.SizePx(
            metrics.widthPixels,
            metrics.heightPixels
        )
        var editing by remember { mutableStateOf<TraitsResolver.Entry?>(null) }
        Box(Modifier.fillMaxSize().background(C.window).safeDrawingPadding()) {
            val e = editing
            if (e == null) {
                AppListScreen(app, resolver, onOpen = { editing = it }, onExit = { finish() })
            } else {
                BackHandler { editing = null }
                EditorScreen(app, resolver, e, display, onDone = { editing = null })
            }
        }
    }
}
