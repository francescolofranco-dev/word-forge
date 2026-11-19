package com.wordforge.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.wordforge.R
import androidx.compose.ui.text.googlefonts.Font as GoogleFontDef

// Google Play Services downloadable-fonts provider. Certs live in
// res/values/font_certs.xml — if the provider can't be reached (no
// Play Services, no network) the GoogleFont API falls back to system
// fonts silently.
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val Bricolage = FontFamily(
    GoogleFontDef(GoogleFont("Bricolage Grotesque"), provider, FontWeight.W700),
    GoogleFontDef(GoogleFont("Bricolage Grotesque"), provider, FontWeight.W800),
)

private val Manrope = FontFamily(
    GoogleFontDef(GoogleFont("Manrope"), provider, FontWeight.W400),
    GoogleFontDef(GoogleFont("Manrope"), provider, FontWeight.W500),
    GoogleFontDef(GoogleFont("Manrope"), provider, FontWeight.W600),
    GoogleFontDef(GoogleFont("Manrope"), provider, FontWeight.W700),
)

private val JetBrainsMono = FontFamily(
    GoogleFontDef(GoogleFont("JetBrains Mono"), provider, FontWeight.W500),
)

val Typography = Typography(
    displayLarge   = TextStyle(fontFamily = Bricolage,    fontWeight = FontWeight.W800, fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-1.4).sp),
    displayMedium  = TextStyle(fontFamily = Bricolage,    fontWeight = FontWeight.W700, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),

    headlineLarge  = TextStyle(fontFamily = Bricolage,    fontWeight = FontWeight.W700, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Bricolage,    fontWeight = FontWeight.W700, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp),

    titleLarge     = TextStyle(fontFamily = Manrope,      fontWeight = FontWeight.W700, fontSize = 16.sp, lineHeight = 22.sp),
    titleMedium    = TextStyle(fontFamily = Manrope,      fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 20.sp),

    bodyLarge      = TextStyle(fontFamily = Manrope,      fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = Manrope,      fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = Manrope,      fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 18.sp),

    labelLarge     = TextStyle(fontFamily = Manrope,       fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium    = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp),
    labelSmall     = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.W500, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.4.sp),
)
