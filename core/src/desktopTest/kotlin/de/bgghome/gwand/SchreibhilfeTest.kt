package de.bgghome.gwand

import de.bgghome.gwand.daten.Schreibhilfe
import de.bgghome.webtrees.nativ.data.DateForm
import de.bgghome.webtrees.nativ.data.DatePart
import de.bgghome.webtrees.nativ.data.DateQualifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchreibhilfeTest {

    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun datumHinUndZurueck() {
        val formen = listOf(
            DateForm(DateQualifier.EXACT, DatePart("14", 3, "1985")),
            DateForm(DateQualifier.EXACT, DatePart("", 3, "1985")),
            DateForm(DateQualifier.EXACT, DatePart("", 0, "1850")),
            DateForm(DateQualifier.ABOUT, DatePart("", 0, "1850")),
            DateForm(DateQualifier.BEFORE, DatePart("1", 5, "1900")),
            DateForm(DateQualifier.AFTER, DatePart("", 0, "1900")),
            DateForm(DateQualifier.BETWEEN, DatePart("", 0, "1850"), DatePart("", 0, "1860")),
        )
        for (f in formen) assertEquals(f, Schreibhilfe.eingabe(Schreibhilfe.datum(f)), "hin und zurueck: $f")
        val d = Schreibhilfe.datum(DateForm(DateQualifier.ABOUT, DatePart("3", 4, "1901")))!!
        assertEquals(listOf("3", "4", "1901", "false"), d["dateval"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("3", d["modifier"]!!.jsonPrimitive.content)
        assertNull(Schreibhilfe.datum(DateForm()))
        assertFailsWith<IllegalArgumentException> { Schreibhilfe.datum(DateForm(DateQualifier.EXACT, DatePart("3", 0, "1901"))) }
    }

    @Test
    fun nichtDarstellbaresDatumBleibt() {
        // geschaetzt (quality 1), julianisch, Zeitraum "von … bis" (modifier 5), reiner Text: nicht als Eingabe darstellbar
        assertNull(Schreibhilfe.eingabe(json("""{"dateval":[0,0,1850,false],"modifier":0,"quality":1,"calendar":0}""")))
        assertNull(Schreibhilfe.eingabe(json("""{"dateval":[1,1,1700,false],"modifier":0,"quality":0,"calendar":1}""")))
        assertNull(Schreibhilfe.eingabe(json("""{"dateval":[0,0,1850,false,0,0,1860,false],"modifier":5,"quality":0,"calendar":0}""")))
        assertNull(Schreibhilfe.eingabe(json("""{"dateval":[0,0,0,false],"modifier":6,"quality":0,"calendar":0,"text":"Ostern"}""")))
        val e = json("""{"handle":"e","type":"Birth","date":{"dateval":[0,0,1850,false],"modifier":0,"quality":1},"place":"p","description":"x","citation_list":["c"]}""")
        val neu = Schreibhilfe.ereignisAendern(e, "Baptism", null, datumAendern = false, ort = "q", beschreibung = "y")
        assertEquals(e["date"], neu["date"], "Datum bleibt unangetastet")
        assertEquals(e["citation_list"], neu["citation_list"], "Zitate bleiben")
        assertEquals("Baptism", neu["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun ereignisseUndIndizes() {
        var p = json("""{"handle":"p","event_ref_list":[{"ref":"a","role":"Primary"}],"birth_ref_index":-1,"death_ref_index":-1,"attribute_list":[{"type":"X","value":"1"}]}""")
        p = Schreibhilfe.ereignisAnPerson(p, "geb", "Birth")
        assertEquals(1, p["birth_ref_index"]!!.jsonPrimitive.content.toInt())
        p = Schreibhilfe.ereignisAnPerson(p, "tod", "Death")
        assertEquals(2, p["death_ref_index"]!!.jsonPrimitive.content.toInt())
        p = Schreibhilfe.ereignisVerschieben(p, "tod", -2)
        assertEquals(listOf("tod", "a", "geb"), (p["event_ref_list"] as JsonArray).map { it.jsonObject["ref"]!!.jsonPrimitive.content })
        assertEquals(2, p["birth_ref_index"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, p["death_ref_index"]!!.jsonPrimitive.content.toInt())
        p = Schreibhilfe.ereignisVonPerson(p, "tod")
        assertEquals(-1, p["death_ref_index"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, p["birth_ref_index"]!!.jsonPrimitive.content.toInt())
        assertTrue((p["attribute_list"] as JsonArray).isNotEmpty(), "fremde Felder bleiben")
    }

    @Test
    fun nameUndFamilie() {
        val p = json("""{"handle":"p","gender":2,"primary_name":{"first_name":"A","call":"","title":"Dr.","surname_list":[{"surname":"von","primary":false},{"surname":"Alt","primary":true}]}}""")
        val n = Schreibhilfe.personAendern(p, "Anna", "Neu", "Anni", 0)["primary_name"]!!.jsonObject
        assertEquals("Anna", n["first_name"]!!.jsonPrimitive.content)
        assertEquals("Dr.", n["title"]!!.jsonPrimitive.content)
        assertEquals(listOf("von", "Neu"), n["surname_list"]!!.jsonArray.map { it.jsonObject["surname"]!!.jsonPrimitive.content })
        var f = Schreibhilfe.familie("f", "v", null, listOf("k1"))
        f = Schreibhilfe.kindHinzu(f, "k2"); f = Schreibhilfe.kindHinzu(f, "k2")
        f = Schreibhilfe.kindVerschieben(f, "k2", -1)
        assertEquals(listOf("k2", "k1"), f["child_ref_list"]!!.jsonArray.map { it.jsonObject["ref"]!!.jsonPrimitive.content })
        assertTrue(Schreibhilfe.hatElternteil(f, vater = true) && !Schreibhilfe.hatElternteil(f, vater = false))
        f = Schreibhilfe.elternteil(f, vater = false, person = "m")
        assertTrue(Schreibhilfe.hatElternteil(f, vater = false))
    }

    @Test
    fun medienUndBeziehungen() {
        var p = json("""{"handle":"p","media_list":[{"ref":"m1","rect":[]}],"person_ref_list":[]}""")
        p = Schreibhilfe.medienverweis(p, "m2")
        p = Schreibhilfe.hauptbild(p, "m2")
        assertEquals(listOf("m2", "m1"), p["media_list"]!!.jsonArray.map { it.jsonObject["ref"]!!.jsonPrimitive.content })
        p = Schreibhilfe.beziehung(p, "pate", "Pate")
        p = Schreibhilfe.beziehung(p, "pate", "Trauzeuge")
        p = Schreibhilfe.beziehungEntfernen(p, "pate", "Pate")
        assertEquals(listOf("Trauzeuge"), p["person_ref_list"]!!.jsonArray.map { it.jsonObject["rel"]!!.jsonPrimitive.content })
        assertTrue(Schreibhilfe.neuesHandle() != Schreibhilfe.neuesHandle())
        assertEquals(JsonPrimitive("x"), JsonObject(mapOf("a" to JsonPrimitive("x")))["a"])
    }
}
