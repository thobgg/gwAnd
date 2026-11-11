package de.bgghome.gwand.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Haupt- und Zweitfarbe wie in Gramps Web; der Betreiber kann sie je Baum setzen (`frontend.primaryColor`). */
data class Baumfarben(val haupt: Color = GRAMPS_HAUPT, val zweit: Color = GRAMPS_ZWEIT) {
    companion object {
        val GRAMPS_HAUPT = Color(0xFF6D4C41)
        val GRAMPS_ZWEIT = Color(0xFF0277BD)

        /** Aus den Baumeinstellungen des Servers; fehlt eine Farbe oder ist sie unlesbar, gilt der Standard von Gramps Web. */
        fun aus(einstellungen: Map<String, String>): Baumfarben = Baumfarben(
            haupt = farbe(einstellungen["frontend.primaryColor"]) ?: GRAMPS_HAUPT,
            zweit = farbe(einstellungen["frontend.secondaryColor"]) ?: GRAMPS_ZWEIT,
        )

        fun farbe(hex: String?): Color? {
            val h = hex?.trim()?.removePrefix("#") ?: return null
            if (h.length != 6 || h.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
            return Color(0xFF000000 or h.toLong(16))
        }
    }
}

private fun auf(c: Color): Color = if (c.luminance() > 0.45f) Color(0xFF1C1B1A) else Color.White

/** Helles und dunkles Schema aus den zwei Baumfarben; Flaechen neutral, damit Fotos und Karten wirken. */
private fun hell(f: Baumfarben) = lightColorScheme(
    primary = f.haupt, onPrimary = auf(f.haupt),
    primaryContainer = lerp(Color.White, f.haupt, 0.18f), onPrimaryContainer = lerp(f.haupt, Color.Black, 0.55f),
    secondary = f.zweit, onSecondary = auf(f.zweit),
    secondaryContainer = lerp(Color.White, f.zweit, 0.16f), onSecondaryContainer = lerp(f.zweit, Color.Black, 0.55f),
    tertiary = f.zweit, onTertiary = auf(f.zweit),
    background = Color(0xFFF4F3F2), onBackground = Color(0xFF2B2826),
    surface = Color.White, onSurface = Color(0xFF2B2826),
    surfaceVariant = Color(0xFFEBE8E6), onSurfaceVariant = Color(0xFF615C59),
    surfaceContainerLowest = Color(0xFFF4F3F2), surfaceContainerLow = Color(0xFFF9F8F7),
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFEFEDEB),
    outline = Color(0xFFA39D99), outlineVariant = Color(0xFFE4E0DD),
)

private fun dunkel(f: Baumfarben): androidx.compose.material3.ColorScheme {
    val haupt = lerp(f.haupt, Color.White, 0.45f)
    val zweit = lerp(f.zweit, Color.White, 0.40f)
    return darkColorScheme(
        primary = haupt, onPrimary = lerp(f.haupt, Color.Black, 0.6f),
        primaryContainer = lerp(f.haupt, Color.Black, 0.35f), onPrimaryContainer = lerp(Color.White, f.haupt, 0.15f),
        secondary = zweit, onSecondary = lerp(f.zweit, Color.Black, 0.6f),
        secondaryContainer = lerp(f.zweit, Color.Black, 0.4f), onSecondaryContainer = lerp(Color.White, f.zweit, 0.15f),
        tertiary = zweit,
        background = Color(0xFF151312), onBackground = Color(0xFFE7E2DF),
        surface = Color(0xFF1C1A19), onSurface = Color(0xFFE7E2DF),
        surfaceVariant = Color(0xFF2E2B29), onSurfaceVariant = Color(0xFFB9B2AE),
        surfaceContainerLowest = Color(0xFF151312), surfaceContainerLow = Color(0xFF191716),
        surfaceContainer = Color(0xFF1C1A19), surfaceContainerHigh = Color(0xFF232120), surfaceContainerHighest = Color(0xFF2E2B29),
        outline = Color(0xFF857E7A), outlineVariant = Color(0xFF3A3634),
    )
}

@Composable
fun GwTheme(farben: Baumfarben = Baumfarben(), dark: Boolean? = null, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark ?: isSystemInDarkTheme()) dunkel(farben) else hell(farben), content = content)
}

/** Darstellung des Kopfbalkens: in der Themenfarbe des Baums (wie bisher) oder hell wie das neue Gramps Web. */
enum class Darstellung { Klassisch, Hell }

val LocalDarstellung = androidx.compose.runtime.staticCompositionLocalOf { Darstellung.Klassisch }

/** Farben fuer alle Kopfbalken der App, nach der gewaehlten Darstellung. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun kopfFarben(): androidx.compose.material3.TopAppBarColors {
    val c = MaterialTheme.colorScheme
    return if (LocalDarstellung.current == Darstellung.Hell) androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
        containerColor = c.surface, titleContentColor = c.onSurface,
        navigationIconContentColor = c.onSurfaceVariant, actionIconContentColor = c.onSurfaceVariant,
    ) else androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
        containerColor = c.primary, titleContentColor = c.onPrimary,
        navigationIconContentColor = c.onPrimary, actionIconContentColor = c.onPrimary,
    )
}
