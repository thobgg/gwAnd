package de.bgghome.gwand.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Eingebettete WebView fuer die Anmeldung beim OIDC-Anbieter. Cookies (Sitzung beim Anbieter) bleiben bis zum
 * Abmelden in gwAnd erhalten, damit eine erneute Anmeldung ohne Passwort beim Anbieter geht.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun OidcWebseite(url: String, ergebnis: (code: String?, fehler: String?) -> Unit, modifier: Modifier) {
    val melden = rememberUpdatedState(ergebnis)
    val erledigt = remember { booleanArrayOf(false) }
    val erlaubt = remember(url) { oidcRuecksprungHosts(url) }
    fun pruefen(u: String): Boolean {
        // Klartext nur im Heimnetz: Zugangsdaten fuer den Anmeldedienst nie unverschluesselt uebers Internet
        if (u.startsWith("http://", true) && !oidcKlartextErlaubt(u, erlaubt)) {
            if (!erledigt[0]) { erledigt[0] = true; melden.value(null, OIDC_KLARTEXT) }
            return true
        }
        val r = oidcRuecksprung(u, erlaubt) ?: return false
        if (!erledigt[0]) { erledigt[0] = true; melden.value(r.first, r.second) }
        return true
    }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Fremde Seiten (Anmeldedienst) sollen weder Dateien noch Inhalte der App erreichen
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    // Umleitungen des Servers kommen hier an, samt Fragment: abfangen, nicht laden
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        pruefen(request.url.toString())

                    // Zur Sicherheit: falls die Seite doch zu laden beginnt, sofort anhalten (bevor ihr Skript den Code einloest)
                    override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) {
                        if (pruefen(u)) view.stopLoading() else super.onPageStarted(view, u, favicon)
                    }
                }
                loadUrl(url)
            }
        },
        modifier = modifier,
        onRelease = { it.stopLoading(); it.destroy() },
    )
}
