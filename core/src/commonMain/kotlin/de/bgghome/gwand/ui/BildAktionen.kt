package de.bgghome.gwand.ui

import androidx.lifecycle.viewModelScope
import de.bgghome.gwand.api.Medium
import de.bgghome.gwand.daten.Bearbeiten
import de.bgghome.gwand.daten.Schreibhilfe
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.saved
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/*
 * Gesichter auf Bildern zuordnen und Text auf Dokumentfotos erkennen lassen. Beides laeuft ueber den Server:
 * die Gesichtserkennung liefert Rahmen in Prozent, die Texterkennung ist eine Hintergrundaufgabe.
 */

fun GwViewModel.gesichterOeffnen(medium: String, titel: String) {
    zustand.update { it.copy(gesichter = Gesichter(medium, titel, client.dateiUrl(medium)), betrachter = null) }
    gesichterLaden(medium)
}

private fun GwViewModel.gesichterLaden(medium: String) {
    viewModelScope.launch(sicher) {
        try {
            val personen = client.personenMitBild(medium)
            val zugeordnet = personen.mapNotNull { p ->
                val r = p.medien.firstOrNull { it.ref == medium }?.rect?.takeIf { it.size == 4 && it != listOf(0, 0, 100, 100) } ?: return@mapNotNull null
                anzeige.person(p) to Rahmen(r[0].toDouble(), r[1].toDouble(), r[2].toDouble(), r[3].toDouble())
            }
            val ohne = personen.filter { p -> zugeordnet.none { it.first.xref == p.handle } }.map(anzeige::person)
            val erkannt = runCatching { client.gesichter(medium) }.getOrDefault(emptyList())
                .filter { it.size == 4 }.map { Rahmen(it[0], it[1], it[2], it[3]) }
                .filter { e -> zugeordnet.none { it.second.ueberdeckung(e) > 0.3 } }
            zustand.update { s ->
                if (s.gesichter?.medium != medium) s
                else s.copy(gesichter = s.gesichter.copy(zugeordnet = zugeordnet, ohneRahmen = ohne.sortedBy { it.sortName }, erkannt = erkannt, laedt = false))
            }
        } catch (e: Exception) {
            zustand.update { s -> if (s.gesichter?.medium == medium) s.copy(gesichter = s.gesichter.copy(laedt = false)) else s }
            fehlschlag(e)
        }
    }
}

fun GwViewModel.gesichterSchliessen() = zustand.update { it.copy(gesichter = null, personVorschlaege = emptyList()) }

/** Rahmen einer Person zuordnen; hatte sie schon einen in diesem Bild, wird er ersetzt. */
fun GwViewModel.gesichtZuordnen(person: String, rahmen: Rahmen) {
    val g = zustand.value.gesichter ?: return
    viewModelScope.launch(sicher) {
        try {
            Bearbeiten(client).ausschnitt(person, g.medium, rahmen.ganzzahlig)
            meldung(getString(Res.string.saved))
            gesichterLaden(g.medium)
            zustand.value.profil?.handle?.let(::profilLaden)
        } catch (e: Exception) {
            fehlschlag(e)
        }
    }
}

fun GwViewModel.gesichtEntfernen(person: String) {
    val g = zustand.value.gesichter ?: return
    viewModelScope.launch(sicher) {
        try {
            Bearbeiten(client).ausschnittEntfernen(person, g.medium)
            gesichterLaden(g.medium)
            zustand.value.profil?.handle?.let(::profilLaden)
        } catch (e: Exception) {
            fehlschlag(e)
        }
    }
}

// ── Texterkennung ────────────────────────────────────────────────────

fun GwViewModel.textErkennen(m: Medium) = textErkennen(m.handle, m.desc)

fun GwViewModel.textErkennen(medium: String, titel: String) {
    zustand.update { it.copy(texterkennung = Texterkennung(medium, titel), betrachter = null) }
    viewModelScope.launch(sicher) {
        try {
            val sprache = when (client.sprache) { "de" -> "deu"; "fr" -> "fra"; "nl" -> "nld"; "es" -> "spa"; else -> "eng" }
            val aufgabe = client.texterkennungStarten(medium, sprache)
            var text: String? = null
            for (i in 0 until 90) {
                val stand = client.aufgabe(aufgabe)
                if (stand.fertig) { text = stand.ergebnis ?: ""; break }
                if (stand.fehlgeschlagen) throw IllegalStateException(stand.info.ifBlank { "Texterkennung fehlgeschlagen" })
                delay(1500)
            }
            zustand.update { s -> if (s.texterkennung?.medium == medium) s.copy(texterkennung = s.texterkennung.copy(text = text ?: "")) else s }
        } catch (e: Exception) {
            zustand.update { s -> if (s.texterkennung?.medium == medium) s.copy(texterkennung = s.texterkennung.copy(fehler = e.message ?: "")) else s }
        }
    }
}

fun GwViewModel.texterkennungSchliessen() = zustand.update { it.copy(texterkennung = null) }

fun GwViewModel.textTeilen(t: Texterkennung) = plattform.teilen(t.titel, t.text.orEmpty())

/** Erkannten Text als Notiz (Art "Transcript") an das Bild haengen. */
fun GwViewModel.textAlsNotiz(t: Texterkennung, text: String) {
    viewModelScope.launch(sicher) {
        try {
            val n = Schreibhilfe.neuesHandle()
            client.anlegen(listOf(Schreibhilfe.notiz(n, text, "Transcript")))
            try {
                client.aendern("media", t.medium) { Schreibhilfe.notizAn(it, n) }
            } catch (e: Exception) {
                runCatching { client.loeschen("notes", n) }; throw e
            }
            zustand.update { it.copy(texterkennung = null) }
            meldung(getString(Res.string.saved))
        } catch (e: Exception) {
            fehlschlag(e)
        }
    }
}
