package de.bgghome.gwand.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import de.bgghome.gwand.res.*
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.ui.PersonRow
import org.jetbrains.compose.resources.stringResource

/**
 * Gesichter eines Bildes: zugeordnete Ausschnitte als Rahmen mit Namen, vom Server erkannte Gesichter als
 * gestrichelte Vorschlaege. Tipp auf einen Rahmen ordnet ihn einer Person zu; mit dem Stift zieht man eigene Rahmen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GesichterSeite(g: Gesichter, s: Zustand, viewModel: GwViewModel) {
    var ziehen by remember { mutableStateOf(false) }
    var gewaehlt by remember(g.medium) { mutableStateOf<Pair<Rahmen, Person?>?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(g.titel.ifBlank { stringResource(Res.string.faces_title) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = viewModel::gesichterSchliessen) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.profile_back)) } },
                actions = {
                    if (s.darfBearbeiten) IconToggleButton(ziehen, { ziehen = it }) {
                        Icon(Icons.Default.Create, stringResource(Res.string.faces_draw), tint = if (ziehen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { innen ->
        Column(Modifier.fillMaxSize().padding(innen)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Bild(g, ziehen, s.darfBearbeiten) { rahmen, person -> gewaehlt = rahmen to person; ziehen = false }
                if (g.laedt) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            val hinweis = when {
                ziehen -> stringResource(Res.string.faces_draw_hint)
                g.erkannt.isNotEmpty() && s.darfBearbeiten -> stringResource(Res.string.faces_hint, g.erkannt.size)
                else -> stringResource(Res.string.faces_count, g.zugeordnet.size)
            }
            Text(hinweis, Modifier.fillMaxWidth().background(Color.Black).padding(16.dp), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }
    }
    gewaehlt?.let { (rahmen, person) ->
        if (s.darfBearbeiten) ZuordnenBlatt(g, rahmen, person, s, viewModel) { gewaehlt = null }
        else if (person != null) { gewaehlt = null; viewModel.profilOeffnen(person.xref) }
    }
}

/** Das Bild in voller Groesse, darueber die Rahmen; Tipp waehlt einen Rahmen, Ziehen (im Zeichenmodus) zieht einen neuen. */
@Composable
private fun Bild(g: Gesichter, ziehen: Boolean, bearbeitbar: Boolean, onRahmen: (Rahmen, Person?) -> Unit) {
    val painter = rememberAsyncImagePainter(g.bild)
    val zustand by painter.state.collectAsState()
    val text = rememberTextMeasurer()
    val farbe = MaterialTheme.colorScheme.primaryContainer
    var neu by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        AsyncImage(model = g.bild, contentDescription = g.titel, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        val groesse = (zustand as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize ?: return@BoxWithConstraints
        val bw = constraints.maxWidth.toFloat(); val bh = constraints.maxHeight.toFloat()
        val faktor = minOf(bw / groesse.width, bh / groesse.height)
        val iw = groesse.width * faktor; val ih = groesse.height * faktor
        val x0 = (bw - iw) / 2; val y0 = (bh - ih) / 2
        fun zuPunkt(r: Rahmen) = Offset(x0 + (r.l / 100 * iw).toFloat(), y0 + (r.o / 100 * ih).toFloat()) to
            Size(((r.r - r.l) / 100 * iw).toFloat(), ((r.u - r.o) / 100 * ih).toFloat())
        fun zuProzent(p: Offset) = ((p.x - x0) / iw * 100).toDouble().coerceIn(0.0, 100.0) to ((p.y - y0) / ih * 100).toDouble().coerceIn(0.0, 100.0)

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(g, ziehen) {
                    if (ziehen) detectDragGestures(
                        onDragStart = { neu = it to it },
                        onDrag = { change, _ -> neu = neu?.let { it.first to change.position } },
                        onDragEnd = {
                            neu?.let { (a, b) ->
                                val (ax, ay) = zuProzent(a); val (bx, by) = zuProzent(b)
                                val r = Rahmen(minOf(ax, bx), minOf(ay, by), maxOf(ax, bx), maxOf(ay, by))
                                if (r.r - r.l > 2 && r.u - r.o > 2) onRahmen(r, null)
                            }
                            neu = null
                        },
                    )
                }
                .pointerInput(g, ziehen) {
                    if (!ziehen) detectTapGestures { p ->
                        val (x, y) = zuProzent(p)
                        g.zugeordnet.firstOrNull { it.second.enthaelt(x, y) }?.let { onRahmen(it.second, it.first); return@detectTapGestures }
                        if (bearbeitbar) g.erkannt.firstOrNull { it.enthaelt(x, y) }?.let { onRahmen(it, null) }
                    }
                },
        ) {
            g.erkannt.forEach { r ->
                val (o, sz) = zuPunkt(r)
                drawRect(Color.White.copy(alpha = 0.9f), o, sz, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            g.zugeordnet.forEach { (p, r) ->
                val (o, sz) = zuPunkt(r)
                drawRect(farbe, o, sz, style = Stroke(3.dp.toPx()))
                val name = p.given.ifBlank { p.name }
                val mass = text.measure(name, TextStyle(color = Color.Black, fontSize = 12.sp))
                val ty = (o.y + sz.height + 2).coerceAtMost(size.height - mass.size.height)
                drawRect(farbe, Offset(o.x, ty), Size(mass.size.width + 8f, mass.size.height.toFloat()))
                drawText(mass, topLeft = Offset(o.x + 4, ty))
            }
            neu?.let { (a, b) ->
                drawRect(Color.Yellow, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = Stroke(2.dp.toPx()))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZuordnenBlatt(g: Gesichter, rahmen: Rahmen, person: Person?, s: Zustand, viewModel: GwViewModel, schliessen: () -> Unit) {
    var suche by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = schliessen) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (person != null) {
                PersonRow(person, selected = true, onClick = { schliessen(); viewModel.gesichterSchliessen(); viewModel.profilOeffnen(person.xref) })
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.gesichtEntfernen(person.xref); schliessen() }) {
                        Text(stringResource(Res.string.faces_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
            Text(stringResource(Res.string.faces_assign), Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
            g.ohneRahmen.forEach { p -> PersonRow(p, label = stringResource(Res.string.faces_on_photo), onClick = { viewModel.gesichtZuordnen(p.xref, rahmen); schliessen() }) }
            OutlinedTextField(suche, { suche = it; viewModel.personVorschlaege(it) }, Modifier.fillMaxWidth().padding(16.dp), singleLine = true,
                label = { Text(stringResource(Res.string.search_person)) })
            s.personVorschlaege.take(10).forEach { p -> PersonRow(p, onClick = { viewModel.gesichtZuordnen(p.xref, rahmen); schliessen() }) }
        }
    }
}

/** Ergebnis der Texterkennung: Text zum Markieren und Kopieren, Teilen, als Abschrift am Bild speichern. */
@Composable
fun TexterkennungDialog(t: Texterkennung, s: Zustand, viewModel: GwViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::texterkennungSchliessen,
        title = { Text(stringResource(Res.string.ocr_title)) },
        text = {
            when {
                t.fehler != null -> Text(t.fehler, color = MaterialTheme.colorScheme.error)
                t.text == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(Res.string.ocr_running))
                }
                t.text.isBlank() -> Text(stringResource(Res.string.ocr_empty))
                else -> SelectionContainer {
                    Text(t.text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (!t.text.isNullOrBlank() && s.darfBearbeiten) TextButton(onClick = { viewModel.textAlsNotiz(t, t.text) }) { Text(stringResource(Res.string.ocr_save)) }
        },
        dismissButton = {
            Row {
                if (!t.text.isNullOrBlank()) IconButton(onClick = { viewModel.textTeilen(t) }) { Icon(Icons.Default.Share, stringResource(Res.string.profile_share)) }
                TextButton(onClick = viewModel::texterkennungSchliessen) { Text(stringResource(Res.string.search_close)) }
            }
        },
    )
}
