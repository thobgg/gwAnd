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
import java.util.Locale

class GwApp : Application(), SingletonImageLoader.Factory {

    lateinit var plattform: GwPlattform
        private set

    /** Setzt die laufende Activity: fragt nach der Erlaubnis fuer Benachrichtigungen (Android 13+). */
    var berechtigungAnfragen: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val client = GwClient(AndroidAblage(this, "gwclient"), "gwAnd/$version (Android ${Build.VERSION.RELEASE})")
        client.sprache = Locale.getDefault().language.ifEmpty { "en" }
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
}
