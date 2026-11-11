package de.bgghome.gwand.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import de.bgghome.gwand.res.home_anniversaries
import de.bgghome.gwand.res.home_anniversaries_none
import de.bgghome.gwand.res.home_bookmarks
import de.bgghome.gwand.res.home_changed
import de.bgghome.gwand.res.home_living
import de.bgghome.gwand.res.home_seen
import de.bgghome.gwand.res.home_today
import de.bgghome.gwand.res.home_years
import de.bgghome.gwand.res.photos_count
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
        abschnitt(Res.string.home_anniversaries)
        if (s.jahrestage.isEmpty()) {
            item {
                if (s.startLaedt) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                else Text(stringResource(Res.string.home_anniversaries_none, s.jahrestageTage), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(s.jahrestage, key = { j -> j.typ + j.personen.joinToString { it.xref } + j.monat + j.tag }) { j ->
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
                        val wann = if (istHeute) stringResource(Res.string.home_today) else "%02d.%02d.".format(j.tag, j.monat)
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
                AsyncImage(
                    model = viewModel.vorschau(m), contentDescription = m.desc, contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).clickable { viewModel.fotoOeffnen(i) },
                )
            }
        }
    }
}
