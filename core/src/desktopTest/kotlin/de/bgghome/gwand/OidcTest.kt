package de.bgghome.gwand

import de.bgghome.gwand.api.GrampsJson
import de.bgghome.gwand.api.OidcKonfig
import de.bgghome.gwand.ui.GwViewModel
import de.bgghome.gwand.ui.oidcRuecksprung
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OidcTest {

    @Test
    fun ruecksprungMitCode() {
        assertEquals("abc.DEF-1" to null, oidcRuecksprung("https://gramps.example.org/oidc/complete#code=abc.DEF-1"))
        assertEquals("x y" to null, oidcRuecksprung("http://192.168.1.5:5000/oidc/complete/#code=x%20y"))
        assertEquals("q" to null, oidcRuecksprung("https://g.example.org/web/oidc/complete?code=q"))
    }

    @Test
    fun ruecksprungMitFehler() {
        assertEquals(null to "link_failed", oidcRuecksprung("https://g.example.org/oidc/complete#error=link_failed"))
        // Server vor API 3.21: Ruecksprung ohne Code
        assertEquals(null to GwViewModel.OIDC_ALT, oidcRuecksprung("https://g.example.org/oidc/complete"))
    }

    @Test
    fun andereSeitenNicht() {
        assertNull(oidcRuecksprung("https://g.example.org/api/oidc/login/?provider=custom"))
        assertNull(oidcRuecksprung("https://idp.example.org/auth?redirect_uri=https%3A%2F%2Fg.example.org%2Foidc%2Fcomplete"))
        assertNull(oidcRuecksprung("https://g.example.org/api/oidc/callback/custom?code=abc&state=s"))
    }

    @Test
    fun konfigLesen() {
        val k = GrampsJson.decodeFromString(OidcKonfig.serializer(), """
            {"enabled": true, "providers": [{"id": "custom", "name": "Authelia",
             "login_url": "https://g.example.org/api/oidc/login/?provider=custom"}],
             "disable_local_auth": false, "auto_redirect": false}
        """)
        assertTrue(k.enabled)
        assertEquals("Authelia", k.providers.single().name)
        assertEquals(false, k.nurOidc)
        assertEquals(false, GrampsJson.decodeFromString(OidcKonfig.serializer(), """{"enabled": false}""").enabled)
    }
}
