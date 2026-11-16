package de.bgghome.gwand.api

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE nach RFC 7636: zufaelliger Verifier und seine S256-Challenge. */
object Pkce {
    private val base64 = Base64.getUrlEncoder().withoutPadding()

    /** 32 Zufallsbytes, base64url: 43 Zeichen aus dem erlaubten Zeichenvorrat. */
    fun verifier(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.let(base64::encodeToString)

    fun challenge(verifier: String): String =
        base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
