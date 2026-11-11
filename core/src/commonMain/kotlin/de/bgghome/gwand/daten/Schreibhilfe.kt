package de.bgghome.gwand.daten

import de.bgghome.webtrees.nativ.data.DateForm
import de.bgghome.webtrees.nativ.data.DatePart
import de.bgghome.webtrees.nativ.data.DateQualifier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom

/**
 * Bausteine fuer Gramps-Objekte in der vereinfachten JSON-Form der Gramps Web API: Typen als englische Grundnamen
 * ("Birth", "Primary"), `_class` nur, wo der Server sonst nicht weiss, was gemeint ist. Alle Aenderungen arbeiten auf
 * dem Objekt, wie der Server es geliefert hat, und fassen nur die genannten Felder an. Reine Rechnung, siehe
 * SchreibhilfeTest.
 */
object Schreibhilfe {

    // ── Handles ──────────────────────────────────────────────────────

    private val zufall = SecureRandom()

    /** Neues Handle nach Art von Gramps (Zeit in Zehntelmillisekunden plus Zufall, hexadezimal). */
    fun neuesHandle(): String =
        "%x%08x".format(System.currentTimeMillis() * 10, zufall.nextInt().toLong() and 0xffffffffL)

    // ── Datum ────────────────────────────────────────────────────────

    /** Gramps-Modifikatoren (gramps.gen.lib.Date). */
    const val MOD_KEINER = 0
    const val MOD_VOR = 1
    const val MOD_NACH = 2
    const val MOD_ETWA = 3
    const val MOD_ZWISCHEN = 4
    const val MOD_TEXT = 6

    /** Datumseingabe (Genauigkeit plus Tag/Monat/Jahr) als Gramps-Datum; null = kein Datum, Fehler = unvollstaendig. */
    fun datum(form: DateForm): JsonObject? {
        if (form.isEmpty) return null
        val erstes = teil(form.first) ?: throw IllegalArgumentException("Datum unvollständig")
        val modifikator = when (form.qualifier) {
            DateQualifier.EXACT -> MOD_KEINER
            DateQualifier.ABOUT -> MOD_ETWA
            DateQualifier.BEFORE -> MOD_VOR
            DateQualifier.AFTER -> MOD_NACH
            DateQualifier.BETWEEN -> MOD_ZWISCHEN
        }
        val werte = if (form.qualifier == DateQualifier.BETWEEN) {
            erstes + (teil(form.second) ?: throw IllegalArgumentException("Datum unvollständig"))
        } else erstes
        return buildJsonObject {
            put("_class", "Date")
            put("calendar", 0)
            put("modifier", modifikator)
            put("quality", 0)
            put("newyear", 0)
            put("text", "")
            put("sortval", 0)
            put("dateval", JsonArray(werte))
        }
    }

    /** Tag, Monat, Jahr, Doppeljahr-Kennzeichen; Tag und Monat 0 = unbekannt. */
    private fun teil(p: DatePart): List<JsonElement>? {
        val jahr = p.year.trim().toIntOrNull()?.takeIf { it in 1..9999 } ?: return null
        val tag = p.day.trim().toIntOrNull() ?: 0
        if (p.month !in 0..12 || tag !in 0..31 || (tag > 0 && p.month == 0)) return null
        return listOf(JsonPrimitive(tag), JsonPrimitive(p.month), JsonPrimitive(jahr), JsonPrimitive(false))
    }

    /**
     * Gramps-Datum als Eingabe, wenn es sich verlustfrei so darstellen laesst (gregorianisch, genau, kein Zeitraum
     * "von … bis", kein Text); sonst null - dann bleibt das Datum beim Bearbeiten unangetastet.
     */
    fun eingabe(datum: JsonObject?): DateForm? {
        if (datum == null) return DateForm()
        val int = { k: String -> (datum[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 }
        val werte = (datum["dateval"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.content }
        if (int("calendar") != 0 || int("quality") != 0 || werte.getOrNull(3) == "true") return null
        val leer = werte.take(3).all { it == "0" || it == null }
        if (leer) return if ((datum["text"] as? JsonPrimitive)?.content.isNullOrBlank()) DateForm() else null
        fun part(i: Int) = DatePart(
            day = werte.getOrNull(i)?.takeIf { it != "0" }.orEmpty(),
            month = werte.getOrNull(i + 1)?.toIntOrNull() ?: 0,
            year = werte.getOrNull(i + 2)?.takeIf { it != "0" }.orEmpty(),
        )
        return when (int("modifier")) {
            MOD_KEINER -> DateForm(DateQualifier.EXACT, part(0))
            MOD_ETWA -> DateForm(DateQualifier.ABOUT, part(0))
            MOD_VOR -> DateForm(DateQualifier.BEFORE, part(0))
            MOD_NACH -> DateForm(DateQualifier.AFTER, part(0))
            MOD_ZWISCHEN -> if (werte.size >= 7) DateForm(DateQualifier.BETWEEN, part(0), part(4)) else null
            else -> null
        }
    }

    // ── Neue Objekte ─────────────────────────────────────────────────

    fun ereignis(handle: String, typ: String, datum: JsonObject?, ort: String?, beschreibung: String): JsonObject = buildJsonObject {
        put("_class", "Event")
        put("handle", handle)
        put("type", typ)
        if (datum != null) put("date", datum)
        put("place", ort.orEmpty())
        put("description", beschreibung)
    }

    /** Geschlecht wie Gramps: 0 weiblich, 1 maennlich, 2 unbekannt. */
    fun person(handle: String, vorname: String, nachname: String, geschlecht: Int, rufname: String = ""): JsonObject = buildJsonObject {
        put("_class", "Person")
        put("handle", handle)
        put("gender", geschlecht)
        put("primary_name", name(vorname, nachname, rufname))
    }

    private fun name(vorname: String, nachname: String, rufname: String): JsonObject = buildJsonObject {
        put("_class", "Name")
        put("type", "Birth Name")
        put("first_name", vorname.trim())
        put("call", rufname.trim())
        put("surname_list", buildJsonArray {
            add(buildJsonObject { put("_class", "Surname"); put("surname", nachname.trim()); put("primary", true) })
        })
    }

    fun familie(handle: String, vater: String?, mutter: String?, kinder: List<String> = emptyList()): JsonObject = buildJsonObject {
        put("_class", "Family")
        put("handle", handle)
        put("father_handle", vater?.let { JsonPrimitive(it) } ?: JsonNull)
        put("mother_handle", mutter?.let { JsonPrimitive(it) } ?: JsonNull)
        put("type", "Married")
        put("child_ref_list", JsonArray(kinder.map(::kindverweis)))
    }

    fun ort(handle: String, name: String): JsonObject = buildJsonObject {
        put("_class", "Place")
        put("handle", handle)
        put("title", name.trim())
        put("name", buildJsonObject { put("_class", "PlaceName"); put("value", name.trim()) })
        put("place_type", "Unknown")
    }

    /** Vertrauensgrad 0 sehr gering … 4 sehr hoch, 2 normal. */
    fun zitat(handle: String, quelle: String, seite: String, vertrauen: Int): JsonObject = buildJsonObject {
        put("_class", "Citation")
        put("handle", handle)
        put("source_handle", quelle)
        put("page", seite.trim())
        put("confidence", vertrauen.coerceIn(0, 4))
    }

    private fun kindverweis(kind: String): JsonObject = buildJsonObject {
        put("_class", "ChildRef")
        put("ref", kind)
        put("frel", "Birth")
        put("mrel", "Birth")
    }

    // ── Aenderungen an vorhandenen Objekten ──────────────────────────

    private fun JsonObject.mit(schluessel: String, wert: JsonElement): JsonObject = JsonObject(this + (schluessel to wert))
    private fun JsonObject.liste(schluessel: String): List<JsonElement> = (this[schluessel] as? JsonArray).orEmpty()
    private fun JsonElement.ref(): String? = ((this as? JsonObject)?.get("ref") as? JsonPrimitive)?.content
    private fun JsonObject.zahl(schluessel: String): Int = (this[schluessel] as? JsonPrimitive)?.content?.toIntOrNull() ?: -1

    /**
     * Ereignisverweis an eine Person haengen. Ist es die erste Geburt bzw. der erste Tod, merkt Gramps sich den Index
     * (birth_ref_index/death_ref_index) - sonst zeigte das Profil die Lebensdaten nicht.
     */
    fun ereignisAnPerson(person: JsonObject, ereignis: String, typ: String, rolle: String = "Primary"): JsonObject {
        val alt = person.liste("event_ref_list")
        if (alt.any { it.ref() == ereignis }) return person
        val verweis = buildJsonObject { put("_class", "EventRef"); put("ref", ereignis); put("role", rolle) }
        var neu = person.mit("event_ref_list", JsonArray(alt + verweis))
        if (rolle == "Primary" && typ == "Birth" && person.zahl("birth_ref_index") < 0) neu = neu.mit("birth_ref_index", JsonPrimitive(alt.size))
        if (rolle == "Primary" && typ == "Death" && person.zahl("death_ref_index") < 0) neu = neu.mit("death_ref_index", JsonPrimitive(alt.size))
        return neu
    }

    /** Ereignisverweis entfernen und die Indizes fuer Geburt und Tod nachziehen. */
    fun ereignisVonPerson(person: JsonObject, ereignis: String): JsonObject {
        val alt = person.liste("event_ref_list")
        val i = alt.indexOfFirst { it.ref() == ereignis }
        if (i < 0) return person
        fun neuIndex(k: String): Int = person.zahl(k).let { when { it == i -> -1; it > i -> it - 1; else -> it } }
        return person.mit("event_ref_list", JsonArray(alt.filterIndexed { j, _ -> j != i }))
            .mit("birth_ref_index", JsonPrimitive(neuIndex("birth_ref_index")))
            .mit("death_ref_index", JsonPrimitive(neuIndex("death_ref_index")))
    }

    /** Typ, Datum, Ort, Beschreibung eines Ereignisses; ein nicht darstellbares Datum ([datum] = null bei [datumAendern] false) bleibt. */
    fun ereignisAendern(ereignis: JsonObject, typ: String, datum: JsonObject?, datumAendern: Boolean, ort: String?, beschreibung: String): JsonObject {
        var neu = ereignis.mit("type", JsonPrimitive(typ)).mit("place", JsonPrimitive(ort.orEmpty())).mit("description", JsonPrimitive(beschreibung))
        if (datumAendern) neu = neu.mit("date", datum ?: leeresDatum())
        return neu
    }

    private fun leeresDatum(): JsonObject = buildJsonObject {
        put("_class", "Date"); put("calendar", 0); put("modifier", 0); put("quality", 0); put("newyear", 0); put("text", ""); put("sortval", 0)
        put("dateval", JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(0), JsonPrimitive(0), JsonPrimitive(false))))
    }

    /** Vorname, Nachname (der primaere), Rufname und Geschlecht; weitere Namensteile und Namen bleiben. */
    fun personAendern(person: JsonObject, vorname: String, nachname: String, rufname: String, geschlecht: Int): JsonObject {
        val name = person["primary_name"] as? JsonObject ?: name(vorname, nachname, rufname)
        val nachnamen = name.liste("surname_list")
        val iPrimaer = nachnamen.indexOfFirst { ((it as? JsonObject)?.get("primary") as? JsonPrimitive)?.content == "true" }.takeIf { it >= 0 } ?: 0
        val neueNachnamen = if (nachnamen.isEmpty()) {
            listOf(buildJsonObject { put("_class", "Surname"); put("surname", nachname.trim()); put("primary", true) })
        } else nachnamen.mapIndexed { i, s -> if (i == iPrimaer) (s as JsonObject).mit("surname", JsonPrimitive(nachname.trim())) else s }
        val neuerName = name.mit("first_name", JsonPrimitive(vorname.trim())).mit("call", JsonPrimitive(rufname.trim()))
            .mit("surname_list", JsonArray(neueNachnamen))
        return person.mit("primary_name", neuerName).mit("gender", JsonPrimitive(geschlecht))
    }

    fun kindHinzu(familie: JsonObject, kind: String): JsonObject {
        val alt = familie.liste("child_ref_list")
        if (alt.any { it.ref() == kind }) return familie
        return familie.mit("child_ref_list", JsonArray(alt + kindverweis(kind)))
    }

    fun kindEntfernen(familie: JsonObject, kind: String): JsonObject =
        familie.mit("child_ref_list", JsonArray(familie.liste("child_ref_list").filter { it.ref() != kind }))

    /** Kind um [schritt] Plaetze verschieben (-1 nach vorn, +1 nach hinten). */
    fun kindVerschieben(familie: JsonObject, kind: String, schritt: Int): JsonObject {
        val liste = familie.liste("child_ref_list").toMutableList()
        val i = liste.indexOfFirst { it.ref() == kind }
        val j = i + schritt
        if (i < 0 || j !in liste.indices) return familie
        liste.add(j, liste.removeAt(i))
        return familie.mit("child_ref_list", JsonArray(liste))
    }

    /** Elternteil setzen (Vater oder Mutter) oder mit null entfernen. */
    fun elternteil(familie: JsonObject, vater: Boolean, person: String?): JsonObject =
        familie.mit(if (vater) "father_handle" else "mother_handle", person?.let { JsonPrimitive(it) } ?: JsonNull)

    fun hatElternteil(familie: JsonObject, vater: Boolean): Boolean =
        ((familie[if (vater) "father_handle" else "mother_handle"] as? JsonPrimitive)?.content).let { !it.isNullOrEmpty() && it != "null" }

    /** Medienverweis anhaengen; mit [vorn] als erstes Bild (das zeigt Gramps als Portraet). */
    fun medienverweis(obj: JsonObject, medium: String, vorn: Boolean = false, rect: List<Int>? = null): JsonObject {
        val alt = obj.liste("media_list").filter { it.ref() != medium }
        val verweis = buildJsonObject {
            put("_class", "MediaRef"); put("ref", medium)
            put("rect", JsonArray((rect ?: emptyList()).map { JsonPrimitive(it) }))
        }
        return obj.mit("media_list", JsonArray(if (vorn) listOf(verweis) + alt else alt + verweis))
    }

    /** Vorhandenen Medienverweis an die erste Stelle (Hauptbild). */
    fun hauptbild(obj: JsonObject, medium: String): JsonObject {
        val liste = obj.liste("media_list")
        val i = liste.indexOfFirst { it.ref() == medium }
        if (i <= 0) return obj
        return obj.mit("media_list", JsonArray(listOf(liste[i]) + liste.filterIndexed { j, _ -> j != i }))
    }

    fun beschreibung(medium: JsonObject, text: String): JsonObject = medium.mit("desc", JsonPrimitive(text.trim()))

    fun zitatAn(obj: JsonObject, zitat: String): JsonObject {
        val alt = obj.liste("citation_list").mapNotNull { (it as? JsonPrimitive)?.content }
        return if (zitat in alt) obj else obj.mit("citation_list", JsonArray((alt + zitat).map { JsonPrimitive(it) }))
    }

    /** Beziehung zu einer anderen Person (Pate, Zeuge …) als Personenverweis mit Beschreibung. */
    fun beziehung(person: JsonObject, andere: String, art: String): JsonObject {
        val alt = person.liste("person_ref_list")
        val verweis = buildJsonObject { put("_class", "PersonRef"); put("ref", andere); put("rel", art.trim()) }
        return person.mit("person_ref_list", JsonArray(alt + verweis))
    }

    fun beziehungEntfernen(person: JsonObject, andere: String, art: String): JsonObject =
        person.mit("person_ref_list", JsonArray(person.liste("person_ref_list").filterNot {
            it.ref() == andere && (((it as? JsonObject)?.get("rel") as? JsonPrimitive)?.content.orEmpty() == art)
        }))

    /** Ereignis innerhalb der Person um [schritt] verschieben, Indizes fuer Geburt und Tod wandern mit. */
    fun ereignisVerschieben(person: JsonObject, ereignis: String, schritt: Int): JsonObject {
        val liste = person.liste("event_ref_list").toMutableList()
        val i = liste.indexOfFirst { it.ref() == ereignis }
        val j = i + schritt
        if (i < 0 || j !in liste.indices) return person
        val geburt = liste.getOrNull(person.zahl("birth_ref_index"))?.ref()
        val tod = liste.getOrNull(person.zahl("death_ref_index"))?.ref()
        liste.add(j, liste.removeAt(i))
        return person.mit("event_ref_list", JsonArray(liste))
            .mit("birth_ref_index", JsonPrimitive(geburt?.let { g -> liste.indexOfFirst { it.ref() == g } } ?: -1))
            .mit("death_ref_index", JsonPrimitive(tod?.let { t -> liste.indexOfFirst { it.ref() == t } } ?: -1))
    }
}
