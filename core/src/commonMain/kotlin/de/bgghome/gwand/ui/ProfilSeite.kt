package de.bgghome.gwand.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.bgghome.gwand.api.FamilienProfil
import de.bgghome.gwand.api.GrampsPerson
import de.bgghome.gwand.api.ZeitleistenEintrag
import de.bgghome.gwand.daten.Anzeige
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.add_event_family
import de.bgghome.gwand.res.delete
import de.bgghome.gwand.res.edit_person
import de.bgghome.gwand.res.move_down
import de.bgghome.gwand.res.move_up
import de.bgghome.gwand.res.menu_history
import de.bgghome.gwand.res.menu_relationship
import de.bgghome.gwand.res.menu_report
import de.bgghome.gwand.res.menu_dna
import de.bgghome.gwand.res.menu_merge
import de.bgghome.gwand.res.make_primary_photo
import de.bgghome.gwand.res.photo_faces
import de.bgghome.gwand.res.photo_ocr
import de.bgghome.gwand.res.rel_child
import de.bgghome.gwand.res.unlink
import de.bgghome.gwand.res.age_at
import de.bgghome.gwand.res.conf_0
import de.bgghome.gwand.res.conf_1
import de.bgghome.gwand.res.conf_2
import de.bgghome.gwand.res.conf_3
import de.bgghome.gwand.res.conf_4
import de.bgghome.gwand.res.conf_label
import de.bgghome.gwand.res.family_associations
import de.bgghome.gwand.res.family_children
import de.bgghome.gwand.res.family_half
import de.bgghome.gwand.res.family_married
import de.bgghome.gwand.res.family_parents
import de.bgghome.gwand.res.family_partner
import de.bgghome.gwand.res.family_siblings
import de.bgghome.gwand.res.menu_more
import de.bgghome.gwand.res.menu_start_person
import de.bgghome.gwand.res.none_map
import de.bgghome.gwand.res.none_notes
import de.bgghome.gwand.res.none_photos
import de.bgghome.gwand.res.none_sources
import de.bgghome.gwand.res.profile_back
import de.bgghome.gwand.res.profile_bookmark
import de.bgghome.gwand.res.profile_browser
import de.bgghome.gwand.res.profile_relation
import de.bgghome.gwand.res.profile_share
import de.bgghome.gwand.res.profile_tree
import de.bgghome.gwand.res.profile_unbookmark
import de.bgghome.gwand.res.tab_family
import de.bgghome.gwand.res.tab_life
import de.bgghome.gwand.res.tab_map
import de.bgghome.gwand.res.tab_notes
import de.bgghome.gwand.res.tab_photos
import de.bgghome.gwand.res.tab_sources
import de.bgghome.webtrees.nativ.ui.Avatar
import de.bgghome.webtrees.nativ.ui.PersonRow
import de.bgghome.webtrees.nativ.ui.karte.GeoPunkt
import de.bgghome.webtrees.nativ.ui.karte.KachelEbene
import de.bgghome.webtrees.nativ.ui.karte.KachelKarte
import de.bgghome.webtrees.nativ.ui.karte.KartenPin
import de.bgghome.webtrees.nativ.ui.karte.KartenZustand
import org.jetbrains.compose.resources.stringResource

/** Notizen dieses Typs schreibt Gramps beim GEDCOM-Import: Protokolle, keine Forschungsnotizen. */
private const val IMPORT_NOTIZ = "GEDCOM import"

/**
 * Das Profil einer Person. Am Handy eine eigene Seite mit Zurueck, am Tablet neben dem Baum ([eingebettet]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilSeite(p: Profil, s: Zustand, viewModel: GwViewModel, eingebettet: Boolean = false) {
    var menue by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p.anzeige?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.zurueck() }) {
                        Icon(if (eingebettet) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.profile_back))
                    }
                },
                colors = kopfFarben(),
                actions = {
                    if (p.person != null) {
                        if (s.darfBearbeiten) IconButton(onClick = viewModel::personBearbeiten) { Icon(Icons.Default.Edit, stringResource(Res.string.edit_person)) }
                        IconButton(onClick = { viewModel.merken(!p.gemerkt) }) {
                            Icon(
                                Icons.Default.Star,
                                stringResource(if (p.gemerkt) Res.string.profile_unbookmark else Res.string.profile_bookmark),
                                tint = sternFarbe(p.gemerkt),
                            )
                        }
                        IconButton(onClick = viewModel::teilen) { Icon(Icons.Default.Share, stringResource(Res.string.profile_share)) }
                        Box {
                            IconButton(onClick = { menue = true }) { Icon(Icons.Default.MoreVert, stringResource(Res.string.menu_more)) }
                            DropdownMenu(menue, onDismissRequest = { menue = false }) {
                                DropdownMenuItem(text = { Text(stringResource(Res.string.profile_tree)) }, onClick = { menue = false; viewModel.inDieMitte(p.handle) })
                                DropdownMenuItem(text = { Text(stringResource(Res.string.menu_start_person)) }, onClick = { menue = false; viewModel.startpersonSetzen(p.handle) })
                                DropdownMenuItem(text = { Text(stringResource(Res.string.profile_browser)) }, onClick = { menue = false; viewModel.imBrowser() })
                                DropdownMenuItem(text = { Text(stringResource(Res.string.menu_relationship)) }, onClick = { menue = false; viewModel.verwandtschaftDialog() })
                                DropdownMenuItem(text = { Text(stringResource(Res.string.menu_report)) }, onClick = { menue = false; viewModel.berichtDialog() })
                                DropdownMenuItem(text = { Text(stringResource(Res.string.menu_dna)) }, onClick = { menue = false; viewModel.dnaDialog() })
                                DropdownMenuItem(text = { Text(stringResource(Res.string.menu_history)) }, onClick = { menue = false; viewModel.objektVerlauf() })
                                if (s.darfBearbeiten) DropdownMenuItem(text = { Text(stringResource(Res.string.menu_merge)) }, onClick = { menue = false; viewModel.zusammenfuehrenDialog() })
                                if (s.darfBearbeiten) DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error) },
                                    onClick = { menue = false; viewModel.personLoeschen() },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innen ->
        Column(Modifier.fillMaxSize().padding(innen)) {
            val person = p.person
            val angezeigt = p.anzeige
            if (person == null || angezeigt == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (p.laedt) CircularProgressIndicator() }
                return@Column
            }
            Kopf(p, viewModel)
            val reiter = listOf(Res.string.tab_life, Res.string.tab_family, Res.string.tab_photos, Res.string.tab_sources, Res.string.tab_notes, Res.string.tab_map)
            PrimaryScrollableTabRow(selectedTabIndex = p.reiter, edgePadding = 8.dp) {
                reiter.forEachIndexed { i, r ->
                    Tab(selected = p.reiter == i, onClick = { viewModel.profilReiter(i) }, text = { Text(stringResource(r)) })
                }
            }
            Box(Modifier.fillMaxSize()) {
                when (p.reiter) {
                    GwViewModel.REITER_LEBENSLAUF -> Lebenslauf(p.zeitleiste, viewModel, s.darfBearbeiten)
                    GwViewModel.REITER_FAMILIE -> Familie(person, p.angehoerige, viewModel, s.darfBearbeiten)
                    GwViewModel.REITER_FOTOS -> Fotos(person, viewModel, s.darfBearbeiten)
                    GwViewModel.REITER_QUELLEN -> Quellen(p, viewModel)
                    GwViewModel.REITER_NOTIZEN -> Notizen(person)
                    GwViewModel.REITER_KARTE -> Karte(p.zeitleiste)
                }
                val plus: (() -> Unit)? = if (!s.darfBearbeiten) null else when (p.reiter) {
                    GwViewModel.REITER_LEBENSLAUF -> viewModel::neuesEreignis
                    GwViewModel.REITER_FAMILIE -> { { viewModel.verwandterDialog(angezeigt) } }
                    GwViewModel.REITER_FOTOS -> viewModel::fotoDialog
                    GwViewModel.REITER_QUELLEN -> viewModel::zitatDialog
                    else -> null
                }
                if (plus != null) {
                    androidx.compose.material3.FloatingActionButton(onClick = plus, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                        Icon(Icons.Default.Add, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun Kopf(p: Profil, viewModel: GwViewModel) {
    val a = p.anzeige ?: return
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clickable(enabled = a.thumb != null) { viewModel.profilFotos(0) }) { Avatar(a, 88.dp) }
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(a.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (a.lifespan.isNotBlank()) Text(a.lifespan, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.verwandtschaft.isNotBlank()) {
                Text(stringResource(Res.string.profile_relation, p.verwandtschaft), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            p.person?.grampsId?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
        }
    }
}

// ── Lebenslauf ───────────────────────────────────────────────────────

@Composable
private fun Lebenslauf(eintraege: List<ZeitleistenEintrag>, viewModel: GwViewModel, bearbeitbar: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(eintraege) { e ->
            val selbst = e.person.selbst
            val farbe = if (selbst) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Row(Modifier.fillMaxWidth().clickable(enabled = bearbeitbar && selbst && e.handle.isNotEmpty()) { viewModel.ereignisBearbeiten(e) }
                .padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    Anzeige.jahr(e.date).takeIf { it > 0 }?.toString().orEmpty(),
                    Modifier.width(56.dp), style = MaterialTheme.typography.titleMedium,
                    color = if (selbst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(e.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selbst) FontWeight.Medium else FontWeight.Normal, color = farbe)
                    if (!selbst && e.person.handle.isNotEmpty()) {
                        val name = listOf(e.person.vorname, e.person.nachname).filter { it.isNotBlank() }.joinToString(" ").ifBlank { e.person.anzeigename }
                        Text(name, Modifier.clickable { viewModel.profilOeffnen(e.person.handle) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    val datum = listOfNotNull(
                        e.date.takeIf { it.isNotBlank() }?.let(Anzeige::anzeigeDatum),
                        e.age.takeIf { it.isNotBlank() && selbst && !it.startsWith("0 ") }?.let { stringResource(Res.string.age_at, it) },
                    ).joinToString(" · ")
                    if (datum.isNotBlank()) Text(datum, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    e.place?.takeIf { it.anzeigename.isNotBlank() || it.name.isNotBlank() }?.let { o ->
                        // Mit Handle verlinkt (Ortsseite); undatierte Ereignisse kennen nur den Namen
                        Text(o.anzeigename.ifBlank { o.name }, Modifier.clickable(enabled = o.handle.isNotEmpty()) { viewModel.ortOeffnen(o.handle) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (o.handle.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodySmall, color = farbe)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// ── Familie ──────────────────────────────────────────────────────────

@Composable
private fun Familie(person: GrampsPerson, angehoerige: Map<String, de.bgghome.webtrees.nativ.api.Person>, viewModel: GwViewModel, bearbeitbar: Boolean) {
    fun zeige(pp: de.bgghome.gwand.api.PersonProfil) = angehoerige[pp.handle] ?: viewModel.anzeigeAus(pp)
    val profil = person.profile ?: return
    val eltern = profil.elternfamilie
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (eltern != null) {
            val liste = listOfNotNull(eltern.father, eltern.mother).filter { it.vorhanden }
            if (liste.isNotEmpty()) {
                ueberschrift(Res.string.family_parents)
                items(liste, key = { "e" + it.handle }) { e ->
                    PersonRow(zeige(e), onClick = { viewModel.profilOeffnen(e.handle) }, trailing = if (!bearbeitbar) null else {
                        { ZeilenMenue(listOf(Res.string.unlink to { viewModel.elternteilLoesen(eltern.handle, e.handle) })) }
                    })
                }
            }
            val geschwister = eltern.children.filter { it.handle != person.handle && it.vorhanden }
            if (geschwister.isNotEmpty()) {
                ueberschrift(Res.string.family_siblings)
                items(geschwister, key = { "g" + it.handle }) { g -> PersonRow(zeige(g), onClick = { viewModel.profilOeffnen(g.handle) }) }
            }
        }
        val halb = profil.weitereElternfamilien.flatMap { it.children }.filter { it.handle != person.handle && it.vorhanden }.distinctBy { it.handle }
        if (halb.isNotEmpty()) {
            ueberschrift(Res.string.family_half)
            items(halb, key = { "h" + it.handle }) { h -> PersonRow(zeige(h), onClick = { viewModel.profilOeffnen(h.handle) }) }
        }
        profil.families.forEachIndexed { i, f -> partnerfamilie(i, f, person.handle, viewModel, ::zeige, bearbeitbar) }
        val beziehungen = person.personenverweise.filter { !it.private }
        val personen = person.extended?.people.orEmpty().associateBy { it.handle }
        if (beziehungen.isNotEmpty() || bearbeitbar) {
            ueberschrift(Res.string.family_associations, if (bearbeitbar) viewModel::beziehungDialog else null)
            items(beziehungen, key = { "b" + it.ref + it.rel }) { b ->
                personen[b.ref]?.let { pp ->
                    PersonRow(angehoerige[b.ref] ?: viewModel.anzeigeAus(pp), label = b.rel, onClick = { viewModel.profilOeffnen(b.ref) }, trailing = if (!bearbeitbar) null else {
                        { ZeilenMenue(listOf(Res.string.delete to { viewModel.beziehungEntfernen(b.ref, b.rel) })) }
                    })
                }
            }
        }
    }
}

private fun LazyListScope.partnerfamilie(i: Int, f: FamilienProfil, selbst: String, viewModel: GwViewModel, zeige: (de.bgghome.gwand.api.PersonProfil) -> de.bgghome.webtrees.nativ.api.Person, bearbeitbar: Boolean) {
    val partner = listOfNotNull(f.father, f.mother).firstOrNull { it.vorhanden && it.handle != selbst }
    item(key = "f$i") {
        Text(
            stringResource(Res.string.family_partner) + if (!f.marriage.leer) " · " + stringResource(Res.string.family_married, Anzeige.anzeigeDatum(f.marriage.date)) else "",
            Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        )
    }
    if (partner != null) item(key = "p$i" + partner.handle) {
        PersonRow(zeige(partner), onClick = { viewModel.profilOeffnen(partner.handle) }, trailing = if (!bearbeitbar) null else {
            { ZeilenMenue(listOf(Res.string.unlink to { viewModel.elternteilLoesen(f.handle, partner.handle) })) }
        })
    }
    if (f.children.isNotEmpty()) {
        item(key = "k$i") {
            Text(stringResource(Res.string.family_children), Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(f.children.filter { it.vorhanden }, key = { "k$i" + it.handle }) { k ->
            PersonRow(zeige(k), onClick = { viewModel.profilOeffnen(k.handle) }, trailing = if (!bearbeitbar) null else {
                {
                    ZeilenMenue(listOf(
                        Res.string.move_up to { viewModel.kindVerschieben(f.handle, k.handle, -1) },
                        Res.string.move_down to { viewModel.kindVerschieben(f.handle, k.handle, 1) },
                        Res.string.unlink to { viewModel.kindLoesen(f.handle, k.handle) },
                    ))
                }
            })
        }
    }
    if (bearbeitbar) item(key = "plus$i") {
        Row(Modifier.padding(horizontal = 8.dp)) {
            androidx.compose.material3.TextButton(onClick = { viewModel.verwandterDialogFuerFamilie(selbst, f.handle) }) { Text("+ " + stringResource(Res.string.rel_child)) }
            androidx.compose.material3.TextButton(onClick = { viewModel.neuesFamilienereignis(f.handle) }) { Text("+ " + stringResource(Res.string.add_event_family)) }
        }
    }
}

private fun LazyListScope.ueberschrift(text: org.jetbrains.compose.resources.StringResource, plus: (() -> Unit)? = null) {
    item(key = "u" + text.key) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(text), Modifier.weight(1f).padding(top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (plus != null) IconButton(onClick = plus) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

/** Drei Punkte am Zeilenende mit den Aktionen der Zeile. */
@Composable
private fun ZeilenMenue(aktionen: List<Pair<org.jetbrains.compose.resources.StringResource, () -> Unit>>) {
    var offen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { offen = true }) { Icon(Icons.Default.MoreVert, null) }
        DropdownMenu(offen, onDismissRequest = { offen = false }) {
            aktionen.forEach { (text, aktion) -> DropdownMenuItem(text = { Text(stringResource(text)) }, onClick = { offen = false; aktion() }) }
        }
    }
}

// ── Fotos ────────────────────────────────────────────────────────────

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Fotos(person: GrampsPerson, viewModel: GwViewModel, bearbeitbar: Boolean) {
    val medien = person.extended?.media.orEmpty().associateBy { it.handle }
    val verweise = person.medien.filter { !it.private && medien[it.ref] != null }
    if (verweise.isEmpty()) { Leer(Res.string.none_photos); return }
    LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(verweise) { i, v ->
            val m = medien[v.ref]
            val bild = m?.istBild == true
            BildMitMenue(
                url = viewModel.bildUrl(v.ref, if (bild) v.rect else null), beschreibung = m?.desc, onClick = { viewModel.profilFotos(i) },
                aktionen = if (!bild) emptyList() else listOfNotNull(
                    if (bearbeitbar && i > 0) Res.string.make_primary_photo to { viewModel.alsHauptbild(v.ref) } else null,
                    Res.string.photo_faces to { viewModel.gesichterOeffnen(v.ref, m?.desc.orEmpty()) },
                    Res.string.photo_ocr to { viewModel.textErkennen(v.ref, m?.desc.orEmpty()) },
                ),
                art = m?.takeIf { !it.istBild }?.let { dateiArt(it) },
                vorschau = m?.hatVorschau != false,
            )
        }
    }
}

// ── Quellen ──────────────────────────────────────────────────────────

@Composable
private fun Quellen(p: Profil, viewModel: GwViewModel) {
    val zitate = p.zitate
    if (zitate == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    if (zitate.isEmpty()) { Leer(Res.string.none_sources); return }
    val stufen = listOf(Res.string.conf_0, Res.string.conf_1, Res.string.conf_2, Res.string.conf_3, Res.string.conf_4)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(zitate.filter { !it.private }, key = { it.handle }) { z ->
            val q = p.quellen[z.quelle]
            Column(Modifier.fillMaxWidth().clickable(enabled = q != null) { viewModel.quelleOeffnen(z.quelle) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(q?.title.orEmpty().ifBlank { z.quelle }, style = MaterialTheme.typography.bodyLarge)
                if (z.page.isNotBlank()) Text(z.page, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.conf_label, stringResource(stufen[z.confidence.coerceIn(0, 4)])),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// ── Notizen ──────────────────────────────────────────────────────────

@Composable
private fun Notizen(person: GrampsPerson) {
    val notizen = person.extended?.notes.orEmpty().filter { !it.private && it.type != IMPORT_NOTIZ && it.text.string.isNotBlank() }
    if (notizen.isEmpty()) { Leer(Res.string.none_notes); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(notizen, key = { it.handle }) { n -> Text(n.text.string, style = MaterialTheme.typography.bodyMedium) }
    }
}

// ── Karte ────────────────────────────────────────────────────────────

@Composable
private fun Karte(zeitleiste: List<ZeitleistenEintrag>) {
    val farbe = MaterialTheme.colorScheme.primary
    val pins = remember(zeitleiste) {
        zeitleiste.filter { it.person.selbst }.mapNotNull { e ->
            val o = e.place ?: return@mapNotNull null
            val lat = o.lat ?: return@mapNotNull null
            val lon = o.long ?: return@mapNotNull null
            Triple(lat, lon, "${Anzeige.jahr(e.date).takeIf { it > 0 } ?: ""} ${e.label}".trim())
        }.groupBy { it.first to it.second }.map { (punkt, liste) ->
            KartenPin(punkt.first, punkt.second, text = null, farbe = farbe, beschriftung = liste.joinToString(", ") { it.third })
        }
    }
    if (pins.isEmpty()) { Leer(Res.string.none_map); return }
    val zustand = remember(pins) { KartenZustand() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dichte = LocalDensity.current
        val b = with(dichte) { maxWidth.roundToPx() }
        val h = with(dichte) { maxHeight.roundToPx() }
        remember(pins, b, h) { zustand.passeEin(pins.map { GeoPunkt(it.lat, it.lon) }, b, h, randPx = 96, einzelZoom = 11); 0 }
        KachelKarte(zustand, KachelEbene.STANDARD, Modifier.fillMaxSize(), pins = pins)
    }
}

@Composable
private fun Leer(text: org.jetbrains.compose.resources.StringResource) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Stern im Kopfbalken: gemerkt kraeftig (im hellen Balken in der Themenfarbe), sonst blass in der Balkenschrift. */
@Composable
private fun sternFarbe(gemerkt: Boolean): androidx.compose.ui.graphics.Color {
    val schrift = kopfFarben().actionIconContentColor
    return when {
        !gemerkt -> schrift.copy(alpha = 0.45f)
        LocalDarstellung.current == Darstellung.Hell -> MaterialTheme.colorScheme.primary
        else -> schrift
    }
}
