package de.bgghome.gwand.daten

import de.bgghome.gwand.api.DnaSegment

/**
 * Rechnung fuer die Chromosomen-Ansicht, uebertragen aus Gramps Web (src/charts/ChromosomeBrowser.js): Laengen der
 * Chromosomen, Zuordnung der Beschriftung aus den Rohdaten, Verteilung ueberlappender Segmente auf Bahnen, Farbfolge.
 * Reine Rechnung, siehe DnaTest.
 */
object Dna {

    /** Laenge der Chromosomen in Basenpaaren, in der Reihenfolge der Anzeige (1 bis 22, X). */
    val LAENGEN: Map<String, Long> = linkedMapOf(
        "1" to 249250621L, "2" to 243199373L, "3" to 198022430L, "4" to 191154276L, "5" to 180915260L, "6" to 171115067L,
        "7" to 159138663L, "8" to 146364022L, "9" to 141213431L, "10" to 135534747L, "11" to 135006516L, "12" to 133851895L,
        "13" to 115169878L, "14" to 107349540L, "15" to 102531392L, "16" to 90354753L, "17" to 81195210L, "18" to 78077248L,
        "19" to 59128983L, "20" to 63025520L, "21" to 48129895L, "22" to 51304566L, "X" to 155270560L,
    )

    /** Das laengste Chromosom gibt die Breite der Zeichnung vor. */
    const val GROESSTE_LAENGE = 249250621L

    /** Schluessel fuer eine Beschriftung aus den Rohdaten ("chr7" → "7", "23" → "X"); null = wird nicht gezeigt. */
    fun schluessel(beschriftung: String?): String? {
        val k = beschriftung.orEmpty().trim().replace(Regex("^chr", RegexOption.IGNORE_CASE), "").uppercase()
        if (k == "23") return "X"
        return k.takeIf { it in LAENGEN }
    }

    /** Spur eines Segments: "P" vaeterlich, "M" muetterlich, alles andere "U" (unbekannt). */
    fun spur(seite: String): String = if (seite == "P" || seite == "M") seite else "U"

    /** Bahn je Segment (in der gegebenen Reihenfolge) und Zahl der Bahnen je Spur. */
    class Bahnen(val bahn: List<Int>, val anzahl: Map<String, Int>)

    /**
     * Gibt jedem Segment eines Chromosoms die niedrigste Bahn seiner Spur, in der es kein anderes ueberlappt.
     * Die vaeterliche und die muetterliche Spur haben immer mindestens eine Bahn, die unbekannte nur mit Segmenten.
     */
    fun bahnen(segmente: List<DnaSegment>): Bahnen {
        val enden = mapOf("P" to mutableListOf<Long>(), "U" to mutableListOf(), "M" to mutableListOf())
        val bahn = IntArray(segmente.size)
        segmente.indices.sortedBy { minOf(segmente[it].start, segmente[it].stop) }.forEach { i ->
            val s = segmente[i]
            val von = minOf(s.start, s.stop)
            val spurEnden = enden.getValue(spur(s.side))
            var b = spurEnden.indexOfFirst { it <= von }
            if (b == -1) { b = spurEnden.size; spurEnden.add(0) }
            spurEnden[b] = maxOf(s.start, s.stop)
            bahn[i] = b
        }
        return Bahnen(bahn.toList(), mapOf(
            "P" to maxOf(1, enden.getValue("P").size),
            "U" to enden.getValue("U").size,
            "M" to maxOf(1, enden.getValue("M").size),
        ))
    }

    /** Farben der Treffer in fester Folge (Gramps Web, palette.js), danach dieselben Toene hell mit Rand. */
    val FARBEN = listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF6250D6, 0xFFE34948)
    private val HELL = listOf(0xFFAAC9EF, 0xFFF7C3AE, 0xFFA4DFCA, 0xFFF8D999, 0xFFF6CADB, 0xFF99CD99, 0xFFC0B9EF, 0xFFF4B6B6)

    /** Fuellfarbe des Treffers Nummer [i]; wiederholt sich nach 16 Treffern. */
    fun farbe(i: Int): Long = (i % (2 * FARBEN.size)).let { if (it < FARBEN.size) FARBEN[it] else HELL[it - FARBEN.size] }

    /** Randfarbe des Treffers Nummer [i], null = ohne Rand. */
    fun rand(i: Int): Long? = (i % (2 * FARBEN.size)).let { if (it < FARBEN.size) null else FARBEN[it - FARBEN.size] }

    /** Segmente in der Reihenfolge der Chromosomen, dann nach Anfang (fuer die Tabelle). */
    fun sortiert(segmente: List<DnaSegment>): List<DnaSegment> {
        val folge = LAENGEN.keys.toList()
        return segmente.sortedWith(compareBy({ schluessel(it.chromosome)?.let(folge::indexOf) ?: Int.MAX_VALUE }, { if (schluessel(it.chromosome) == null) it.chromosome else "" }, { minOf(it.start, it.stop) }))
    }
}
