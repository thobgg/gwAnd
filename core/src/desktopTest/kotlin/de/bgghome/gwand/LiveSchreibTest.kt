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
            // Der Server fuehrt `change` nur sekundengenau: ohne Pause faellt die Aenderung in dieselbe Sekunde
            Thread.sleep(1100)
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

    /** DNA-Treffer wie die Web-Oberflaeche: Vorschau, eintragen (Beziehung "DNA" plus Notiz, gleich privat), lesen, loeschen. */
    @Test
    fun dnaTrefferEintragenUndLoeschen() = runBlocking {
        val pass = File(System.getProperty("user.home"), ".config/gwand/editor-pass")
        if (!pass.exists()) return@runBlocking
        val client = GwClient(Speicher(), "gwAnd-Test")
        Thread.sleep(1500)
        try { client.anmelden("http://192.168.178.73:8096", "editor", pass.readText().trim()) } catch (e: Exception) { return@runBlocking }
        val b = Bearbeiten(client)
        val personen = mutableListOf<String>()
        val notizen = mutableListOf<String>()
        try {
            val p = b.personAnlegen(NeuePerson("Dna", "Testperson", 1)); personen += p
            val vater = b.verwandterHinzu(p, 1, Verwandtschaftsart.Vater, NeuePerson("DnaVater", "Testperson", 1), null); personen += vater
            val rohdaten = "Chromosome,Start,End,cM,SNPs\n3,56950055,64247327,10.9,1404\nX,1000000,20000000,22.5,3000"

            val vorschau = client.dnaVorschau(rohdaten)
            assertEquals(listOf("3", "X"), vorschau.map { it.chromosome })
            assertEquals(33.4, vorschau.sumOf { it.cM }, 0.001)
            assertTrue(client.dnaVorschau("das ist keine Tabelle").isEmpty(), "Unlesbares ergibt eine leere Vorschau")
            assertTrue(client.dnaTreffer(p).isEmpty())

            val notiz = b.dnaTrefferHinzu(p, vater, "\n$rohdaten\n", privat = true); notizen += notiz
            val verweis = (client.roh("people", p)["person_ref_list"] as JsonArray).single().jsonObject
            assertEquals("DNA", verweis["rel"]!!.jsonPrimitive.content)
            assertEquals(vater, verweis["ref"]!!.jsonPrimitive.content)
            assertEquals("true", verweis["private"]!!.jsonPrimitive.content)
            assertEquals(notiz, (verweis["note_list"] as JsonArray).single().jsonPrimitive.content)
            val n = client.roh("notes", notiz)
            assertEquals(rohdaten, n["text"]!!.jsonObject["string"]!!.jsonPrimitive.content, "Rohdaten unveraendert (nur aussen gekuerzt) in der Notiz")
            assertEquals("true", n["private"]!!.jsonPrimitive.content, "Notiz so privat wie die Beziehung")

            val t = client.dnaTreffer(p).single()
            assertEquals(vater, t.handle)
            assertEquals(listOf(notiz), t.notizen)
            assertEquals(0, t.verweisIndex)
            assertEquals(2, t.segments.size)
            assertEquals(22.5, t.groesstes, 0.001)
            assertTrue(t.relation.isNotBlank(), "Verwandtschaft zum Vater benannt")
            assertEquals("P", t.segments.single { it.chromosome == "3" }.side, "ueber den Vater verwandt: vaeterliche Seite")
            assertEquals("M", t.segments.single { it.chromosome == "X" }.side, "das X eines Mannes kommt von der Mutter")
            assertEquals(null, client.ydna(p).haplogruppe, "ohne Y-DNA-Attribut keine Haplogruppe")

            // Loeschen nimmt nur die DNA-Beziehung: eine andere Beziehung zur selben Person und die Notiz bleiben
            b.beziehungHinzu(p, vater, "Pate")
            b.dnaTrefferEntfernen(p, vater)
            assertTrue(client.dnaTreffer(p).isEmpty())
            assertEquals("Pate", (client.roh("people", p)["person_ref_list"] as JsonArray).single().jsonObject["rel"]!!.jsonPrimitive.content)
            assertEquals(rohdaten, client.roh("notes", notiz)["text"]!!.jsonObject["string"]!!.jsonPrimitive.content)

            // Scheitert das Eintragen an der Person, bleibt keine Notiz zurueck
            val vorher = client.roh("people", p)["person_ref_list"]
            assertFailsWith<Exception> { b.dnaTrefferHinzu("gibtesnicht", vater, rohdaten, false) }
            assertEquals(vorher, client.roh("people", p)["person_ref_list"])
        } finally {
            for (h in notizen) runCatching { client.loeschen("notes", h) }
            for (h in personen) runCatching {
                val r = client.roh("people", h)
                for (k in listOf("family_list", "parent_family_list")) (r[k] as? JsonArray).orEmpty().forEach { runCatching { client.loeschen("families", it.jsonPrimitive.content) } }
            }
            for (h in personen) runCatching { client.loeschen("people", h) }
        }
    }

    @Test
    fun exportBerichtVerwandtschaft() = runBlocking {
        val pass = File(System.getProperty("user.home"), ".config/gwand/editor-pass")
        if (!pass.exists()) return@runBlocking
        val client = GwClient(Speicher(), "gwAnd-Test")
        Thread.sleep(1500)
        try { client.anmelden("http://192.168.178.73:8096", "editor", pass.readText().trim()) } catch (e: Exception) { return@runBlocking }
        val ged = client.export("ged")
        assertTrue(String(ged.daten.copyOf(200)).contains("0 HEAD"), "GEDCOM beginnt mit HEAD")
        val pdf = client.bericht("ancestor_report", "I0001", "Ahnentafel")
        assertTrue(pdf.name.endsWith(".pdf") && String(pdf.daten.copyOf(5)) == "%PDF-", "Bericht ist ein PDF")
        // Handles ueber die Gramps-ID holen: sie aendern sich, wenn der Testbaum neu eingespielt wird
        client.metadaten() // wie die App beim Start: daran erkennt der Client, welche Abfrageform der Server versteht
        val jonas = client.personNachId("I0001")!!.handle
        val wege = client.verwandtschaftsWege(jonas, client.personNachId("I0008")!!.handle)
        assertTrue(wege.isNotEmpty() && wege.first().text.isNotBlank())
        // Der Dialog "Verwandtschaft zu" holt zu den Wegen die gemeinsamen Vorfahren nach (Issue 1: das scheiterte in 1.2 an API 3.26)
        val cousin = client.verwandtschaftsWege(jonas, client.personNachId("I0012")!!.handle)
        val vorfahren = cousin.first().gemeinsameVorfahren.toSet()
        assertEquals(2, vorfahren.size, "Cousins haben ein gemeinsames Grosselternpaar")
        assertEquals(vorfahren, client.personenNachHandle(vorfahren).map { it.handle }.toSet())
        // Nicht verwandt: ein leerer Eintrag, den der Dialog als "keine Verwandtschaft" zeigt
        val fremd = client.verwandtschaftsWege(jonas, client.personNachId("I0493")!!.handle)
        assertTrue(fremd.all { it.text.isBlank() && it.gemeinsameVorfahren.isEmpty() })
    }

    @Test
    fun offlineLesecache() = runBlocking {
        val pass = File(System.getProperty("user.home"), ".config/gwand/editor-pass")
        if (!pass.exists()) return@runBlocking
        val ordner = kotlin.io.path.createTempDirectory("gwand-cache").toFile()
        val client = GwClient(Speicher(), "gwAnd-Test", ordner)
        var offline = false
        client.offline = { offline = it }
        Thread.sleep(1500)
        try { client.anmelden("http://192.168.178.73:8096", "editor", pass.readText().trim()) } catch (e: Exception) { return@runBlocking }
        val online = client.metadaten()
        val liste = client.personen("", 1, 5)
        assertTrue(!offline)
        client.netzGesperrt = true
        assertEquals(online.database.name, client.metadaten().database.name, "Metadaten aus dem Cache")
        assertEquals(liste.gesamt, client.personen("", 1, 5).gesamt, "Gesamtzahl aus dem Cache")
        assertTrue(offline, "Offline gemeldet")
        assertFailsWith<java.io.IOException> { client.personen("NieGesuchterName", 1, 5) }
        assertFailsWith<java.io.IOException> { client.anlegen(listOf(Schreibhilfe.notiz(Schreibhilfe.neuesHandle(), "x"))) }
        client.abmelden()
        assertFailsWith<java.io.IOException> { client.metadaten() }
        ordner.deleteRecursively()
    }
}
