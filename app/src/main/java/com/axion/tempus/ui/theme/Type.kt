package com.axion.tempus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.axion.tempus.R

private val MontserratFamily = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
)

private val defaultTypography = Typography()

val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = MontserratFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = MontserratFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = MontserratFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = MontserratFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = MontserratFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = MontserratFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = MontserratFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = MontserratFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = MontserratFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = MontserratFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = MontserratFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = MontserratFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = MontserratFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = MontserratFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = MontserratFamily),
)
