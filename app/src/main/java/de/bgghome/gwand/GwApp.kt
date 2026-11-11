package de.bgghome.gwand

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import de.bgghome.gwand.api.GwClient
import de.bgghome.gwand.ui.GwPlattform
import de.bgghome.gwand.ui.gwBildlader
import de.bgghome.webtrees.nativ.data.AndroidAblage
import de.bgghome.webtrees.nativ.ui.karte.kachelUserAgent

class GwApp : Application(), SingletonImageLoader.Factory {

    lateinit var plattform: GwPlattform
        private set

    /** Setzt die laufende Activity: fragt nach der Erlaubnis fuer Benachrichtigungen (Android 13+). */
    var berechtigungAnfragen: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val client = GwClient(AndroidAblage(this, "gwclient"), "gwAnd/$version (Android ${Build.VERSION.RELEASE})", cacheDir)
        // Sprache der Oberflaeche: wie das System oder im Menue gewaehlt (gemeinsam mit wtAnd); der Server spricht dieselbe
        de.bgghome.webtrees.nativ.Sprache.start(de.bgghome.webtrees.nativ.data.Settings(AndroidAblage(this, "einstellungen")))
        client.sprache = de.bgghome.webtrees.nativ.Sprache.aktiv
        // OpenStreetMap verlangt eine ehrliche Kennung der App.
        kachelUserAgent = "gwAnd/$version (Android; https://github.com/thobgg/gwAnd)"
        plattform = GwPlattform(
            client = client,
            einstellungen = AndroidAblage(this, "einstellungen"),
            versionName = version,
            teilen = { titel, text ->
                val senden = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, titel).putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(senden, titel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            oeffnen = { url ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            dateiOeffnen = { datei, teilen ->
                // In den Cache schreiben und ueber den FileProvider an eine andere App geben (Ansehen oder Teilen)
                val ordner = java.io.File(cacheDir, "dateien").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val name = datei.name.replace(Regex("[^A-Za-z0-9._ äöüÄÖÜß–-]"), "_")
                val f = java.io.File(ordner, name).apply { writeBytes(datei.daten) }
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", f)
                val absicht = if (teilen) Intent(Intent.ACTION_SEND).setType(datei.mime).putExtra(Intent.EXTRA_STREAM, uri)
                else Intent(Intent.ACTION_VIEW).setDataAndType(uri, datei.mime)
                absicht.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { startActivity(Intent.createChooser(absicht, datei.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            anmeldedatenVergessen = {
                // Sitzung beim OIDC-Anbieter aus dem Anmeldefenster: beim Abmelden mitloeschen
                runCatching { android.webkit.CookieManager.getInstance().removeAllCookies(null) }
            },
            kannErinnern = true,
            erinnerungSetzen = { an ->
                if (an) berechtigungAnfragen?.invoke()
                Erinnerung.planen(this, an)
            },
        )
        // Zeitplan nach einem Update auffrischen
        if (AndroidAblage(this, "einstellungen").getBoolean(de.bgghome.gwand.ui.GwViewModel.K_ERINNERUNG, false)) Erinnerung.planen(this, true)
    }

    // Vorschaubilder brauchen das Token der Anmeldung.
    override fun newImageLoader(context: PlatformContext): ImageLoader = gwBildlader(context, plattform.client)

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Android setzt die Voreinstellung bei Drehen oder neuer Systemsprache zurueck: die Wahl erneut anlegen
        de.bgghome.webtrees.nativ.Sprache.anwenden()
    }
}
