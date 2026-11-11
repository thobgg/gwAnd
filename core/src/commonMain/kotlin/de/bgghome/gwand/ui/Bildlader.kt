package de.bgghome.gwand.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import de.bgghome.gwand.api.GwClient

/** Vorschaubilder brauchen das Token der Anmeldung; der OkHttp-Client von [GwClient] haengt es an Anfragen an den eigenen Server. */
fun gwBildlader(context: PlatformContext, client: GwClient): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client.http })) }
        .crossfade(true)
        .build()
