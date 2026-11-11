package de.bgghome.gwand

import de.bgghome.gwand.api.GrampsJson
import de.bgghome.gwand.api.GrampsPerson
import de.bgghome.gwand.api.Metadaten
import de.bgghome.gwand.api.ProfilEreignis
import de.bgghome.gwand.daten.Anzeige
import de.bgghome.gwand.daten.BaumBau
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Baum aus den aufgezeichneten Antworten des Testservers (Falkenrath 1.8, Mittelperson I0008 Bernd Falkenrath). */
class BaumBauTest {

    private val ordner = File(System.getProperty("gwand.testdaten"), "api")
    private fun liste(name: String): List<GrampsPerson> =
        GrampsJson.decodeFromString(ListSerializer(GrampsPerson.serializer()), File(ordner, "$name.json").readText())

    private val anzeige = Anzeige(bild = { p -> p.medien.firstOrNull()?.let { "bild:${it.ref}" } })
    private val bau = BaumBau(anzeige)

    private val wurzel = liste("baum-wurzel-I0008").single()
    private val vorfahren = liste("baum-vorfahren-I0008")
    private val nachkommen = liste("baum-nachkommen-I0008")
    private val partner = liste("baum-partner-I0008")

    @Test
    fun ahnentafelHatKekuleNummernUndVierGenerationen() {
        val tafel = bau.ahnentafel(wurzel.handle, vorfahren)
        assertEquals(31, tafel.ancestors.size)
        assertEquals(5, tafel.generations)
        assertEquals((1..31).toList(), tafel.ancestors.map { it.n })
        val vater = tafel.ancestors.first { it.n == 2 }.person
        val mutter = tafel.ancestors.first { it.n == 3 }.person
        assertEquals("M", vater.sex)
        assertEquals("F", mutter.sex)
        assertEquals("Bernd Falkenrath", tafel.ancestors.first { it.n == 1 }.person.name)
    }

    @Test
    fun nachkommenMitPartnernUndKindern() {
        assertEquals(setOf("I0002", "I0006", "I0009"), partner.map { it.grampsId }.toSet())
        assertEquals(partner.map { it.handle }.toSet(), BaumBau.fehlendePartner(nachkommen + wurzel))
        val baum = bau.nachkommen(wurzel, nachkommen, partner)
        assertEquals(wurzel.handle, baum.person.xref)
        val alle = mutableListOf<DescendantNode>()
        fun sammeln(k: DescendantNode) { alle += k; k.families.forEach { f -> f.children.forEach(::sammeln) } }
        sammeln(baum)
        assertEquals(nachkommen.size + 1, alle.size, "jeder Nachkomme genau einmal")
        assertTrue(baum.families.isNotEmpty() && baum.families.all { it.spouse != null }, "Partner der Mittelperson geladen")
    }

    @Test
    fun layoutLaesstSichBauen() {
        val layout = TreeLayout.build(bau.ahnentafel(wurzel.handle, vorfahren), bau.nachkommen(wurzel, nachkommen, partner), canEdit = false)
        assertEquals(wurzel.handle, layout.focus.person.xref)
        assertTrue(layout.boxes.size >= 31 + nachkommen.size)
    }

    @Test
    fun lebensdatenUndDatum() {
        assertEquals("1955–2010", Anzeige.lebensdaten(ProfilEreignis(date = "1955-11-03"), ProfilEreignis(date = "2010-01-01")))
        assertEquals("1955–", Anzeige.lebensdaten(ProfilEreignis(date = "1955-11-03"), ProfilEreignis()))
        assertEquals("–1804", Anzeige.lebensdaten(ProfilEreignis(), ProfilEreignis(date = "abt 1804")))
        assertEquals("", Anzeige.lebensdaten(ProfilEreignis(), ProfilEreignis()))
        assertEquals("03.11.1955", Anzeige.anzeigeDatum("1955-11-03"))
        assertEquals("11.1955", Anzeige.anzeigeDatum("1955-11-00"))
        assertEquals("1955", Anzeige.anzeigeDatum("1955-00-00"))
        assertEquals("um 03.04.1901", Anzeige.anzeigeDatum("um 1901-04-03"))
        assertEquals("zwischen 1850 und 06.1860", Anzeige.anzeigeDatum("zwischen 1850-00-00 und 1860-06-00"))
        assertEquals(0, BaumBau.generation(1)); assertEquals(1, BaumBau.generation(3)); assertEquals(4, BaumBau.generation(31))
    }

    @Test
    fun metadatenLesen() {
        val m = GrampsJson.decodeFromString(Metadaten.serializer(), File(ordner, "metadata.json").readText())
        assertTrue(m.api.version.startsWith("3."))
        assertEquals("Falkenrath", m.database.name)
        assertEquals(493, m.anzahlen["people"])
    }
}
