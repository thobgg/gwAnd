package de.bgghome.gwand.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.cancel
import de.bgghome.gwand.res.login_oidc_title
import org.jetbrains.compose.resources.stringResource
import java.net.URLDecoder

/*
 * Anmeldung ueber OIDC. Gramps Web leitet nach der Anmeldung beim Anbieter fest auf seine eigene Web-Oberflaeche um
 * (`…/oidc/complete#code=…`); eine Ruecksprungadresse fuer Apps gibt es nicht. Darum laeuft die Anmeldung in einem
 * eingebetteten Browserfenster, das diese Adresse abfaengt, bevor die Web-Oberflaeche den Code selbst einloest.
 */

/** Die Webseite des Anmeldefensters; meldet genau einmal Code, Fehler oder (beides null) nichts. */
@Composable
expect fun OidcWebseite(url: String, ergebnis: (code: String?, fehler: String?) -> Unit, modifier: Modifier)

@Composable
fun OidcAnmeldung(f: OidcFenster, viewModel: GwViewModel) {
    BackHandler { viewModel.oidcErgebnis(null, null) }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.oidcErgebnis(null, null) }) {
                Icon(Icons.Default.Close, stringResource(Res.string.cancel))
            }
            Text(
                stringResource(Res.string.login_oidc_title, f.anbieter), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        OidcWebseite(f.url, viewModel::oidcErgebnis, Modifier.fillMaxSize().imePadding())
    }
}

/**
 * Ist [url] der Ruecksprung nach `…/oidc/complete`? Dann Code bzw. Fehler daraus, sonst null.
 * Ohne Code und ohne Fehler ist es ein Server vor API 3.21, der die Token noch ueber Cookies weitergab.
 */
fun oidcRuecksprung(url: String): Pair<String?, String?>? {
    val ohneFragment = url.substringBefore('#')
    val pfad = ohneFragment.substringBefore('?').trimEnd('/')
    if (!pfad.endsWith("/oidc/complete")) return null
    fun werte(teil: String) = teil.split('&').filter { '=' in it }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }
    val w = werte(ohneFragment.substringAfter('?', "")) + werte(url.substringAfter('#', ""))
    w["code"]?.takeIf { it.isNotBlank() }?.let { return it to null }
    w["error"]?.takeIf { it.isNotBlank() }?.let { return null to it }
    return null to GwViewModel.OIDC_ALT
}
