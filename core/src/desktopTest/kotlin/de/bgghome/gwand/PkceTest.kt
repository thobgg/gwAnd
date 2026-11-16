package de.bgghome.gwand

import de.bgghome.gwand.api.GwClient
import de.bgghome.gwand.api.Pkce
import de.bgghome.gwand.ui.oidcRuecksprung
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PkceTest {
    @Test
    fun challengeWieImStandard() {
        // RFC 7636, Anhang B
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun verifierImErlaubtenFormat() {
        val v = Pkce.verifier()
        assertTrue(Regex("^[A-Za-z0-9._~-]{43,128}$").matches(v), v)
        assertNotEquals(v, Pkce.verifier())
    }

    @Test
    fun ruecksprungUeberAppLink() {
        val hosts = setOf("gwand.bgg-home.de")
        // direkt aus der Weiterleitung (Fragment) und ueber den Knopf der Rueckseite (Abfrage)
        assertEquals("c1" to null, oidcRuecksprung(GwClient.APP_RUECKSPRUNG + "#code=c1", hosts))
        assertEquals("c2" to null, oidcRuecksprung(GwClient.APP_RUECKSPRUNG + "/?code=c2", hosts))
        assertEquals(null to "account_not_active", oidcRuecksprung(GwClient.APP_RUECKSPRUNG + "#error=account_not_active", hosts))
        assertNull(oidcRuecksprung("https://anderer.example.org/oidc/complete#code=x", hosts))
    }
}
