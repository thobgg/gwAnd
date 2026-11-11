package de.bgghome.gwand.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.bgghome.gwand.daten.Anzeige
import de.bgghome.gwand.daten.NeuePerson
import de.bgghome.gwand.daten.Schreibhilfe
import de.bgghome.gwand.daten.Verwandtschaftsart
import de.bgghome.gwand.res.*
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.data.DateForm
import de.bgghome.webtrees.nativ.data.DatePart
import de.bgghome.webtrees.nativ.data.DateQualifier
import de.bgghome.webtrees.nativ.ui.PersonRow
import de.bgghome.webtrees.nativ.ui.rememberPhotoSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Ereignisarten oben in der Auswahl; alle weiteren folgen darunter. */
private val HAEUFIGE_TYPEN = listOf("Birth", "Baptism", "Christening", "Death", "Burial", "Marriage", "Occupation", "Residence",
    "Confirmation", "Education", "Military Service", "Emigration", "Immigration", "Census", "Religion", "Engagement", "Divorce")

/** Den offenen Bearbeiten-Dialog zeigen. */
@Composable
fun BearbeitenDialoge(s: Zustand, viewModel: GwViewModel) {
    when (val d = s.dialog) {
        null -> {}
        is Dialog.PersonDialog -> PersonFormular(d, s, viewModel)
        is Dialog.EreignisDialog -> EreignisFormular(d, s, viewModel)
        is Dialog.VerwandterDialog -> VerwandterFormular(d, s, viewModel)
        is Dialog.FotoDialog -> FotoFormular(d, s, viewModel)
        is Dialog.ZitatDialog -> ZitatFormular(d, s, viewModel)
        is Dialog.BeziehungDialog -> BeziehungFormular(d, s, viewModel)
        is Dialog.ObjektVerlaufDialog -> AlertDialog(
            onDismissRequest = viewModel::dialogSchliessen,
            title = { Text(stringResource(Res.string.menu_history)) },
            text = {
                val e = d.eintraege
                when {
                    e == null -> CircularProgressIndicator()
                    e.isEmpty() -> Text(stringResource(Res.string.history_empty))
                    else -> Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) { e.forEach { VerlaufZeile(it, s, viewModel) } }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dialogSchliessen) { Text(stringResource(Res.string.search_close)) } },
        )
        is Dialog.ZusammenfuehrenDialog -> ZusammenfuehrenFormular(d, s, viewModel)
        is Dialog.VerwandtschaftDialog -> VerwandtschaftFenster(d, s, viewModel)
        is Dialog.BerichtDialog -> AlertDialog(
            onDismissRequest = viewModel::dialogSchliessen,
            title = { Text(stringResource(Res.string.report_title)) },
            text = {
                val liste = d.berichte
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(d.person.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                    when {
                        d.laeuft != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(Res.string.report_running))
                        }
                        liste == null -> CircularProgressIndicator()
                        else -> liste.forEach { b ->
                            Text(s.typen[b.name] ?: b.name, Modifier.fillMaxWidth().clickable { viewModel.berichtErzeugen(d, b) }.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dialogSchliessen) { Text(stringResource(Res.string.cancel)) } },
        )
        Dialog.UeberDialog -> AlertDialog(
            onDismissRequest = viewModel::dialogSchliessen,
            title = { Text(stringResource(Res.string.more_about, s.version)) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.about_text), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.about_source), Modifier.clickable { viewModel.oeffnen(QUELLCODE) },
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dialogSchliessen) { Text(stringResource(Res.string.search_close)) } },
        )
        is Dialog.ExportDialog -> AlertDialog(
            onDismissRequest = viewModel::dialogSchliessen,
            title = { Text(stringResource(Res.string.export_title)) },
            text = {
                if (d.laeuft != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(Res.string.export_running))
                } else Column {
                    listOf("ged" to Res.string.export_ged, "ged7" to Res.string.export_ged7, "gramps" to Res.string.export_gramps, "csv" to Res.string.export_csv).forEach { (f, t) ->
                        Text(stringResource(t), Modifier.fillMaxWidth().clickable { viewModel.exportieren(f) }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dialogSchliessen) { Text(stringResource(Res.string.cancel)) } },
        )
        is Dialog.DnaDialog -> AlertDialog(
            onDismissRequest = viewModel::dialogSchliessen,
            title = { Text(stringResource(Res.string.menu_dna)) },
            text = {
                val t = d.treffer
                when {
                    t == null -> CircularProgressIndicator()
                    t.isEmpty() -> Text(stringResource(Res.string.dna_none))
                    else -> Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                        t.forEach { (p, m) ->
                            if (p != null) PersonRow(p, label = m.relation, onClick = { viewModel.dialogSchliessen(); viewModel.profilOeffnen(p.xref) })
                            Text(stringResource(Res.string.dna_cm, "%.1f".format(m.cM), m.segments.size), Modifier.padding(start = 72.dp, bottom = 8.dp),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dialogSchliessen) { Text(stringResource(Res.string.search_close)) } },
        )
        is Dialog.LoeschenDialog -> AlertDialog(
            onDismissRequest = viewModel::dialogSchliessen,
            text = { Text(d.text) },
            confirmButton = { TextButton(onClick = { viewModel.loeschenBestaetigt(d) }, enabled = !s.speichert) { Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::dialogSchliessen) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

/** Vollbild-Formular mit Kopfzeile: Schliessen links, Speichern rechts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Formular(titel: StringResource, s: Zustand, viewModel: GwViewModel, speichernAktiv: Boolean, onSpeichern: () -> Unit, inhalt: @Composable () -> Unit) {
    Dialog(onDismissRequest = viewModel::dialogSchliessen, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(titel)) },
                    navigationIcon = { IconButton(onClick = viewModel::dialogSchliessen) { Icon(Icons.Default.Close, stringResource(Res.string.cancel)) } },
                    actions = {
                        if (s.speichert) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                        else TextButton(onClick = onSpeichern, enabled = speichernAktiv) { Text(stringResource(Res.string.save)) }
                    },
                )
            },
        ) { innen ->
            Column(
                Modifier.fillMaxSize().padding(innen).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { inhalt() }
        }
    }
}

// ── Person ───────────────────────────────────────────────────────────

@Composable
private fun PersonFormular(d: Dialog.PersonDialog, s: Zustand, viewModel: GwViewModel) {
    var vorname by remember(d) { mutableStateOf(d.vorname) }
    var nachname by remember(d) { mutableStateOf(d.nachname) }
    var rufname by remember(d) { mutableStateOf(d.rufname) }
    var geschlecht by remember(d) { mutableIntStateOf(d.geschlecht) }
    var geburt by remember(d) { mutableStateOf(DateForm()) }
    val datumOk = runCatching { Schreibhilfe.datum(geburt) }.isSuccess
    Formular(if (d.handle == null) Res.string.new_person else Res.string.edit_person, s, viewModel,
        speichernAktiv = (vorname.isNotBlank() || nachname.isNotBlank()) && datumOk,
        onSpeichern = { viewModel.personSpeichern(d, vorname, nachname, rufname, geschlecht, geburt.takeIf { d.handle == null && !it.isEmpty }) }) {
        Feld(vorname, { vorname = it }, Res.string.field_given)
        Feld(nachname, { nachname = it }, Res.string.field_surname)
        if (d.handle != null) Feld(rufname, { rufname = it }, Res.string.field_call)
        GeschlechtWahl(geschlecht) { geschlecht = it }
        if (d.handle == null) {
            Text(stringResource(Res.string.birth_optional), style = MaterialTheme.typography.titleSmall)
            DatumFeld(geburt) { geburt = it }
        }
    }
}

@Composable
private fun GeschlechtWahl(wert: Int, onWahl: (Int) -> Unit) {
    val optionen = listOf(1 to Res.string.gender_m, 0 to Res.string.gender_f, 2 to Res.string.gender_u)
    Text(stringResource(Res.string.field_gender), style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        optionen.forEachIndexed { i, (g, text) ->
            SegmentedButton(selected = wert == g, onClick = { onWahl(g) }, shape = SegmentedButtonDefaults.itemShape(i, optionen.size)) { Text(stringResource(text)) }
        }
    }
}

// ── Ereignis ─────────────────────────────────────────────────────────

@Composable
private fun EreignisFormular(d: Dialog.EreignisDialog, s: Zustand, viewModel: GwViewModel) {
    var typ by remember(d) { mutableStateOf(d.typ) }
    var datum by remember(d) { mutableStateOf(d.datum) }
    var ort by remember(d) { mutableStateOf(d.ort) }
    var ortText by remember(d) { mutableStateOf(d.ortName) }
    var ortNeu by remember(d) { mutableStateOf<String?>(null) }
    var beschreibung by remember(d) { mutableStateOf(d.beschreibung) }
    val datumOk = datum?.let { runCatching { Schreibhilfe.datum(it) }.isSuccess } ?: true
    Formular(if (d.ereignis == null) Res.string.new_event else Res.string.edit_event, s, viewModel, speichernAktiv = datumOk,
        onSpeichern = { viewModel.ereignisSpeichern(d, typ, datum, ort, ortNeu, beschreibung) }) {
        TypWahl(typ, s) { typ = it }
        Text(stringResource(Res.string.field_date), style = MaterialTheme.typography.labelLarge)
        val aktuell = datum
        if (aktuell == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.date_kept, Anzeige.anzeigeDatum(d.datumText)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { datum = DateForm() }) { Text(stringResource(Res.string.date_replace)) }
            }
        } else {
            DatumFeld(aktuell) { datum = it }
            if (!datumOk) Text(stringResource(Res.string.date_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OrtFeld(ortText, s, viewModel, onText = { ortText = it; ort = null; ortNeu = it.takeIf { t -> t.isNotBlank() } }, onOrt = { h, name -> ort = h; ortText = name; ortNeu = null })
        Feld(beschreibung, { beschreibung = it }, Res.string.field_description)
        if (d.ereignis != null && d.person != null) {
            OutlinedButton(onClick = { viewModel.ereignisLoeschen(d) }) { Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypWahl(typ: String, s: Zustand, onWahl: (String) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    val alle = (HAEUFIGE_TYPEN + s.ereignisTypen.filter { it !in HAEUFIGE_TYPEN }.sortedBy { s.typen[it] ?: it }).distinct()
    ExposedDropdownMenuBox(offen, { offen = it }) {
        OutlinedTextField(
            s.typen[typ] ?: typ, {}, readOnly = true, label = { Text(stringResource(Res.string.field_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(offen) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(offen, { offen = false }, Modifier.heightIn(max = 420.dp)) {
            alle.forEachIndexed { i, t ->
                if (i == HAEUFIGE_TYPEN.size) HorizontalDivider()
                DropdownMenuItem(text = { Text(s.typen[t] ?: t) }, onClick = { onWahl(t); offen = false })
            }
        }
    }
}

/** Datum als Genauigkeit plus Tag/Monat/Jahr; bei "zwischen" ein zweites Datum. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DatumFeld(form: DateForm, onChange: (DateForm) -> Unit) {
    val arten = listOf(DateQualifier.EXACT to Res.string.date_exact, DateQualifier.ABOUT to Res.string.date_about,
        DateQualifier.BEFORE to Res.string.date_before, DateQualifier.AFTER to Res.string.date_after, DateQualifier.BETWEEN to Res.string.date_between)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        arten.forEach { (q, text) -> FilterChip(form.qualifier == q, { onChange(form.copy(qualifier = q)) }, label = { Text(stringResource(text)) }) }
    }
    DatumTeile(form.first) { onChange(form.copy(first = it)) }
    if (form.qualifier == DateQualifier.BETWEEN) {
        Text(stringResource(Res.string.date_and), style = MaterialTheme.typography.labelLarge)
        DatumTeile(form.second) { onChange(form.copy(second = it)) }
    }
}

@Composable
private fun DatumTeile(teil: DatePart, onChange: (DatePart) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(teil.day, { onChange(teil.copy(day = it.filter(Char::isDigit).take(2))) }, Modifier.width(72.dp), singleLine = true,
            label = { Text(stringResource(Res.string.date_day)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(if (teil.month == 0) "" else teil.month.toString(), { t -> onChange(teil.copy(month = t.filter(Char::isDigit).take(2).toIntOrNull() ?: 0)) },
            Modifier.width(80.dp), singleLine = true, label = { Text(stringResource(Res.string.date_month)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(teil.year, { onChange(teil.copy(year = it.filter(Char::isDigit).take(4))) }, Modifier.weight(1f), singleLine = true,
            label = { Text(stringResource(Res.string.date_year)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

/** Ort waehlen: Tippen sucht vorhandene Orte; ohne Wahl wird der getippte Name als neuer Ort angelegt. */
@Composable
private fun OrtFeld(text: String, s: Zustand, viewModel: GwViewModel, onText: (String) -> Unit, onOrt: (String, String) -> Unit) {
    var suche by remember { mutableStateOf(false) }
    // Nach der Wahl den Fokus aus dem Feld nehmen: sonst landet weiteres Tippen im Ortsnamen (und legt einen neuen Ort an).
    val fokus = androidx.compose.ui.platform.LocalFocusManager.current
    OutlinedTextField(text, { onText(it); suche = true; viewModel.ortVorschlaege(it) }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(Res.string.field_place)) })
    if (suche && text.isNotBlank()) {
        Column {
            s.ortVorschlaege.take(8).forEach { o ->
                Text(o.anzeigename, Modifier.fillMaxWidth().clickable { onOrt(o.handle, o.anzeigename); suche = false; fokus.clearFocus() }.padding(vertical = 10.dp, horizontal = 4.dp))
            }
            if (s.ortVorschlaege.none { it.anzeigename.equals(text.trim(), ignoreCase = true) }) {
                Text(stringResource(Res.string.place_new, text.trim()), Modifier.fillMaxWidth().clickable { suche = false; fokus.clearFocus() }.padding(vertical = 10.dp, horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ── Verwandte ────────────────────────────────────────────────────────

@Composable
private fun VerwandterFormular(d: Dialog.VerwandterDialog, s: Zustand, viewModel: GwViewModel) {
    var art by remember(d) { mutableStateOf(d.art ?: Verwandtschaftsart.Kind) }
    var vorhanden by remember(d) { mutableStateOf(false) }
    var gewaehlt by remember(d) { mutableStateOf<Person?>(null) }
    var vorname by remember(d) { mutableStateOf("") }
    var nachname by remember(d) { mutableStateOf(if (art == Verwandtschaftsart.Partner) "" else d.zuName.substringAfterLast(' ')) }
    var geschlecht by remember(d) { mutableIntStateOf(2) }
    var geburt by remember(d) { mutableStateOf(DateForm()) }
    val datumOk = runCatching { Schreibhilfe.datum(geburt) }.isSuccess
    val bereit = if (vorhanden) gewaehlt != null else (vorname.isNotBlank() || nachname.isNotBlank()) && datumOk
    Formular(Res.string.add_relative, s, viewModel, speichernAktiv = bereit, onSpeichern = {
        val g = when (art) { Verwandtschaftsart.Vater -> 1; Verwandtschaftsart.Mutter -> 0; else -> geschlecht }
        viewModel.verwandterSpeichern(d, art,
            neu = if (vorhanden) null else NeuePerson(vorname, nachname, g, geburt.takeIf { !it.isEmpty }?.let(Schreibhilfe::datum)),
            vorhanden = if (vorhanden) gewaehlt?.xref else null)
    }) {
        Text(d.zuName, style = MaterialTheme.typography.titleMedium)
        if (d.art == null) {
            val arten = listOf(Verwandtschaftsart.Vater to Res.string.rel_father, Verwandtschaftsart.Mutter to Res.string.rel_mother,
                Verwandtschaftsart.Partner to Res.string.rel_partner, Verwandtschaftsart.Kind to Res.string.rel_child)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                arten.forEachIndexed { i, (a, text) ->
                    SegmentedButton(selected = art == a, onClick = { art = a }, shape = SegmentedButtonDefaults.itemShape(i, arten.size)) { Text(stringResource(text), maxLines = 1) }
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(!vorhanden, { vorhanden = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(Res.string.relative_new)) }
            SegmentedButton(vorhanden, { vorhanden = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(Res.string.relative_existing)) }
        }
        if (vorhanden) {
            PersonSuche(s, viewModel, gewaehlt) { gewaehlt = it }
        } else {
            Feld(vorname, { vorname = it }, Res.string.field_given)
            Feld(nachname, { nachname = it }, Res.string.field_surname)
            if (art == Verwandtschaftsart.Partner || art == Verwandtschaftsart.Kind) GeschlechtWahl(geschlecht) { geschlecht = it }
            Text(stringResource(Res.string.birth_optional), style = MaterialTheme.typography.titleSmall)
            DatumFeld(geburt) { geburt = it }
        }
    }
}

@Composable
private fun PersonSuche(s: Zustand, viewModel: GwViewModel, gewaehlt: Person?, onWahl: (Person) -> Unit) {
    var text by remember { mutableStateOf("") }
    OutlinedTextField(text, { text = it; viewModel.personVorschlaege(it) }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(Res.string.search_person)) })
    gewaehlt?.let { PersonRow(it, selected = true) }
    s.personVorschlaege.filter { it.xref != gewaehlt?.xref }.take(12).forEach { p -> PersonRow(p, onClick = { onWahl(p) }) }
}

// ── Foto ─────────────────────────────────────────────────────────────

@Composable
private fun FotoFormular(d: Dialog.FotoDialog, s: Zustand, viewModel: GwViewModel) {
    var daten by remember(d) { mutableStateOf<ByteArray?>(null) }
    var name by remember(d) { mutableStateOf("") }
    var beschreibung by remember(d) { mutableStateOf("") }
    var hauptbild by remember(d) { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val quellen = rememberPhotoSources { foto ->
        name = foto.name
        scope.launch {
            // Verkleinern auf hoechstens 2560 Pixel und 4 MB, sonst unveraendert
            daten = withContext(Dispatchers.IO) { foto.prepareJpeg(4L * 1024 * 1024) ?: runCatching { foto.readBytes() }.getOrNull() }
        }
    }
    Formular(Res.string.add_photo, s, viewModel, speichernAktiv = daten != null,
        onSpeichern = { daten?.let { viewModel.fotoSpeichern(d, it, "image/jpeg", beschreibung, hauptbild) } }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (quellen.canTake) OutlinedButton(onClick = quellen.take) { Text(stringResource(Res.string.photo_camera)) }
            OutlinedButton(onClick = quellen.pick) { Text(stringResource(Res.string.photo_gallery)) }
        }
        if (daten != null) Text(stringResource(Res.string.photo_chosen) + ": " + name, style = MaterialTheme.typography.bodyMedium)
        Feld(beschreibung, { beschreibung = it }, Res.string.photo_description)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { hauptbild = !hauptbild }) {
            Checkbox(hauptbild, { hauptbild = it })
            Text(stringResource(Res.string.photo_primary))
        }
    }
}

// ── Zitat ────────────────────────────────────────────────────────────

@Composable
private fun ZitatFormular(d: Dialog.ZitatDialog, s: Zustand, viewModel: GwViewModel) {
    var ziel by remember(d) { mutableStateOf(d.ziele.first().first) }
    var quelle by remember(d) { mutableStateOf<de.bgghome.gwand.api.Quelle?>(null) }
    var suche by remember(d) { mutableStateOf("") }
    var seite by remember(d) { mutableStateOf("") }
    var vertrauen by remember(d) { mutableIntStateOf(2) }
    Formular(Res.string.add_citation, s, viewModel, speichernAktiv = quelle != null,
        onSpeichern = { quelle?.let { viewModel.zitatSpeichern(ziel, it.handle, seite, vertrauen) } }) {
        Text(stringResource(Res.string.field_target), style = MaterialTheme.typography.labelLarge)
        d.ziele.forEach { (z, text) ->
            Row(Modifier.fillMaxWidth().clickable { ziel = z }, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.RadioButton(ziel == z, { ziel = z })
                Text(text)
            }
        }
        Text(stringResource(Res.string.field_source), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(suche, { suche = it; viewModel.quellVorschlaege(it) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(Res.string.more_search)) })
        quelle?.let { Text(it.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge) }
        s.quellVorschlaege.filter { it.handle != quelle?.handle }.take(10).forEach { q ->
            Text(q.title, Modifier.fillMaxWidth().clickable { quelle = q }.padding(vertical = 8.dp))
        }
        Feld(seite, { seite = it }, Res.string.field_page)
        Text(stringResource(Res.string.field_confidence), style = MaterialTheme.typography.labelLarge)
        val stufen = listOf(Res.string.conf_0, Res.string.conf_1, Res.string.conf_2, Res.string.conf_3, Res.string.conf_4)
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stufen.forEachIndexed { i, t -> FilterChip(vertrauen == i, { vertrauen = i }, label = { Text(stringResource(t)) }) }
        }
    }
}

// ── Beziehung ────────────────────────────────────────────────────────

@Composable
private fun BeziehungFormular(d: Dialog.BeziehungDialog, s: Zustand, viewModel: GwViewModel) {
    var gewaehlt by remember(d) { mutableStateOf<Person?>(null) }
    var art by remember(d) { mutableStateOf("") }
    Formular(Res.string.add_association, s, viewModel, speichernAktiv = gewaehlt != null && art.isNotBlank(),
        onSpeichern = { gewaehlt?.let { viewModel.beziehungSpeichern(d, it.xref, art) } }) {
        OutlinedTextField(art, { art = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(Res.string.association_type)) }, placeholder = { Text(stringResource(Res.string.association_hint)) })
        PersonSuche(s, viewModel, gewaehlt) { gewaehlt = it }
    }
}

@Composable
private fun Feld(wert: String, onChange: (String) -> Unit, label: StringResource) {
    OutlinedTextField(wert, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(label)) })
}

// ── Zusammenfuehren ──────────────────────────────────────────────────

@Composable
private fun ZusammenfuehrenFormular(d: Dialog.ZusammenfuehrenDialog, s: Zustand, viewModel: GwViewModel) {
    Formular(Res.string.merge_title, s, viewModel, speichernAktiv = d.geht != null && d.geht.xref != d.bleibt.xref,
        onSpeichern = { viewModel.zusammenfuehren(d) }) {
        PersonRow(d.bleibt, label = stringResource(Res.string.merge_keeps), selected = true)
        if (d.geht != null) {
            PersonRow(d.geht, label = stringResource(Res.string.merge_goes))
            OutlinedButton(onClick = { viewModel.zusammenfuehrenTauschen(d) }) { Text(stringResource(Res.string.merge_swap)) }
        }
        PersonSuche(s, viewModel, d.geht) { viewModel.zusammenfuehrenWahl(d, it) }
    }
}

// ── Verwandtschaft ───────────────────────────────────────────────────

@Composable
private fun VerwandtschaftFenster(d: Dialog.VerwandtschaftDialog, s: Zustand, viewModel: GwViewModel) {
    Formular(Res.string.menu_relationship, s, viewModel, speichernAktiv = false, onSpeichern = {}) {
        PersonRow(d.von, selected = true)
        val zu = d.zu
        if (zu != null) {
            PersonRow(zu)
            val wege = d.wege
            when {
                d.laedt || wege == null -> CircularProgressIndicator()
                wege.isEmpty() -> Text(stringResource(Res.string.rel_none))
                else -> wege.forEach { (text, vorfahren) ->
                    Text(text.ifBlank { "–" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    if (vorfahren.isNotEmpty()) {
                        Text(stringResource(Res.string.rel_common), style = MaterialTheme.typography.labelLarge)
                        vorfahren.forEach { v -> PersonRow(v, onClick = { viewModel.dialogSchliessen(); viewModel.profilOeffnen(v.xref) }) }
                    }
                    HorizontalDivider()
                }
            }
        }
        PersonSuche(s, viewModel, zu) { viewModel.verwandtschaftBerechnen(d, it) }
    }
}
