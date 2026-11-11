package de.bgghome.gwand

import de.bgghome.gwand.ui.einhaengen
import de.bgghome.gwand.ui.zuklappen
import de.bgghome.webtrees.nativ.api.DescendantFamily
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AstTest {
    private fun p(x: String) = Person(xref = x, name = x)

    @Test
    fun einhaengenUndZuklappen() {
        // Mitte A mit Kind B; B ist unterste Reihe mit weiteren Nachkommen
        val baum = DescendantNode(p("A"), listOf(DescendantFamily("F1", children = listOf(DescendantNode(p("B"), weitere = true)))))
        val zweig = DescendantNode(p("B"), listOf(DescendantFamily("F2", spouse = p("P"), children = listOf(DescendantNode(p("C"))))))

        val auf = baum.einhaengen("B", zweig)
        val b = auf.families.single().children.single()
        assertTrue(b.aufgeklappt)
        assertFalse(b.weitere)
        assertEquals("C", b.families.single().children.single().person.xref)

        val zu = auf.zuklappen("B")
        val b2 = zu.families.single().children.single()
        assertTrue(b2.weitere)
        assertFalse(b2.aufgeklappt)
        assertTrue(b2.families.single().children.isEmpty())
        assertEquals("P", b2.families.single().spouse?.xref, "Partner bleibt beim Zuklappen")
    }

    @Test
    fun nurAufklappbareKnoten() {
        val baum = DescendantNode(p("A"), listOf(DescendantFamily("F1", children = listOf(DescendantNode(p("B"))))))
        assertEquals(baum, baum.einhaengen("B", DescendantNode(p("B"))))
        assertEquals(baum, baum.zuklappen("B"))
    }
}
