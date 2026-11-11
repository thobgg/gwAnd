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

/** Der Datensatz wurde seit dem Laden von jemand anderem geaendert; gespeichert wurde nichts. */
class Konflikt : IOException("Inzwischen geaendert")

/** Ein vom Server angelegtes Objekt: Handle, Klasse ("Person", "Event" …) und vergebene Gramps-ID. */
data class Angelegt(val handle: String, val klasse: String, val grampsId: String)

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
class GwClient(private val ablage: Ablage, val userAgent: String, private val cacheOrdner: java.io.File? = null) {

    /**
     * Offline-Lesecache: jede erfolgreiche Leseabfrage landet als Datei im privaten Cache der App, je Server und Konto.
     * Ohne Netz liefert [sendenMitKopf] die zuletzt geladene Antwort und meldet ueber [offline], dass es Altstand ist.
     * Schreiben geht ohne Netz nicht; beim Abmelden wird der Cache geloescht (er enthaelt Daten lebender Personen).
     */
    var offline: (Boolean) -> Unit = {}

    /** Nur fuer Tests: wirkt wie ein fehlendes Netz. */
    internal var netzGesperrt = false

    private fun cacheDatei(adresse: HttpUrl): java.io.File? {
        val ordner = cacheOrdner ?: return null
        val schluessel = "$benutzername|" + adresse.newBuilder().removeAllQueryParameters("jwt").build().toString()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(schluessel.toByteArray()).joinToString("") { "%02x".format(it) }
        return java.io.File(ordner, "api/${hash.take(2)}/$hash")
    }

    private fun cacheSchreiben(adresse: HttpUrl, gesamt: Int?, text: String) {
        val f = cacheDatei(adresse) ?: return
        runCatching {
            f.parentFile?.mkdirs()
            f.writeText("${gesamt ?: -1}\n$text")
            if (Math.random() < 0.02) cacheAufraeumen()
        }
    }

    private fun cacheLesen(adresse: HttpUrl): Pair<Int?, String>? {
        val f = cacheDatei(adresse)?.takeIf { it.exists() } ?: return null
        val inhalt = runCatching { f.readText() }.getOrNull() ?: return null
        val gesamt = inhalt.substringBefore('\n').toIntOrNull()?.takeIf { it >= 0 }
        return gesamt to inhalt.substringAfter('\n')
    }

    /** Hoechstens 100 MB: die aeltesten Eintraege gehen zuerst. */
    private fun cacheAufraeumen() {
        val dateien = java.io.File(cacheOrdner ?: return, "api").walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        var summe = dateien.sumOf { it.length() }
        for (f in dateien) { if (summe <= 100L * 1024 * 1024) break; summe -= f.length(); f.delete() }
    }

    fun cacheLeeren() { cacheOrdner?.let { java.io.File(it, "api").deleteRecursively() } }

    @Volatile private var access: String? = null

    var basisUrl: String
        get() = ablage.getString(K_BASIS, "").orEmpty()
        private set(value) = ablage.putString(K_BASIS, value)

    var benutzername: String
        get() = ablage.getString(K_BENUTZER, "").orEmpty()
        private set(value) = ablage.putString(K_BENUTZER, value)

    /** Zuletzt ueber OIDC angemeldet: der Benutzername stammt dann vom Anbieter und gehoert nicht ins Anmeldefeld. */
    var perOidc: Boolean
        get() = ablage.getBoolean(K_OIDC, false)
        private set(value) = ablage.putBoolean(K_OIDC, value)

    /** Vorschlag fuer das Feld Benutzername der Anmeldemaske. */
    val namensvorschlag: String get() = if (perOidc) "" else benutzername

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
        if (netzGesperrt) throw IOException("Netz gesperrt (Test)")
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
        perOidc = false
    }

    /**
     * Anbieter fuer die Anmeldung ueber OIDC. Server ohne OIDC (oder vor API 3.4) liefern `enabled = false` bzw. 404;
     * beides heisst: nur Passwort.
     */
    suspend fun oidcKonfig(adresse: String): OidcKonfig {
        val basis = normalisieren(adresse).toHttpUrlOrNull() ?: return OidcKonfig()
        val u = basis.newBuilder().addPathSegments("api/oidc/config/").build()
        return try {
            senden(Request.Builder().url(u).header(KEIN_TOKEN, "1").get().build(), OidcKonfig.serializer())
        } catch (e: ApiFehler) {
            OidcKonfig()
        }
    }

    /** Adresse, unter der die Anmeldung bei [anbieter] beginnt (die Seite des Anbieters oeffnet sich danach). */
    fun oidcStartUrl(adresse: String, anbieter: OidcAnbieter): String =
        anbieter.loginUrl.takeIf { it.startsWith("http") }
            ?: (normalisieren(adresse) + "/api/oidc/login/?provider=" + java.net.URLEncoder.encode(anbieter.id, "UTF-8"))

    /**
     * Nach der Anmeldung beim Anbieter leitet der Server auf `…/oidc/complete#code=…` um. Den Code (einmalig, kurz
     * gueltig) gegen Access- und Refresh-Token tauschen; ab API 3.21.
     */
    suspend fun oidcAnmelden(adresse: String, code: String) {
        basisUrl = normalisieren(adresse)
        val rumpf = buildJsonObject { put("code", code) }.toString()
        val token = senden(Request.Builder().url(url("/oidc/tokens/")).header(KEIN_TOKEN, "1")
            .post(rumpf.toRequestBody(JSON_TYP)).build(), Token.serializer())
        access = token.access
        refresh = token.refresh
        benutzername = ich().name
        perOidc = true
    }

    /** Mit dem gespeicherten Refresh-Token ein frisches Access-Token holen (App-Start). */
    suspend fun fortsetzen() = withContext(Dispatchers.IO) { erneuernBlockierend() }

    fun abmelden() {
        access = null
        refresh = null
        cacheLeeren()
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

    /** Familien in aufbereiteter Form (Heirat, Eltern, Kinder) nach Handle. */
    suspend fun familienProfile(handles: Collection<String>): List<FamilienProfil> {
        if (handles.isEmpty()) return emptyList()
        return handles.distinct().chunked(40).flatMap { teil ->
            holen("/families/", ListSerializer(Familienzeile.serializer()), mapOf(
                "gql" to teil.joinToString(" or ") { "handle = \"$it\"" }, "profile" to "self", "keys" to "handle,profile", "locale" to sprache,
            )).mapNotNull { it.profile }
        }
    }

    /** Ereignisse nach Handle samt ihren Zitaten (fuer den Reiter Quellen im Profil). */
    suspend fun ereignisZitate(handles: Collection<String>): List<String> {
        if (handles.isEmpty()) return emptyList()
        return handles.distinct().chunked(40).flatMap { teil ->
            holen("/events/", ListSerializer(Ereigniszitate.serializer()), mapOf(
                "gql" to teil.joinToString(" or ") { "handle = \"$it\"" }, "keys" to "handle,citation_list",
            )).flatMap { it.zitate }
        }
    }

    suspend fun zeitleiste(handle: String): List<ZeitleistenEintrag> =
        holen("/people/$handle/timeline", ListSerializer(ZeitleistenEintrag.serializer()), mapOf("locale" to sprache))

    /** Ereignisse an den Tagen [tage] (Monat to Tag) mit Teilnehmern, fuer die Jahrestage. */
    suspend fun ereignisseAnTagen(tage: List<Pair<Int, Int>>): List<Ereignis> {
        if (tage.isEmpty()) return emptyList()
        val gql = tage.joinToString(" or ") { (m, t) -> "(date.dateval[1] = $m and date.dateval[0] = $t)" }
        return holen("/events/", ListSerializer(Ereignis.serializer()), mapOf(
            "gql" to gql, "profile" to "all", "keys" to "handle,gramps_id,type,date,private,profile", "locale" to sprache,
        ))
    }

    suspend fun zuletztGeaendert(anzahl: Int = 10): List<GrampsPerson> = holen(
        "/people/", ListSerializer(GrampsPerson.serializer()),
        mapOf("sort" to "-change", "page" to "1", "pagesize" to "$anzahl", "profile" to "self", "keys" to LISTE_FELDER, "locale" to sprache),
    )

    suspend fun lesezeichen(): List<String> = holen("/bookmarks/people", ListSerializer(kotlinx.serialization.serializer<String>()))

    suspend fun lesezeichenSetzen(handle: String, an: Boolean) = withContext(Dispatchers.IO) {
        val anfrage = Request.Builder().url(url("/bookmarks/people/$handle")).apply {
            if (an) put(ByteArray(0).toRequestBody(null)) else delete()
        }.build()
        http.newCall(anfrage).execute().use { a -> if (!a.isSuccessful) throw ApiFehler(a.code, meldung(a.body?.string().orEmpty())) }
    }

    /** Seitenweise Liste einer Objektart; mit [suche] ueber die Abfragesprache (`feld ~ "text"`). */
    private suspend fun <T> liste(pfad: String, s: KSerializer<T>, seite: Int, groesse: Int, sort: String, suchfeld: String?, suche: String, extra: Map<String, String> = emptyMap()): Seite<T> {
        val p = mutableMapOf("page" to "$seite", "pagesize" to "$groesse", "sort" to sort, "locale" to sprache) + extra
        val mitSuche = if (suchfeld != null && suche.isNotBlank()) p + ("gql" to "$suchfeld ~ \"${suche.replace("\"", "")}\"") else p
        val (gesamt, eintraege) = sendenMitKopf(Request.Builder().url(url(pfad, mitSuche)).get().build(), ListSerializer(s))
        return Seite(eintraege, seite, groesse, gesamt ?: eintraege.size)
    }

    suspend fun medien(seite: Int, groesse: Int = 60): Seite<Medium> =
        liste("/media/", Medium.serializer(), seite, groesse, "-change", null, "", mapOf("keys" to "handle,gramps_id,desc,mime,path,private"))

    suspend fun medium(handle: String): Medium =
        holen("/media/$handle", Medium.serializer(), mapOf("profile" to "all", "locale" to sprache))

    suspend fun orte(suche: String, seite: Int, groesse: Int = 60): Seite<Ort> =
        liste("/places/", Ort.serializer(), seite, groesse, "title", "title", suche, mapOf("profile" to "self"))

    suspend fun ort(handle: String): Ort = holen("/places/$handle", Ort.serializer(), mapOf("profile" to "all", "locale" to sprache))

    suspend fun quellen(suche: String, seite: Int, groesse: Int = 60): Seite<Quelle> =
        liste("/sources/", Quelle.serializer(), seite, groesse, "title", "title", suche)

    suspend fun quelle(handle: String): Quelle =
        holen("/sources/$handle", Quelle.serializer(), mapOf("profile" to "all", "extend" to "all", "locale" to sprache))

    suspend fun archive(suche: String, seite: Int, groesse: Int = 60): Seite<Archiv> =
        liste("/repositories/", Archiv.serializer(), seite, groesse, "name", "name", suche)

    suspend fun archiv(handle: String): Archiv =
        holen("/repositories/$handle", Archiv.serializer(), mapOf("backlinks" to "true", "locale" to sprache))

    suspend fun quellenNachHandle(handles: Collection<String>): List<Quelle> {
        if (handles.isEmpty()) return emptyList()
        return handles.distinct().chunked(40).flatMap { teil ->
            holen("/sources/", ListSerializer(Quelle.serializer()), mapOf("gql" to teil.joinToString(" or ") { "handle = \"$it\"" }, "locale" to sprache))
        }
    }

    suspend fun zitate(handles: Collection<String>): List<Zitat> {
        if (handles.isEmpty()) return emptyList()
        return handles.distinct().chunked(40).flatMap { teil ->
            holen("/citations/", ListSerializer(Zitat.serializer()), mapOf(
                "gql" to teil.joinToString(" or ") { "handle = \"$it\"" }, "profile" to "self", "locale" to sprache,
            ))
        }
    }

    /** Uebersetzung der englischen Grundnamen einer Typenliste (`repository_types`, `place_types` …) in die App-Sprache. */
    suspend fun typen(art: String): Map<String, String> =
        holen("/types/default/$art/map", JsonObject.serializer(), mapOf("locale" to sprache))
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }.toMap()

    /** Volles Bild (Datei) eines Mediums; der Bildlader haengt das Token an. */
    fun dateiUrl(medium: String): String = url("/media/$medium/file").toString()

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

    // ── Schreiben ────────────────────────────────────────────────────
    //
    // Neue Objekte gehen gebuendelt an /objects/ (ein Vorgang; der Server vergibt Gramps-IDs und traegt bei neuen
    // Familien die Verweise in den Personen nach). Aenderungen holen das Objekt frisch, pruefen den Aenderungszeitpunkt
    // gegen den Stand beim Oeffnen des Dialogs und schicken das ganze Objekt zurueck - nur die geaenderten Felder sind
    // anders, alles Uebrige bleibt, wie der Server es geliefert hat.

    /** Ein Objekt in der Form, die PUT zurueckerwartet (ohne profile/extend). [art] ist der Pfad: people, events … */
    suspend fun roh(art: String, handle: String): JsonObject = holen("/$art/$handle", JsonObject.serializer())

    /**
     * Objekt aendern: frisch holen, [stand] (Feld `change` beim Oeffnen) pruefen, [aendern] anwenden, zuruecksenden.
     * @throws Konflikt wenn jemand anderes das Objekt inzwischen geaendert hat
     */
    suspend fun aendern(art: String, handle: String, stand: Long? = null, aendern: (JsonObject) -> JsonObject): JsonObject {
        val alt = roh(art, handle)
        if (stand != null && (alt["change"] as? JsonPrimitive)?.content?.toLongOrNull() != stand) throw Konflikt()
        val neu = aendern(alt)
        if (neu == alt) return alt
        senden(Request.Builder().url(url("/$art/$handle")).put(neu.toString().toRequestBody(JSON_TYP)).build(), kotlinx.serialization.json.JsonElement.serializer())
        return neu
    }

    /** Mehrere neue Objekte in einem Vorgang; Handles vergibt der Client ([neuesHandle]), damit sie sich verweisen koennen. */
    suspend fun anlegen(objekte: List<JsonObject>): List<Angelegt> {
        if (objekte.isEmpty()) return emptyList()
        val antwort = senden(Request.Builder().url(url("/objects/")).post(JsonArray(objekte).toString().toRequestBody(JSON_TYP)).build(), JsonArray.serializer())
        return antwort.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            if ((o["type"] as? JsonPrimitive)?.content != "add") return@mapNotNull null
            val neu = o["new"] as? JsonObject
            Angelegt(
                handle = (o["handle"] as? JsonPrimitive)?.content.orEmpty(),
                klasse = (o["_class"] as? JsonPrimitive)?.content.orEmpty(),
                grampsId = (neu?.get("gramps_id") as? JsonPrimitive)?.content.orEmpty(),
            )
        }
    }

    suspend fun loeschen(art: String, handle: String) {
        senden(Request.Builder().url(url("/$art/$handle")).delete().build(), kotlinx.serialization.json.JsonElement.serializer())
    }

    /** Wer auf ein Objekt verweist (Klasse to Handles). */
    suspend fun verweiseAuf(art: String, handle: String): Map<String, List<String>> {
        val o = holen("/$art/$handle", JsonObject.serializer(), mapOf("backlinks" to "true", "keys" to "backlinks"))
        val b = o["backlinks"] as? JsonObject ?: return emptyMap()
        return b.mapValues { (_, v) -> (v as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content } }
    }

    /** Bild hochladen; der Server legt dafuer ein Medienobjekt an. Ergebnis: dessen Handle. */
    suspend fun hochladen(daten: ByteArray, mime: String): String {
        val antwort = senden(Request.Builder().url(url("/media/")).post(daten.toRequestBody(mime.toMediaType())).build(), JsonArray.serializer())
        return antwort.mapNotNull { it as? JsonObject }.firstOrNull { (it["_class"] as? JsonPrimitive)?.content == "Media" }
            ?.let { (it["handle"] as? JsonPrimitive)?.content } ?: throw ApiFehler(500, "kein Medienobjekt angelegt")
    }

    /** Vom Server erkannte Gesichter eines Bildes: je links, oben, rechts, unten in Prozent. */
    suspend fun gesichter(medium: String): List<List<Double>> =
        holen("/media/$medium/face_detection", ListSerializer(ListSerializer(kotlinx.serialization.serializer<Double>())))

    /** Personen, die auf ein Bild verweisen, mit ihren Medienverweisen (fuer die Ausschnitte). */
    suspend fun personenMitBild(medium: String): List<GrampsPerson> =
        personenNachHandle(verweiseAuf("media", medium)["person"].orEmpty())

    /** Texterkennung eines Bildes starten (Hintergrundaufgabe); Ergebnis: Aufgaben-ID. */
    suspend fun texterkennungStarten(medium: String, sprache: String): String {
        val antwort = senden(Request.Builder().url(url("/media/$medium/ocr", mapOf("lang" to sprache, "format" to "string")))
            .post(ByteArray(0).toRequestBody(null)).build(), JsonObject.serializer())
        return ((antwort["task"] as? JsonObject)?.get("id") as? JsonPrimitive)?.content ?: throw ApiFehler(500, "keine Aufgabe")
    }

    suspend fun aufgabe(id: String): Aufgabe = holen("/tasks/$id", Aufgabe.serializer())

    // ── Verlauf, Rueckgaengig, Zusammenfuehren ───────────────────────

    /** Aenderungsverlauf des Baums, neueste zuerst; Klasse "Reference" sind interne Verweis-Eintraege. */
    suspend fun verlauf(seite: Int, groesse: Int = 30): Seite<Vorgang> {
        val (gesamt, liste) = sendenMitKopf(Request.Builder().url(url("/transactions/history/",
            mapOf("page" to "$seite", "pagesize" to "$groesse", "sort" to "-id"))).get().build(), ListSerializer(Vorgang.serializer()))
        return Seite(liste, seite, groesse, gesamt ?: liste.size)
    }

    /** Verlauf eines Objekts ([klasse] "Person", "Family" …), aelteste zuerst. */
    suspend fun objektVerlauf(klasse: String, handle: String): List<Aenderung> =
        holen("/transactions/history/objects/$klasse/$handle", ListSerializer(Aenderung.serializer()))

    suspend fun rueckgaengigPruefen(vorgang: Int): RueckgaengigPruefung =
        holen("/transactions/history/$vorgang/undo", RueckgaengigPruefung.serializer())

    /** Vorgang zuruecknehmen (Hintergrundaufgabe, ohne Zwang: scheitert bei Konflikten). Ergebnis: Aufgaben-ID oder null, wenn sofort erledigt. */
    suspend fun rueckgaengig(vorgang: Int): String? {
        val antwort = senden(Request.Builder().url(url("/transactions/history/$vorgang/undo")).post(ByteArray(0).toRequestBody(null)).build(),
            kotlinx.serialization.json.JsonElement.serializer())
        return (((antwort as? JsonObject)?.get("task") as? JsonObject)?.get("id") as? JsonPrimitive)?.content
    }

    /** Auf eine Hintergrundaufgabe warten (hoechstens [sekunden]); Ergebnis: die fertige Aufgabe. */
    suspend fun warten(aufgabe: String, sekunden: Int = 120): Aufgabe {
        repeat(sekunden) {
            val a = aufgabe(aufgabe)
            if (a.fertig || a.fehlgeschlagen) return a
            kotlinx.coroutines.delay(1000)
        }
        throw ApiFehler(504, "Aufgabe dauert zu lange")
    }

    /** Zwei Personen zusammenfuehren: [bleibt] uebernimmt die Angaben von [geht], [geht] wird geloescht. */
    suspend fun zusammenfuehren(bleibt: String, geht: String) {
        senden(Request.Builder().url(url("/people/$bleibt/merge/$geht")).post("""{"family_merger":true}""".toRequestBody(JSON_TYP)).build(),
            kotlinx.serialization.json.JsonElement.serializer())
    }

    // ── Verwandtschaft, Export, Berichte, DNA, Chat ──────────────────

    /** Alle Verwandtschaftswege zwischen zwei Personen (Bezeichnung und gemeinsame Vorfahren). */
    suspend fun verwandtschaftsWege(von: String, zu: String): List<VerwandtschaftWeg> =
        holen("/relations/$von/$zu/all", ListSerializer(VerwandtschaftWeg.serializer()), mapOf("locale" to sprache))

    suspend fun berichte(): List<Bericht> = holen("/reports/", ListSerializer(Bericht.serializer()), mapOf("locale" to sprache))

    /** Stammbaum exportieren ([format] z. B. "ged", "ged7", "gramps"); der Server erzeugt die Datei im Hintergrund. */
    suspend fun export(format: String): Datei = dateiAuftrag(url("/exporters/$format/file"), "stammbaum.$format")

    /** Bericht als PDF fuer eine Person (Gramps-ID). */
    suspend fun bericht(id: String, grampsId: String, name: String): Datei {
        val optionen = buildJsonObject { put("off", "pdf"); put("pid", grampsId); put("papers", "A4") }.toString()
        return dateiAuftrag(url("/reports/$id/file", mapOf("options" to optionen, "locale" to sprache)), "$name.pdf")
    }

    /** Auftrag abschicken, auf die Hintergrundaufgabe warten, Datei holen. */
    private suspend fun dateiAuftrag(adresse: HttpUrl, ersatzname: String): Datei {
        val antwort = senden(Request.Builder().url(adresse).post(ByteArray(0).toRequestBody(null)).build(), JsonObject.serializer())
        val aufgabe = ((antwort["task"] as? JsonObject)?.get("id") as? JsonPrimitive)?.content
        val ergebnis = if (aufgabe != null) {
            val a = warten(aufgabe, 300)
            if (!a.fertig) throw ApiFehler(500, a.info.ifBlank { "Aufgabe fehlgeschlagen" })
            GrampsJson.parseToJsonElement(a.ergebnisRoh ?: "{}") as JsonObject
        } else antwort
        val pfad = (ergebnis["url"] as? JsonPrimitive)?.content ?: throw ApiFehler(500, "keine Datei")
        val name = (ergebnis["file_name"] as? JsonPrimitive)?.content ?: ersatzname
        val basis = basisUrl.toHttpUrlOrNull() ?: throw IOException("Keine Serveradresse")
        return withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(basis.newBuilder().encodedPath(pfad.substringBefore('?')).build()).get().build()).execute().use { a ->
                if (!a.isSuccessful) throw ApiFehler(a.code, "Datei")
                Datei(name, a.header("Content-Type")?.substringBefore(';') ?: "application/octet-stream", a.body!!.bytes())
            }
        }
    }

    suspend fun dnaTreffer(person: String): List<DnaTreffer> =
        holen("/people/$person/dna/matches", ListSerializer(DnaTreffer.serializer()), mapOf("locale" to sprache))

    /** Frage an den KI-Assistenten des Servers; [verlauf] als (Rolle, Text). Ergebnis: Antworttext. */
    suspend fun chat(frage: String, verlauf: List<Pair<String, String>>): String {
        val rumpf = buildJsonObject {
            put("query", frage)
            put("history", JsonArray(verlauf.map { (rolle, text) -> buildJsonObject { put("role", rolle); put("message", text) } }))
        }.toString()
        val a = senden(Request.Builder().url(url("/chat/")).post(rumpf.toRequestBody(JSON_TYP)).build(), JsonObject.serializer())
        return (a["response"] as? JsonPrimitive)?.content ?: a.toString()
    }

    suspend fun ereignisTypen(): List<String> = holen("/types/default/event_types", ListSerializer(kotlinx.serialization.serializer<String>()))

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
        val lesen = anfrage.method == "GET"
        try {
            http.newCall(anfrage.newBuilder().header("Accept", "application/json").build()).execute().use { a ->
                val text = a.body?.string().orEmpty()
                if (!a.isSuccessful) throw ApiFehler(a.code, meldung(text))
                val gesamt = a.header("X-Total-Count")?.toIntOrNull()
                val ergebnis = gesamt to GrampsJson.decodeFromString(s, text)
                if (lesen) { cacheSchreiben(anfrage.url, gesamt, text); offline(false) }
                ergebnis
            }
        } catch (e: IOException) {
            // Nur echte Netzfehler fallen auf den Cache zurueck - keine Antworten des Servers, keine abgelaufene Anmeldung.
            if (!lesen || e is ApiFehler || e is AnmeldungAbgelaufen || e is KlartextAusserhalb) throw e
            val (gesamt, text) = cacheLesen(anfrage.url) ?: throw e
            offline(true)
            gesamt to GrampsJson.decodeFromString(s, text)
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
        private const val K_OIDC = "perOidc"
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
