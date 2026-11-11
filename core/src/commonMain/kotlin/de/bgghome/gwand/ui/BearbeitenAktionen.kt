package de.bgghome.gwand.ui

import androidx.lifecycle.viewModelScope
import de.bgghome.gwand.api.Konflikt
import de.bgghome.gwand.api.ZeitleistenEintrag
import de.bgghome.gwand.daten.Bearbeiten
import de.bgghome.gwand.daten.NeuePerson
import de.bgghome.gwand.daten.Schreibhilfe
import de.bgghome.gwand.daten.Verwandtschaftsart
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.conflict_reloaded
import de.bgghome.gwand.res.delete_event_confirm
import de.bgghome.gwand.res.delete_person_confirm
import de.bgghome.gwand.res.saved
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.data.DateForm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.getString

/*
 * Bearbeiten im ViewModel: Dialoge oeffnen, Eingaben ueber [Bearbeiten] speichern, danach Profil und Baum neu laden.
 * Ein Konflikt (jemand anderes hat inzwischen geaendert) speichert nichts, laedt neu und sagt es.
 */

private val GwViewModel.bearbeiten: Bearbeiten get() = Bearbeiten(client)

private var vorschlagJob: Job? = null

/** Speichern: Dialog zu, Profil und Baum neu, Meldung; bei Fehler bleibt der Dialog offen. */
private fun GwViewModel.speichern(schliessen: Boolean = true, nachher: (suspend () -> Unit)? = null, schritt: suspend () -> Unit) {
    if (zustand.value.speichert) return
    zustand.update { it.copy(speichert = true) }
    viewModelScope.launch(sicher) {
        try {
            schritt()
            zustand.update { it.copy(speichert = false, dialog = if (schliessen) null else it.dialog) }
            nachher?.invoke()
            neuLadenNachSchreiben()
            meldung(getString(Res.string.saved))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Konflikt) {
            zustand.update { it.copy(speichert = false, dialog = null) }
            neuLadenNachSchreiben()
            meldung(getString(Res.string.conflict_reloaded))
        } catch (e: Exception) {
            zustand.update { it.copy(speichert = false) }
            fehlschlag(e)
        }
    }
}

private fun GwViewModel.neuLadenNachSchreiben() {
    zustand.value.profil?.handle?.let(::profilLaden)
    if (zustand.value.wurzel != null) neuLaden()
}

fun GwViewModel.dialogSchliessen() = zustand.update { it.copy(dialog = null, ortVorschlaege = emptyList(), personVorschlaege = emptyList(), quellVorschlaege = emptyList()) }

private fun JsonObject.text(k: String): String = (this[k] as? JsonPrimitive)?.content.orEmpty()
private fun JsonObject.stand(): Long? = text("change").toLongOrNull()

// ── Person ───────────────────────────────────────────────────────────

fun GwViewModel.personBearbeiten() {
    val p = zustand.value.profil?.person ?: return
    viewModelScope.launch(sicher) {
        val roh = client.roh("people", p.handle)
        val name = roh["primary_name"] as? JsonObject
        val nachnamen = (name?.get("surname_list") as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val nachname = (nachnamen.firstOrNull { it.text("primary") == "true" } ?: nachnamen.firstOrNull())?.text("surname").orEmpty()
        zustand.update {
            it.copy(dialog = Dialog.PersonDialog(p.handle, name?.text("first_name").orEmpty(), nachname, name?.text("call").orEmpty(),
                roh.text("gender").toIntOrNull() ?: 2, roh.stand()))
        }
    }
}

fun GwViewModel.neuePerson() = zustand.update { it.copy(dialog = Dialog.PersonDialog(null)) }

fun GwViewModel.personSpeichern(d: Dialog.PersonDialog, vorname: String, nachname: String, rufname: String, geschlecht: Int, geburt: DateForm?) {
    speichern(nachher = null) {
        if (d.handle == null) {
            val h = bearbeiten.personAnlegen(NeuePerson(vorname, nachname, geschlecht, geburt?.let(Schreibhilfe::datum)))
            profilOeffnen(h)
        } else {
            bearbeiten.personAendern(d.handle, d.stand, vorname, nachname, rufname, geschlecht)
        }
    }
}

fun GwViewModel.personLoeschen() {
    val p = zustand.value.profil?.anzeige ?: return
    viewModelScope.launch(sicher) {
        val text = getString(Res.string.delete_person_confirm, p.name)
        zustand.update {
            it.copy(dialog = Dialog.LoeschenDialog(text) {
                bearbeiten.personLoeschen(p.xref)
                zustand.update { s -> s.copy(profil = null, profilVerlauf = emptyList()) }
                if (zustand.value.wurzel == p.xref) zustand.update { s -> s.copy(wurzel = null, ahnentafel = null, nachkommen = null) }
            })
        }
    }
}

fun GwViewModel.loeschenBestaetigt(d: Dialog.LoeschenDialog) = speichern { d.aktion() }

// ── Ereignisse ───────────────────────────────────────────────────────

/** Neues Ereignis der Person im Profil. */
fun GwViewModel.neuesEreignis() {
    val p = zustand.value.profil?.person ?: return
    zustand.update {
        it.copy(dialog = Dialog.EreignisDialog(p.handle, null, null, "Birth", DateForm(), "", null, "", "", null, p.change))
    }
}

/** Ein eigenes Ereignis aus dem Lebenslauf bearbeiten. */
fun GwViewModel.ereignisBearbeiten(e: ZeitleistenEintrag) {
    val p = zustand.value.profil?.person ?: return
    viewModelScope.launch(sicher) {
        val roh = client.roh("events", e.handle)
        val datum = roh["date"] as? JsonObject
        zustand.update {
            it.copy(dialog = Dialog.EreignisDialog(
                person = p.handle, familie = null, ereignis = e.handle, typ = roh.text("type").ifEmpty { e.type },
                datum = Schreibhilfe.eingabe(datum), datumText = e.date, ort = roh.text("place").ifEmpty { null },
                ortName = e.place?.anzeigename.orEmpty().ifEmpty { e.place?.name.orEmpty() }, beschreibung = roh.text("description"),
                stand = roh.stand(), personStand = p.change,
            ))
        }
    }
}

/** Neues Familienereignis (Heirat …) einer Partnerfamilie. */
fun GwViewModel.neuesFamilienereignis(familie: String) = zustand.update {
    it.copy(dialog = Dialog.EreignisDialog(null, familie, null, "Marriage", DateForm(), "", null, "", "", null, null))
}

fun GwViewModel.ereignisSpeichern(d: Dialog.EreignisDialog, typ: String, datum: DateForm?, ort: String?, ortNeu: String?, beschreibung: String) {
    speichern {
        val ortHandle = ortNeu?.takeIf { it.isNotBlank() }?.let { bearbeiten.ortAnlegen(it) } ?: ort
        val datumJson = datum?.let(Schreibhilfe::datum)
        when {
            d.ereignis != null -> bearbeiten.ereignisAendern(d.ereignis, d.stand, typ, datumJson, datumAendern = datum != null, ort = ortHandle, beschreibung = beschreibung)
            d.familie != null -> bearbeiten.familienereignisAnlegen(d.familie, typ, datumJson, ortHandle, beschreibung)
            d.person != null -> bearbeiten.ereignisAnlegen(d.person, d.personStand, typ, datumJson, ortHandle, beschreibung)
        }
    }
}

fun GwViewModel.ereignisLoeschen(d: Dialog.EreignisDialog) {
    val person = d.person ?: return
    val ereignis = d.ereignis ?: return
    viewModelScope.launch(sicher) {
        val text = getString(Res.string.delete_event_confirm)
        zustand.update { it.copy(dialog = Dialog.LoeschenDialog(text) { bearbeiten.ereignisEntfernen(person, ereignis) }) }
    }
}

// ── Verwandte ────────────────────────────────────────────────────────

/** Verwandten-Dialog fuer eine Person (aus dem Profil oder ueber die "+"-Lasche im Baum). */
fun GwViewModel.verwandterDialog(person: Person, art: Verwandtschaftsart? = null, familie: String? = null) {
    val geschlecht = when (person.sex) { "M" -> 1; "F" -> 0; else -> 2 }
    zustand.update { it.copy(dialog = Dialog.VerwandterDialog(person.xref, person.name, geschlecht, art, familie), personVorschlaege = emptyList()) }
}

/** Kind in eine bestimmte Partnerfamilie der Person im Profil. */
fun GwViewModel.verwandterDialogFuerFamilie(selbst: String, familie: String) {
    val p = zustand.value.profil?.anzeige ?: return
    if (p.xref == selbst) verwandterDialog(p, Verwandtschaftsart.Kind, familie)
}

fun GwViewModel.verwandterSpeichern(d: Dialog.VerwandterDialog, art: Verwandtschaftsart, neu: NeuePerson?, vorhanden: String?) {
    speichern { bearbeiten.verwandterHinzu(d.zu, d.geschlechtZu, art, neu, vorhanden, d.familie) }
}

fun GwViewModel.kindLoesen(familie: String, kind: String) = speichern { bearbeiten.kindLoesen(familie, kind) }

fun GwViewModel.elternteilLoesen(familie: String, person: String) = speichern { bearbeiten.elternteilLoesen(familie, person) }

fun GwViewModel.kindVerschieben(familie: String, kind: String, schritt: Int) = speichern { bearbeiten.kindVerschieben(familie, kind, schritt) }

// ── Fotos ────────────────────────────────────────────────────────────

fun GwViewModel.fotoDialog() {
    val p = zustand.value.profil?.person ?: return
    zustand.update { it.copy(dialog = Dialog.FotoDialog(p.handle)) }
}

fun GwViewModel.fotoSpeichern(d: Dialog.FotoDialog, daten: ByteArray, mime: String, beschreibung: String, hauptbild: Boolean) =
    speichern { bearbeiten.fotoHinzu(d.person, daten, mime, beschreibung, hauptbild) }

fun GwViewModel.alsHauptbild(medium: String) {
    val p = zustand.value.profil?.person ?: return
    speichern { bearbeiten.hauptbild(p.handle, medium) }
}

// ── Quellen und Beziehungen ──────────────────────────────────────────

fun GwViewModel.zitatDialog() {
    val p = zustand.value.profil ?: return
    val person = p.person ?: return
    val ziele = listOf(("people" to person.handle) to (p.anzeige?.name ?: person.grampsId)) +
        p.zeitleiste.filter { it.person.selbst && it.handle.isNotEmpty() }.map { e ->
            ("events" to e.handle) to listOf(e.label, e.date.takeIf { it.isNotBlank() }?.let(de.bgghome.gwand.daten.Anzeige::anzeigeDatum)).filterNotNull().joinToString(" · ")
        }
    zustand.update { it.copy(dialog = Dialog.ZitatDialog(ziele), quellVorschlaege = emptyList()) }
    quellVorschlaege("")
}

fun GwViewModel.zitatSpeichern(ziel: Pair<String, String>, quelle: String, seite: String, vertrauen: Int) =
    speichern { bearbeiten.zitatHinzu(ziel.first, ziel.second, quelle, seite, vertrauen) }

fun GwViewModel.beziehungDialog() {
    val p = zustand.value.profil?.person ?: return
    zustand.update { it.copy(dialog = Dialog.BeziehungDialog(p.handle), personVorschlaege = emptyList()) }
}

fun GwViewModel.beziehungSpeichern(d: Dialog.BeziehungDialog, andere: String, art: String) =
    speichern { bearbeiten.beziehungHinzu(d.person, andere, art) }

fun GwViewModel.beziehungEntfernen(andere: String, art: String) {
    val p = zustand.value.profil?.person ?: return
    speichern { bearbeiten.beziehungEntfernen(p.handle, andere, art) }
}

// ── Vorschlaege beim Tippen ──────────────────────────────────────────

fun GwViewModel.ortVorschlaege(text: String) = vorschlagen {
    val orte = if (text.isBlank()) emptyList() else client.orte(text, 1, 15).eintraege
    zustand.update { it.copy(ortVorschlaege = orte) }
}

fun GwViewModel.personVorschlaege(text: String) = vorschlagen {
    val personen = if (text.isBlank()) emptyList() else client.personen(text, 1, 20).eintraege.map(anzeige::person)
    zustand.update { it.copy(personVorschlaege = personen) }
}

fun GwViewModel.quellVorschlaege(text: String) = vorschlagen {
    zustand.update { it.copy(quellVorschlaege = client.quellen(text, 1, 30).eintraege) }
}

private fun GwViewModel.vorschlagen(laden: suspend () -> Unit) {
    vorschlagJob?.cancel()
    vorschlagJob = viewModelScope.launch(sicher) {
        delay(300)
        runCatching { laden() }
    }
}
