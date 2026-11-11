package de.bgghome.gwand.daten

import de.bgghome.gwand.api.GrampsPerson
import de.bgghome.gwand.api.ProfilEreignis
import de.bgghome.webtrees.nativ.api.DateJson
import de.bgghome.webtrees.nativ.api.EventJson
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.PlaceJson

/**
 * Uebersetzt Gramps-Personen in die Anzeigeform der geteilten Darstellung (Baumkarten, Avatar, Listen).
 * Die Kennung der Anzeige (`xref`) ist das Gramps-Handle, nicht die Gramps-ID: Handles aendern sich nie.
 *
 * @param bild liefert zur Person die Adresse des Vorschaubilds oder null (haengt am Client, darum von aussen)
 * @param web  liefert zur Gramps-ID die Adresse der Person in Gramps Web
 */
class Anzeige(
    private val bild: (GrampsPerson) -> String? = { null },
    private val web: (String) -> String = { "" },
) {

    fun person(p: GrampsPerson): Person {
        val profil = p.profile
        val vorname = profil?.vorname.orEmpty().trim()
        val nachname = profil?.nachname.orEmpty().trim()
        val name = listOf(vorname, nachname).filter { it.isNotEmpty() }.joinToString(" ")
            .ifEmpty { profil?.anzeigename.orEmpty() }
        val geburt = profil?.birth ?: ProfilEreignis()
        val tod = profil?.death ?: ProfilEreignis()
        return Person(
            xref = p.handle,
            name = name,
            sortName = profil?.anzeigename.orEmpty().ifEmpty { name },
            given = vorname,
            surname = nachname,
            sex = geschlecht(p),
            isDead = !tod.leer,
            isPrivate = p.private,
            lifespan = lebensdaten(geburt, tod),
            birth = ereignis(geburt),
            death = ereignis(tod),
            thumb = bild(p),
            url = web(p.grampsId),
        )
    }

    companion object {
        /** Gramps: 0 weiblich, 1 maennlich, 2 unbekannt. Die aufbereitete Form sagt es schon als Buchstaben. */
        fun geschlecht(p: GrampsPerson): String = when (p.profile?.sex) {
            "M", "F" -> p.profile.sex
            else -> when (p.gender) { 1 -> "M"; 0 -> "F"; else -> "U" }
        }

        /** Das Jahr aus dem ISO-Datum der aufbereiteten Form ("1955-11-03", "abt 1850", "1850"), sonst 0. */
        fun jahr(datum: String): Int =
            Regex("""(?<!\d)(\d{3,4})(?!\d)""").find(datum)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        /** "1955–2010", "1955–" fuer Lebende mit Geburt, "–2010" ohne Geburtsjahr; leer ohne beides. */
        fun lebensdaten(geburt: ProfilEreignis, tod: ProfilEreignis): String {
            val g = jahr(geburt.date)
            val t = jahr(tod.date)
            return when {
                g == 0 && t == 0 -> ""
                else -> (if (g > 0) "$g" else "") + "–" + (if (t > 0) "$t" else "")
            }
        }

        fun ereignis(e: ProfilEreignis): EventJson? {
            if (e.leer) return null
            val ort = e.ortsname.ifEmpty { e.place }
            return EventJson(
                date = e.date.takeIf { it.isNotBlank() }?.let { DateJson(text = anzeigeDatum(it), year = jahr(it)) },
                place = ort.takeIf { it.isNotBlank() }?.let { PlaceJson(name = it, short = it.substringBefore(',').trim()) },
            )
        }

        /** "1955-11-03" -> "03.11.1955", "1955-11-00" -> "11.1955", "1955-00-00" -> "1955"; sonst unveraendert. */
        fun anzeigeDatum(iso: String): String {
            val m = Regex("""^(\d{3,4})-(\d{2})-(\d{2})$""").find(iso.trim()) ?: return iso
            val (j, mo, t) = m.destructured
            return when {
                mo == "00" -> j
                t == "00" -> "$mo.$j"
                else -> "$t.$mo.$j"
            }
        }
    }
}
