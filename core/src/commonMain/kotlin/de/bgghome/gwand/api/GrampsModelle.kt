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
)

@Serializable
data class Medienverweis(
    val ref: String,
    /** Bildausschnitt in Prozent: links, oben, rechts, unten. Null oder [0,0,100,100] = ganzes Bild. */
    val rect: List<Int>? = null,
    val private: Boolean = false,
)

@Serializable
data class Kindverweis(val ref: String, val frel: String = "", val mrel: String = "", val private: Boolean = false)

@Serializable
data class Ereignisverweis(val ref: String, val role: String = "", val private: Boolean = false)

@Serializable
data class Familie(
    val handle: String,
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
)

/** Eine Person, wie die API sie liefert; welche Felder gefuellt sind, haengt von `keys`, `profile` und `extend` ab. */
@Serializable
data class GrampsPerson(
    val handle: String,
    @SerialName("gramps_id") val grampsId: String = "",
    /** 0 weiblich, 1 maennlich, 2 unbekannt (Gramps). */
    val gender: Int = 2,
    val private: Boolean = false,
    @SerialName("media_list") val medien: List<Medienverweis> = emptyList(),
    @SerialName("parent_family_list") val elternfamilien: List<String> = emptyList(),
    @SerialName("family_list") val familien: List<String> = emptyList(),
    @SerialName("event_ref_list") val ereignisse: List<Ereignisverweis> = emptyList(),
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
