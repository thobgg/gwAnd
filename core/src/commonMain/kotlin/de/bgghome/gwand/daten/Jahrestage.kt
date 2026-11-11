package de.bgghome.gwand.daten

import de.bgghome.gwand.api.Ereignis
import de.bgghome.gwand.api.PersonProfil
import de.bgghome.gwand.ui.Jahrestag
import de.bgghome.webtrees.nativ.api.Person
import java.time.LocalDate

/** Jahrestage der naechsten Tage aus den Ereignissen des Servers. Reine Rechnung, siehe JahrestageTest. */
object Jahrestage {

    /** Englische Grundnamen der Ereignisse, die als Jahrestag zaehlen. */
    val TYPEN = setOf("Birth", "Marriage", "Death", "Burial")

    /** (Monat, Tag) fuer heute und die folgenden Tage. */
    fun tage(heute: LocalDate, anzahl: Int): List<Pair<Int, Int>> =
        (0 until anzahl).map { heute.plusDays(it.toLong()) }.map { it.monthValue to it.dayOfMonth }

    fun aus(ereignisse: List<Ereignis>, heute: LocalDate, anzahl: Int, anzeige: (PersonProfil) -> Person): List<Jahrestag> {
        val reihenfolge = tage(heute, anzahl)
        return ereignisse.asSequence()
            .filter { it.type in TYPEN && !it.private }
            // Nur genaue Daten: kein "etwa", "vor", "zwischen", kein geschaetztes oder berechnetes Datum.
            .filter { it.date.modifier == 0 && it.date.quality == 0 && it.date.year > 0 }
            .filter { (it.date.monat to it.date.tag) in reihenfolge }
            .mapNotNull { e ->
                val p = e.profile ?: return@mapNotNull null
                val profile = if (e.type == "Marriage") {
                    p.participants.families.flatMap { listOfNotNull(it.family.father, it.family.mother) }
                } else {
                    p.participants.people.map { it.person }.take(1)
                }.filter { it.vorhanden }
                if (profile.isEmpty()) return@mapNotNull null
                val lebend = when (e.type) {
                    "Birth", "Marriage" -> profile.all { it.death.leer }
                    else -> false
                }
                Jahrestag(
                    personen = profile.map(anzeige), art = p.type, typ = e.type,
                    monat = e.date.monat, tag = e.date.tag, jahre = heute.year - e.date.year, lebend = lebend,
                )
            }
            .sortedWith(compareBy({ reihenfolge.indexOf(it.monat to it.tag) }, { it.jahre }))
            .toList()
    }
}
