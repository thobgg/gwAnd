package de.bgghome.gwand.daten

import de.bgghome.gwand.api.GrampsPerson
import de.bgghome.webtrees.nativ.api.Ancestor
import de.bgghome.webtrees.nativ.api.DescendantFamily
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.EventJson
import de.bgghome.webtrees.nativ.api.Pedigree

/**
 * Baut aus den Filterabfragen der Gramps Web API die Eingaben des geteilten Baumlayouts: Ahnentafel mit
 * Kekule-Nummern nach oben, Nachkommen als Baum nach unten. Reine Rechnung ohne Netz, siehe BaumBauTest.
 */
class BaumBau(private val anzeige: Anzeige) {

    /**
     * @param wurzel Handle der Mittelperson
     * @param vorfahren Ergebnis von [de.bgghome.gwand.api.GwClient.vorfahren]: Mittelperson und Vorfahren, je mit Elternfamilie
     */
    fun ahnentafel(wurzel: String, vorfahren: List<GrampsPerson>): Pedigree {
        val nachHandle = vorfahren.associateBy { it.handle }
        val ahnen = mutableListOf<Ancestor>()
        var tiefe = 0
        val warteschlange = ArrayDeque(listOf(1 to wurzel))
        while (warteschlange.isNotEmpty()) {
            val (n, handle) = warteschlange.removeFirst()
            val p = nachHandle[handle] ?: continue
            val eltern = p.extended?.elternfamilie?.takeIf { it.handle.isNotEmpty() }
            ahnen += Ancestor(n = n, person = anzeige.person(p), hasParents = eltern != null && (eltern.vater != null || eltern.mutter != null))
            tiefe = maxOf(tiefe, generation(n))
            eltern?.vater?.let { warteschlange += 2 * n to it }
            eltern?.mutter?.let { warteschlange += 2 * n + 1 to it }
        }
        return Pedigree(root = wurzel, generations = tiefe + 1, ancestors = ahnen.sortedBy { it.n })
    }

    /**
     * @param wurzel Mittelperson mit ihren Partnerfamilien (`extend=family_list`)
     * @param nachkommen Ergebnis von [de.bgghome.gwand.api.GwClient.nachkommen], je mit Partnerfamilien
     * @param partner die Partner aller beteiligten Familien, soweit sie nicht selbst Nachkommen sind
     */
    fun nachkommen(
        wurzel: GrampsPerson,
        nachkommen: List<GrampsPerson>,
        partner: List<GrampsPerson>,
        heiraten: Map<String, EventJson> = emptyMap(),
    ): DescendantNode {
        val nachHandle = (nachkommen + wurzel).associateBy { it.handle }
        val partnerNachHandle = partner.associateBy { it.handle } + nachHandle
        fun knoten(p: GrampsPerson, besucht: Set<String>): DescendantNode {
            var weitere = false
            val familien = p.extended?.families.orEmpty().map { f ->
                val kinder = f.kinder.mapNotNull { k ->
                    val kind = nachHandle[k.ref]
                    if (kind == null || k.ref in besucht) { weitere = true; null } else knoten(kind, besucht + k.ref)
                }
                DescendantFamily(
                    xref = f.handle,
                    spouse = f.partnerVon(p.handle)?.let { partnerNachHandle[it] }?.let { anzeige.person(it) },
                    marriage = heiraten[f.handle],
                    children = kinder,
                )
            }
            return DescendantNode(person = anzeige.person(p), families = familien, weitere = weitere)
        }
        return knoten(wurzel, setOf(wurzel.handle))
    }

    companion object {
        /** Alle Familien der Nachkommen und der Mittelperson (fuer die Heiratsdaten). */
        fun familien(personen: List<GrampsPerson>): Set<String> = personen.flatMap { p -> p.extended?.families.orEmpty().map { it.handle } }.toSet()

        /** Partner aller Familien von [personen], die nicht selbst in [personen] stehen - die muss der Client nachladen. */
        fun fehlendePartner(personen: List<GrampsPerson>): Set<String> {
            val bekannt = personen.map { it.handle }.toSet()
            return personen.flatMap { p -> p.extended?.families.orEmpty().mapNotNull { it.partnerVon(p.handle) } }
                .filterNot { it in bekannt }.toSet()
        }

        /** Generation einer Kekule-Nummer: 1 -> 0, 2..3 -> 1, 4..7 -> 2 … */
        fun generation(n: Int): Int = 31 - Integer.numberOfLeadingZeros(n)
    }
}
