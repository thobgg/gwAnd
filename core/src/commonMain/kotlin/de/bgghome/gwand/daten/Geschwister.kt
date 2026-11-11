package de.bgghome.gwand.daten

import de.bgghome.gwand.api.GrampsPerson
import de.bgghome.webtrees.nativ.api.Pedigree
import de.bgghome.webtrees.nativ.ui.tree.Sibling

/**
 * Geschwister der Mittelperson und ihrer Ahnen fuer das Baumlayout, je mit Partnern; bei den Geschwistern der Eltern
 * auch deren Kinder (die Cousins) und deren Partner. Der Client laedt in drei Schritten nach (siehe GwViewModel),
 * diese Klasse sagt, was zu laden ist, und baut daraus die Eingabe des Layouts.
 */
object Geschwister {

    /** So viele Reihen von unten bekommen Geschwister: Mittelperson, Eltern, Grosseltern. */
    const val REIHEN = 3

    /** Ahnen (Handle) mit Generation, deren Geschwister gezeigt werden. */
    fun ahnen(tafel: Pedigree): List<Pair<String, Int>> =
        tafel.ancestors.filter { BaumBau.generation(it.n) < REIHEN }.map { it.person.xref to BaumBau.generation(it.n) }

    /** Schritt 1: die Geschwister-Handles je Ahn aus dessen Elternfamilie. */
    fun geschwisterHandles(tafel: Pedigree, vorfahren: List<GrampsPerson>): Map<String, List<String>> {
        val nachHandle = vorfahren.associateBy { it.handle }
        return ahnen(tafel).associate { (h, _) ->
            h to nachHandle[h]?.extended?.elternfamilie?.takeIf { it.handle.isNotEmpty() }?.kinder.orEmpty().map { it.ref }.filter { it != h }
        }.filterValues { it.isNotEmpty() }
    }

    /** Schritt 2: Partner aller Geschwister und, bei den Geschwistern der Eltern, deren Kinder. */
    fun zweiterSchritt(tafel: Pedigree, geschwister: Map<String, List<String>>, personen: Map<String, GrampsPerson>, cousins: Boolean): Set<String> {
        val generation = ahnen(tafel).toMap()
        return geschwister.flatMap { (ahn, liste) ->
            liste.flatMap { s ->
                val familien = personen[s]?.extended?.families.orEmpty()
                familien.mapNotNull { it.partnerVon(s) } +
                    if (cousins && generation[ahn] == 1) familien.flatMap { f -> f.kinder.map { it.ref } } else emptyList()
            }
        }.filterNot { it in personen }.toSet()
    }

    /** Schritt 3: Partner der Cousins. */
    fun dritterSchritt(cousins: Collection<GrampsPerson>, bekannt: Set<String>): Set<String> =
        cousins.flatMap { c -> c.extended?.families.orEmpty().mapNotNull { it.partnerVon(c.handle) } }.filterNot { it in bekannt }.toSet()

    fun bauen(
        tafel: Pedigree,
        geschwister: Map<String, List<String>>,
        personen: Map<String, GrampsPerson>,
        anzeige: Anzeige,
        cousins: Boolean,
    ): Map<String, List<Sibling>> {
        val generation = ahnen(tafel).toMap()
        return geschwister.mapValues { (ahn, liste) ->
            liste.mapNotNull { personen[it] }.map { s ->
                val familien = s.extended?.families.orEmpty()
                val partner = familien.mapNotNull { it.partnerVon(s.handle) }.mapNotNull { personen[it] }
                val kinder = if (cousins && generation[ahn] == 1) familien.flatMap { f -> f.kinder.map { it.ref } }.mapNotNull { personen[it] } else emptyList()
                Sibling(
                    person = anzeige.person(s),
                    spouses = partner.map(anzeige::person),
                    children = kinder.map(anzeige::person),
                    childSpouses = kinder.associate { k ->
                        k.handle to k.extended?.families.orEmpty().mapNotNull { it.partnerVon(k.handle) }.mapNotNull { personen[it] }.map(anzeige::person)
                    }.filterValues { it.isNotEmpty() },
                )
            }
        }.filterValues { it.isNotEmpty() }
    }
}
