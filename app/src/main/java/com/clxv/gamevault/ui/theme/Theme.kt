package com.clxv.gamevault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.clxv.gamevault.core.settings.ThemeColor

private val IndigoLight = lightColorScheme(
    primary = Color(0xFF3A5BA8), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDAE2FF), onPrimaryContainer = Color(0xFF001849),
    secondary = Color(0xFF585E71), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE2F9), onSecondaryContainer = Color(0xFF151B2C),
    tertiary = Color(0xFF725572), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F9FE), onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFF8F9FE), onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE1E2EC), onSurfaceVariant = Color(0xFF45464F),
    surfaceContainer = Color(0xFFECEEF6), surfaceContainerHigh = Color(0xFFE6E8F0),
    outline = Color(0xFF757680), outlineVariant = Color(0xFFC5C6D0),
)

private val IndigoDark = darkColorScheme(
    primary = Color(0xFFB2C5FF), onPrimary = Color(0xFF002B74),
    primaryContainer = Color(0xFF1F4390), onPrimaryContainer = Color(0xFFDAE2FF),
    secondary = Color(0xFFC0C6DC), onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF414659), onSecondaryContainer = Color(0xFFDDE2F9),
    tertiary = Color(0xFFE0BBE0), onTertiary = Color(0xFF422743),
    background = Color(0xFF121318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF121318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF45464F), onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceContainer = Color(0xFF1B1C22), surfaceContainerHigh = Color(0xFF23242B),
    outline = Color(0xFF8F909A), outlineVariant = Color(0xFF45464F),
)

private val EmeraldLight = lightColorScheme(
    primary = Color(0xFF006C4C), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8BF8C8), onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4C6358), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DA), onSecondaryContainer = Color(0xFF082017),
    tertiary = Color(0xFF3E6374), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6FBF5), onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF6FBF5), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE), onSurfaceVariant = Color(0xFF404944),
    surfaceContainer = Color(0xFFEAF2EB), surfaceContainerHigh = Color(0xFFE2EBE4),
    outline = Color(0xFF707974), outlineVariant = Color(0xFFBFC9C2),
)

private val EmeraldDark = darkColorScheme(
    primary = Color(0xFF6DDBAD), onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139), onPrimaryContainer = Color(0xFF8BF8C8),
    secondary = Color(0xFFB2CCC0), onSecondary = Color(0xFF1E352C),
    secondaryContainer = Color(0xFF344B41), onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFA4CDDF), onTertiary = Color(0xFF083544),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDFE4DF),
    surfaceVariant = Color(0xFF404944), onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceContainer = Color(0xFF181F1B), surfaceContainerHigh = Color(0xFF202925),
    outline = Color(0xFF89938D), outlineVariant = Color(0xFF404944),
)

private val SunsetLight = lightColorScheme(
    primary = Color(0xFF9A4520), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCE), onPrimaryContainer = Color(0xFF380D00),
    secondary = Color(0xFF77574B), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCE), onSecondaryContainer = Color(0xFF2C160D),
    tertiary = Color(0xFF6C5A2E), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F6), onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6), onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED6), onSurfaceVariant = Color(0xFF53433D),
    surfaceContainer = Color(0xFFFCEEE8), surfaceContainerHigh = Color(0xFFF5E6E0),
    outline = Color(0xFF85736C), outlineVariant = Color(0xFFD8C2BA),
)

private val SunsetDark = darkColorScheme(
    primary = Color(0xFFFFB59B), onPrimary = Color(0xFF5C1900),
    primaryContainer = Color(0xFF7B2E0A), onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFFE7BDB0), onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF5D3F35), onSecondaryContainer = Color(0xFFFFDBCE),
    tertiary = Color(0xFFDAC58C), onTertiary = Color(0xFF3C2F04),
    background = Color(0xFF1A110E), onBackground = Color(0xFFF1DFD9),
    surface = Color(0xFF1A110E), onSurface = Color(0xFFF1DFD9),
    surfaceVariant = Color(0xFF53433D), onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceContainer = Color(0xFF241815), surfaceContainerHigh = Color(0xFF2C201C),
    outline = Color(0xFFA08C85), outlineVariant = Color(0xFF53433D),
)

private val AmethystLight = lightColorScheme(
    primary = Color(0xFF6F4DA0), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDCFF), onPrimaryContainer = Color(0xFF260058),
    secondary = Color(0xFF635B70), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DEF8), onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7E525F), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFDF7FF), onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF7FF), onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EB), onSurfaceVariant = Color(0xFF49454E),
    surfaceContainer = Color(0xFFF3ECF7), surfaceContainerHigh = Color(0xFFEDE5F1),
    outline = Color(0xFF7A757F), outlineVariant = Color(0xFFCBC4CF),
)

private val AmethystDark = darkColorScheme(
    primary = Color(0xFFD4BBFF), onPrimary = Color(0xFF3F1B6E),
    primaryContainer = Color(0xFF563487), onPrimaryContainer = Color(0xFFEBDCFF),
    secondary = Color(0xFFCDC2DB), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE9DEF8),
    tertiary = Color(0xFFF0B7C6), onTertiary = Color(0xFF4A2531),
    background = Color(0xFF16121A), onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF16121A), onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454E), onSurfaceVariant = Color(0xFFCBC4CF),
    surfaceContainer = Color(0xFF201B24), surfaceContainerHigh = Color(0xFF28222C),
    outline = Color(0xFF948F99), outlineVariant = Color(0xFF49454E),
)

private val GraphiteLight = lightColorScheme(
    primary = Color(0xFF45464F), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCDDE3), onPrimaryContainer = Color(0xFF121318),
    secondary = Color(0xFF5A5A63), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E1E9), onSecondaryContainer = Color(0xFF17171C),
    tertiary = Color(0xFF5E3F47), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFCF8F8), onBackground = Color(0xFF1B1B1D),
    surface = Color(0xFFFCF8F8), onSurface = Color(0xFF1B1B1D),
    surfaceVariant = Color(0xFFE2E1E6), onSurfaceVariant = Color(0xFF45464C),
    surfaceContainer = Color(0xFFF0EDF1), surfaceContainerHigh = Color(0xFFEAE7EB),
    outline = Color(0xFF76767D), outlineVariant = Color(0xFFC6C6CC),
)

private val GraphiteDark = darkColorScheme(
    primary = Color(0xFFC6C6D0), onPrimary = Color(0xFF2E2F38),
    primaryContainer = Color(0xFF45464F), onPrimaryContainer = Color(0xFFE3E1E9),
    secondary = Color(0xFFC7C6D0), onSecondary = Color(0xFF2F3038),
    secondaryContainer = Color(0xFF45464F), onSecondaryContainer = Color(0xFFE3E1E9),
    tertiary = Color(0xFFE6B9C5), onTertiary = Color(0xFF44252E),
    background = Color(0xFF131315), onBackground = Color(0xFFE4E1E6),
    surface = Color(0xFF131315), onSurface = Color(0xFFE4E1E6),
    surfaceVariant = Color(0xFF45464C), onSurfaceVariant = Color(0xFFC6C6CC),
    surfaceContainer = Color(0xFF1C1C1F), surfaceContainerHigh = Color(0xFF242428),
    outline = Color(0xFF909096), outlineVariant = Color(0xFF45464C),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun GameVaultTheme(
    darkTheme: Boolean,
    themeColor: ThemeColor = ThemeColor.DEFAULT,
    content: @Composable () -> Unit,
) {
    val scheme = when (themeColor) {
        ThemeColor.DEFAULT -> if (darkTheme) IndigoDark else IndigoLight
        ThemeColor.EMERALD -> if (darkTheme) EmeraldDark else EmeraldLight
        ThemeColor.SUNSET -> if (darkTheme) SunsetDark else SunsetLight
        ThemeColor.AMETHYST -> if (darkTheme) AmethystDark else AmethystLight
        ThemeColor.GRAPHITE -> if (darkTheme) GraphiteDark else GraphiteLight
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
