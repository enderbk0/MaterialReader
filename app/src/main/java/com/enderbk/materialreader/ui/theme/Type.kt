package com.enderbk.materialreader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.enderbk.materialreader.R

// Material 3 type scale. We intentionally use the Material defaults (which track
// the official M3 spec) so the app follows system font-size/accessibility
// settings instead of imposing custom metrics.
val Typography = Typography()

/**
 * Rounded interface typeface (Nunito, SIL OFL 1.1 — see
 * app/src/main/assets/licenses/OFL-Nunito.txt). Opt-in via Settings; PDF
 * content itself always renders with its embedded fonts.
 */
val RoundedFontFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_bold, FontWeight.Bold)
)

private fun TextStyle.rounded() = copy(fontFamily = RoundedFontFamily)

/** Same M3 scale as [Typography], set in the rounded family. */
val RoundedTypography = Typography(
    displayLarge = Typography.displayLarge.rounded(),
    displayMedium = Typography.displayMedium.rounded(),
    displaySmall = Typography.displaySmall.rounded(),
    headlineLarge = Typography.headlineLarge.rounded(),
    headlineMedium = Typography.headlineMedium.rounded(),
    headlineSmall = Typography.headlineSmall.rounded(),
    titleLarge = Typography.titleLarge.rounded(),
    titleMedium = Typography.titleMedium.rounded(),
    titleSmall = Typography.titleSmall.rounded(),
    bodyLarge = Typography.bodyLarge.rounded(),
    bodyMedium = Typography.bodyMedium.rounded(),
    bodySmall = Typography.bodySmall.rounded(),
    labelLarge = Typography.labelLarge.rounded(),
    labelMedium = Typography.labelMedium.rounded(),
    labelSmall = Typography.labelSmall.rounded()
)
