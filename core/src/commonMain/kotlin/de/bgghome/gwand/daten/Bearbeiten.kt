package de.bgghome.gwand.daten

import de.bgghome.gwand.api.GwClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Neue Person aus einem Dialog: Name, Geschlecht (0 weiblich, 1 maennlich, 2 unbekannt), wahlweise Geburt. */
data class NeuePerson(
    val vorname: String,
    val nachname: String,
    val geschlecht: Int,
    val geburt: JsonObject? = null,
    val geburtsort: String? = null,
)

enum class Verwandtschaftsart { Vater, Mutter, Partner, Kind }

/**
 * Die Schreibvorgaenge von gwAnd. Jeder Vorgang legt zuerst alle neuen Objekte in einem Rutsch an und aendert danach
 * die vorhandenen; schlaegt das Aendern fehl, werden die neuen Objekte wieder geloescht, damit nichts verwaist.
 * Aenderungen pruefen den Stand ([stand] = Feld `change` beim Oeffnen), siehe [GwClient.aendern].
 */
class Bearbeiten(private val client: GwClient) {

    private suspend fun <T> mitRuecknahme(neu: List<Pair<String, String>>, schritt: suspend () -> T): T = try {
        schritt()
    } catch (e: Exception) {
        neu.reversed().forEach { (art, h) -> runCatching { client.loeschen(art, h) } }
        throw e
    }

    // ── Ereignisse ───────────────────────────────────────────────────

    suspend fun ereignisAnlegen(person: String, stand: Long?, typ: String, datum: JsonObject?, ort: String?, beschreibung: String): String {
        val h = Schreibhilfe.neuesHandle()
        client.anlegen(listOf(Schreibhilfe.ereignis(h, typ, datum, ort, beschreibung)))
        mitRuecknahme(listOf("events" to h)) {
            client.aendern("people", person, stand) { Schreibhilfe.ereignisAnPerson(it, h, typ) }
        }
        return h
    }

    /** Familienereignis (Heirat, Scheidung …) an einer Familie. */
    suspend fun familienereignisAnlegen(familie: String, typ: String, datum: JsonObject?, ort: String?, beschreibung: String): String {
        val h = Schreibhilfe.neuesHandle()
        client.anlegen(listOf(Schreibhilfe.ereignis(h, typ, datum, ort, beschreibung)))
        mitRuecknahme(listOf("events" to h)) {
            client.aendern("families", familie) { f -> Schreibhilfe.ereignisAnPerson(f, h, typ, "Family") }
        }
        return h
    }

    suspend fun ereignisAendern(ereignis: String, stand: Long?, typ: String, datum: JsonObject?, datumAendern: Boolean, ort: String?, beschreibung: String) {
        client.aendern("events", ereignis, stand) { Schreibhilfe.ereignisAendern(it, typ, datum, datumAendern, ort, beschreibung) }
    }

    /** Verweis loesen; das Ereignis selbst nur loeschen, wenn niemand sonst darauf zeigt (geteilte Ereignisse bleiben). */
    suspend fun ereignisEntfernen(person: String, ereignis: String) {
        client.aendern("people", person) { Schreibhilfe.ereignisVonPerson(it, ereignis) }
        val verweise = runCatching { client.verweiseAuf("events", ereignis) }.getOrDefault(mapOf("?" to listOf("?")))
        if (verweise.values.all { it.isEmpty() }) client.loeschen("events", ereignis)
    }

    suspend fun ereignisVerschieben(person: String, ereignis: String, schritt: Int) {
        client.aendern("people", person) { Schreibhilfe.ereignisVerschieben(it, ereignis, schritt) }
    }

    /** Neuer Ort nur mit Namen; Ergebnis: Handle. */
    suspend fun ortAnlegen(name: String): String {
        val h = Schreibhilfe.neuesHandle()
        client.anlegen(listOf(Schreibhilfe.ort(h, name)))
        return h
    }

    // ── Person ───────────────────────────────────────────────────────

    suspend fun personAendern(person: String, stand: Long?, vorname: String, nachname: String, rufname: String, geschlecht: Int) {
        client.aendern("people", person, stand) { Schreibhilfe.personAendern(it, vorname, nachname, rufname, geschlecht) }
    }

    /** Neue Person ohne Verwandtschaft (Reiter Personen). Ergebnis: Handle. */
    suspend fun personAnlegen(p: NeuePerson): String {
        val (objekte, h) = personObjekte(p)
        client.anlegen(objekte)
        return h
    }

    suspend fun personLoeschen(person: String) = client.loeschen("people", person)

    private fun personObjekte(p: NeuePerson): Pair<List<JsonObject>, String> {
        val h = Schreibhilfe.neuesHandle()
        var person = Schreibhilfe.person(h, p.vorname, p.nachname, p.geschlecht)
        val objekte = mutableListOf<JsonObject>()
        if (p.geburt != null || p.geburtsort != null) {
            val e = Schreibhilfe.neuesHandle()
            objekte += Schreibhilfe.ereignis(e, "Birth", p.geburt, p.geburtsort, "")
            person = Schreibhilfe.ereignisAnPerson(person, e, "Birth")
        }
        return (objekte + person) to h
    }

    // ── Verwandte ────────────────────────────────────────────────────

    /**
     * Verwandten zu [zu] hinzufuegen - eine neue Person ([neu]) oder eine vorhandene ([vorhanden]).
     * Eltern kommen in die erste Elternfamilie (fehlt der Platz dort, in eine neue), ein Partner bekommt eine neue
     * Familie, ein Kind kommt in [familie] (sonst in die erste Partnerfamilie, sonst in eine neue).
     * @param geschlechtZu Geschlecht von [zu]: bestimmt, ob [zu] in einer neuen Familie Vater oder Mutter ist
     * @return Handle der hinzugefuegten Person
     */
    suspend fun verwandterHinzu(zu: String, geschlechtZu: Int, art: Verwandtschaftsart, neu: NeuePerson?, vorhanden: String?, familie: String? = null): String {
        require((neu == null) != (vorhanden == null))
        val person = client.roh("people", zu)
        val objekte = mutableListOf<JsonObject>()
        val andere = vorhanden ?: personObjekte(neu!!).let { (o, h) -> objekte += o; h }
        val neuAngelegt = if (neu != null) objekte.mapNotNull { o ->
            val h = (o["handle"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            (if ((o["_class"] as? JsonPrimitive)?.content == "Event") "events" else "people") to h
        } else emptyList()
        val elternfamilien = handles(person, "parent_family_list")
        val partnerfamilien = handles(person, "family_list")

        when (art) {
            Verwandtschaftsart.Vater, Verwandtschaftsart.Mutter -> {
                val vater = art == Verwandtschaftsart.Vater
                val vorhandeneFamilie = elternfamilien.firstOrNull()?.let { f -> client.roh("families", f) }
                if (vorhandeneFamilie != null && !Schreibhilfe.hatElternteil(vorhandeneFamilie, vater)) {
                    client.anlegen(objekte)
                    mitRuecknahme(neuAngelegt) {
                        client.aendern("families", elternfamilien.first()) { Schreibhilfe.elternteil(it, vater, andere) }
                    }
                } else {
                    val f = Schreibhilfe.neuesHandle()
                    client.anlegen(objekte + Schreibhilfe.familie(f, if (vater) andere else null, if (vater) null else andere, listOf(zu)))
                }
            }
            Verwandtschaftsart.Partner -> {
                val f = Schreibhilfe.neuesHandle()
                val zuIstMutter = geschlechtZu == 0
                client.anlegen(objekte + Schreibhilfe.familie(f, if (zuIstMutter) andere else zu, if (zuIstMutter) zu else andere))
            }
            Verwandtschaftsart.Kind -> {
                val ziel = familie ?: partnerfamilien.firstOrNull()
                if (ziel != null) {
                    client.anlegen(objekte)
                    mitRuecknahme(neuAngelegt) { client.aendern("families", ziel) { Schreibhilfe.kindHinzu(it, andere) } }
                } else {
                    val f = Schreibhilfe.neuesHandle()
                    val zuIstMutter = geschlechtZu == 0
                    client.anlegen(objekte + Schreibhilfe.familie(f, if (zuIstMutter) null else zu, if (zuIstMutter) zu else null, listOf(andere)))
                }
            }
        }
        return andere
    }

    /** Kind aus einer Familie loesen (die Person bleibt). */
    suspend fun kindLoesen(familie: String, kind: String) = client.aendern("families", familie) { Schreibhilfe.kindEntfernen(it, kind) }

    /** Elternteil aus einer Familie loesen (die Person bleibt). */
    suspend fun elternteilLoesen(familie: String, person: String) = client.aendern("families", familie) { f ->
        val vater = (f["father_handle"] as? JsonPrimitive)?.content == person
        Schreibhilfe.elternteil(f, vater, null)
    }

    suspend fun kindVerschieben(familie: String, kind: String, schritt: Int) =
        client.aendern("families", familie) { Schreibhilfe.kindVerschieben(it, kind, schritt) }

    // ── Fotos ────────────────────────────────────────────────────────

    /** Bild hochladen, beschriften und an die Person haengen; mit [hauptbild] als erstes Bild. Ergebnis: Medien-Handle. */
    suspend fun fotoHinzu(person: String, daten: ByteArray, mime: String, beschreibung: String, hauptbild: Boolean): String {
        val m = client.hochladen(daten, mime)
        mitRuecknahme(listOf("media" to m)) {
            if (beschreibung.isNotBlank()) client.aendern("media", m) { Schreibhilfe.beschreibung(it, beschreibung) }
            client.aendern("people", person) { Schreibhilfe.medienverweis(it, m, vorn = hauptbild) }
        }
        return m
    }

    suspend fun hauptbild(person: String, medium: String) = client.aendern("people", person) { Schreibhilfe.hauptbild(it, medium) }

    /** Gesicht bzw. Ausschnitt eines Bildes einer Person zuordnen (Prozent: links, oben, rechts, unten). */
    suspend fun ausschnitt(person: String, medium: String, rect: List<Int>) =
        client.aendern("people", person) { Schreibhilfe.ausschnittSetzen(it, medium, rect) }

    suspend fun ausschnittEntfernen(person: String, medium: String) =
        client.aendern("people", person) { Schreibhilfe.ausschnittEntfernen(it, medium) }

    // ── Quellen und Beziehungen ──────────────────────────────────────

    /** Zitat anlegen und an ein Objekt haengen ([art] "people" oder "events"). */
    suspend fun zitatHinzu(art: String, ziel: String, quelle: String, seite: String, vertrauen: Int): String {
        val z = Schreibhilfe.neuesHandle()
        client.anlegen(listOf(Schreibhilfe.zitat(z, quelle, seite, vertrauen)))
        mitRuecknahme(listOf("citations" to z)) { client.aendern(art, ziel) { Schreibhilfe.zitatAn(it, z) } }
        return z
    }

    suspend fun beziehungHinzu(person: String, andere: String, art: String) =
        client.aendern("people", person) { Schreibhilfe.beziehung(it, andere, art) }

    suspend fun beziehungEntfernen(person: String, andere: String, art: String) =
        client.aendern("people", person) { Schreibhilfe.beziehungEntfernen(it, andere, art) }

    private fun handles(o: JsonObject, schluessel: String): List<String> =
        (o[schluessel] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
}
