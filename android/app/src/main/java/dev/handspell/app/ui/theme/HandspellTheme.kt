package dev.handspell.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
}

object AslShapes {
    val extraSmall = 8.dp
    val small = 12.dp
    val medium = 16.dp
    val large = 20.dp
    val extraLarge = 28.dp
}

/** Motion timings used only to orient feedback changes; callers honour system animation scale. */
object AslMotion {
    const val quickMillis = 200
    const val standardMillis = 350
}

data class AslColors(
    val label: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val backgroundGrouped: Color,
    val surface: Color,
    val separator: Color,
    val accent: Color,
    val feedbackMatch: Color,
    val feedbackAdjust: Color,
    val feedbackNeutral: Color,
)

private val LightAslColors = AslColors(
    label = Color(0xFF0A0A0B),
    labelSecondary = Color(0xFF55595F),
    labelTertiary = Color(0xFF6E7278),
    backgroundGrouped = Color(0xFFF2F3F5),
    surface = Color.White,
    separator = Color(0xFFD3D6DA),
    accent = Color(0xFF0A6C74),
    feedbackMatch = Color(0xFF0E6F3C),
    feedbackAdjust = Color(0xFF8A5A00),
    feedbackNeutral = Color(0xFF55595F),
)

private val DarkAslColors = AslColors(
    label = Color(0xFFF2F3F5),
    labelSecondary = Color(0xFFB6BBC2),
    labelTertiary = Color(0xFF8A8F96),
    backgroundGrouped = Color(0xFF101113),
    surface = Color(0xFF1B1D20),
    separator = Color(0xFF2E3136),
    accent = Color(0xFF4FD1D9),
    feedbackMatch = Color(0xFF3DDC84),
    feedbackAdjust = Color(0xFFFFC65C),
    feedbackNeutral = Color(0xFFB6BBC2),
)

val LocalAslColors = staticCompositionLocalOf { LightAslColors }

object AslText {
    val largeTitle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp)
    val title1 = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp)
    val title2 = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
    val title3 = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 25.sp)
    val headline = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 22.sp)
    val callout = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 21.sp)
    val subhead = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
fun HandspellTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (dark) DarkAslColors else LightAslColors
    val scheme: ColorScheme = if (dark) {
        darkColorScheme(primary = colors.accent, onPrimary = Color.Black, background = Color.Black, onBackground = colors.label, surface = colors.surface, onSurface = colors.label)
    } else {
        lightColorScheme(primary = colors.accent, onPrimary = Color.White, background = Color.White, onBackground = colors.label, surface = colors.surface, onSurface = colors.label)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAslColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = androidx.compose.material3.Typography(
                displaySmall = AslText.largeTitle, headlineMedium = AslText.title1, headlineSmall = AslText.title2,
                titleLarge = AslText.title3, titleMedium = AslText.headline, bodyLarge = AslText.body,
                bodyMedium = AslText.callout, bodySmall = AslText.subhead, labelMedium = AslText.footnote,
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
