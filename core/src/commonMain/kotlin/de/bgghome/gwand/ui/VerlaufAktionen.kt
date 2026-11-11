package de.bgghome.gwand.ui

import androidx.lifecycle.viewModelScope
import de.bgghome.gwand.api.Aenderung
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.merge_confirm
import de.bgghome.gwand.res.saved
import de.bgghome.gwand.res.undo_confirm
import de.bgghome.gwand.res.undo_conflict
import de.bgghome.gwand.res.undo_done
import de.bgghome.webtrees.nativ.api.Person
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/*
 * Aenderungsverlauf (des Baums im Reiter Mehr, einer Person im Profil), Rueckgaengig und Zusammenfuehren.
 * Rueckgaengig prueft vorher beim Server, ob es ohne Konflikt geht; sonst wird nichts zurueckgenommen.
 */

/** Interne Verweis-Eintraege des Verlaufs zaehlen nicht als Aenderung. */
private fun Aenderung.zaehlt() = klasse.isNotBlank() && klasse != "Reference" && klasse.toIntOrNull() == null

private suspend fun GwViewModel.eintraege(aenderungen: List<Aenderung>): List<Verlaufseintrag> {
    val gruppen = aenderungen.filter { it.zaehlt() }.groupBy { it.transaktion }
    val personen = runCatching {
        client.personenNachHandle(gruppen.values.flatten().filter { it.klasse == "Person" }.map { it.handle }.toSet())
    }.getOrDefault(emptyList()).associate { it.handle to anzeige.person(it) }
    return gruppen.map { (id, liste) ->
        val erste = liste.first()
        Verlaufseintrag(
            vorgang = id, wann = erste.connection.timestamp.takeIf { it > 0 } ?: erste.timestamp,
            wer = erste.connection.user.vollerName.ifBlank { erste.connection.user.name },
            aenderungen = liste.groupingBy { it.klasse to it.art }.eachCount().map { (k, n) -> "${k.first}|${k.second}" to n },
            personen = liste.filter { it.klasse == "Person" }.mapNotNull { personen[it.handle] }.distinctBy { it.xref },
        )
    }.sortedByDescending { it.vorgang }
}

/** Verlauf des ganzen Baums fuer den Reiter Mehr. */
fun GwViewModel.verlaufLaden() {
    zustand.update { it.copy(nachschlagLaedt = true) }
    viewModelScope.launch(sicher) {
        try {
            val seite = client.verlauf(1, 40)
            val liste = eintraege(seite.eintraege.flatMap { v -> v.changes.map { it.copy(transaktion = v.id, connection = v.connection) } })
            zustand.update { it.copy(verlauf = liste, nachschlagGesamt = seite.gesamt, nachschlagNaechste = null, nachschlagLaedt = false) }
        } catch (e: Exception) {
            zustand.update { it.copy(nachschlagLaedt = false) }
            fehlschlag(e)
        }
    }
}

/** Verlauf der Person im Profil. */
fun GwViewModel.objektVerlauf() {
    val p = zustand.value.profil ?: return
    val name = p.anzeige?.name.orEmpty()
    zustand.update { it.copy(dialog = Dialog.ObjektVerlaufDialog(name)) }
    viewModelScope.launch(sicher) {
        try {
            val liste = eintraege(client.objektVerlauf("Person", p.handle))
            zustand.update { s -> if (s.dialog is Dialog.ObjektVerlaufDialog) s.copy(dialog = Dialog.ObjektVerlaufDialog(name, liste)) else s }
        } catch (e: Exception) {
            zustand.update { s -> if (s.dialog is Dialog.ObjektVerlaufDialog) s.copy(dialog = Dialog.ObjektVerlaufDialog(name, emptyList())) else s }
            fehlschlag(e)
        }
    }
}

/** Erst pruefen, dann nachfragen, dann zuruecknehmen. */
fun GwViewModel.rueckgaengig(vorgang: Int) {
    viewModelScope.launch(sicher) {
        try {
            if (!client.rueckgaengigPruefen(vorgang).ohneKonflikt) {
                meldung(getString(Res.string.undo_conflict))
                return@launch
            }
            val text = getString(Res.string.undo_confirm)
            zustand.update {
                it.copy(dialog = Dialog.LoeschenDialog(text) {
                    client.rueckgaengig(vorgang)?.let { a ->
                        val ergebnis = client.warten(a)
                        if (ergebnis.fehlgeschlagen) throw IllegalStateException(getString(Res.string.undo_conflict))
                    }
                    meldung(getString(Res.string.undo_done))
                    if (zustand.value.bereich == Bereich.Mehr && zustand.value.nachschlagart == Nachschlagart.Verlauf) verlaufLaden()
                })
            }
        } catch (e: Exception) {
            fehlschlag(e)
        }
    }
}

// ── Zusammenfuehren ──────────────────────────────────────────────────

fun GwViewModel.zusammenfuehrenDialog() {
    val p = zustand.value.profil?.anzeige ?: return
    zustand.update { it.copy(dialog = Dialog.ZusammenfuehrenDialog(p), personVorschlaege = emptyList()) }
}

fun GwViewModel.zusammenfuehrenWahl(d: Dialog.ZusammenfuehrenDialog, geht: Person?) =
    zustand.update { it.copy(dialog = d.copy(geht = geht)) }

/** Seiten tauschen: die andere Person bleibt. */
fun GwViewModel.zusammenfuehrenTauschen(d: Dialog.ZusammenfuehrenDialog) {
    val geht = d.geht ?: return
    zustand.update { it.copy(dialog = Dialog.ZusammenfuehrenDialog(geht, d.bleibt)) }
}

fun GwViewModel.zusammenfuehren(d: Dialog.ZusammenfuehrenDialog) {
    val geht = d.geht ?: return
    viewModelScope.launch(sicher) {
        val text = getString(Res.string.merge_confirm, geht.name, d.bleibt.name)
        zustand.update {
            it.copy(dialog = Dialog.LoeschenDialog(text) {
                client.zusammenfuehren(d.bleibt.xref, geht.xref)
                // Das Profil der verschwundenen Person schliessen, die bleibende zeigen
                zustand.update { s -> s.copy(profilVerlauf = s.profilVerlauf.filter { h -> h != geht.xref }) }
                if (zustand.value.wurzel == geht.xref) zustand.update { s -> s.copy(wurzel = d.bleibt.xref) }
                profilOeffnen(d.bleibt.xref)
                meldung(getString(Res.string.saved))
            })
        }
    }
}
