package de.bgghome.gwand

import de.bgghome.gwand.ui.tageBis
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TageBisTest {
    @Test
    fun heuteMorgenUndSpaeter() {
        val heute = LocalDate.of(2026, 11, 11)
        assertEquals(0, tageBis(heute, 11, 11))
        assertEquals(1, tageBis(heute, 11, 12))
        assertEquals(14, tageBis(heute, 11, 25))
    }

    @Test
    fun ueberDenJahreswechsel() {
        assertEquals(3, tageBis(LocalDate.of(2026, 12, 30), 1, 2))
        // Gestern war es schon: dann erst im naechsten Jahr
        assertEquals(364, tageBis(LocalDate.of(2026, 11, 11), 11, 10))
    }

    @Test
    fun neunundzwanzigsterFebruar() {
        // 2027 ist kein Schaltjahr: der 29.2. faellt auf den 28.2.
        assertEquals(1, tageBis(LocalDate.of(2027, 2, 27), 2, 29))
        assertEquals(1, tageBis(LocalDate.of(2028, 2, 28), 2, 29))
    }
}
