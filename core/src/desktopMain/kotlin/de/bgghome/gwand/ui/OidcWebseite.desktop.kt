package de.bgghome.gwand.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Der Desktop-Zweig dient nur den Tests; eine eingebettete Anmeldeseite gibt es dort nicht. */
@Composable
actual fun OidcWebseite(url: String, ergebnis: (code: String?, fehler: String?) -> Unit, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { Text(url) }
}
