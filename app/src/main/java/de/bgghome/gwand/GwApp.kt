package de.bgghome.gwand

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import de.bgghome.gwand.api.GwClient
import de.bgghome.gwand.ui.GwPlattform
import de.bgghome.gwand.ui.gwBildlader
import de.bgghome.webtrees.nativ.data.AndroidAblage
import java.util.Locale

class GwApp : Application(), SingletonImageLoader.Factory {

    lateinit var plattform: GwPlattform
        private set

    override fun onCreate() {
        super.onCreate()
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val client = GwClient(AndroidAblage(this, "gwclient"), "gwAnd/$version (Android ${Build.VERSION.RELEASE})")
        client.sprache = Locale.getDefault().language.ifEmpty { "en" }
        plattform = GwPlattform(client, AndroidAblage(this, "einstellungen"), version)
    }

    // Vorschaubilder brauchen das Token der Anmeldung.
    override fun newImageLoader(context: PlatformContext): ImageLoader = gwBildlader(context, plattform.client)
}
