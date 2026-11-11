package de.bgghome.gwand.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bgghome.gwand.api.AnmeldungAbgelaufen
import de.bgghome.gwand.api.ApiFehler
import de.bgghome.gwand.api.Benutzer
import de.bgghome.gwand.api.GwClient
import de.bgghome.gwand.api.KlartextAusserhalb
import de.bgghome.gwand.daten.Anzeige
import de.bgghome.gwand.daten.BaumBau
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.error_api
import de.bgghome.gwand.res.error_cleartext
import de.bgghome.gwand.res.error_expired
import de.bgghome.gwand.res.error_login
import de.bgghome.gwand.res.error_network
import de.bgghome.gwand.res.error_version
import de.bgghome.gwand.res.no_start_person
import de.bgghome.gwand.res.start_person_saved
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Pedigree
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import java.io.IOException

enum class Bildschirm { Laden, Anmeldung, Haupt }

data class Zustand(
    val bildschirm: Bildschirm = Bildschirm.Laden,
    val beschaeftigt: Boolean = false,
    /** Fehler auf dem Anmeldebildschirm */
    val fehler: String? = null,
    /** Kurze Meldung unten (Snackbar) */
    val meldung: String? = null,
    val adresse: String = "",
    val benutzername: String = "",
    val konto: Benutzer? = null,
    val baumName: String = "",
    val farben: Baumfarben = Baumfarben(),
    val wurzel: String? = null,
    val ahnenGenerationen: Int = 4,
    val nachkommenGenerationen: Int = 2,
    val ahnentafel: Pedigree? = null,
    val nachkommen: DescendantNode? = null,
    val ausgewaehlt: Person? = null,
    val sucheOffen: Boolean = false,
    val suche: String = "",
    val treffer: List<Person> = emptyList(),
    val trefferGesamt: Int = 0,
    val naechsteSeite: Int? = null,
    val sucheLaedt: Boolean = false,
)

/** Was das ViewModel von der Plattform braucht: den Client und eine Ablage fuer kleine Einstellungen. */
class GwPlattform(val client: GwClient, val einstellungen: Ablage, val versionName: String)

/**
 * Das ViewModel von gwAnd. Haelt den Zustand und spricht ueber [GwClient] mit Gramps Web. Antworten kommen nur an,
 * wenn sie noch zum Zustand passen (eine neue Suche verwirft die alte, ein neuer Baum den alten).
 */
class GwViewModel(private val plattform: GwPlattform) : ViewModel() {

    private val client = plattform.client
    private val anzeige = Anzeige(
        bild = { p -> p.medien.firstOrNull { !it.private }?.let { client.bildUrl(it.ref, it.rect) } },
        web = client::webUrl,
    )
    private val bau = BaumBau(anzeige)

    private val zustand = MutableStateFlow(Zustand(adresse = client.basisUrl, benutzername = client.benutzername))
    val state: StateFlow<Zustand> = zustand

    private var baumJob: Job? = null
    private var sucheJob: Job? = null

    init {
        start()
    }

    private fun start() {
        if (!client.hatAnmeldung) {
            zustand.update { it.copy(bildschirm = Bildschirm.Anmeldung) }
            return
        }
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        val wurzel = plattform.einstellungen.getString(startSchluessel(), null) ?: meta.startperson
        zustand.update {
            it.copy(
                bildschirm = Bildschirm.Haupt, beschaeftigt = false, fehler = null, konto = konto,
                baumName = meta.database.name, farben = farben, adresse = client.basisUrl,
            )
        }
        if (wurzel != null) baumLaden(wurzel) else {
            zustand.update { it.copy(meldung = getString(Res.string.no_start_person), sucheOffen = true) }
            suchen("")
        }
    }

    fun abmelden() {
        client.abmelden()
        baumJob?.cancel()
        zustand.update { Zustand(bildschirm = Bildschirm.Anmeldung, adresse = client.basisUrl, benutzername = client.benutzername) }
    }

    // ── Baum ─────────────────────────────────────────────────────────

    /** Den Baum um [handle] laden: Vorfahren, Nachkommen und deren Partner, parallel. */
    fun baumLaden(handle: String) {
        baumJob?.cancel()
        val s = zustand.value
        zustand.update { it.copy(wurzel = handle, ahnentafel = null, nachkommen = null) }
        baumJob = viewModelScope.launch {
            try {
                val wurzel = client.personenNachHandle(listOf(handle), mitFamilien = true).firstOrNull()
                    ?: throw ApiFehler(404, handle)
                val vorfahren = async { client.vorfahren(wurzel.grampsId, s.ahnenGenerationen) }
                val nachkommen = async { client.nachkommen(wurzel.grampsId, s.nachkommenGenerationen) }
                val alle = nachkommen.await() + wurzel
                val partner = client.personenNachHandle(BaumBau.fehlendePartner(alle))
                val tafel = bau.ahnentafel(handle, vorfahren.await())
                val unten = bau.nachkommen(wurzel, nachkommen.await(), partner)
                zustand.update { if (it.wurzel == handle) it.copy(ahnentafel = tafel, nachkommen = unten) else it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                fehlschlag(e)
            }
        }
    }

    fun neuLaden() { zustand.value.wurzel?.let(::baumLaden) }

    fun waehlen(person: Person) = zustand.update { it.copy(ausgewaehlt = person) }

    fun auswahlSchliessen() = zustand.update { it.copy(ausgewaehlt = null) }

    fun inDieMitte(person: Person) {
        zustand.update { it.copy(ausgewaehlt = null, sucheOffen = false) }
        baumLaden(person.xref)
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

    fun alsStartperson(person: Person) {
        plattform.einstellungen.putString(startSchluessel(), person.xref)
        viewModelScope.launch { zustand.update { it.copy(ausgewaehlt = null, meldung = null) }; meldung(getString(Res.string.start_person_saved)) }
    }

    // ── Suche ────────────────────────────────────────────────────────

    fun sucheOeffnen() {
        zustand.update { it.copy(sucheOffen = true) }
        if (zustand.value.treffer.isEmpty()) suchen(zustand.value.suche)
    }

    fun sucheSchliessen() = zustand.update { it.copy(sucheOffen = false) }

    fun suchen(text: String) {
        sucheJob?.cancel()
        zustand.update { it.copy(suche = text, sucheLaedt = true) }
        sucheJob = viewModelScope.launch {
            delay(if (text.isBlank()) 0 else 350)
            seiteLaden(text, 1)
        }
    }

    fun weitereTreffer() {
        val s = zustand.value
        val n = s.naechsteSeite ?: return
        if (s.sucheLaedt) return
        zustand.update { it.copy(sucheLaedt = true) }
        sucheJob = viewModelScope.launch { seiteLaden(s.suche, n) }
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            zustand.update { it.copy(sucheLaedt = false) }
            fehlschlag(e)
        }
    }

    // ── Meldungen ────────────────────────────────────────────────────

    fun meldungGezeigt() = zustand.update { it.copy(meldung = null) }

    private fun meldung(text: String) = zustand.update { it.copy(meldung = text) }

    private suspend fun fehlschlag(e: Exception) {
        if (e is AnmeldungAbgelaufen) {
            client.abmelden()
            zustand.update { Zustand(bildschirm = Bildschirm.Anmeldung, adresse = client.basisUrl, benutzername = client.benutzername, fehler = getString(Res.string.error_expired)) }
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

    /** Startperson je Server und Konto. */
    private fun startSchluessel() = "start|${client.basisUrl}|${client.benutzername}"
}
