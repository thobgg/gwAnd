package de.bgghome.gwand

import de.bgghome.gwand.api.GwClient
import de.bgghome.gwand.api.versionMindestens
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Abfragen nach Handle und Suche in Listen: ab Gramps Web API 3.23 ueber die Regel MatchesQuery (GOQL), davor ueber
 * `gql`, das mit 3.26 wegfaellt. Der Live-Teil prueft beide Wege am Testserver (laeuft nur mit Passwortdatei).
 */
class AbfrageTest {

    private class Speicher : Ablage {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = m[key] as? String ?: default
        override fun putString(key: String, value: String?) { m[key] = value }
        override fun getBoolean(key: String, default: Boolean) = m[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
        override fun alle() = m.filterValues { it is String }.mapValues { it.value as String }
        override fun leeren() = m.clear()
    }

    @Test
    fun versionen() {
        assertTrue(versionMindestens("3.23.0", 3, 23))
        assertTrue(versionMindestens("3.25.2", 3, 23))
        assertTrue(versionMindestens("v3.26.0", 3, 23))
        assertTrue(versionMindestens("4.0", 3, 23))
        assertFalse(versionMindestens("3.22.9", 3, 23))
        assertFalse(versionMindestens("2.9", 3, 23))
        assertFalse(versionMindestens("", 3, 23))
        assertFalse(versionMindestens("unbekannt", 3, 23))
    }

    @Test
    fun beideWegeGegenTestserver() = runBlocking {
        val pass = File(System.getProperty("user.home"), ".config/gwand/editor-pass")
        if (!pass.exists()) { println("AbfrageTest uebersprungen: keine Passwortdatei"); return@runBlocking }
        val client = GwClient(Speicher(), "gwAnd-Test")
        try { client.anmelden("http://192.168.178.73:8096", "editor", pass.readText().trim()) } catch (e: Exception) {
            println("AbfrageTest uebersprungen: ${e.message}"); return@runBlocking
        }
        val version = client.metadaten().api.version
        val handles = client.personen("", 1, 3).eintraege.map { it.handle }
        assertEquals(3, handles.size)

        fun pruefen(weg: String) = runBlocking {
            assertEquals(handles.toSet(), client.personenNachHandle(handles).map { it.handle }.toSet(), "$weg: Personen nach Handle")
            assertTrue(client.orte("celle", 1).eintraege.isNotEmpty(), "$weg: Ortssuche")
            assertTrue(client.archive("archiv", 1).eintraege.isNotEmpty(), "$weg: Archivsuche")
            // Platzhalter und Anfuehrungszeichen in der Eingabe duerfen die Abfrage nicht brechen
            client.quellen("50% \"x' _", 1)
        }

        if (versionMindestens(version, 3, 23)) {
            assertTrue(client.mitGoql)
            pruefen("GOQL")
        }
        if (!versionMindestens(version, 3, 26)) {
            // Der alte Weg, solange der Server gql noch kennt
            client.apiVersion = "3.22.0"
            assertFalse(client.mitGoql)
            pruefen("gql")
        }
    }
}
