package de.bgghome.gwand.ui

import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.api.Person
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * Was Gramps zusaetzlich kann: Faecher, Verwandtschaftsweg, Berichte, Export, DNA-Treffer, KI-Assistent.
 */

fun GwViewModel.baumArt(art: BaumArt) = zustand.update { it.copy(baumArt = art) }

// ── Verwandtschaft ───────────────────────────────────────────────────

fun GwViewModel.verwandtschaftDialog() {
    val p = zustand.value.profil?.anzeige ?: return
    zustand.update { it.copy(dialog = Dialog.VerwandtschaftDialog(p), personVorschlaege = emptyList()) }
}

fun GwViewModel.verwandtschaftBerechnen(d: Dialog.VerwandtschaftDialog, zu: Person) {
    zustand.update { it.copy(dialog = d.copy(zu = zu, wege = null, laedt = true)) }
    viewModelScope.launch(sicher) {
        try {
            val wege = client.verwandtschaftsWege(d.von.xref, zu.xref)
            val vorfahren = client.personenNachHandle(wege.flatMap { it.gemeinsameVorfahren }.toSet()).associate { it.handle to anzeige.person(it) }
            val ergebnis = wege.map { w -> w.text to w.gemeinsameVorfahren.mapNotNull { vorfahren[it] } }
            zustand.update { s -> val dd = s.dialog; if (dd is Dialog.VerwandtschaftDialog && dd.zu?.xref == zu.xref) s.copy(dialog = dd.copy(wege = ergebnis, laedt = false)) else s }
        } catch (e: Exception) {
            zustand.update { s -> val dd = s.dialog; if (dd is Dialog.VerwandtschaftDialog) s.copy(dialog = dd.copy(wege = emptyList(), laedt = false)) else s }
            fehlschlag(e)
        }
    }
}

// ── Berichte und Export ──────────────────────────────────────────────

/** Berichte, die fuer eine Person Sinn ergeben (die anderen betreffen den ganzen Baum oder brauchen Filter). */
private val PERSONENBERICHTE = listOf("ancestor_report", "descend_report", "det_ancestor_report", "det_descendant_report",
    "family_group", "indiv_complete", "kinship_report", "ancestor_chart", "descend_chart", "fan_chart", "hourglass_graph")

fun GwViewModel.berichtDialog() {
    val p = zustand.value.profil ?: return
    val anzeige = p.anzeige ?: return
    val gid = p.person?.grampsId ?: return
    zustand.update { it.copy(dialog = Dialog.BerichtDialog(anzeige, gid)) }
    viewModelScope.launch(sicher) {
        try {
            val liste = client.berichte().filter { it.id in PERSONENBERICHTE }.sortedBy { PERSONENBERICHTE.indexOf(it.id) }
            zustand.update { s -> val d = s.dialog; if (d is Dialog.BerichtDialog) s.copy(dialog = d.copy(berichte = liste)) else s }
        } catch (e: Exception) {
            fehlschlag(e)
        }
    }
}

fun GwViewModel.berichtErzeugen(d: Dialog.BerichtDialog, bericht: de.bgghome.gwand.api.Bericht) {
    zustand.update { it.copy(dialog = d.copy(laeuft = bericht.id)) }
    viewModelScope.launch(sicher) {
        try {
            val datei = client.bericht(bericht.id, d.grampsId, "${bericht.name} – ${d.person.name}")
            zustand.update { s -> val dd = s.dialog; if (dd is Dialog.BerichtDialog) s.copy(dialog = dd.copy(laeuft = null)) else s }
            plattform.dateiOeffnen(datei, false)
        } catch (e: Exception) {
            zustand.update { s -> val dd = s.dialog; if (dd is Dialog.BerichtDialog) s.copy(dialog = dd.copy(laeuft = null)) else s }
            fehlschlag(e)
        }
    }
}

fun GwViewModel.exportDialog() = zustand.update { it.copy(dialog = Dialog.ExportDialog()) }

fun GwViewModel.exportieren(format: String) {
    zustand.update { it.copy(dialog = Dialog.ExportDialog(format)) }
    viewModelScope.launch(sicher) {
        try {
            val datei = client.export(format)
            zustand.update { s -> if (s.dialog is Dialog.ExportDialog) s.copy(dialog = null) else s }
            plattform.dateiOeffnen(datei, true)
        } catch (e: Exception) {
            zustand.update { s -> if (s.dialog is Dialog.ExportDialog) s.copy(dialog = Dialog.ExportDialog()) else s }
            fehlschlag(e)
        }
    }
}

// ── DNA ──────────────────────────────────────────────────────────────

fun GwViewModel.dnaDialog() {
    val p = zustand.value.profil ?: return
    zustand.update { it.copy(dialog = Dialog.DnaDialog(p.anzeige?.name.orEmpty())) }
    viewModelScope.launch(sicher) {
        try {
            val treffer = client.dnaTreffer(p.handle)
            val personen = client.personenNachHandle(treffer.map { it.handle }.toSet()).associate { it.handle to anzeige.person(it) }
            zustand.update { s -> val d = s.dialog; if (d is Dialog.DnaDialog) s.copy(dialog = d.copy(treffer = treffer.sortedByDescending { it.cM }.map { personen[it.handle] to it })) else s }
        } catch (e: Exception) {
            zustand.update { s -> val d = s.dialog; if (d is Dialog.DnaDialog) s.copy(dialog = d.copy(treffer = emptyList())) else s }
            fehlschlag(e)
        }
    }
}

// ── KI-Assistent ─────────────────────────────────────────────────────

fun GwViewModel.chatOeffnen() = zustand.update { it.copy(chat = it.chat ?: Chat()) }

fun GwViewModel.chatSchliessen() = zustand.update { it.copy(chat = null) }

fun GwViewModel.chatFragen(frage: String) {
    val c = zustand.value.chat ?: return
    if (frage.isBlank() || c.laeuft) return
    zustand.update { it.copy(chat = c.copy(verlauf = c.verlauf + ("human" to frage), laeuft = true)) }
    viewModelScope.launch(sicher) {
        try {
            val antwort = client.chat(frage, c.verlauf)
            zustand.update { s -> s.chat?.let { s.copy(chat = it.copy(verlauf = it.verlauf + ("ai" to antwort), laeuft = false)) } ?: s }
        } catch (e: Exception) {
            zustand.update { s -> s.chat?.let { s.copy(chat = it.copy(laeuft = false)) } ?: s }
            fehlschlag(e)
        }
    }
}
