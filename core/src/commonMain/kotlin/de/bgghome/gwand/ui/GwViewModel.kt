package de.bgghome.gwand.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bgghome.gwand.api.AnmeldungAbgelaufen
import de.bgghome.gwand.api.ApiFehler
import de.bgghome.gwand.api.GrampsPerson
import de.bgghome.gwand.api.GwClient
import de.bgghome.gwand.api.KlartextAusserhalb
import de.bgghome.gwand.api.Medium
import de.bgghome.gwand.daten.Anzeige
import de.bgghome.gwand.daten.BaumBau
import de.bgghome.gwand.daten.Geschwister
import de.bgghome.gwand.daten.Jahrestage
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.bookmark_added
import de.bgghome.gwand.res.bookmark_removed
import de.bgghome.gwand.res.error_api
import de.bgghome.gwand.res.error_cleartext
import de.bgghome.gwand.res.error_expired
import de.bgghome.gwand.res.error_login
import de.bgghome.gwand.res.error_network
import de.bgghome.gwand.res.error_version
import de.bgghome.gwand.res.link_not_found
import de.bgghome.gwand.res.no_start_person
import de.bgghome.gwand.res.start_person_saved
import de.bgghome.webtrees.nativ.api.EventJson
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.data.Ablage
import de.bgghome.webtrees.nativ.ui.LinkState
import de.bgghome.webtrees.nativ.ui.ViewerItem
import de.bgghome.webtrees.nativ.ui.ViewerSource
import de.bgghome.webtrees.nativ.ui.ViewerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import java.io.IOException
import java.time.LocalDate

/**
 * Was das ViewModel von der Plattform braucht: den Client, eine Ablage fuer kleine Einstellungen und die zwei
 * Handgriffe, die nur die Plattform kann (Text teilen, Adresse im Browser oeffnen).
 */
class GwPlattform(
    val client: GwClient,
    val einstellungen: Ablage,
    val versionName: String,
    val teilen: (titel: String, text: String) -> Unit = { _, _ -> },
    val oeffnen: (url: String) -> Unit = {},
    /** Taegliche Erinnerung an Jahrestage (Android: WorkManager und Benachrichtigung); der Desktop kann das nicht. */
    val kannErinnern: Boolean = false,
    val erinnerungSetzen: (Boolean) -> Unit = {},
    /** Datei (Export, Bericht) oeffnen oder mit anderen Apps teilen. */
    val dateiOeffnen: (datei: de.bgghome.gwand.api.Datei, teilen: Boolean) -> Unit = { _, _ -> },
)

/**
 * Das ViewModel von gwAnd. Haelt den Zustand und spricht ueber [GwClient] mit Gramps Web. Antworten kommen nur an,
 * wenn sie noch zum Zustand passen (eine neue Suche verwirft die alte, ein neuer Baum den alten, ein anderes Profil
 * das vorige).
 */
class GwViewModel(internal val plattform: GwPlattform) : ViewModel() {

    internal val client = plattform.client
    private val ablage = plattform.einstellungen
    internal val anzeige = Anzeige(
        bild = { p -> p.medien.firstOrNull { !it.private }?.let { client.bildUrl(it.ref, it.rect) } },
        web = client::webUrl,
    )
    private val bau = BaumBau(anzeige)

    internal val zustand = MutableStateFlow(
        Zustand(
            adresse = client.basisUrl, benutzername = client.benutzername, version = plattform.versionName,
            zeigeGeschwister = ablage.getBoolean(K_GESCHWISTER, true), zeigeCousins = ablage.getBoolean(K_COUSINS, true),
            kannErinnern = plattform.kannErinnern, erinnerung = ablage.getBoolean(K_ERINNERUNG, false),
        ),
    )
    val state: StateFlow<Zustand> = zustand

    /** Netz fuer alles, was ein Ladevorgang nicht selbst abfaengt: eine Meldung statt eines Absturzes. */
    internal val sicher = CoroutineExceptionHandler { _, e -> zustand.update { it.copy(meldung = e.message ?: e::class.simpleName) } }

    private var baumJob: Job? = null
    private var sucheJob: Job? = null
    private var profilJob: Job? = null
    private var nachschlagJob: Job? = null
    /** Ein geteilter Link, der vor der Anmeldung kam: nach dem Laden oeffnen. */
    private var wartenderLink: String? = null

    init {
        start()
    }

    // ── Sitzung ──────────────────────────────────────────────────────

    private fun start() {
        if (!client.hatAnmeldung) {
            zustand.update { it.copy(bildschirm = Bildschirm.Anmeldung) }
            return
        }
        viewModelScope.launch(sicher) {
            try {
                client.fortsetzen()
                nachAnmeldung()
            } catch (e: Exception) {
                zustand.update { it.copy(bildschirm = Bildschirm.Anmeldung, fehler = text(e)) }
            }
        }
    }

    fun anmelden(adresse: String, name: String, passwort: String) {
        if (zustand.value.beschaeftigt) return
        zustand.update { it.copy(beschaeftigt = true, fehler = null, adresse = adresse, benutzername = name) }
        viewModelScope.launch(sicher) {
            try {
                client.anmelden(adresse, name.trim(), passwort)
                nachAnmeldung()
            } catch (e: Exception) {
                val grund = text(e)
                zustand.update { it.copy(beschaeftigt = false, fehler = getString(Res.string.error_login, grund)) }
            }
        }
    }

    private suspend fun nachAnmeldung() {
        val meta = client.metadaten()
        if (!meta.api.version.startsWith("3.")) {
            client.abmelden()
            zustand.update { it.copy(bildschirm = Bildschirm.Anmeldung, beschaeftigt = false, fehler = getString(Res.string.error_version, meta.api.version)) }
            return
        }
        val konto = runCatching { client.ich() }.getOrNull()
        val farben = runCatching { Baumfarben.aus(client.baumEinstellungen()) }.getOrDefault(Baumfarben())
        val start = ablage.getString(schluessel("start"), null) ?: meta.startperson
        zustand.update {
            it.copy(
                bildschirm = Bildschirm.Haupt, beschaeftigt = false, fehler = null, konto = konto,
                baumName = meta.database.name, farben = farben, adresse = client.basisUrl, startperson = start,
                chatMoeglich = meta.server.chat,
            )
        }
        viewModelScope.launch(sicher) { typenLaden() }
        startseiteLaden()
        if (start != null) baumLaden(start) else {
            zustand.update { it.copy(meldung = getString(Res.string.no_start_person), bereich = Bereich.Personen) }
            suchen("")
        }
        wartenderLink?.let { wartenderLink = null; linkOeffnen(it) }
    }

    fun abmelden() {
        client.abmelden()
        baumJob?.cancel(); profilJob?.cancel()
        zustand.update { Zustand(bildschirm = Bildschirm.Anmeldung, adresse = client.basisUrl, benutzername = client.benutzername, version = plattform.versionName) }
    }

    fun bereich(b: Bereich) {
        zustand.update { it.copy(bereich = b, ausgewaehlt = null) }
        when (b) {
            Bereich.Start -> startseiteLaden()
            Bereich.Personen -> if (zustand.value.treffer.isEmpty()) suchen(zustand.value.suche)
            Bereich.Fotos -> if (zustand.value.fotos.isEmpty()) weitereFotos()
            Bereich.Mehr -> if (zustand.value.orte.isEmpty() && zustand.value.quellenListe.isEmpty() && zustand.value.archive.isEmpty()) nachschlagen(zustand.value.nachschlagart, "")
            Bereich.Baum -> {}
        }
    }

    /** Zurueck: Betrachter, Detail, Profil, dann zum Baum. false = nichts mehr zu schliessen (App darf gehen). */
    fun zurueck(): Boolean {
        val s = zustand.value
        when {
            s.chat != null -> zustand.update { it.copy(chat = null) }
            s.gesichter != null -> zustand.update { it.copy(gesichter = null) }
            s.betrachter != null -> zustand.update { it.copy(betrachter = null) }
            s.detail != null -> zustand.update { it.copy(detail = null) }
            s.profil != null -> profilZurueck()
            s.ausgewaehlt != null -> zustand.update { it.copy(ausgewaehlt = null) }
            s.bereich != Bereich.Baum -> zustand.update { it.copy(bereich = Bereich.Baum) }
            else -> return false
        }
        return true
    }

    fun kannZurueck(): Boolean = zustand.value.let { it.betrachter != null || it.detail != null || it.profil != null || it.ausgewaehlt != null || it.bereich != Bereich.Baum }

    // ── Baum ─────────────────────────────────────────────────────────

    /** Den Baum um [handle] laden: Vorfahren, Nachkommen, Partner, Heiraten und Geschwister. */
    fun baumLaden(handle: String) {
        baumJob?.cancel()
        val s = zustand.value
        zustand.update { it.copy(wurzel = handle, ahnentafel = null, nachkommen = null, geschwister = emptyMap()) }
        baumJob = viewModelScope.launch(sicher) {
            try {
                val wurzel = client.personenNachHandle(listOf(handle), mitFamilien = true).firstOrNull()
                    ?: throw ApiFehler(404, handle)
                val (vorfahren, tafel, baum) = coroutineScope {
                    val vorfahrenA = async { client.vorfahren(wurzel.grampsId, s.ahnenGenerationen) }
                    val nachkommenA = async { client.nachkommen(wurzel.grampsId, s.nachkommenGenerationen) }
                    val unten = nachkommenA.await()
                    val alle = unten + wurzel
                    val partnerA = async { client.personenNachHandle(BaumBau.fehlendePartner(alle)) }
                    val heiratenA = async { heiraten(BaumBau.familien(alle)) }
                    val vorfahren = vorfahrenA.await()
                    Triple(vorfahren, bau.ahnentafel(handle, vorfahren), bau.nachkommen(wurzel, unten, partnerA.await(), heiratenA.await()))
                }
                zustand.update { if (it.wurzel == handle) it.copy(ahnentafel = tafel, nachkommen = baum) else it }
                if (s.zeigeGeschwister) {
                    val geschwister = geschwisterLaden(tafel, vorfahren, s.zeigeCousins)
                    zustand.update { if (it.wurzel == handle) it.copy(geschwister = geschwister) else it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fehlschlag(e)
            }
        }
    }

    private suspend fun heiraten(familien: Set<String>): Map<String, EventJson> =
        runCatching { client.familienProfile(familien) }.getOrDefault(emptyList())
            .filter { !it.marriage.leer }.associate { it.handle to Anzeige.ereignis(it.marriage)!! }

    private suspend fun geschwisterLaden(tafel: de.bgghome.webtrees.nativ.api.Pedigree, vorfahren: List<GrampsPerson>, cousins: Boolean) =
        runCatching {
            val handles = Geschwister.geschwisterHandles(tafel, vorfahren)
            if (handles.isEmpty()) return@runCatching emptyMap()
            val personen = mutableMapOf<String, GrampsPerson>()
            client.personenNachHandle(handles.values.flatten(), mitFamilien = true).forEach { personen[it.handle] = it }
            val zweite = client.personenNachHandle(Geschwister.zweiterSchritt(tafel, handles, personen, cousins), mitFamilien = true)
            zweite.forEach { personen[it.handle] = it }
            if (cousins) client.personenNachHandle(Geschwister.dritterSchritt(zweite, personen.keys)).forEach { personen[it.handle] = it }
            Geschwister.bauen(tafel, handles, personen, anzeige, cousins)
        }.getOrDefault(emptyMap())

    fun neuLaden() { zustand.value.wurzel?.let(::baumLaden) }

    fun waehlen(person: Person) = zustand.update { it.copy(ausgewaehlt = person) }

    fun auswahlSchliessen() = zustand.update { it.copy(ausgewaehlt = null) }

    fun inDieMitte(person: Person) = inDieMitte(person.xref)

    fun inDieMitte(handle: String) {
        zustand.update { it.copy(ausgewaehlt = null, profil = null, profilVerlauf = emptyList(), bereich = Bereich.Baum) }
        baumLaden(handle)
    }

    /** Weiter nach oben: drei Generationen mehr (hoechstens acht). */
    fun mehrVorfahren() {
        zustand.update { it.copy(ahnenGenerationen = minOf(8, it.ahnenGenerationen + 3)) }
        neuLaden()
    }

    /** Weiter nach unten: zwei Generationen mehr (hoechstens sechs). */
    fun mehrNachkommen() {
        zustand.update { it.copy(nachkommenGenerationen = minOf(6, it.nachkommenGenerationen + 2)) }
        neuLaden()
    }

    fun zeigeGeschwister(an: Boolean) {
        ablage.putBoolean(K_GESCHWISTER, an)
        zustand.update { it.copy(zeigeGeschwister = an) }
        neuLaden()
    }

    fun zeigeCousins(an: Boolean) {
        ablage.putBoolean(K_COUSINS, an)
        zustand.update { it.copy(zeigeCousins = an) }
        neuLaden()
    }

    fun erinnerung(an: Boolean) {
        ablage.putBoolean(K_ERINNERUNG, an)
        zustand.update { it.copy(erinnerung = an) }
        plattform.erinnerungSetzen(an)
    }

    fun alsStartperson(person: Person) {
        ablage.putString(schluessel("start"), person.xref)
        zustand.update { it.copy(ausgewaehlt = null, startperson = person.xref) }
        viewModelScope.launch(sicher) { meldung(getString(Res.string.start_person_saved)) }
    }

    // ── Profil ───────────────────────────────────────────────────────

    /** Profil oeffnen; ein schon offenes kommt in den Verlauf, damit Zurueck dorthin fuehrt. */
    fun profilOeffnen(handle: String) {
        val vorher = zustand.value.profil?.handle
        zustand.update {
            it.copy(
                ausgewaehlt = null, detail = null,
                profil = Profil(handle),
                profilVerlauf = if (vorher != null && vorher != handle) it.profilVerlauf + vorher else it.profilVerlauf,
            )
        }
        profilLaden(handle)
    }

    private fun profilZurueck() {
        val verlauf = zustand.value.profilVerlauf
        if (verlauf.isEmpty()) {
            zustand.update { it.copy(profil = null) }
        } else {
            zustand.update { it.copy(profil = Profil(verlauf.last()), profilVerlauf = verlauf.dropLast(1)) }
            profilLaden(verlauf.last())
        }
    }

    fun profilSchliessen() = zustand.update { it.copy(profil = null, profilVerlauf = emptyList()) }

    internal fun profilLaden(handle: String) {
        profilJob?.cancel()
        profilJob = viewModelScope.launch(sicher) {
            try {
                val startperson = zustand.value.startperson
                val geladen = coroutineScope {
                    val personA = async { client.person(handle) }
                    val zeitleisteA = async { client.zeitleiste(handle) }
                    val verwandtschaftA = async {
                        if (startperson == null || startperson == handle) "" else runCatching { client.verwandtschaft(startperson, handle).text }.getOrDefault("")
                    }
                    val gemerktA = async { runCatching { handle in client.lesezeichen() }.getOrDefault(false) }
                    val person = personA.await()
                    Profil(
                        handle = handle, person = person, anzeige = anzeige.person(person), zeitleiste = mitUndatierten(person, zeitleisteA.await()),
                        verwandtschaft = verwandtschaftA.await(), gemerkt = gemerktA.await(), laedt = false,
                    )
                }
                val angezeigt = geladen.anzeige!!
                zustand.update { s ->
                    if (s.profil?.handle != handle) s else s.copy(profil = geladen.copy(reiter = s.profil.reiter))
                }
                gesehen(angezeigt)
                angehoerigeLaden(geladen.person!!)
                if (zustand.value.profil?.reiter == REITER_QUELLEN) quellenLaden(handle)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                zustand.update { s -> if (s.profil?.handle == handle) s.copy(profil = s.profil.copy(laedt = false)) else s }
                fehlschlag(e)
            }
        }
    }

    /** Alle Personen der Familienliste einmal vollstaendig holen, damit sie ihre Bilder zeigen. */
    private suspend fun angehoerigeLaden(person: GrampsPerson) {
        val pr = person.profile ?: return
        val familien = listOfNotNull(pr.elternfamilie) + pr.weitereElternfamilien + pr.families
        val handles = familien.flatMap { f -> listOfNotNull(f.father, f.mother) + f.children }.filter { it.vorhanden }.map { it.handle } +
            person.personenverweise.map { it.ref }
        val geladen = runCatching { client.personenNachHandle(handles.toSet() - person.handle) }.getOrNull() ?: return
        val karte = geladen.associate { it.handle to anzeige.person(it) }
        zustand.update { s -> if (s.profil?.handle == person.handle) s.copy(profil = s.profil.copy(angehoerige = karte)) else s }
    }

    /**
     * Die Zeitleiste des Servers laesst Ereignisse ohne Datum weg; im Lebenslauf muessen sie trotzdem stehen, sonst
     * waeren sie weder sichtbar noch bearbeitbar. Sie kommen ans Ende, mit Typ und Ort aus der aufbereiteten Person
     * (deren Ereignisse stehen in derselben Reihenfolge wie die Ereignisverweise).
     */
    private fun mitUndatierten(person: GrampsPerson, zeitleiste: List<de.bgghome.gwand.api.ZeitleistenEintrag>): List<de.bgghome.gwand.api.ZeitleistenEintrag> {
        val profilEreignisse = person.profile?.events.orEmpty()
        if (profilEreignisse.size != person.ereignisse.size) return zeitleiste
        val vorhanden = zeitleiste.map { it.handle }.toSet()
        val fehlend = person.ereignisse.mapIndexedNotNull { i, ref ->
            if (ref.ref in vorhanden || ref.private) return@mapIndexedNotNull null
            val e = profilEreignisse[i]
            de.bgghome.gwand.api.ZeitleistenEintrag(
                handle = ref.ref, label = e.type, role = e.role, date = e.date,
                person = de.bgghome.gwand.api.ZeitleistenPerson(relationship = "self"),
                place = e.ortsname.ifBlank { e.place }.takeIf { it.isNotBlank() }?.let { de.bgghome.gwand.api.OrtKurz(name = it) },
            )
        }
        return zeitleiste + fehlend
    }

    fun profilReiter(reiter: Int) {
        val p = zustand.value.profil ?: return
        zustand.update { it.copy(profil = p.copy(reiter = reiter)) }
        if (reiter == REITER_QUELLEN && p.zitate == null && p.person != null) quellenLaden(p.handle)
    }

    /** Zitate der Person und aller ihrer Ereignisse, dann die Quellen dazu. */
    private fun quellenLaden(handle: String) {
        val person = zustand.value.profil?.person ?: return
        viewModelScope.launch(sicher) {
            try {
                val ereignisZitate = client.ereignisZitate(person.ereignisse.map { it.ref })
                val alle = (person.extended?.citations.orEmpty().map { it.handle } + ereignisZitate).distinct()
                val zitate = client.zitate(alle)
                val quellen = client.quellenNachHandle(zitate.map { it.quelle }.filter { it.isNotEmpty() }).associateBy { it.handle }
                zustand.update { s ->
                    if (s.profil?.handle != handle) s else s.copy(profil = s.profil.copy(zitate = zitate, quellen = quellen))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                zustand.update { s -> if (s.profil?.handle == handle) s.copy(profil = s.profil.copy(zitate = emptyList())) else s }
                fehlschlag(e)
            }
        }
    }

    fun merken(an: Boolean) {
        val p = zustand.value.profil ?: return
        viewModelScope.launch(sicher) {
            try {
                client.lesezeichenSetzen(p.handle, an)
                zustand.update { s -> if (s.profil?.handle == p.handle) s.copy(profil = s.profil.copy(gemerkt = an)) else s }
                meldung(getString(if (an) Res.string.bookmark_added else Res.string.bookmark_removed))
                lesezeichenLaden()
            } catch (e: Exception) {
                fehlschlag(e)
            }
        }
    }

    fun teilen() {
        val p = zustand.value.profil?.anzeige ?: return
        val gid = zustand.value.profil?.person?.grampsId ?: return
        plattform.teilen(p.name, "${p.name} ${p.lifespan}".trim() + "\n" + client.webUrl(gid))
    }

    fun imBrowser() {
        val gid = zustand.value.profil?.person?.grampsId ?: return
        plattform.oeffnen(client.webUrl(gid))
    }

    /** Fotos der Person im Betrachter, bei [index] beginnend. */
    fun profilFotos(index: Int) {
        val p = zustand.value.profil?.person ?: return
        val medien = p.extended?.media.orEmpty().associateBy { it.handle }
        val eintraege = p.medien.filter { !it.private }.mapNotNull { medien[it.ref] }.filter { it.istBild }
        betrachter(eintraege, index, ViewerSource.Profile)
    }

    /** Fuer Familienlisten und Teilnehmer: Anzeigeform aus der aufbereiteten Person. */
    fun anzeigeAus(p: de.bgghome.gwand.api.PersonProfil): Person = anzeige.ausProfil(p)

    fun anzeigeAus(p: GrampsPerson): Person = anzeige.person(p)

    fun bildUrl(medium: String, rect: List<Int>?, groesse: Int = 300): String = client.bildUrl(medium, rect, groesse)

    fun startpersonSetzen(handle: String) {
        ablage.putString(schluessel("start"), handle)
        zustand.update { it.copy(startperson = handle) }
        viewModelScope.launch(sicher) { meldung(getString(Res.string.start_person_saved)) }
    }

    // ── Geteilte Links ───────────────────────────────────────────────

    /** Ein Text mit Link auf eine Person des eigenen Servers (aus dem Teilen-Menue anderer Apps). */
    fun linkOeffnen(text: String?) {
        val t = text ?: return
        if (zustand.value.bildschirm != Bildschirm.Haupt) { wartenderLink = t; return }
        val basis = client.basisUrl
        val gid = Regex("""${Regex.escape(basis)}/person/([A-Za-z0-9_]+)""").find(t)?.groupValues?.get(1)
        viewModelScope.launch(sicher) {
            val p = gid?.let { runCatching { client.personNachId(it) }.getOrNull() }
            if (p == null) meldung(getString(Res.string.link_not_found)) else profilOeffnen(p.handle)
        }
    }

    // ── Start ────────────────────────────────────────────────────────

    fun startseiteLaden() {
        zustand.update { it.copy(startLaedt = true) }
        viewModelScope.launch(sicher) {
            val tage = zustand.value.jahrestageTage
            val heute = LocalDate.now()
            val jahrestageA = async { runCatching { Jahrestage.aus(client.ereignisseAnTagen(Jahrestage.tage(heute, tage)), heute, tage, anzeige::ausProfil) }.getOrNull() }
            val geaendertA = async { runCatching { client.zuletztGeaendert(8).map(anzeige::person) }.getOrNull() }
            val gesehenA = async { runCatching { gesehenLaden() }.getOrNull() }
            lesezeichenLaden()
            val jahrestage = jahrestageA.await()?.let { liste ->
                // Die Teilnehmer kommen ohne Bild; einmal vollstaendig holen, damit die Karten Portraets zeigen.
                val voll = runCatching { client.personenNachHandle(liste.flatMap { j -> j.personen.map { it.xref } }.toSet()) }
                    .getOrDefault(emptyList()).associate { it.handle to anzeige.person(it) }
                liste.map { j -> j.copy(personen = j.personen.map { voll[it.xref] ?: it }) }
            }
            val geaendert = geaendertA.await(); val gesehen = gesehenA.await()
            zustand.update {
                it.copy(
                    jahrestage = jahrestage ?: it.jahrestage, zuletztGeaendert = geaendert ?: it.zuletztGeaendert,
                    zuletztGesehen = gesehen ?: it.zuletztGesehen, startLaedt = false,
                )
            }
        }
    }

    private suspend fun lesezeichenLaden() {
        val personen = runCatching { client.personenNachHandle(client.lesezeichen()).map(anzeige::person) }.getOrNull() ?: return
        zustand.update { it.copy(lesezeichen = personen.sortedBy { p -> p.sortName }) }
    }

    /** Zuletzt angesehene Profile: Handles in der Ablage, je Server und Konto, hoechstens zehn. */
    private fun gesehen(p: Person) {
        val liste = (listOf(p.xref) + gesehenHandles().filter { it != p.xref }).take(10)
        ablage.putString(schluessel("gesehen"), liste.joinToString(","))
        zustand.update { s -> s.copy(zuletztGesehen = (listOf(p) + s.zuletztGesehen.filter { it.xref != p.xref }).take(10)) }
    }

    private fun gesehenHandles(): List<String> = ablage.getString(schluessel("gesehen"), "").orEmpty().split(',').filter { it.isNotBlank() }

    private suspend fun gesehenLaden(): List<Person> {
        val handles = gesehenHandles()
        val nachHandle = client.personenNachHandle(handles).associateBy { it.handle }
        return handles.mapNotNull { nachHandle[it] }.map(anzeige::person)
    }

    // ── Personen ─────────────────────────────────────────────────────

    fun suchen(text: String) {
        sucheJob?.cancel()
        zustand.update { it.copy(suche = text, sucheLaedt = true) }
        sucheJob = viewModelScope.launch(sicher) {
            delay(if (text.isBlank()) 0 else 350)
            seiteLaden(text, 1)
        }
    }

    fun weitereTreffer() {
        val s = zustand.value
        val n = s.naechsteSeite ?: return
        if (s.sucheLaedt) return
        zustand.update { it.copy(sucheLaedt = true) }
        sucheJob = viewModelScope.launch(sicher) { seiteLaden(s.suche, n) }
    }

    private suspend fun seiteLaden(text: String, seite: Int) {
        try {
            val s = client.personen(text, seite)
            val neu = s.eintraege.map(anzeige::person)
            zustand.update {
                if (it.suche != text) it else it.copy(
                    treffer = if (seite == 1) neu else it.treffer + neu, trefferGesamt = s.gesamt,
                    naechsteSeite = s.naechste, sucheLaedt = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            zustand.update { it.copy(sucheLaedt = false) }
            fehlschlag(e)
        }
    }

    // ── Fotos ────────────────────────────────────────────────────────

    fun weitereFotos() {
        val s = zustand.value
        val seite = s.fotosNaechste ?: return
        if (s.fotosLaedt) return
        zustand.update { it.copy(fotosLaedt = true) }
        viewModelScope.launch(sicher) {
            try {
                val r = client.medien(seite)
                zustand.update {
                    it.copy(fotos = (if (seite == 1) emptyList() else it.fotos) + r.eintraege.filter { m -> m.istBild && !m.private },
                        fotosGesamt = r.gesamt, fotosNaechste = r.naechste, fotosLaedt = false)
                }
            } catch (e: Exception) {
                zustand.update { it.copy(fotosLaedt = false) }
                fehlschlag(e)
            }
        }
    }

    fun fotoOeffnen(index: Int) = betrachter(zustand.value.fotos, index, ViewerSource.Tree)

    fun vorschau(m: Medium, groesse: Int = 300): String = client.bildUrl(m.handle, null, groesse)

    private fun betrachter(medien: List<Medium>, index: Int, quelle: ViewerSource) {
        if (medien.isEmpty()) return
        val items = medien.map { m ->
            ViewerItem(
                image = client.dateiUrl(m.handle), thumb = client.bildUrl(m.handle, null, 500).substringBefore("?"),
                caption = m.desc.ifBlank { m.path.substringAfterLast('/') }, subtitle = m.profile?.date.orEmpty(),
                webUrl = "${client.basisUrl}/media/${m.grampsId}", link = LinkState.Persons,
            )
        }
        zustand.update { it.copy(betrachter = ViewerState(items, index.coerceIn(0, items.lastIndex), quelle)) }
    }

    fun betrachterIndex(i: Int) = zustand.update { s -> s.copy(betrachter = s.betrachter?.copy(index = i)) }

    fun betrachterSchliessen() = zustand.update { it.copy(betrachter = null) }

    fun oeffnen(url: String) = plattform.oeffnen(url)

    // ── Mehr: Orte, Quellen, Archive ─────────────────────────────────

    fun nachschlagen(art: Nachschlagart, suche: String) {
        nachschlagJob?.cancel()
        if (art == Nachschlagart.Verlauf) {
            zustand.update { it.copy(nachschlagart = art, nachschlagSuche = "", detail = null) }
            verlaufLaden()
            return
        }
        zustand.update { it.copy(nachschlagart = art, nachschlagSuche = suche, nachschlagLaedt = true, detail = null) }
        nachschlagJob = viewModelScope.launch(sicher) {
            delay(if (suche.isBlank()) 0 else 350)
            nachschlagSeite(art, suche, 1)
        }
    }

    fun weitereNachschlagen() {
        val s = zustand.value
        val n = s.nachschlagNaechste ?: return
        if (s.nachschlagLaedt) return
        zustand.update { it.copy(nachschlagLaedt = true) }
        nachschlagJob = viewModelScope.launch(sicher) { nachschlagSeite(s.nachschlagart, s.nachschlagSuche, n) }
    }

    private suspend fun nachschlagSeite(art: Nachschlagart, suche: String, seite: Int) {
        try {
            val passt = { s: Zustand -> s.nachschlagart == art && s.nachschlagSuche == suche }
            when (art) {
                Nachschlagart.Orte -> client.orte(suche, seite).let { r ->
                    zustand.update { if (!passt(it)) it else it.copy(orte = (if (seite == 1) emptyList() else it.orte) + r.eintraege, nachschlagGesamt = r.gesamt, nachschlagNaechste = r.naechste, nachschlagLaedt = false) }
                }
                Nachschlagart.Quellen -> client.quellen(suche, seite).let { r ->
                    zustand.update { if (!passt(it)) it else it.copy(quellenListe = (if (seite == 1) emptyList() else it.quellenListe) + r.eintraege, nachschlagGesamt = r.gesamt, nachschlagNaechste = r.naechste, nachschlagLaedt = false) }
                }
                Nachschlagart.Verlauf -> {}
                Nachschlagart.Archive -> client.archive(suche, seite).let { r ->
                    zustand.update { if (!passt(it)) it else it.copy(archive = (if (seite == 1) emptyList() else it.archive) + r.eintraege, nachschlagGesamt = r.gesamt, nachschlagNaechste = r.naechste, nachschlagLaedt = false) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            zustand.update { it.copy(nachschlagLaedt = false) }
            fehlschlag(e)
        }
    }

    fun ortOeffnen(handle: String) = detailLaden { Detail.OrtDetail(client.ort(handle)) }

    fun quelleOeffnen(handle: String) = detailLaden { Detail.QuelleDetail(client.quelle(handle)) }

    fun archivOeffnen(handle: String) = detailLaden {
        val a = client.archiv(handle)
        Detail.ArchivDetail(a, client.quellenNachHandle(a.backlinks["source"].orEmpty()).sortedBy { it.title })
    }

    private fun detailLaden(laden: suspend () -> Detail) {
        viewModelScope.launch(sicher) {
            try {
                val d = laden()
                zustand.update { it.copy(detail = d, betrachter = null) }
            } catch (e: Exception) {
                fehlschlag(e)
            }
        }
    }

    fun detailSchliessen() = zustand.update { it.copy(detail = null) }

    /** Ein Ort aus der Zeitleiste oder Personenliste: Ortsseite oeffnen. */
    fun ortAusZeitleiste(handle: String) = ortOeffnen(handle)

    private suspend fun typenLaden() {
        // Der Server uebersetzt Typlisten nur in seine eigene Sprache; die App bringt die Namen selbst mit.
        val alle = de.bgghome.gwand.daten.Typnamen.fuer(client.sprache)
        val ereignisTypen = runCatching { client.ereignisTypen() }.getOrDefault(emptyList())
        zustand.update { it.copy(typen = alle, ereignisTypen = ereignisTypen) }
    }

    // ── Meldungen ────────────────────────────────────────────────────

    fun meldungGezeigt() = zustand.update { it.copy(meldung = null) }

    internal fun meldung(text: String) = zustand.update { it.copy(meldung = text) }

    internal suspend fun fehlschlag(e: Exception) {
        if (e is AnmeldungAbgelaufen) {
            client.abmelden()
            zustand.update { Zustand(bildschirm = Bildschirm.Anmeldung, adresse = client.basisUrl, benutzername = client.benutzername, fehler = getString(Res.string.error_expired), version = plattform.versionName) }
            return
        }
        meldung(text(e))
    }

    private suspend fun text(e: Exception): String = when (e) {
        is AnmeldungAbgelaufen -> getString(Res.string.error_expired)
        is KlartextAusserhalb -> getString(Res.string.error_cleartext)
        is ApiFehler -> getString(Res.string.error_api, e.status, e.text)
        is IOException -> getString(Res.string.error_network, e.message ?: e::class.simpleName.orEmpty())
        else -> e.message ?: e::class.simpleName.orEmpty()
    }

    /** Einstellungen je Server und Konto. */
    private fun schluessel(name: String) = "$name|${client.basisUrl}|${client.benutzername}"

    companion object {
        const val REITER_LEBENSLAUF = 0
        const val REITER_FAMILIE = 1
        const val REITER_FOTOS = 2
        const val REITER_QUELLEN = 3
        const val REITER_NOTIZEN = 4
        const val REITER_KARTE = 5
        private const val K_GESCHWISTER = "zeigeGeschwister"
        private const val K_COUSINS = "zeigeCousins"
        const val K_ERINNERUNG = "erinnerung"
    }
}
