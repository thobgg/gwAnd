package de.bgghome.gwand.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.bgghome.gwand.res.Res
import de.bgghome.webtrees.nativ.res.Res as WRes
import de.bgghome.webtrees.nativ.res.anniv_in_days
import de.bgghome.webtrees.nativ.res.anniv_living_only
import de.bgghome.webtrees.nativ.res.anniv_today
import de.bgghome.webtrees.nativ.res.anniv_tomorrow
import de.bgghome.gwand.res.home_anniversaries
import de.bgghome.gwand.res.new_person
import de.bgghome.gwand.res.home_anniversaries_none
import de.bgghome.gwand.res.home_bookmarks
import de.bgghome.gwand.res.home_changed
import de.bgghome.gwand.res.home_living
import de.bgghome.gwand.res.home_seen
import de.bgghome.gwand.res.home_today
import de.bgghome.gwand.res.home_years
import de.bgghome.gwand.res.photos_count
import de.bgghome.gwand.res.photo_faces
import de.bgghome.gwand.res.photo_ocr
import de.bgghome.gwand.res.search_count
import de.bgghome.gwand.res.search_hint
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.ui.Avatar
import de.bgghome.webtrees.nativ.ui.PersonRow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate

// ── Start ────────────────────────────────────────────────────────────

@Composable
fun Startseite(s: Zustand, viewModel: GwViewModel) {
    val heute = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        // Ueberschrift mit Filter "Nur Lebende" (wie in wtAnd)
        item(key = "jahrestage-kopf") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.home_anniversaries), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.FilterChip(
                    selected = s.nurLebende, onClick = { viewModel.nurLebende(!s.nurLebende) },
                    label = { Text(stringResource(WRes.string.anniv_living_only)) },
                )
            }
        }
        val jahrestage = if (s.nurLebende) s.jahrestage.filter { it.lebend } else s.jahrestage
        if (jahrestage.isEmpty()) {
            item {
                if (s.startLaedt) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                else Text(stringResource(Res.string.home_anniversaries_none, s.jahrestageTage), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(jahrestage, key = { j -> j.typ + j.personen.joinToString { it.xref } + j.monat + j.tag }) { j ->
            val istHeute = j.monat == heute.monthValue && j.tag == heute.dayOfMonth
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { viewModel.profilOeffnen(j.personen.first().xref) },
                colors = CardDefaults.cardColors(containerColor = if (istHeute) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                        Avatar(j.personen.first(), 48.dp)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(j.personen.joinToString(" & ") { it.name }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val wann = when (val n = tageBis(heute, j.monat, j.tag)) {
                            0 -> stringResource(WRes.string.anniv_today)
                            1 -> stringResource(WRes.string.anniv_tomorrow)
                            else -> stringResource(WRes.string.anniv_in_days, n) + " · %02d.%02d.".format(j.tag, j.monat)
                        }
                        val lebend = if (j.lebend) " · " + stringResource(Res.string.home_living) else ""
                        Text("${j.art} · $wann$lebend", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(Res.string.home_years, j.jahre), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End)
                }
            }
        }
        if (s.zuletztGesehen.isNotEmpty()) {
            abschnitt(Res.string.home_seen)
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.zuletztGesehen, key = { it.xref }) { p ->
                        Column(Modifier.width(76.dp).clickable { viewModel.profilOeffnen(p.xref) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(p, 56.dp)
                            Text(p.given.ifBlank { p.name }, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Text(p.surname, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        personenAbschnitt(Res.string.home_bookmarks, s.lesezeichen, viewModel, "l")
        personenAbschnitt(Res.string.home_changed, s.zuletztGeaendert, viewModel, "a")
    }
}

private fun LazyListScope.abschnitt(titel: StringResource) {
    item(key = "t" + titel.key) {
        Text(stringResource(titel), Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun LazyListScope.personenAbschnitt(titel: StringResource, personen: List<Person>, viewModel: GwViewModel, praefix: String) {
    if (personen.isEmpty()) return
    abschnitt(titel)
    items(personen, key = { praefix + it.xref }) { p -> PersonRow(p, onClick = { viewModel.profilOeffnen(p.xref) }) }
}

// ── Personen ─────────────────────────────────────────────────────────

@Composable
fun PersonenSeite(s: Zustand, viewModel: GwViewModel) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            s.suche, viewModel::suchen, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true,
            placeholder = { Text(stringResource(Res.string.search_hint)) }, leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        Text(pluralStringResource(Res.plurals.search_count, s.trefferGesamt, s.trefferGesamt), Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(s.treffer, key = { _, p -> p.xref }) { i, p ->
                if (i >= s.treffer.size - 10) LaunchedEffect(s.treffer.size) { viewModel.weitereTreffer() }
                PersonRow(p, onClick = { viewModel.profilOeffnen(p.xref) })
                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (s.sucheLaedt) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
        }
    }
    // Neue Personen duerfen schon Beitragende anlegen (Rolle 2); alles Weitere aendert Vorhandenes und braucht Editor.
    if (s.konto?.darfAnlegen == true) {
        androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = viewModel::neuePerson, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text(stringResource(Res.string.new_person)) },
        )
    }
    }
}

// ── Fotos ────────────────────────────────────────────────────────────

@Composable
fun FotoSeite(s: Zustand, viewModel: GwViewModel) {
    Column(Modifier.fillMaxSize()) {
        if (s.fotosGesamt > 0) {
            Text(stringResource(Res.string.photos_count, s.fotosGesamt), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(s.fotos, key = { _, m -> m.handle }) { i, m ->
                if (i >= s.fotos.size - 12) LaunchedEffect(s.fotos.size) { viewModel.weitereFotos() }
                BildMitMenue(
                    url = viewModel.vorschau(m), beschreibung = m.desc, onClick = { viewModel.fotoOeffnen(i) },
                    aktionen = if (!m.istBild) emptyList() else listOf(
                        Res.string.photo_faces to { viewModel.gesichterOeffnen(m.handle, m.desc) },
                        Res.string.photo_ocr to { viewModel.textErkennen(m) },
                    ),
                    art = if (m.istBild) null else dateiArt(m), vorschau = m.hatVorschau,
                )
            }
        }
    }
}

/** Quadratisches Vorschaubild; Tipp oeffnet, langer Druck zeigt ein Menue mit [aktionen]. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BildMitMenue(
    url: String, beschreibung: String?, onClick: () -> Unit, aktionen: List<Pair<org.jetbrains.compose.resources.StringResource, () -> Unit>>,
    ausschnitt: Modifier = Modifier,
    /** Kennzeichen fuer Dokumente ("PDF", "DOCX" …); null bei Bildern. */
    art: String? = null,
    /** false: der Server hat kein Vorschaubild (z. B. Word-Datei) - dann Kennzeichen und Name statt Bild. */
    vorschau: Boolean = true,
) {
    var offen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        val form = ausschnitt.aspectRatio(1f).clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = if (aktionen.isEmpty()) null else { { offen = true } })
        if (vorschau) AsyncImage(model = url, contentDescription = beschreibung, contentScale = ContentScale.Crop, modifier = form)
        else Box(form.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp), contentAlignment = Alignment.Center) {
            Text(beschreibung.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        // Kennzeichen unten links: zeigt, dass es ein Dokument ist und kein Foto
        art?.let {
            Text(it, Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary).padding(horizontal = 5.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
        }
        androidx.compose.material3.DropdownMenu(offen, onDismissRequest = { offen = false }) {
            aktionen.forEach { (text, aktion) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(text)) }, onClick = { offen = false; aktion() })
            }
        }
    }
}

/** Kurzes Kennzeichen der Dateiart fuer Dokumente: PDF, DOCX, MP3 … */
fun dateiArt(m: de.bgghome.gwand.api.Medium): String =
    if (m.istPdf) "PDF" else m.dateiname.substringAfterLast('.', "").uppercase().take(5).ifBlank { m.mime.substringAfter('/').uppercase().take(5) }

/** Tage bis zum naechsten (Monat, Tag) ab [heute]; der 29. Februar zaehlt in Nicht-Schaltjahren als 28. */
fun tageBis(heute: LocalDate, monat: Int, tag: Int): Int {
    fun am(jahr: Int) = LocalDate.of(jahr, monat, minOf(tag, java.time.YearMonth.of(jahr, monat).lengthOfMonth()))
    val dieses = am(heute.year)
    val ziel = if (dieses.isBefore(heute)) am(heute.year + 1) else dieses
    return java.time.temporal.ChronoUnit.DAYS.between(heute, ziel).toInt()
}
