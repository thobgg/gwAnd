package de.bgghome.gwand

import de.bgghome.gwand.api.GwClient
import de.bgghome.gwand.api.Konflikt
import de.bgghome.gwand.daten.Bearbeiten
import de.bgghome.gwand.daten.NeuePerson
import de.bgghome.gwand.daten.Schreibhilfe
import de.bgghome.gwand.daten.Verwandtschaftsart
import de.bgghome.webtrees.nativ.data.Ablage
import de.bgghome.webtrees.nativ.data.DateForm
import de.bgghome.webtrees.nativ.data.DatePart
import de.bgghome.webtrees.nativ.data.DateQualifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Alle Schreibvorgaenge gegen den Testserver (Konto "editor"). Laeuft nur mit ~/.config/gwand/editor-pass und
 * erreichbarem Server; legt eigene Testpersonen an und loescht sie am Ende wieder, auch bei Fehlern.
 */
class LiveSchreibTest {

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
    fun schreibenGegenTestserver() = runBlocking {
        val pass = File(System.getProperty("user.home"), ".config/gwand/editor-pass")
        if (!pass.exists()) { println("LiveSchreibTest uebersprungen: keine Passwortdatei"); return@runBlocking }
        val client = GwClient(Speicher(), "gwAnd-Test")
        try { client.anmelden("http://192.168.178.73:8096", "editor", pass.readText().trim()) } catch (e: Exception) {
            println("LiveSchreibTest uebersprungen: ${e.message}"); return@runBlocking
        }
        val b = Bearbeiten(client)
        val personen = mutableListOf<String>()
        try {
            val geburt = Schreibhilfe.datum(DateForm(DateQualifier.EXACT, DatePart("14", 3, "1985")))
            val p = b.personAnlegen(NeuePerson("Gwand", "Testperson", 1, geburt)); personen += p
            var roh = client.roh("people", p)
            assertEquals(0, roh["birth_ref_index"]!!.jsonPrimitive.content.toInt(), "Geburt als Lebensdatum eingetragen")

            // Ereignis anlegen, aendern, Konflikt bei veraltetem Stand
            val stand = roh["change"]!!.jsonPrimitive.content.toLong()
            val e = b.ereignisAnlegen(p, stand, "Baptism", Schreibhilfe.datum(DateForm(DateQualifier.EXACT, DatePart("20", 3, "1985"))), null, "")
            assertFailsWith<Konflikt> { b.personAendern(p, stand, "Gwand", "Testperson", "Gwa", 1) }
            roh = client.roh("people", p)
            b.personAendern(p, roh["change"]!!.jsonPrimitive.content.toLong(), "Gwand", "Testperson", "Gwa", 1)
            b.ereignisAendern(e, null, "Christening", null, datumAendern = false, ort = null, beschreibung = "Testtaufe")
            assertEquals("Christening", client.roh("events", e)["type"]!!.jsonPrimitive.content)
            assertEquals("Gwa", client.roh("people", p)["primary_name"]!!.jsonObject["call"]!!.jsonPrimitive.content)

            // Verwandte: neuer Vater, neues Kind, vorhandene Person als Partnerin
            val vater = b.verwandterHinzu(p, 1, Verwandtschaftsart.Vater, NeuePerson("Vater", "Testperson", 1), null); personen += vater
            val mutter = b.verwandterHinzu(p, 1, Verwandtschaftsart.Mutter, NeuePerson("Mutter", "Testperson", 0), null); personen += mutter
            val partnerin = b.personAnlegen(NeuePerson("Partnerin", "Testperson", 0)); personen += partnerin
            b.verwandterHinzu(p, 1, Verwandtschaftsart.Partner, null, partnerin)
            val kind = b.verwandterHinzu(p, 1, Verwandtschaftsart.Kind, NeuePerson("Kind", "Testperson", 2), null); personen += kind
            roh = client.roh("people", p)
            val elternfamilie = (roh["parent_family_list"] as JsonArray).single().jsonPrimitive.content
            val f = client.roh("families", elternfamilie)
            assertEquals(vater, f["father_handle"]!!.jsonPrimitive.content)
            assertEquals(mutter, f["mother_handle"]!!.jsonPrimitive.content, "Mutter kam in dieselbe Elternfamilie")
            val partnerfamilie = (roh["family_list"] as JsonArray).single().jsonPrimitive.content
            assertTrue((client.roh("families", partnerfamilie)["child_ref_list"] as JsonArray).any { it.jsonObject["ref"]!!.jsonPrimitive.content == kind },
                "Kind kam in die Familie mit der Partnerin")

            // Foto, Zitat, Beziehung, Ereignis entfernen
            val jpg = Base64.getDecoder().decode("/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=")
            val m = b.fotoHinzu(p, jpg, "image/jpeg", "Testbild", hauptbild = true)
            val quelle = client.quellen("", 1, 1).eintraege.first().handle
            val z = b.zitatHinzu("events", e, quelle, "Testseite", 3)
            b.beziehungHinzu(p, partnerin, "Pate")
            roh = client.roh("people", p)
            assertEquals(m, (roh["media_list"] as JsonArray).first().jsonObject["ref"]!!.jsonPrimitive.content)
            assertTrue((client.roh("events", e)["citation_list"] as JsonArray).any { it.jsonPrimitive.content == z })
            b.ereignisEntfernen(p, e)
            assertFailsWith<Exception> { client.roh("events", e) }
            client.loeschen("citations", z)
            client.loeschen("media", m)
        } finally {
            // Familien und Ereignisse der Testpersonen, dann die Personen
            for (h in personen) runCatching {
                val r = client.roh("people", h)
                for (k in listOf("family_list", "parent_family_list")) (r[k] as? JsonArray).orEmpty().forEach { runCatching { client.loeschen("families", it.jsonPrimitive.content) } }
                (r["event_ref_list"] as? JsonArray).orEmpty().forEach { runCatching { client.loeschen("events", it.jsonObject["ref"]!!.jsonPrimitive.content) } }
            }
            for (h in personen) runCatching { client.loeschen("people", h) }
        }
    }

    @Test
    fun zusammenfuehrenVerlaufRueckgaengig() = runBlocking {
        val pass = File(System.getProperty("user.home"), ".config/gwand/editor-pass")
        if (!pass.exists()) return@runBlocking
        val client = GwClient(Speicher(), "gwAnd-Test")
        Thread.sleep(1500) // Anmeldesperre des Servers: hoechstens etwa eine Anmeldung je Sekunde
        try { client.anmelden("http://192.168.178.73:8096", "editor", pass.readText().trim()) } catch (e: Exception) { return@runBlocking }
        val b = Bearbeiten(client)
        val a = b.personAnlegen(NeuePerson("Doppel", "Testperson", 1, Schreibhilfe.datum(DateForm(DateQualifier.EXACT, DatePart("", 0, "1900")))))
        val zwei = b.personAnlegen(NeuePerson("Doppel", "Testperson", 1, Schreibhilfe.datum(DateForm(DateQualifier.ABOUT, DatePart("", 0, "1900")))))
        try {
            client.zusammenfuehren(a, zwei)
            assertFailsWith<Exception> { client.roh("people", zwei) }
            val geburten = (client.roh("people", a)["event_ref_list"] as JsonArray).size
            assertEquals(2, geburten, "beide Geburten liegen jetzt bei der bleibenden Person")

            // Verlauf: Rufname aendern, dann genau diese Aenderung zuruecknehmen
            b.personAendern(a, null, "Doppel", "Testperson", "Dop", 1)
            val letzte = client.objektVerlauf("Person", a).maxBy { it.transaktion }
            assertTrue(client.rueckgaengigPruefen(letzte.transaktion).ohneKonflikt)
            client.rueckgaengig(letzte.transaktion)?.let { assertTrue(client.warten(it).fertig) }
            assertEquals("", client.roh("people", a)["primary_name"]!!.jsonObject["call"]!!.jsonPrimitive.content)
            assertTrue(client.verlauf(1, 5).eintraege.isNotEmpty())
        } finally {
            for (h in listOf(a, zwei)) runCatching {
                val r = client.roh("people", h)
                (r["event_ref_list"] as? JsonArray).orEmpty().forEach { runCatching { client.loeschen("events", it.jsonObject["ref"]!!.jsonPrimitive.content) } }
                client.loeschen("people", h)
            }
        }
    }
}
