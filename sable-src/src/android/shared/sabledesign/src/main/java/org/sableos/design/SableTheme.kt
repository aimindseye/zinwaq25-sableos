package org.sableos.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppearanceMode(
    val stableValue: String,
) {
    FollowSystem("follow-system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromStableValue(value: String?): AppearanceMode = entries.firstOrNull { it.stableValue == value } ?: FollowSystem
    }
}

enum class AccentPreset(
    val stableValue: String,
    val displayName: String,
    val color: Color,
    val onColor: Color,
) {
    Blue("blue", "Sable Blue", Color(0xFF4D9CFF), Color(0xFF06111F)),
    Green("green", "Green", Color(0xFF35C66B), Color(0xFF04140A)),
    Purple("purple", "Purple", Color(0xFF7A42E8), Color.White),
    Orange("orange", "Orange", Color(0xFFF28C45), Color(0xFF1D0C02)),
    Slate("slate", "Slate", Color(0xFF64748B), Color.White),
    ;

    companion object {
        fun fromStableValue(value: String?): AccentPreset = entries.firstOrNull { it.stableValue == value } ?: Blue
    }
}

data class SableAppearance(
    val mode: AppearanceMode = AppearanceMode.FollowSystem,
    val accent: AccentPreset = AccentPreset.Blue,
)

object SableDesignContract {
    const val SCHEMA_VERSION = 2
    const val KEY_APPEARANCE_MODE = "sable.appearance.mode"
    const val KEY_ACCENT = "sable.appearance.accent"

    const val MIN_TOUCH_TARGET_DP = 48
    const val SCREEN_HORIZONTAL_PADDING_DP = 24
    const val SCREEN_VERTICAL_PADDING_DP = 20
    const val MAX_STANDARD_CORNER_RADIUS_DP = 6
    const val FAST_MOTION_MS = 120
    const val STANDARD_MOTION_MS = 180
    const val EMPHASIZED_MOTION_MS = 240
}

object SableSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val Xxxl = 48.dp

    val ScreenHorizontal = SableDesignContract.SCREEN_HORIZONTAL_PADDING_DP.dp
    val ScreenVertical = SableDesignContract.SCREEN_VERTICAL_PADDING_DP.dp
}

object SableSize {
    val TouchTarget = SableDesignContract.MIN_TOUCH_TARGET_DP.dp
    val ControlHeight = 52.dp
    val ListRowHeight = 58.dp
    val AccentBarWidth = 3.dp
}

object SableColorTokens {
    val DarkBackground = Color(0xFF05070A)
    val DarkSurface = Color(0xFF0D1117)
    val DarkSurfaceRaised = Color(0xFF161C24)
    val DarkText = Color(0xFFF7F9FC)
    val DarkTextMuted = Color(0xFFAAB4C0)

    val LightBackground = Color(0xFFF5F7F9)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceRaised = Color(0xFFE9EDF2)
    val LightText = Color(0xFF12171C)
    val LightTextMuted = Color(0xFF515B66)
}

private val SableTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontSize = 44.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.8).sp,
            ),
        headlineLarge =
            TextStyle(
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.4).sp,
            ),
        headlineMedium =
            TextStyle(
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Normal,
            ),
        titleLarge =
            TextStyle(
                fontSize = 21.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Medium,
            ),
        titleMedium =
            TextStyle(
                fontSize = 17.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Medium,
            ),
        bodyLarge =
            TextStyle(
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
            ),
        bodyMedium =
            TextStyle(
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Normal,
            ),
        labelLarge =
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            ),
    )

private val SableShapes =
    Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(3.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(6.dp),
    )

@Composable
fun SableTheme(
    appearance: SableAppearance = SableAppearance(),
    content: @Composable () -> Unit,
) {
    val dark =
        when (appearance.mode) {
            AppearanceMode.FollowSystem -> isSystemInDarkTheme()
            AppearanceMode.Light -> false
            AppearanceMode.Dark -> true
        }
    val accent = appearance.accent
    val colors =
        if (dark) {
            darkColorScheme(
                primary = accent.color,
                onPrimary = accent.onColor,
                background = SableColorTokens.DarkBackground,
                onBackground = SableColorTokens.DarkText,
                surface = SableColorTokens.DarkSurface,
                onSurface = SableColorTokens.DarkText,
                surfaceVariant = SableColorTokens.DarkSurfaceRaised,
                onSurfaceVariant = SableColorTokens.DarkTextMuted,
                outline = Color(0xFF526170),
                outlineVariant = Color(0xFF26313D),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
            )
        } else {
            lightColorScheme(
                primary = accent.color,
                onPrimary = accent.onColor,
                background = SableColorTokens.LightBackground,
                onBackground = SableColorTokens.LightText,
                surface = SableColorTokens.LightSurface,
                onSurface = SableColorTokens.LightText,
                surfaceVariant = SableColorTokens.LightSurfaceRaised,
                onSurfaceVariant = SableColorTokens.LightTextMuted,
                outline = Color(0xFF737E8A),
                outlineVariant = Color(0xFFD0D6DD),
                error = Color(0xFFBA1A1A),
                onError = Color.White,
            )
        }

    MaterialTheme(
        colorScheme = colors,
        typography = SableTypography,
        shapes = SableShapes,
        content = content,
    )
}
