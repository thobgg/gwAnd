package de.bgghome.gwand.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Antworten der Gramps Web API, nur so weit, wie gwAnd sie braucht. Unbekannte Felder werden ignoriert, damit neue
 * Server-Fassungen die App nicht brechen. Feldnamen wie im JSON der API (snake_case), siehe testdaten/api/.
 */
val GrampsJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class Token(
    @SerialName("access_token") val access: String,
    @SerialName("refresh_token") val refresh: String? = null,
)

@Serializable
data class Versionsangabe(val version: String = "", val schema: String = "")

@Serializable
data class Datenbankangabe(val id: String = "", val name: String = "", val type: String = "")

@Serializable
data class Serverangabe(
    @SerialName("multi_tree") val mehrereBaeume: Boolean = false,
    @SerialName("task_queue") val aufgabenWarteschlange: Boolean = false,
    val chat: Boolean = false,
    val ocr: Boolean = false,
)

@Serializable
data class Metadaten(
    @SerialName("gramps_webapi") val api: Versionsangabe = Versionsangabe(),
    val gramps: Versionsangabe = Versionsangabe(),
    val database: Datenbankangabe = Datenbankangabe(),
    @SerialName("default_person") val startperson: String? = null,
    val server: Serverangabe = Serverangabe(),
    @SerialName("object_counts") val anzahlen: Map<String, Int> = emptyMap(),
)

/** Das eigene Konto (`/users/-/`). Rollen: 0 Gast, 1 Mitglied, 2 Beitragende, 3 Editor, 4 Besitzer, 5 Admin. */
@Serializable
data class Benutzer(
    val name: String = "",
    @SerialName("full_name") val vollerName: String = "",
    val role: Int = 0,
    val tree: String? = null,
) {
    val darfAnlegen: Boolean get() = role >= ROLLE_BEITRAG
    val darfBearbeiten: Boolean get() = role >= ROLLE_EDITOR

    companion object {
        const val ROLLE_GAST = 0
        const val ROLLE_MITGLIED = 1
        const val ROLLE_BEITRAG = 2
        const val ROLLE_EDITOR = 3
        const val ROLLE_BESITZER = 4
    }
}

@Serializable
data class Baum(val id: String = "", val name: String = "")

/** Ein Ereignis in der aufbereiteten Form (`profile`): Datum als ISO-Text, Ort als Anzeigetext, Typ uebersetzt. */
@Serializable
data class ProfilEreignis(
    val date: String = "",
    val place: String = "",
    @SerialName("place_name") val ortsname: String = "",
    val type: String = "",
    val summary: String = "",
    val age: String = "",
    /** Rolle der Person am Ereignis, uebersetzt ("Primär", "Pate" …); nur in `profile.events`. */
    val role: String = "",
    /** Zahl der Zitate und hoechster Vertrauensgrad (0 sehr gering … 4 sehr hoch). */
    val citations: Int = 0,
    val confidence: Int = 0,
) {
    val leer: Boolean get() = date.isBlank() && place.isBlank() && type.isBlank()
}

/** Die aufbereitete Person (`profile=self` bzw. `profile=all`). */
@Serializable
data class PersonProfil(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    @SerialName("name_given") val vorname: String = "",
    @SerialName("name_surname") val nachname: String = "",
    @SerialName("name_display") val anzeigename: String = "",
    @SerialName("name_suffix") val namenszusatz: String = "",
    val sex: String = "U",
    val birth: ProfilEreignis = ProfilEreignis(),
    val death: ProfilEreignis = ProfilEreignis(),
    /** Nur mit `profile=all`: alle Ereignisse, Partnerfamilien, Elternfamilie und weitere Elternfamilien. */
    val events: List<ProfilEreignis> = emptyList(),
    val families: List<FamilienProfil> = emptyList(),
    @SerialName("primary_parent_family") val elternfamilie: FamilienProfil? = null,
    @SerialName("other_parent_families") val weitereElternfamilien: List<FamilienProfil> = emptyList(),
) {
    /** Leere Objekte (`{}`) stehen fuer "nicht vorhanden", etwa ein fehlender Vater. */
    val vorhanden: Boolean get() = handle.isNotEmpty()
}

/** Eine Familie in der aufbereiteten Form: Eltern, Kinder, Heirat, Art der Beziehung (uebersetzt). */
@Serializable
data class FamilienProfil(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    val father: PersonProfil? = null,
    val mother: PersonProfil? = null,
    val children: List<PersonProfil> = emptyList(),
    val marriage: ProfilEreignis = ProfilEreignis(),
    val divorce: ProfilEreignis = ProfilEreignis(),
    val relationship: String = "",
    val events: List<ProfilEreignis> = emptyList(),
)

/** Eine Zeile der Zeitleiste (`/people/<h>/timeline`): eigene Ereignisse und solche der Angehoerigen. */
@Serializable
data class ZeitleistenEintrag(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    val date: String = "",
    val age: String = "",
    val label: String = "",
    val type: String = "",
    val role: String = "",
    val description: String = "",
    val person: ZeitleistenPerson = ZeitleistenPerson(),
    val place: OrtKurz? = null,
)

@Serializable
data class ZeitleistenPerson(
    /** "self" fuer die Person selbst, sonst die Verwandtschaft ("Bruder", "Tochter" …). */
    val relationship: String = "",
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    @SerialName("name_given") val vorname: String = "",
    @SerialName("name_surname") val nachname: String = "",
    @SerialName("name_display") val anzeigename: String = "",
    val sex: String = "U",
) {
    val selbst: Boolean get() = relationship == "self" || relationship.isEmpty()
}

/** Ort in Kurzform, wie Zeitleiste und Profile ihn mitliefern; Koordinaten als Zahlen. */
@Serializable
data class OrtKurz(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    val name: String = "",
    @SerialName("display_name") val anzeigename: String = "",
    val lat: Double? = null,
    val long: Double? = null,
    val type: String = "",
)

@Serializable
data class Personenverweis(val ref: String = "", val rel: String = "", val private: Boolean = false)

@Serializable
data class Text(val string: String = "")

@Serializable
data class Notiz(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    val text: Text = Text(),
    val type: String = "",
    val private: Boolean = false,
)

@Serializable
data class Medium(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    val desc: String = "",
    val mime: String = "",
    val path: String = "",
    val private: Boolean = false,
    val profile: MedienProfil? = null,
) {
    val istBild: Boolean get() = mime.startsWith("image/")
}

@Serializable
data class MedienProfil(val date: String = "", val references: Verweise = Verweise())

/** Wer auf ein Objekt verweist, in aufbereiteter Form (`profile.references`). */
@Serializable
data class Verweise(
    val person: List<PersonProfil> = emptyList(),
    val event: List<ProfilEreignis> = emptyList(),
    val family: List<FamilienProfil> = emptyList(),
    val citation: List<ZitatProfil> = emptyList(),
)

@Serializable
data class QuelleKurz(
    val title: String = "",
    val author: String = "",
    val pubinfo: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
)

@Serializable
data class ZitatProfil(
    @SerialName("gramps_id") val grampsId: String = "",
    val page: String = "",
    val date: String = "",
    val source: QuelleKurz = QuelleKurz(),
)

@Serializable
data class Zitat(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    val page: String = "",
    val confidence: Int = 0,
    @SerialName("source_handle") val quelle: String = "",
    val private: Boolean = false,
    val profile: ZitatProfil? = null,
)

@Serializable
data class Archivverweis(
    val ref: String = "",
    @SerialName("call_number") val signatur: String = "",
    @SerialName("media_type") val medienart: String = "",
)

@Serializable
data class QuelleErweitert(
    val repositories: List<Archiv> = emptyList(),
    val notes: List<Notiz> = emptyList(),
    val media: List<Medium> = emptyList(),
)

@Serializable
data class Quelle(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    val title: String = "",
    val author: String = "",
    val pubinfo: String = "",
    val abbrev: String = "",
    val private: Boolean = false,
    @SerialName("reporef_list") val archivverweise: List<Archivverweis> = emptyList(),
    val profile: QuelleProfil? = null,
    val extended: QuelleErweitert? = null,
)

@Serializable
data class QuelleProfil(val references: Verweise = Verweise())

@Serializable
data class Adresse(
    val street: String = "",
    val locality: String = "",
    val city: String = "",
    val postal: String = "",
    val state: String = "",
    val country: String = "",
    val phone: String = "",
) {
    val text: String get() = listOf(street, locality, listOf(postal, city).filter { it.isNotBlank() }.joinToString(" "), state, country)
        .filter { it.isNotBlank() }.joinToString(", ")
}

@Serializable
data class Netzadresse(val path: String = "", val desc: String = "")

@Serializable
data class Archiv(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    val name: String = "",
    val type: String = "",
    @SerialName("address_list") val adressen: List<Adresse> = emptyList(),
    val urls: List<Netzadresse> = emptyList(),
    val private: Boolean = false,
    val backlinks: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class Ortsname(val value: String = "")

@Serializable
data class OrtProfil(
    val name: String = "",
    val lat: Double? = null,
    val long: Double? = null,
    @SerialName("parent_places") val uebergeordnet: List<OrtKurz> = emptyList(),
    val references: Verweise = Verweise(),
)

@Serializable
data class Ort(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    val title: String = "",
    val name: Ortsname = Ortsname(),
    @SerialName("place_type") val art: String = "",
    val private: Boolean = false,
    val profile: OrtProfil? = null,
) {
    val anzeigename: String get() = profile?.name?.ifBlank { null } ?: title.ifBlank { name.value }
}

@Serializable
data class Teilnehmer(val person: PersonProfil = PersonProfil(), val role: String = "")

@Serializable
data class Familienteilnehmer(val family: FamilienProfil = FamilienProfil(), val role: String = "")

@Serializable
data class Teilnehmende(val people: List<Teilnehmer> = emptyList(), val families: List<Familienteilnehmer> = emptyList())

@Serializable
data class EreignisProfil(
    val date: String = "",
    val type: String = "",
    val place: String = "",
    val participants: Teilnehmende = Teilnehmende(),
)

@Serializable
data class GrampsDatum(
    val dateval: List<kotlinx.serialization.json.JsonElement> = emptyList(),
    val year: Int = 0,
    val modifier: Int = 0,
    val quality: Int = 0,
) {
    val tag: Int get() = (dateval.getOrNull(0) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
    val monat: Int get() = (dateval.getOrNull(1) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
}

/** Ein Ereignis mit Teilnehmern (fuer Jahrestage). `type` ist der englische Grundname ("Birth"). */
@Serializable
data class Ereignis(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    val type: String = "",
    val date: GrampsDatum = GrampsDatum(),
    val private: Boolean = false,
    val profile: EreignisProfil? = null,
)

/** Lesezeichen des eigenen Kontos, je Objektart die Handles. */
@Serializable
data class Lesezeichen(val people: List<String> = emptyList(), val families: List<String> = emptyList())

@Serializable
data class Medienverweis(
    val ref: String = "",
    /** Bildausschnitt in Prozent: links, oben, rechts, unten. Null oder [0,0,100,100] = ganzes Bild. */
    val rect: List<Int>? = null,
    val private: Boolean = false,
)

@Serializable
data class Kindverweis(val ref: String = "", val frel: String = "", val mrel: String = "", val private: Boolean = false)

@Serializable
data class Ereignisverweis(val ref: String = "", val role: String = "", val private: Boolean = false)

@Serializable
data class Familie(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    @SerialName("father_handle") val vater: String? = null,
    @SerialName("mother_handle") val mutter: String? = null,
    @SerialName("child_ref_list") val kinder: List<Kindverweis> = emptyList(),
    @SerialName("event_ref_list") val ereignisse: List<Ereignisverweis> = emptyList(),
    val private: Boolean = false,
) {
    /** Der andere Elternteil zu [handle]; null, wenn [handle] kein Elternteil ist oder der Partner fehlt. */
    fun partnerVon(handle: String): String? = when (handle) {
        vater -> mutter
        mutter -> vater
        else -> null
    }
}

@Serializable
data class PersonErweitert(
    @SerialName("primary_parent_family") val elternfamilie: Familie? = null,
    val families: List<Familie> = emptyList(),
    val media: List<Medium> = emptyList(),
    val notes: List<Notiz> = emptyList(),
    val citations: List<Zitat> = emptyList(),
    /** Personen aus `person_ref_list` (Paten, Zeugen, Freunde …). */
    val people: List<GrampsPerson> = emptyList(),
)

/** Eine Person, wie die API sie liefert; welche Felder gefuellt sind, haengt von `keys`, `profile` und `extend` ab. */
@Serializable
data class GrampsPerson(
    val handle: String = "",
    @SerialName("gramps_id") val grampsId: String = "",
    /** 0 weiblich, 1 maennlich, 2 unbekannt (Gramps). */
    val gender: Int = 2,
    val private: Boolean = false,
    @SerialName("media_list") val medien: List<Medienverweis> = emptyList(),
    @SerialName("parent_family_list") val elternfamilien: List<String> = emptyList(),
    @SerialName("family_list") val familien: List<String> = emptyList(),
    @SerialName("event_ref_list") val ereignisse: List<Ereignisverweis> = emptyList(),
    @SerialName("person_ref_list") val personenverweise: List<Personenverweis> = emptyList(),
    val change: Long = 0,
    val profile: PersonProfil? = null,
    val extended: PersonErweitert? = null,
)

@Serializable
data class Verwandtschaft(
    @SerialName("relationship_string") val text: String = "",
    @SerialName("distance_common_origin") val abstandA: Int = -1,
    @SerialName("distance_common_other") val abstandB: Int = -1,
)

/** Eine Seite einer Liste samt Gesamtzahl aus dem Kopf `X-Total-Count`. */
data class Seite<T>(val eintraege: List<T>, val seite: Int, val groesse: Int, val gesamt: Int) {
    val naechste: Int? get() = if (seite * groesse < gesamt) seite + 1 else null
}

@Serializable
data class Familienzeile(val handle: String = "", val profile: FamilienProfil? = null)

@Serializable
data class Ereigniszitate(val handle: String = "", @SerialName("citation_list") val zitate: List<String> = emptyList())

/** Stand einer Hintergrundaufgabe (`/tasks/<id>`); das Ergebnis kommt als JSON-Text. */
@Serializable
data class Aufgabe(val state: String? = null, val status: String? = null, val info: String = "", val result: kotlinx.serialization.json.JsonElement? = null) {
    val fertig: Boolean get() = state == "SUCCESS" || status == "succeeded"
    val fehlgeschlagen: Boolean get() = state == "FAILURE" || status == "failed"
    /** Das Ergebnis, wie es kommt (JSON als Text). */
    val ergebnisRoh: String? get() = (result as? kotlinx.serialization.json.JsonPrimitive)?.content

    /** Das Ergebnis ist ein JSON-kodierter Text ("\"Zeile 1\nZeile 2\""); einmal auspacken. */
    val ergebnis: String? get() {
        val r = (result as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null
        return runCatching { GrampsJson.decodeFromString(kotlinx.serialization.serializer<String>(), r) }.getOrDefault(r)
    }
}

@Serializable
data class Nutzer(val name: String = "", @SerialName("full_name") val vollerName: String = "")

@Serializable
data class Verbindung(val id: Int = 0, val timestamp: Double = 0.0, val user: Nutzer = Nutzer())

/** Eine Aenderung an einem Objekt: 0 angelegt, 1 geaendert, 2 geloescht. */
@Serializable
data class Aenderung(
    @SerialName("obj_class") val klasse: String = "",
    @SerialName("trans_type") val art: Int = 1,
    @SerialName("obj_handle") val handle: String = "",
    val timestamp: Double = 0.0,
    val connection: Verbindung = Verbindung(),
    @SerialName("transaction_id") val transaktion: Int = 0,
)

/** Ein Vorgang im Aenderungsverlauf des Baums. */
@Serializable
data class Vorgang(
    val id: Int = 0,
    val description: String = "",
    val timestamp: Double = 0.0,
    val undo: Boolean = false,
    val connection: Verbindung = Verbindung(),
    val changes: List<Aenderung> = emptyList(),
)

@Serializable
data class RueckgaengigPruefung(
    @SerialName("can_undo_without_force") val ohneKonflikt: Boolean = false,
    @SerialName("conflicts_count") val konflikte: Int = 0,
)

/** Ein Verwandtschaftsweg zwischen zwei Personen: Bezeichnung und gemeinsame Vorfahren (Handles). */
@Serializable
data class VerwandtschaftWeg(
    @SerialName("relationship_string") val text: String = "",
    @SerialName("common_ancestors") val gemeinsameVorfahren: List<String> = emptyList(),
)

@Serializable
data class DnaSegment(val chromosome: String = "", val start: Long = 0, val stop: Long = 0, val cM: Double = 0.0, @SerialName("SNPs") val snps: Int = 0)

/** Ein DNA-Treffer einer Person: wer (Handle), Verwandtschaft, gemeinsame Segmente. */
@Serializable
data class DnaTreffer(val handle: String = "", val relation: String = "", val segments: List<DnaSegment> = emptyList()) {
    val cM: Double get() = segments.sumOf { it.cM }
}

/** Ein Bericht des Servers: ID, Name, Kategorie (0 Text, 1 Grafik, 5 Graph). */
@Serializable
data class Bericht(val id: String = "", val name: String = "", val category: Int = 0, val description: String = "")

/** Eine fertige Datei vom Server (Export, Bericht). */
class Datei(val name: String, val mime: String, val daten: ByteArray)
