package de.bgghome.gwand

import com.sun.net.httpserver.HttpServer
import de.bgghome.gwand.api.AnmeldungAbgelaufen
import de.bgghome.gwand.api.GwClient
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Erneuern des Access-Tokens: Nur wenn der Server das Refresh-Token ablehnt, ist die Anmeldung weg. Voruebergehende
 * Fehler (Neustart, Proxy, keine Verbindung) lassen sie stehen; 429 wird wiederholt.
 */
class ErneuernTest {

    private class Speicher : Ablage {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = m[key] as? String ?: default
        override fun putString(key: String, value: String?) { m[key] = value }
        override fun getBoolean(key: String, default: Boolean) = m[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
        override fun alle() = m.filterValues { it is String }.mapValues { it.value as String }
        override fun leeren() = m.clear()
    }

    /** Lokaler Server: `/api/token/refresh/` antwortet der Reihe nach mit den Statuscodes aus [antworten]. */
    private fun mitServer(vararg antworten: Int, block: (GwClient, AtomicInteger) -> Unit) {
        val aufrufe = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/token/refresh/") { x ->
            val code = antworten[minOf(aufrufe.getAndIncrement(), antworten.size - 1)]
            val text = (if (code == 200) """{"access_token":"neu"}""" else """{"message":"x"}""").toByteArray()
            x.sendResponseHeaders(code, text.size.toLong())
            x.responseBody.use { it.write(text) }
        }
        server.start()
        try {
            val ablage = Speicher().apply {
                putString("basisUrl", "http://127.0.0.1:${server.address.port}")
                putString("refreshToken", "alt")
            }
            val client = GwClient(ablage, "gwAnd-Test").apply { warten429 = { 10L } }
            block(client, aufrufe)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun erfolg() = mitServer(200) { c, n ->
        runBlocking { c.fortsetzen() }
        assertTrue(c.hatAnmeldung)
        assertEquals(1, n.get())
    }

    @Test
    fun abgelehntMeldetAb() {
        for (code in listOf(401, 403, 422)) mitServer(code) { c, _ ->
            assertFailsWith<AnmeldungAbgelaufen>("HTTP $code") { runBlocking { c.fortsetzen() } }
            assertFalse(c.hatAnmeldung, "HTTP $code")
        }
    }

    @Test
    fun voruebergehendBleibtAngemeldet() {
        for (code in listOf(500, 502, 503, 504)) mitServer(code) { c, _ ->
            val e = assertFailsWith<IOException>("HTTP $code") { runBlocking { c.fortsetzen() } }
            assertFalse(e is AnmeldungAbgelaufen, "HTTP $code")
            assertTrue(c.hatAnmeldung, "HTTP $code")
        }
    }

    @Test
    fun zuVieleAnfragenWirdWiederholt() = mitServer(429, 429, 200) { c, n ->
        runBlocking { c.fortsetzen() }
        assertTrue(c.hatAnmeldung)
        assertEquals(3, n.get())
    }

    @Test
    fun dauerhaft429BleibtAngemeldet() = mitServer(429) { c, n ->
        val e = assertFailsWith<IOException> { runBlocking { c.fortsetzen() } }
        assertFalse(e is AnmeldungAbgelaufen)
        assertTrue(c.hatAnmeldung)
        assertEquals(3, n.get())
    }

    @Test
    fun ohneServerBleibtAngemeldet() {
        val ablage = Speicher().apply {
            putString("basisUrl", "http://127.0.0.1:9")
            putString("refreshToken", "alt")
        }
        val c = GwClient(ablage, "gwAnd-Test")
        val e = assertFailsWith<IOException> { runBlocking { c.fortsetzen() } }
        assertFalse(e is AnmeldungAbgelaufen)
        assertTrue(c.hatAnmeldung)
    }
}
