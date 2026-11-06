package app.samehadaku.dl.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MidnightGlassColors = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = Ink,
    primaryContainer = Color(0xFF063A4D),
    onPrimaryContainer = Color(0xFFBDEFFF),
    secondary = BrandAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A1F55),
    onSecondaryContainer = Color(0xFFDCD2FF),
    tertiary = BrandPink,
    onTertiary = Ink,
    background = Midnight,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1A2638),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF334258),
    outlineVariant = Color(0xFF223047),
    error = Danger,
    onError = Ink,
    errorContainer = Color(0xFF3A1722),
    onErrorContainer = Color(0xFFFFC7D0),
    inverseSurface = Color(0xFFE7F1FF),
    inverseOnSurface = Ink,
    inversePrimary = BrandPrimaryDark,
    surfaceTint = BrandPrimary,
)

/**
 * The redesigned app intentionally uses a fixed midnight glass theme so the
 * brand palette, artwork and glass surfaces stay consistent on every device.
 */
@Composable
fun SamehadakuTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MidnightGlassColors,
        typography = SamehadakuTypography,
        content = content,
    )
}
