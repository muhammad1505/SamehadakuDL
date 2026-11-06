@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.samehadaku.dl.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.samehadaku.dl.R

/**
 * Plus Jakarta Sans variable font (SIL Open Font License).
 * Weight axes are instantiated explicitly so every Compose weight renders correctly.
 */
val PlusJakartaSans = FontFamily(
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
)

private fun base(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

val SamehadakuTypography = Typography(
    displayLarge = base(FontWeight.ExtraBold, 44, 50, (-0.6)),
    displayMedium = base(FontWeight.ExtraBold, 36, 42, (-0.5)),
    displaySmall = base(FontWeight.Bold, 30, 36, (-0.4)),
    headlineLarge = base(FontWeight.Bold, 28, 34, (-0.3)),
    headlineMedium = base(FontWeight.Bold, 24, 30, (-0.2)),
    headlineSmall = base(FontWeight.Bold, 21, 27, (-0.1)),
    titleLarge = base(FontWeight.Bold, 20, 26, (-0.1)),
    titleMedium = base(FontWeight.SemiBold, 16, 22),
    titleSmall = base(FontWeight.SemiBold, 14, 20),
    bodyLarge = base(FontWeight.Normal, 15, 23),
    bodyMedium = base(FontWeight.Normal, 13, 19),
    bodySmall = base(FontWeight.Normal, 12, 17),
    labelLarge = base(FontWeight.SemiBold, 14, 18, 0.1),
    labelMedium = base(FontWeight.Medium, 12, 16, 0.2),
    labelSmall = base(FontWeight.Medium, 10, 14, 0.3),
)
