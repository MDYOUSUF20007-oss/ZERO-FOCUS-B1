package app.amoledfocus.ui

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Accents = listOf(0xFF3D8BFF, 0xFF22D3EE, 0xFFA78BFA, 0xFF34D399, 0xFFFBBF24, 0xFFF87171).map { it.toInt() }

@Composable fun FocusTheme(appearance: String, accent: Int, content: @Composable () -> Unit) {
    val a = Color(accent)
    val scheme = when (appearance) {
        "LIGHT" -> lightColorScheme(primary = a, background = Color(0xFFFAFAFA), surface = Color(0xFFFAFAFA), surfaceContainer = Color(0xFFEFEFF1), onBackground = Color(0xFF101012), onSurface = Color(0xFF101012), onSurfaceVariant = Color(0xFF55555C), outline = Color(0xFFD0D0D6))
        "DARK" -> darkColorScheme(primary = a, background = Color(0xFF121214), surface = Color(0xFF121214), surfaceContainer = Color(0xFF1C1C20), onBackground = Color(0xFFF2F2F4), onSurface = Color(0xFFF2F2F4), onSurfaceVariant = Color(0xFFA0A0A8), outline = Color(0xFF34343A))
        else -> darkColorScheme(primary = a, background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF0B0B0D), onBackground = Color(0xFFF2F2F4), onSurface = Color(0xFFF2F2F4), onSurfaceVariant = Color(0xFF9A9AA2), outline = Color(0xFF26262A))
    }
    val num = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontFeatureSettings = "tnum")
    MaterialTheme(colorScheme = scheme, typography = Typography(
        displayLarge = num.copy(fontSize = 64.sp, letterSpacing = (-1).sp), displayMedium = num.copy(fontSize = 44.sp),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp)), content = content)
}

/** Honors the system "remove animations" setting. */
@Composable fun reducedMotion(): Boolean =
    Settings.Global.getFloat(LocalContext.current.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
