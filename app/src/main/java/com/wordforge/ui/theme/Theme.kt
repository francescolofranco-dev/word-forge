package com.wordforge.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme

val WordForgeLightColors = lightColorScheme(
    primary              = ForgeOrange,
    onPrimary            = SurfaceWhite,
    primaryContainer     = ForgeOrangeSoft,
    onPrimaryContainer   = ForgeOrangeDeep,

    secondary            = Sage,
    onSecondary          = SurfaceWhite,
    secondaryContainer   = SageSoft,
    onSecondaryContainer = Sage,

    tertiary             = Gold,
    onTertiary           = SurfaceWhite,
    tertiaryContainer    = GoldSoft,
    onTertiaryContainer  = Ink,

    background              = Cream,
    onBackground            = Ink,
    surface                 = SurfaceWhite,
    onSurface               = Ink,
    surfaceVariant          = CreamTint,
    onSurfaceVariant        = InkSoft,
    surfaceContainerLowest  = SurfaceWhite,
    surfaceContainerLow     = SurfaceWhite,
    surfaceContainer        = SurfaceWhite,
    surfaceContainerHigh    = SurfaceWhite,
    surfaceContainerHighest = CreamTint,

    outline                 = Divider,
    outlineVariant          = Divider,
)

val WordForgeDarkColors = darkColorScheme(
    primary                 = ForgeOrangeDark,
    onPrimary               = Charcoal,
    primaryContainer        = ForgeOrangeDarkSoft,
    onPrimaryContainer      = ForgeOrangeDarkDeep,

    secondary               = SageDark,
    onSecondary             = Charcoal,
    secondaryContainer      = SageDarkSoft,
    onSecondaryContainer    = SageDark,

    tertiary                = GoldDark,
    onTertiary              = Charcoal,
    tertiaryContainer       = GoldDarkSoft,
    onTertiaryContainer     = GoldDark,

    background              = Charcoal,
    onBackground            = Parchment,
    surface                 = CharcoalElevated,
    onSurface               = Parchment,
    surfaceVariant          = CharcoalTint,
    onSurfaceVariant        = ParchmentSoft,
    surfaceContainerLowest  = Charcoal,
    surfaceContainerLow     = CharcoalElevated,
    surfaceContainer        = CharcoalElevated,
    surfaceContainerHigh    = CharcoalTint,
    surfaceContainerHighest = CharcoalTint,

    outline                 = DividerDark,
    outlineVariant          = DividerDark,

    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),
)

data class WordForgeSemanticColors(
    val correct: Color,
    val onCorrect: Color,
    val correctContainer: Color,
    val onCorrectContainer: Color,
    val incorrect: Color,
    val onIncorrect: Color,
    val incorrectContainer: Color,
    val onIncorrectContainer: Color,
    val celebration: Color,
    val onCelebration: Color,
    val celebrationContainer: Color,
    val onCelebrationContainer: Color,
    val snackbarContainer: Color,
    val snackbarContent: Color,
)

private val LightSemanticColors = WordForgeSemanticColors(
    correct = Sage,
    onCorrect = SurfaceWhite,
    correctContainer = SageSoft,
    onCorrectContainer = Sage,
    incorrect = ForgeOrangeDeep,
    onIncorrect = SurfaceWhite,
    incorrectContainer = ForgeOrangeSoft,
    onIncorrectContainer = ForgeOrangeDeep,
    celebration = Gold,
    onCelebration = SurfaceWhite,
    celebrationContainer = GoldSoft,
    onCelebrationContainer = Ink,
    snackbarContainer = Ink,
    snackbarContent = SurfaceWhite,
)

private val DarkSemanticColors = WordForgeSemanticColors(
    correct = SageDark,
    onCorrect = Charcoal,
    correctContainer = SageDarkSoft,
    onCorrectContainer = Parchment,
    incorrect = ForgeOrangeDark,
    onIncorrect = Charcoal,
    incorrectContainer = ForgeOrangeDarkSoft,
    onIncorrectContainer = Parchment,
    celebration = GoldDark,
    onCelebration = Charcoal,
    celebrationContainer = GoldDarkSoft,
    onCelebrationContainer = Parchment,
    snackbarContainer = Parchment,
    snackbarContent = Charcoal,
)

val LocalWordForgeColors = staticCompositionLocalOf { LightSemanticColors }

// Rounder than M3 defaults — playful direction for any component that
// doesn't override its shape inline.
private val PlayfulShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun WordForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) WordForgeDarkColors else WordForgeLightColors
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalWordForgeColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = PlayfulShapes,
            content = content
        )
    }
}
