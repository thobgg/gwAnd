package de.bgghome.gwand.api

import de.bgghome.webtrees.nativ.api.Heimnetz
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Der Server hat mit einem Fehler geantwortet; [status] ist der HTTP-Status, [text] die Meldung des Servers. */
class ApiFehler(val status: Int, val text: String) : IOException("HTTP $status: $text")

/** Die Anmeldung gilt nicht mehr (Refresh-Token abgelaufen oder widerrufen). */
class AnmeldungAbgelaufen : IOException("Anmeldung abgelaufen")

/** http:// zu einem Server ausserhalb des Heimnetzes. */
class KlartextAusserhalb : IOException("http:// nur im Heimnetz")

/**
 * Client fuer die Gramps Web API (3.x).
 *
 * Anmeldung mit JWT: `/token/` liefert Access-Token (15 Minuten) und Refresh-Token. Gespeichert wird nur das
 * Refresh-Token, nie das Passwort. Laeuft das Access-Token ab (HTTP 401), holt der Client einmal ein neues und
 * wiederholt die Anfrage. Derselbe OkHttp-Client ([http]) laedt auch die Bilder: Er haengt das Token an jede Anfrage
 * an den eigenen Server, sonst an nichts.
 */
class GwClient(private val ablage: Ablage, val userAgent: String) {

    @Volatile private var access: String? = null

    var basisUrl: String
        get() = ablage.getString(K_BASIS, "").orEmpty()
        private set(value) = ablage.putString(K_BASIS, value)

    var benutzername: String
        get() = ablage.getString(K_BENUTZER, "").orEmpty()
        private set(value) = ablage.putString(K_BENUTZER, value)

    private var refresh: String?
        get() = ablage.getString(K_REFRESH, null)
        set(value) = ablage.putString(K_REFRESH, value)

    /** Gibt es eine gespeicherte Anmeldung, mit der die App ohne Passwort starten kann? */
    val hatAnmeldung: Boolean get() = basisUrl.isNotEmpty() && !refresh.isNullOrEmpty()

    private val eigenerHost: String? get() = basisUrl.toHttpUrlOrNull()?.host

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain -> mitToken(chain) })
        .build()

    private fun mitToken(chain: Interceptor.Chain): Response {
        val anfrage = chain.request()
        if (anfrage.url.scheme == "http" && !Heimnetz.hostAufgeloest(anfrage.url.host)) throw KlartextAusserhalb()
        val eigen = anfrage.url.host == eigenerHost
        val ohneToken = anfrage.header(KEIN_TOKEN) != null
        val gebaut = anfrage.newBuilder().removeHeader(KEIN_TOKEN).header("User-Agent", userAgent).apply {
            if (eigen && !ohneToken) access?.let { header("Authorization", "Bearer $it") }
        }.build()
        val antwort = chain.proceed(gebaut)
        if (antwort.code != 401 || !eigen || ohneToken || refresh.isNullOrEmpty()) return antwort
        // Access-Token abgelaufen: einmal erneuern und wiederholen.
        antwort.close()
        erneuernBlockierend()
        return chain.proceed(gebaut.newBuilder().header("Authorization", "Bearer $access").build())
    }

    @Synchronized
    private fun erneuernBlockierend() {
        val r = refresh ?: throw AnmeldungAbgelaufen()
        val anfrage = Request.Builder().url(url("/token/refresh/"))
            .header("Authorization", "Bearer $r").header(KEIN_TOKEN, "1")
            .post(ByteArray(0).toRequestBody(null)).build()
        http.newCall(anfrage).execute().use { a ->
            if (!a.isSuccessful) {
                refresh = null
                throw AnmeldungAbgelaufen()
            }
            access = GrampsJson.decodeFromString(Token.serializer(), a.body!!.string()).access
        }
    }

    // ── Anmeldung ────────────────────────────────────────────────────

    suspend fun anmelden(adresse: String, name: String, passwort: String) {
        val basis = normalisieren(adresse)
        basisUrl = basis
        val rumpf = buildJsonObject { put("username", name); put("password", passwort) }.toString()
        val token = senden(Request.Builder().url(url("/token/")).header(KEIN_TOKEN, "1")
            .post(rumpf.toRequestBody(JSON_TYP)).build(), Token.serializer())
        access = token.access
        refresh = token.refresh
        benutzername = name
    }

    /** Mit dem gespeicherten Refresh-Token ein frisches Access-Token holen (App-Start). */
    suspend fun fortsetzen() = withContext(Dispatchers.IO) { erneuernBlockierend() }

    fun abmelden() {
        access = null
        refresh = null
    }

    // ── Lesen ────────────────────────────────────────────────────────

    suspend fun metadaten(): Metadaten = holen("/metadata/", Metadaten.serializer())

    suspend fun ich(): Benutzer = holen("/users/-/", Benutzer.serializer())

    suspend fun baeume(): List<Baum> = holen("/trees/", ListSerializer(Baum.serializer()))

    /** Einstellungen des eigenen Baums, z. B. `frontend.primaryColor`; leer, wenn der Betreiber nichts gesetzt hat. */
    suspend fun baumEinstellungen(): Map<String, String> =
        holen("/trees/-/config", JsonObject.serializer()).mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }.toMap()

    /** Personenliste fuer Auswahl und Suche, nach Namen sortiert. Mit [suche] ueber die Regel `SearchName`. */
    suspend fun personen(suche: String, seite: Int, groesse: Int = 50): Seite<GrampsPerson> {
        val p = mutableMapOf(
            "page" to "$seite", "pagesize" to "$groesse", "sort" to "name", "profile" to "self", "locale" to sprache,
            "keys" to LISTE_FELDER,
        )
        if (suche.isNotBlank()) p["rules"] = regel("SearchName", suche.trim())
        val (gesamt, liste) = sendenMitKopf(Request.Builder().url(url("/people/", p)).get().build(),
            ListSerializer(GrampsPerson.serializer()))
        return Seite(liste, seite, groesse, gesamt ?: liste.size)
    }

    suspend fun person(handle: String): GrampsPerson =
        holen("/people/$handle", GrampsPerson.serializer(), mapOf("profile" to "all", "extend" to "all", "locale" to sprache))

    /** Vorfahren samt Mittelperson ueber [generationen] Generationen, jede mit ihrer Elternfamilie. */
    suspend fun vorfahren(grampsId: String, generationen: Int): List<GrampsPerson> = holen(
        "/people/", ListSerializer(GrampsPerson.serializer()), mapOf(
            "rules" to regel("IsLessThanNthGenerationAncestorOf", grampsId, "${generationen + 1}"),
            "profile" to "self", "extend" to "primary_parent_family", "keys" to "$LISTE_FELDER,extended", "locale" to sprache,
        ),
    )

    /** Nachkommen ohne die Mittelperson ueber [generationen] Generationen, jede mit ihren Partnerfamilien. */
    suspend fun nachkommen(grampsId: String, generationen: Int): List<GrampsPerson> = holen(
        "/people/", ListSerializer(GrampsPerson.serializer()), mapOf(
            "rules" to regel("IsLessThanNthGenerationDescendantOf", grampsId, "${generationen + 1}"),
            "profile" to "self", "extend" to "family_list", "keys" to "$LISTE_FELDER,extended", "locale" to sprache,
        ),
    )

    /** Mehrere Personen nach Handle in einer Abfrage (Abfragesprache von Gramps Web). */
    suspend fun personenNachHandle(handles: Collection<String>, mitFamilien: Boolean = false): List<GrampsPerson> {
        if (handles.isEmpty()) return emptyList()
        val ergebnis = mutableListOf<GrampsPerson>()
        for (teil in handles.distinct().chunked(40)) {
            val p = mutableMapOf(
                "gql" to teil.joinToString(" or ") { "handle = \"$it\"" },
                "profile" to "self", "keys" to if (mitFamilien) "$LISTE_FELDER,extended" else LISTE_FELDER, "locale" to sprache,
                "pagesize" to "${teil.size}",
            )
            if (mitFamilien) p["extend"] = "family_list"
            ergebnis += holen("/people/", ListSerializer(GrampsPerson.serializer()), p)
        }
        return ergebnis
    }

    suspend fun personNachId(grampsId: String): GrampsPerson? = holen(
        "/people/", ListSerializer(GrampsPerson.serializer()),
        mapOf("gramps_id" to grampsId, "profile" to "self", "keys" to LISTE_FELDER, "locale" to sprache),
    ).firstOrNull()

    suspend fun verwandtschaft(von: String, zu: String): Verwandtschaft =
        holen("/relations/$von/$zu", Verwandtschaft.serializer(), mapOf("locale" to sprache))

    /** Vorschaubild eines Mediums; der Bildlader haengt das Token an. Mit [rect] der Ausschnitt (z. B. ein Gesicht). */
    fun bildUrl(medium: String, rect: List<Int>?, groesse: Int = 200): String {
        val ganz = rect == null || rect.size != 4 || rect == listOf(0, 0, 100, 100)
        val pfad = if (ganz) "/media/$medium/thumbnail/$groesse"
        else "/media/$medium/cropped/${rect!![0]}/${rect[1]}/${rect[2]}/${rect[3]}/thumbnail/$groesse"
        return url(pfad).newBuilder().addQueryParameter("square", "true").build().toString()
    }

    /** Adresse der Person in Gramps Web (zum Teilen und fuer "Im Browser oeffnen"). */
    fun webUrl(grampsId: String): String = "$basisUrl/person/$grampsId"

    // ── Technik ──────────────────────────────────────────────────────

    /** Sprache der aufbereiteten Texte (Ereignistypen, Verwandtschaft, Alter). */
    var sprache: String = "de"

    private fun url(pfad: String, parameter: Map<String, String> = emptyMap()): HttpUrl {
        val basis = basisUrl.toHttpUrlOrNull() ?: throw IOException("Keine Serveradresse")
        return basis.newBuilder().addPathSegments("api" + pfad.trimEnd('/')).apply {
            if (pfad.endsWith("/")) addPathSegment("")
            parameter.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
    }

    private suspend fun <T> holen(pfad: String, s: KSerializer<T>, parameter: Map<String, String> = emptyMap()): T =
        senden(Request.Builder().url(url(pfad, parameter)).get().build(), s)

    private suspend fun <T> senden(anfrage: Request, s: KSerializer<T>): T = sendenMitKopf(anfrage, s).second

    private suspend fun <T> sendenMitKopf(anfrage: Request, s: KSerializer<T>): Pair<Int?, T> = withContext(Dispatchers.IO) {
        http.newCall(anfrage.newBuilder().header("Accept", "application/json").build()).execute().use { a ->
            val text = a.body?.string().orEmpty()
            if (!a.isSuccessful) throw ApiFehler(a.code, meldung(text))
            a.header("X-Total-Count")?.toIntOrNull() to GrampsJson.decodeFromString(s, text)
        }
    }

    private fun meldung(text: String): String = runCatching {
        val o = Json.parseToJsonElement(text) as JsonObject
        ((o["error"] as? JsonObject)?.get("message") as? JsonPrimitive)?.content
            ?: (o["message"] as? JsonPrimitive)?.content ?: text
    }.getOrDefault(text).take(300)

    companion object {
        private const val K_BASIS = "basisUrl"
        private const val K_BENUTZER = "benutzer"
        private const val K_REFRESH = "refreshToken"
        /** Interner Kopf: diese Anfrage ohne Access-Token senden (Anmeldung, Erneuern). Geht nie ans Netz. */
        private const val KEIN_TOKEN = "X-Gwand-Ohne-Token"
        private val JSON_TYP = "application/json".toMediaType()
        const val LISTE_FELDER = "handle,gramps_id,gender,private,media_list,parent_family_list,family_list,change,profile"

        /** Eine einzelne Filterregel als `rules`-Parameter. */
        fun regel(name: String, vararg werte: String): String = buildJsonObject {
            put("rules", JsonArray(listOf(buildJsonObject {
                put("name", name)
                put("values", JsonArray(werte.map { JsonPrimitive(it) }))
            })))
        }.toString()

        /** "192.168.1.5:5000/" -> "http://…" im Heimnetz, sonst https://; ohne Pfad am Ende. */
        fun normalisieren(eingabe: String): String {
            var u = eingabe.trim().trimEnd('/')
            if (u.isEmpty()) return ""
            if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) {
                val host = u.substringBefore('/').substringBefore(':')
                u = (if (Heimnetz.host(host)) "http://" else "https://") + u
            }
            return u.substringBefore("/api").trimEnd('/')
        }
    }
}
