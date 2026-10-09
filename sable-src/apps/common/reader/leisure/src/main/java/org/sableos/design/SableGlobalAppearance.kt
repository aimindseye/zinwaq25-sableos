package org.sableos.design

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

object SableGlobalAppearanceContract {
    const val AUTHORITY = "org.sableos.appearance"
    const val PATH_APPEARANCE = "appearance"
    const val COLUMN_MODE = "mode"
    const val COLUMN_ACCENT = "accent"

    val CONTENT_URI: Uri =
        Uri.parse("content://$AUTHORITY/$PATH_APPEARANCE")
}

fun readGlobalSableAppearance(
    context: Context,
): SableAppearance =
    runCatching {
        context.contentResolver
            .query(
                SableGlobalAppearanceContract.CONTENT_URI,
                arrayOf(
                    SableGlobalAppearanceContract.COLUMN_MODE,
                    SableGlobalAppearanceContract.COLUMN_ACCENT,
                ),
                null,
                null,
                null,
            )
            ?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use SableAppearance()
                }

                val mode =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            SableGlobalAppearanceContract.COLUMN_MODE,
                        ),
                    )
                val accent =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            SableGlobalAppearanceContract.COLUMN_ACCENT,
                        ),
                    )

                SableAppearance(
                    mode = AppearanceMode.fromStableValue(mode),
                    accent = AccentPreset.fromStableValue(accent),
                )
            }
            ?: SableAppearance()
    }.getOrDefault(SableAppearance())


fun writeGlobalSableAppearance(
    context: Context,
    appearance: SableAppearance,
): Boolean =
    runCatching {
        val values =
            ContentValues().apply {
                put(
                    SableGlobalAppearanceContract.COLUMN_MODE,
                    appearance.mode.stableValue,
                )
                put(
                    SableGlobalAppearanceContract.COLUMN_ACCENT,
                    appearance.accent.stableValue,
                )
            }

        context.contentResolver.update(
            SableGlobalAppearanceContract.CONTENT_URI,
            values,
            null,
            null,
        ) == 1
    }.getOrDefault(false)

@Composable
fun rememberGlobalSableAppearance(
    context: Context = LocalContext.current,
): SableAppearance {
    var appearance by remember(context) {
        mutableStateOf(readGlobalSableAppearance(context))
    }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer =
            object : ContentObserver(
                Handler(Looper.getMainLooper()),
            ) {
                override fun onChange(selfChange: Boolean) {
                    appearance = readGlobalSableAppearance(context)
                }
            }

        runCatching {
            resolver.registerContentObserver(
                SableGlobalAppearanceContract.CONTENT_URI,
                false,
                observer,
            )
        }

        onDispose {
            runCatching {
                resolver.unregisterContentObserver(observer)
            }
        }
    }

    return appearance
}

@Composable
fun SableGlobalTheme(
    window: Window? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val appearance = rememberGlobalSableAppearance(context)

    SideEffect {
        window?.let {
            applySableSystemBars(
                window = it,
                darkTheme =
                    resolveSableDarkAppearance(
                        context = context,
                        appearance = appearance,
                    ),
            )
        }
    }

    SableTheme(
        appearance = appearance,
        content = content,
    )
}
