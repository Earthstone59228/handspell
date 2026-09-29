package dev.handspell.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.handspell.app.prefs.ThemeMode

/** The only spacing values screens may use. */
object Spacing {
    val hairline = 1.dp
    val stroke = 2.dp
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp
    val letterTile: Dp = 64.dp
    val referenceGuide: Dp = 88.dp
    val touchTarget: Dp = 48.dp
    val primaryButton: Dp = 54.dp
    val captureSignerPicker: Dp = 112.dp
    val captureLetterPicker: Dp = 160.dp
    val captureRecordButton: Dp = 64.dp
}

object AslShapes {
    val extraSmall = 8.dp
    val small = 12.dp
    val button = 15.dp
    val medium = 16.dp
    val large = 20.dp
    val extraLarge = 28.dp
}

/** Motion timings used only to orient feedback changes; callers honour system animation scale. */
object AslMotion {
    const val quickMillis = 200
    const val standardMillis = 350
}

/**
 * Colours from the alphabet menu (`ionic-app/src/css/style.css`): dark grey background, off-white text, blue for
 * highlights, plus one lighter grey ([Slate]) for cards and menus so they read as raised without turning white.
 * Every other colour on screen is one of these at a lower opacity. Do not add more.
 */
object AslPalette {
    val Ink = Color(0xFF1D1D1F)
    val Paper = Color(0xFFF5F5F7)
    val Blue = Color(0xFF007AFF)
    val Slate = Color(0xFF2C2C2E)
    /** The web menu's white (buttons' text, and the light appearance's cards). */
    val White = Color(0xFFFFFFFF)
}

data class AslColors(
    /** Text on the dark background. */
    val label: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val backgroundGrouped: Color,
    /** Raised card / menu fill: a lighter grey than the background. */
    val surface: Color,
    /** Text on [surface]. */
    val onSurface: Color,
    val onSurfaceSecondary: Color,
    /** Hairline on [surface]. */
    val separator: Color,
    val accent: Color,
    /** Text on an [accent] fill. */
    val onAccent: Color,
    val feedbackMatch: Color,
    val feedbackAdjust: Color,
    val feedbackNeutral: Color,
    val destructive: Color,
    /** The alphabet menu's letter cards: an off-white card with dark text, raised against the dark ground. */
    val card: Color = AslPalette.Paper,
    val onCard: Color = AslPalette.Ink,
    val onCardSecondary: Color = AslPalette.Ink.copy(alpha = 0.62f),
)

private val AslDarkColors = AslColors(
    label = AslPalette.Paper,
    labelSecondary = AslPalette.Paper.copy(alpha = 0.62f),
    labelTertiary = AslPalette.Paper.copy(alpha = 0.45f),
    backgroundGrouped = AslPalette.Ink,
    surface = AslPalette.Slate,
    onSurface = AslPalette.Paper,
    onSurfaceSecondary = AslPalette.Paper.copy(alpha = 0.62f),
    separator = AslPalette.Paper.copy(alpha = 0.14f),
    accent = AslPalette.Blue,
    onAccent = AslPalette.Paper,
    // Feedback never relies on colour alone (see FeedbackBadge): match is a filled blue disc, adjust a white
    // ring with a progress arc, and the two neutral states are dotted/dashed grey-white rings.
    feedbackMatch = AslPalette.Blue,
    feedbackAdjust = AslPalette.Paper,
    feedbackNeutral = AslPalette.Paper.copy(alpha = 0.62f),
    destructive = AslPalette.Paper,
)

/**
 * Light appearance: the same palette swapped, as the web menu's light mode does. Paper ground, Ink text, white cards
 * and rows, Blue for actions; secondary text and hairlines are Ink at the dark theme's opacities.
 */
private val AslLightColors = AslColors(
    label = AslPalette.Ink,
    labelSecondary = AslPalette.Ink.copy(alpha = 0.62f),
    labelTertiary = AslPalette.Ink.copy(alpha = 0.45f),
    backgroundGrouped = AslPalette.Paper,
    surface = AslPalette.White,
    onSurface = AslPalette.Ink,
    onSurfaceSecondary = AslPalette.Ink.copy(alpha = 0.62f),
    separator = AslPalette.Ink.copy(alpha = 0.14f),
    accent = AslPalette.Blue,
    onAccent = AslPalette.Paper,
    feedbackMatch = AslPalette.Blue,
    feedbackAdjust = AslPalette.Ink,
    feedbackNeutral = AslPalette.Ink.copy(alpha = 0.62f),
    destructive = AslPalette.Ink,
    card = AslPalette.White,
    onCard = AslPalette.Ink,
    onCardSecondary = AslPalette.Ink.copy(alpha = 0.62f),
)

val LocalAslColors = staticCompositionLocalOf { AslDarkColors }

/** The resolved appearance (the Theme setting, with System read from the phone). */
val LocalDarkTheme = staticCompositionLocalOf { true }

/** System follows the phone; Light and Dark are fixed. */
fun resolveDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** The main menu's quick toggle: always flips what is on screen now, and stores an explicit Light or Dark. */
fun toggledTheme(mode: ThemeMode, systemDark: Boolean): ThemeMode =
    if (resolveDark(mode, systemDark)) ThemeMode.LIGHT else ThemeMode.DARK

/**
 * True when the system animator scale is 0 (Settings > Accessibility > Remove animations). Every animation checks it
 * and becomes an instant state change (docs/DESIGN.md §1, Shape and motion).
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

internal fun reduceMotionEnabled(context: android.content.Context): Boolean =
    android.provider.Settings.Global.getFloat(
        context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    ) == 0f

object AslText {
    val largeTitle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1.5).sp)
    val title1 = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-1).sp)
    val title2 = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp)
    val title3 = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.4).sp)
    val headline = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 24.sp)
    val callout = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 22.sp)
    val subhead = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp)
    /** The alphabet sheet's big letter tile (web `.practice-letter`), for a single letter on a reward card. */
    val hero = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 96.sp, lineHeight = 100.sp, letterSpacing = (-3).sp)
    /** Small uppercase-style section label, like the alphabet's progress line. */
    val eyebrow = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp)
}

/** Dark (the alphabet menu's look) or its light counterpart, from the Theme setting. */
@Composable
fun HandspellTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = resolveDark(themeMode, androidx.compose.foundation.isSystemInDarkTheme())
    val colors = if (dark) AslDarkColors else AslLightColors
    val base: ColorScheme = if (dark) darkColorScheme() else androidx.compose.material3.lightColorScheme()
    val scheme: ColorScheme = base.copy(
        primary = colors.accent, onPrimary = colors.onAccent,
        background = colors.backgroundGrouped, onBackground = colors.label,
        surface = colors.backgroundGrouped, onSurface = colors.label,
        surfaceVariant = colors.surface, onSurfaceVariant = colors.onSurface,
        surfaceContainer = colors.backgroundGrouped, surfaceContainerHigh = colors.backgroundGrouped,
        surfaceContainerHighest = colors.backgroundGrouped,
        outline = colors.labelTertiary, outlineVariant = colors.labelTertiary,
        secondaryContainer = colors.accent, onSecondaryContainer = colors.onAccent,
        error = colors.label, onError = colors.backgroundGrouped,
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = androidx.compose.runtime.remember(context) { reduceMotionEnabled(context) }
    androidx.compose.runtime.CompositionLocalProvider(LocalAslColors provides colors, LocalReduceMotion provides reduceMotion, LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = scheme,
            typography = androidx.compose.material3.Typography(
                displaySmall = AslText.largeTitle, headlineMedium = AslText.title1, headlineSmall = AslText.title2,
                titleLarge = AslText.title3, titleMedium = AslText.headline, bodyLarge = AslText.body,
                bodyMedium = AslText.callout, bodySmall = AslText.subhead, labelMedium = AslText.footnote,
                labelSmall = AslText.eyebrow,
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(AslShapes.extraSmall),
                small = RoundedCornerShape(AslShapes.small),
                medium = RoundedCornerShape(AslShapes.medium),
                large = RoundedCornerShape(AslShapes.large),
                extraLarge = RoundedCornerShape(AslShapes.extraLarge),
            ),
            content = content,
        )
    }
}
