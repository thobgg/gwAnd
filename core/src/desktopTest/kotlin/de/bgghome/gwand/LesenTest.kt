package de.bgghome.gwand

import de.bgghome.gwand.api.Archiv
import de.bgghome.gwand.api.Ereignis
import de.bgghome.gwand.api.GrampsJson
import de.bgghome.gwand.api.GrampsPerson
import de.bgghome.gwand.api.Medium
import de.bgghome.gwand.api.Ort
import de.bgghome.gwand.api.Quelle
import de.bgghome.gwand.api.ZeitleistenEintrag
import de.bgghome.gwand.api.Zitat
import de.bgghome.gwand.daten.Anzeige
import de.bgghome.gwand.daten.BaumBau
import de.bgghome.gwand.daten.Geschwister
import de.bgghome.gwand.daten.Jahrestage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Alle Lese-Antworten aus testdaten/api/ lassen sich in die Modelle einlesen; Jahrestage und Geschwister stimmen. */
class LesenTest {

    private val ordner = File(System.getProperty("gwand.testdaten"), "api")
    private fun <T> liste(name: String, s: KSerializer<T>): List<T> = GrampsJson.decodeFromString(ListSerializer(s), File(ordner, "$name.json").readText())

    @Test
    fun profilMitFamilienMedienUndNotizen() {
        val p = liste("profil-I0021", GrampsPerson.serializer()).single()
        val profil = p.profile!!
        assertEquals("Heinrich", profil.vorname)
        assertTrue(profil.events.isNotEmpty())
        assertTrue(profil.elternfamilie!!.children.any { it.handle == p.handle }, "die Person steht unter den Kindern ihrer Eltern")
        assertTrue(profil.families.isNotEmpty())
        assertTrue(p.medien.isNotEmpty() && p.extended!!.media.isNotEmpty())
        assertTrue(p.extended!!.notes.all { it.text.string.isNotEmpty() })
    }

    @Test
    fun zeitleisteMitAngehoerigenUndKoordinaten() {
        val t = liste("timeline-I0021", ZeitleistenEintrag.serializer())
        assertTrue(t.any { it.person.selbst } && t.any { !it.person.selbst })
        assertTrue(t.any { it.place?.lat != null && it.place.long != null })
        assertTrue(t.filter { !it.person.selbst }.all { it.person.handle.isNotEmpty() })
    }

    @Test
    fun nachschlagenUndMedien() {
        assertTrue(liste("quellen", Quelle.serializer()).first().archivverweise.isNotEmpty())
        assertTrue(liste("archive", Archiv.serializer()).first().backlinks["source"].orEmpty().isNotEmpty())
        assertTrue(liste("zitate", Zitat.serializer()).first().quelle.isNotEmpty())
        val ort = liste("orte", Ort.serializer()).first()
        assertTrue(ort.profile!!.lat != null && ort.anzeigename.isNotBlank())
        assertTrue(liste("medien-voll", Medium.serializer()).all { it.istBild })
    }

    @Test
    fun jahrestageAm11November() {
        val ereignisse = liste("jahrestage", Ereignis.serializer())
        val tage = Jahrestage.aus(ereignisse, LocalDate.of(2026, 11, 11), 3, Anzeige()::ausProfil)
        assertTrue(tage.isNotEmpty())
        assertTrue(tage.all { it.typ in Jahrestage.TYPEN && it.monat == 11 && it.tag in 11..13 })
        assertEquals(tage.sortedBy { it.tag }.map { it.tag }, tage.map { it.tag }, "nach Tag sortiert")
        val margarete = tage.first { j -> j.personen.any { it.name.contains("Margarete") } }
        assertEquals(96, margarete.jahre)
        tage.filter { it.typ == "Marriage" }.forEach { assertEquals(2, it.personen.size) }
    }

    @Test
    fun geschwisterDerAhnen() {
        val vorfahren = liste("baum-vorfahren-I0008", GrampsPerson.serializer())
        val wurzel = liste("baum-wurzel-I0008", GrampsPerson.serializer()).single()
        val tafel = BaumBau(Anzeige()).ahnentafel(wurzel.handle, vorfahren)
        val handles = Geschwister.geschwisterHandles(tafel, vorfahren)
        assertTrue(handles.keys.all { k -> tafel.ancestors.any { it.person.xref == k && BaumBau.generation(it.n) < Geschwister.REIHEN } })
        assertTrue(handles.isNotEmpty(), "Falkenrath: mindestens ein Ahn von I0008 hat Geschwister")
        handles.forEach { (ahn, liste) -> assertTrue(ahn !in liste, "ein Ahn ist nicht sein eigenes Geschwister") }
    }

    @Test
    fun leereObjekteStattFehlender() {
        // Ohne Eltern liefert der Server fuer die Elternfamilie ein leeres Objekt, ohne Vater ein leeres Vater-Objekt.
        val p = GrampsJson.decodeFromString(GrampsPerson.serializer(),
            """{"handle":"x","extended":{"primary_parent_family":{}},"profile":{"primary_parent_family":{"father":{},"mother":{}}}}""")
        assertEquals("", p.extended!!.elternfamilie!!.handle)
        assertTrue(!p.profile!!.elternfamilie!!.father!!.vorhanden)
        val tafel = BaumBau(Anzeige()).ahnentafel("x", listOf(p))
        assertTrue(!tafel.ancestors.single().hasParents)
    }
}
