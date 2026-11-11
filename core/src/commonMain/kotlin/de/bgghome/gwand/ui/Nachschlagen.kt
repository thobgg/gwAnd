package de.bgghome.gwand.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.gwand.daten.Anzeige
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.call_number
import de.bgghome.gwand.res.change_0
import de.bgghome.gwand.res.change_1
import de.bgghome.gwand.res.change_2
import de.bgghome.gwand.res.history_empty
import de.bgghome.gwand.res.more_history
import de.bgghome.gwand.res.undo
import de.bgghome.gwand.res.more_places
import de.bgghome.gwand.res.more_repositories
import de.bgghome.gwand.res.more_search
import de.bgghome.gwand.res.more_sources
import de.bgghome.gwand.res.place_events
import de.bgghome.gwand.res.place_parents
import de.bgghome.gwand.res.profile_back
import de.bgghome.gwand.res.repo_address
import de.bgghome.gwand.res.repo_sources
import de.bgghome.gwand.res.entries_count
import de.bgghome.gwand.res.source_author
import de.bgghome.gwand.res.source_citations
import de.bgghome.gwand.res.source_pubinfo
import de.bgghome.gwand.res.source_repositories
import de.bgghome.gwand.res.tab_notes
import de.bgghome.webtrees.nativ.ui.karte.KachelEbene
import de.bgghome.webtrees.nativ.ui.karte.KachelKarte
import de.bgghome.webtrees.nativ.ui.karte.KartenPin
import de.bgghome.webtrees.nativ.ui.karte.KartenZustand
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Reiter "Mehr": Orte, Quellen und Archive nachschlagen, mit Suche und Nachladen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MehrSeite(s: Zustand, viewModel: GwViewModel) {
    Column(Modifier.fillMaxSize()) {
        val arten = listOf(Nachschlagart.Orte to Res.string.more_places, Nachschlagart.Quellen to Res.string.more_sources,
            Nachschlagart.Archive to Res.string.more_repositories, Nachschlagart.Verlauf to Res.string.more_history)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            arten.forEachIndexed { i, (art, text) ->
                SegmentedButton(
                    selected = s.nachschlagart == art, onClick = { viewModel.nachschlagen(art, "") },
                    shape = SegmentedButtonDefaults.itemShape(i, arten.size),
                ) { Text(stringResource(text), maxLines = 1) }
            }
        }
        if (s.nachschlagart != Nachschlagart.Verlauf) OutlinedTextField(
            s.nachschlagSuche, { viewModel.nachschlagen(s.nachschlagart, it) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
            placeholder = { Text(stringResource(Res.string.more_search)) }, leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        Text(pluralStringResource(Res.plurals.entries_count, s.nachschlagGesamt, s.nachschlagGesamt),
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxSize()) {
            when (s.nachschlagart) {
                Nachschlagart.Orte -> itemsIndexed(s.orte, key = { _, o -> o.handle }) { i, o ->
                    if (i >= s.orte.size - 10) LaunchedEffect(s.orte.size) { viewModel.weitereNachschlagen() }
                    Zeile(o.anzeigename, s.typen[o.art] ?: o.art.takeIf { it != "Unknown" }) { viewModel.ortOeffnen(o.handle) }
                }
                Nachschlagart.Quellen -> itemsIndexed(s.quellenListe, key = { _, q -> q.handle }) { i, q ->
                    if (i >= s.quellenListe.size - 10) LaunchedEffect(s.quellenListe.size) { viewModel.weitereNachschlagen() }
                    Zeile(q.title, q.author.ifBlank { null }) { viewModel.quelleOeffnen(q.handle) }
                }
                Nachschlagart.Verlauf -> {
                    if (s.verlauf.isEmpty() && !s.nachschlagLaedt) item { Text(stringResource(Res.string.history_empty), Modifier.padding(16.dp)) }
                    items(s.verlauf, key = { it.vorgang }) { v -> VerlaufZeile(v, s, viewModel) }
                }
                Nachschlagart.Archive -> itemsIndexed(s.archive, key = { _, a -> a.handle }) { i, a ->
                    if (i >= s.archive.size - 10) LaunchedEffect(s.archive.size) { viewModel.weitereNachschlagen() }
                    Zeile(a.name, a.adressen.firstOrNull()?.text?.ifBlank { null } ?: s.typen[a.type] ?: a.type) { viewModel.archivOeffnen(a.handle) }
                }
            }
            if (s.nachschlagLaedt) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
        }
    }
}

@Composable
private fun Zeile(titel: String, untertitel: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(titel, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        untertitel?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Detailseite eines Orts, einer Quelle oder eines Archivs, mit eigener Kopfzeile und Zurueck. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSeite(d: Detail, s: Zustand, viewModel: GwViewModel) {
    val titel = when (d) {
        is Detail.OrtDetail -> d.ort.anzeigename
        is Detail.QuelleDetail -> d.quelle.title
        is Detail.ArchivDetail -> d.archiv.name
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = viewModel::detailSchliessen) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.profile_back)) } },
                colors = kopfFarben(),
            )
        },
    ) { innen ->
        LazyColumn(Modifier.fillMaxSize().padding(innen), contentPadding = PaddingValues(bottom = 24.dp)) {
            when (d) {
                is Detail.OrtDetail -> ort(d, s)
                is Detail.QuelleDetail -> quelle(d, s, viewModel)
                is Detail.ArchivDetail -> archiv(d, viewModel)
            }
        }
    }
}

private fun LazyListScope.kopf(titel: String, zeilen: List<String>) {
    item {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            zeilen.filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun LazyListScope.ueber(text: StringResource) {
    item { Text(stringResource(text), Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
}

private fun LazyListScope.ort(d: Detail.OrtDetail, s: Zustand) {
    val o = d.ort
    val p = o.profile
    kopf(o.anzeigename, listOfNotNull(s.typen[o.art] ?: o.art.takeIf { it != "Unknown" }, o.grampsId))
    val lat = p?.lat
    val lon = p?.long
    if (lat != null && lon != null) {
        item {
            val farbe = MaterialTheme.colorScheme.primary
            val zustand = remember(o.handle) { KartenZustand(lat, lon, 12) }
            KachelKarte(zustand, KachelEbene.STANDARD, Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)),
                pins = listOf(KartenPin(lat, lon, farbe = farbe)))
        }
    }
    p?.uebergeordnet?.takeIf { it.isNotEmpty() }?.let { eltern ->
        ueber(Res.string.place_parents)
        items(eltern) { e -> Text(e.anzeigename.ifBlank { e.name }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyLarge) }
    }
    val ereignisse = p?.references?.event.orEmpty()
    if (ereignisse.isNotEmpty()) {
        ueber(Res.string.place_events)
        items(ereignisse.sortedBy { it.date }) { e ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(e.summary.ifBlank { e.type }, style = MaterialTheme.typography.bodyLarge)
                if (e.date.isNotBlank()) Text(Anzeige.anzeigeDatum(e.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun LazyListScope.quelle(d: Detail.QuelleDetail, s: Zustand, viewModel: GwViewModel) {
    val q = d.quelle
    kopf(q.title, listOf(q.grampsId))
    if (q.author.isNotBlank()) { ueber(Res.string.source_author); item { Text(q.author, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge) } }
    if (q.pubinfo.isNotBlank()) { ueber(Res.string.source_pubinfo); item { Text(q.pubinfo, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge) } }
    val archive = q.extended?.repositories.orEmpty().associateBy { it.handle }
    if (q.archivverweise.isNotEmpty()) {
        ueber(Res.string.source_repositories)
        items(q.archivverweise) { v ->
            val a = archive[v.ref]
            Column(Modifier.fillMaxWidth().clickable(enabled = a != null) { viewModel.archivOeffnen(v.ref) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(a?.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                val zusatz = listOfNotNull(v.signatur.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.call_number, it) },
                    s.typen[v.medienart] ?: v.medienart.takeIf { it.isNotBlank() && it != "Unknown" }).joinToString(" · ")
                if (zusatz.isNotBlank()) Text(zusatz, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val notizen = q.extended?.notes.orEmpty().filter { !it.private && it.type != "GEDCOM import" && it.text.string.isNotBlank() }
    if (notizen.isNotEmpty()) {
        ueber(Res.string.tab_notes)
        items(notizen) { n -> Text(n.text.string, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium) }
    }
    val zitate = q.profile?.references?.citation.orEmpty()
    if (zitate.isNotEmpty()) {
        ueber(Res.string.source_citations)
        items(zitate) { z ->
            Text(z.page.ifBlank { z.grampsId }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun LazyListScope.archiv(d: Detail.ArchivDetail, viewModel: GwViewModel) {
    val a = d.archiv
    kopf(a.name, listOf(a.grampsId))
    val adressen = a.adressen.map { it.text }.filter { it.isNotBlank() }
    if (adressen.isNotEmpty()) {
        ueber(Res.string.repo_address)
        items(adressen) { t -> Text(t, Modifier.padding(horizontal = 16.dp, vertical = 2.dp), style = MaterialTheme.typography.bodyLarge) }
    }
    if (a.urls.isNotEmpty()) {
        items(a.urls) { u ->
            Text(u.desc.ifBlank { u.path }, Modifier.clickable { viewModel.oeffnen(u.path) }.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
    if (d.quellen.isNotEmpty()) {
        ueber(Res.string.repo_sources)
        items(d.quellen, key = { it.handle }) { q -> Zeile(q.title, q.author.ifBlank { null }) { viewModel.quelleOeffnen(q.handle) } }
    }
}

/** Ein Vorgang im Verlauf: wann, wer, was; Personen antippbar; Rueckgaengig fuer Bearbeiter. */
@Composable
fun VerlaufZeile(v: Verlaufseintrag, s: Zustand, viewModel: GwViewModel) {
    val zeit = java.time.Instant.ofEpochMilli((v.wann * 1000).toLong()).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    val arten = listOf(Res.string.change_0, Res.string.change_1, Res.string.change_2).map { stringResource(it) }
    val sprache = java.util.Locale.getDefault().language
    val was = v.aenderungen.joinToString(" · ") { (schluessel, n) ->
        val (klasse, art) = schluessel.split('|').let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 1) }
        (if (n > 1) "$n× " else "") + de.bgghome.gwand.daten.Typnamen.klasse(klasse, sprache) + " " + arten.getOrElse(art) { "" }
    }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$zeit · ${v.wer}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(was, style = MaterialTheme.typography.bodyMedium)
            v.personen.take(4).forEach { p ->
                Text(p.name, Modifier.clickable { viewModel.profilOeffnen(p.xref) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (s.darfBearbeiten) androidx.compose.material3.TextButton(onClick = { viewModel.rueckgaengig(v.vorgang) }) { Text(stringResource(Res.string.undo)) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
