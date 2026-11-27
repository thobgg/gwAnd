package de.bgghome.gwand

import de.bgghome.gwand.api.DnaSegment
import de.bgghome.gwand.api.DnaTreffer
import de.bgghome.gwand.api.GrampsJson
import de.bgghome.gwand.api.YDna
import de.bgghome.gwand.daten.Dna
import de.bgghome.gwand.daten.Schreibhilfe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rechnung der Chromosomen-Ansicht (wie ChromosomeBrowser.js in Gramps Web), Lesen der Antworten, Bau der Beziehung. */
class DnaTest {

    private fun seg(chr: String, start: Long, stop: Long, seite: String = "U", cM: Double = 10.0) = DnaSegment(chr, start, stop, seite, cM)

    @Test
    fun chromosomenSchluessel() {
        assertEquals("7", Dna.schluessel("chr7"))
        assertEquals("7", Dna.schluessel(" Chr7 "))
        assertEquals("X", Dna.schluessel("23"))
        assertEquals("X", Dna.schluessel("x"))
        assertNull(Dna.schluessel("Y"))
        assertNull(Dna.schluessel("24"))
        assertNull(Dna.schluessel(null))
        assertEquals(23, Dna.LAENGEN.size)
        assertEquals(Dna.GROESSTE_LAENGE, Dna.LAENGEN.values.max())
    }

    @Test
    fun bahnenOhneUeberlappung() {
        // Zwei ueberlappende vaeterliche Segmente brauchen zwei Bahnen, das dritte passt wieder in die erste
        val b = Dna.bahnen(listOf(seg("1", 100, 200, "P"), seg("1", 150, 300, "P"), seg("1", 200, 250, "P"), seg("1", 120, 130, "M")))
        assertEquals(listOf(0, 1, 0, 0), b.bahn)
        assertEquals(mapOf("P" to 2, "U" to 0, "M" to 1), b.anzahl)
    }

    @Test
    fun bahnenLeerUndVertauschteEnden() {
        val leer = Dna.bahnen(emptyList())
        assertEquals(mapOf("P" to 1, "U" to 0, "M" to 1), leer.anzahl, "vaeterliche und muetterliche Spur gibt es immer")
        // Start und Ende vertauscht, Reihenfolge unsortiert; unbekannte und fremde Seiten ("F") in die mittlere Spur
        val b = Dna.bahnen(listOf(seg("2", 500, 400), seg("2", 100, 450, "F"), seg("2", 10, 20)))
        assertEquals(listOf(1, 0, 0), b.bahn)
        assertEquals(2, b.anzahl["U"])
    }

    @Test
    fun farbfolge() {
        assertEquals(0xFF2A78D6, Dna.farbe(0))
        assertNull(Dna.rand(7))
        assertEquals(0xFFAAC9EF, Dna.farbe(8))
        assertEquals(0xFF2A78D6, Dna.rand(8))
        assertEquals(Dna.farbe(0), Dna.farbe(16))
    }

    @Test
    fun tabelleNachChromosomUndAnfang() {
        val s = Dna.sortiert(listOf(seg("X", 5, 9), seg("10", 7, 9), seg("2", 300, 400), seg("chr2", 100, 200), seg("Y", 1, 2)))
        assertEquals(listOf("chr2", "2", "10", "X", "Y"), s.map { it.chromosome })
    }

    @Test
    fun trefferLesen() {
        val t = GrampsJson.decodeFromString(ListSerializer(DnaTreffer.serializer()),
            File(System.getProperty("gwand.testdaten"), "api/dna-matches.json").readText())
        assertTrue(t.isNotEmpty())
        t.forEach { m ->
            assertTrue(m.handle.isNotEmpty() && m.segments.isNotEmpty() && m.notizen.isNotEmpty() && m.verweisIndex >= 0)
            assertEquals(m.segments.sumOf { it.cM }, m.cM, 1e-9)
            assertEquals(m.segments.maxOf { it.cM }, m.groesstes, 1e-9)
            assertTrue(m.segments.all { it.side in setOf("P", "M", "U") && Dna.schluessel(it.chromosome) != null })
        }
        assertTrue(t.any { it.vorfahren.isNotEmpty() && it.relation.isNotBlank() }, "ein Treffer mit gemeinsamen Vorfahren")
    }

    @Test
    fun ydnaLesen() {
        assertNull(GrampsJson.decodeFromString(YDna.serializer(), "{}").haplogruppe)
        val y = GrampsJson.decodeFromString(YDna.serializer(),
            """{"clade_lineage":[{"name":"A0-T","age_info":null,"score":null},{"name":"R-M269","age_info":{"formed":6400},"score":1.0}],"tree_version":"13.07.00"}""")
        assertEquals("R-M269", y.haplogruppe)
        assertEquals("13.07.00", y.baumVersion)
    }

    @Test
    fun beziehungWieDieWebOberflaeche() {
        val person = buildJsonObject {
            put("handle", "p")
            put("person_ref_list", JsonArray(listOf(buildJsonObject { put("_class", "PersonRef"); put("ref", "b"); put("rel", "Pate") })))
        }
        val neu = Schreibhilfe.dnaBeziehung(person, "b", "n1", privat = true)
        val verweise = neu["person_ref_list"] as JsonArray
        assertEquals(2, verweise.size, "vorhandene Beziehungen bleiben")
        val v = verweise.last().jsonObject
        assertEquals(setOf("_class", "ref", "rel", "note_list", "private"), v.keys)
        assertEquals("DNA", v["rel"]!!.jsonPrimitive.content)
        assertEquals("n1", (v["note_list"] as JsonArray).single().jsonPrimitive.content)
        assertEquals("true", v["private"]!!.jsonPrimitive.content)
        val notiz = Schreibhilfe.dnaNotiz("n1", "1,2,3,4", privat = true)
        assertEquals("1,2,3,4", notiz["text"]!!.jsonObject["string"]!!.jsonPrimitive.content)
        assertEquals("true", notiz["private"]!!.jsonPrimitive.content)
        // Loeschen: nur die DNA-Beziehung zu dieser Person
        val ohne = Schreibhilfe.beziehungEntfernen(neu, "b", Schreibhilfe.DNA)
        assertEquals(listOf("Pate"), (ohne["person_ref_list"] as JsonArray).map { it.jsonObject["rel"]!!.jsonPrimitive.content })
    }
}
