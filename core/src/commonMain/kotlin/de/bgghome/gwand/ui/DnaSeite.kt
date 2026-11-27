package de.bgghome.gwand.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bgghome.gwand.api.DnaSegment
import de.bgghome.gwand.api.DnaTreffer
import de.bgghome.gwand.api.YDna
import de.bgghome.gwand.daten.Dna
import de.bgghome.gwand.res.*
import de.bgghome.webtrees.nativ.ui.PersonRow
import org.jetbrains.compose.resources.stringResource

/**
 * DNA einer Person wie in Gramps Web: Y-DNA-Haplogruppe, Chromosomen-Ansicht aller Treffer, Trefferliste; ein
 * angetippter Treffer zeigt Zusammenfassung, gemeinsame Vorfahren und Segmenttabelle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnaSeite(d: DnaAnsicht, s: Zustand, viewModel: GwViewModel) {
    val eintrag = d.treffer?.firstOrNull { it.treffer.handle == d.gewaehlt }
    // Die Uebersicht behaelt ihre Stelle, ein geoeffneter Treffer beginnt oben
    val uebersicht = rememberScrollState()
    val einzeln = remember(d.gewaehlt) { ScrollState(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(Res.string.menu_dna), maxLines = 1)
                        d.person?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                navigationIcon = { IconButton(onClick = { viewModel.zurueck() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.profile_back)) } },
                actions = {
                    if (s.darfBearbeiten && eintrag == null && !s.offline) {
                        IconButton(onClick = viewModel::dnaNeuDialog) { Icon(Icons.Default.Add, stringResource(Res.string.dna_add)) }
                    }
                },
                colors = kopfFarben(),
            )
        },
    ) { innen ->
        Column(Modifier.fillMaxSize().padding(innen).verticalScroll(if (eintrag != null) einzeln else uebersicht).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val treffer = d.treffer
            when {
                eintrag != null -> Einzeltreffer(d, eintrag, s, viewModel)
                treffer == null -> CircularProgressIndicator(Modifier.padding(16.dp))
                else -> {
                    d.ydna?.let { YDnaBlock(it, viewModel) }
                    if (treffer.isEmpty()) Text(stringResource(Res.string.dna_none), Modifier.padding(horizontal = 16.dp))
                    else Uebersicht(treffer, viewModel)
                }
            }
        }
    }
}

@Composable
private fun Abschnitt(titel: String) =
    Text(titel, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun YDnaBlock(y: YDna, viewModel: GwViewModel) {
    val gruppe = y.haplogruppe ?: return
    Abschnitt(stringResource(Res.string.dna_ydna))
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(Res.string.dna_haplogroup), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(gruppe, Modifier.clickable { viewModel.oeffnen("https://yfull.com/tree/" + java.net.URLEncoder.encode(gruppe, "UTF-8")) },
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        if (y.baumVersion.isNotBlank()) Text(stringResource(Res.string.dna_ytree, y.baumVersion), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun Uebersicht(treffer: List<DnaEintrag>, viewModel: GwViewModel) {
    Abschnitt(stringResource(Res.string.dna_chromosomes))
    ChromosomenBild(treffer.map { it.treffer }, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
    Text(stringResource(Res.string.dna_legend_sides), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider()
    Abschnitt(stringResource(Res.string.dna_matches))
    treffer.forEachIndexed { i, e ->
        Row(Modifier.fillMaxWidth().clickable { viewModel.dnaWaehlen(e.treffer.handle) }, verticalAlignment = Alignment.CenterVertically) {
            Farbfeld(i, Modifier.padding(start = 16.dp))
            Column(Modifier.weight(1f)) {
                if (e.person != null) PersonRow(e.person, label = e.treffer.relation.ifBlank { null })
                else Text(stringResource(Res.string.dna_person_unknown), Modifier.padding(16.dp))
                Text(stringResource(Res.string.dna_cm, "%.1f".format(e.treffer.cM), e.treffer.segments.size), Modifier.padding(start = 72.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Farbe eines Treffers wie in der Chromosomen-Ansicht (ab dem neunten heller Ton mit Rand). */
@Composable
private fun Farbfeld(i: Int, modifier: Modifier = Modifier) {
    val form = RoundedCornerShape(3.dp)
    var m = modifier.size(14.dp).background(Color(Dna.farbe(i)), form)
    Dna.rand(i)?.let { m = m.border(1.dp, Color(it), form) }
    Box(m)
}

@Composable
private fun Einzeltreffer(d: DnaAnsicht, e: DnaEintrag, s: Zustand, viewModel: GwViewModel) {
    val t = e.treffer
    if (e.person != null) PersonRow(e.person, onClick = { viewModel.dnaPerson(t.handle) })
    else Text(stringResource(Res.string.dna_person_unknown), Modifier.padding(horizontal = 16.dp))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Kennzahl(stringResource(Res.string.dna_shared), "%.1f cM".format(t.cM))
        Kennzahl(stringResource(Res.string.dna_segments), "%d".format(t.segments.size))
        Kennzahl(stringResource(Res.string.dna_largest), "%.1f cM".format(t.groesstes))
        Kennzahl(stringResource(Res.string.dna_relation), t.relation.ifBlank { stringResource(Res.string.dna_unknown) })
    }
    if (e.vorfahren.isNotEmpty()) {
        HorizontalDivider()
        Abschnitt(stringResource(Res.string.rel_common))
        e.vorfahren.forEach { v -> PersonRow(v, onClick = { viewModel.dnaPerson(v.xref) }) }
    }
    HorizontalDivider()
    Abschnitt(stringResource(Res.string.dna_chromosomes))
    val nummer = d.treffer.orEmpty().indexOf(e).coerceAtLeast(0)
    ChromosomenBild(listOf(t), Modifier.fillMaxWidth().padding(horizontal = 8.dp), ersteFarbe = nummer)
    Text(stringResource(Res.string.dna_legend_sides), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider()
    Abschnitt(stringResource(Res.string.dna_segment_table))
    Box(Modifier.padding(horizontal = 16.dp)) { DnaSegmentTabelle(t.segments) }
    if (s.darfBearbeiten && !s.offline) {
        TextButton(onClick = { viewModel.dnaLoeschen(e) }, Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Kennzahl(name: String, wert: String) {
    Column {
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Segmente als Tabelle (Chromosom, Start, Ende, Seite, cM, SNPs), nach Chromosom und Anfang geordnet; seitlich schiebbar. */
@Composable
internal fun DnaSegmentTabelle(segmente: List<DnaSegment>) {
    val spalten = listOf(
        // So bemessen, dass an einem 360 dp breiten Handy alles bis cM ohne Schieben passt (zusammen 326 dp);
        // nur die SNPs liegen rechts ausserhalb
        Res.string.dna_col_chr to 30.dp, Res.string.dna_col_start to 88.dp, Res.string.dna_col_end to 88.dp,
        Res.string.dna_col_side to 74.dp, null to 46.dp, null to 72.dp,
    )
    val kopf = listOf("", "", "", "", "cM", "SNPs")
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            spalten.forEachIndexed { i, (titel, breite) ->
                Text(titel?.let { stringResource(it) } ?: kopf[i], Modifier.width(breite).padding(vertical = 6.dp, horizontal = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (i in listOf(1, 2, 4, 5)) TextAlign.End else TextAlign.Start, maxLines = 1)
            }
        }
        Dna.sortiert(segmente).forEach { g ->
            val seite = stringResource(when (Dna.spur(g.side)) { "P" -> Res.string.dna_side_p; "M" -> Res.string.dna_side_m; else -> Res.string.dna_side_u })
            val werte = listOf(g.chromosome, "%,d".format(g.start), "%,d".format(g.stop), seite, "%.1f".format(g.cM), if (g.snps > 0) "%,d".format(g.snps) else "")
            Row {
                werte.forEachIndexed { i, w ->
                    Text(w, Modifier.width(spalten[i].second).padding(vertical = 6.dp, horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium,
                        textAlign = if (i in listOf(1, 2, 4, 5)) TextAlign.End else TextAlign.Start, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Chromosomen-Ansicht nach Gramps Web (ChromosomeBrowser.js): je Chromosom (1 bis 22, X) eine vaeterliche Spur oben und
 * eine muetterliche unten, dazwischen eine fuer Segmente unbekannter Seite, wenn es welche gibt. Ueberlappende Segmente
 * einer Spur liegen in eigenen, schmaleren Bahnen; die vaeterlichen wachsen nach oben, die anderen nach unten.
 * Farbe je Treffer in der Reihenfolge der Liste ([ersteFarbe] = Nummer des ersten), unbekannte Seite schraffiert.
 */
@Composable
internal fun ChromosomenBild(treffer: List<DnaTreffer>, modifier: Modifier = Modifier, ersteFarbe: Int = 0) {
    class Balken(val g: DnaSegment, val farbe: Int, val oben: Float, val hoehe: Float, val unbekannt: Boolean)
    class Spur(val oben: Float, val hoehe: Float, val art: String)
    class Zeile(val schluessel: String, val y: Float, val hoehe: Float, val spuren: List<Spur>, val balken: List<Balken>)

    // Masse wie in der Vorlage, dort Pixel, hier dp
    val zeilenAbstand = 11f; val spurAbstand = 2f; val bahnAbstand = 2f; val balkenHoehe = 11f; val gestapelt = 7f
    val achse = 24f
    val (zeilen, gesamt) = remember(treffer, ersteFarbe) {
        val alle = treffer.flatMapIndexed { i, t -> t.segments.mapNotNull { g -> Dna.schluessel(g.chromosome)?.let { Triple(it, g, ersteFarbe + i) } } }
        var y = zeilenAbstand
        val liste = Dna.LAENGEN.keys.map { k ->
            val hier = alle.filter { it.first == k }
            val bahnen = Dna.bahnen(hier.map { it.second })
            var oben = 0f
            val spuren = linkedMapOf<String, Pair<Spur, Float>>()
            listOf("P", "U", "M").filter { bahnen.anzahl.getValue(it) > 0 }.forEach { art ->
                val n = bahnen.anzahl.getValue(art)
                val bahnHoehe = if (n > 1) gestapelt else balkenHoehe
                val h = n * (bahnHoehe + bahnAbstand) - bahnAbstand
                spuren[art] = Spur(oben, h, art) to bahnHoehe
                oben += h + spurAbstand
            }
            val balken = hier.mapIndexed { i, (_, g, farbe) ->
                val art = Dna.spur(g.side)
                val (spur, bahnHoehe) = spuren.getValue(art)
                val versatz = bahnen.bahn[i] * (bahnHoehe + bahnAbstand)
                Balken(g, farbe, if (art == "P") spur.oben + spur.hoehe - bahnHoehe - versatz else spur.oben + versatz, bahnHoehe, art == "U")
            }
            Zeile(k, y, oben - spurAbstand, spuren.values.map { it.first }, balken).also { y += it.hoehe + zeilenAbstand }
        }
        liste to y
    }
    val dunkel = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Spurfarben der Vorlage; im dunklen Design dieselben Toene abgedunkelt
    val spurFarbe = if (dunkel) mapOf("P" to Color(0xFF2A313B), "U" to Color(0xFF303030), "M" to Color(0xFF3B2C2F))
        else mapOf("P" to Color(0xFFEEF1F5), "U" to Color(0xFFF2F2F2), "M" to Color(0xFFF9ECEE))
    val schrift = MaterialTheme.colorScheme.onSurface
    val messer = rememberTextMeasurer()
    val beschriftung = TextStyle(color = schrift, fontSize = 12.sp)
    val klein = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)

    Canvas(modifier.height((achse + gesamt).dp)) {
        val links = 30.dp.toPx(); val rechts = 8.dp.toPx()
        val breite = size.width - links - rechts
        fun x(bp: Long): Float = links + bp.toFloat() / Dna.GROESSTE_LAENGE * breite
        val oben = achse.dp.toPx()

        // Achse in Megabasen
        for (mb in 0..200 step 50) {
            val px = x(mb * 1_000_000L)
            drawLine(schrift.copy(alpha = 0.5f), Offset(px, oben - 4.dp.toPx()), Offset(px, oben), 1.dp.toPx())
            val text = messer.measure("$mb Mb", klein)
            drawText(text, topLeft = Offset((px - text.size.width / 2f).coerceIn(0f, size.width - text.size.width), oben - 6.dp.toPx() - text.size.height))
        }

        zeilen.forEach { z ->
            val zy = oben + z.y.dp.toPx()
            val name = messer.measure(z.schluessel, beschriftung)
            drawText(name, topLeft = Offset(links - 8.dp.toPx() - name.size.width, zy + z.hoehe.dp.toPx() / 2f - name.size.height / 2f))
            // Nur die aeusseren Ecken des Chromosoms sind rund, damit die Spuren als ein Balken wirken
            val r = CornerRadius(4.dp.toPx())
            z.spuren.forEachIndexed { i, sp ->
                val flaeche = Rect(links, zy + sp.oben.dp.toPx(), x(Dna.LAENGEN.getValue(z.schluessel)), zy + (sp.oben + sp.hoehe).dp.toPx())
                val rundOben = if (i == 0) r else CornerRadius.Zero
                val rundUnten = if (i == z.spuren.lastIndex) r else CornerRadius.Zero
                drawPath(Path().apply { addRoundRect(RoundRect(flaeche, rundOben, rundOben, rundUnten, rundUnten)) }, spurFarbe.getValue(sp.art))
            }
            z.balken.forEach { b ->
                val von = x(minOf(b.g.start, b.g.stop)); val bis = x(maxOf(b.g.start, b.g.stop))
                val ecke = Offset(von, zy + b.oben.dp.toPx())
                val groesse = Size((bis - von).coerceAtLeast(1.5f), b.hoehe.dp.toPx())
                val rund = CornerRadius(2.dp.toPx())
                val farbe = Color(Dna.farbe(b.farbe)); val rand = Dna.rand(b.farbe)?.let { Color(it) }
                if (b.unbekannt) {
                    // Seite unbekannt: schraege Streifen in der Farbe des Treffers
                    val form = Path().apply { addRoundRect(RoundRect(Rect(ecke, groesse), rund)) }
                    clipPath(form) {
                        val schritt = 6.dp.toPx(); val h = groesse.height
                        var sx = ecke.x - h
                        while (sx < ecke.x + groesse.width + h) {
                            drawLine(rand ?: farbe, Offset(sx, ecke.y + h), Offset(sx + h, ecke.y), 3.dp.toPx())
                            sx += schritt
                        }
                    }
                } else drawRoundRect(farbe, ecke, groesse, rund)
                if (rand != null) drawRoundRect(rand, ecke, groesse, rund, style = Stroke(1.dp.toPx()))
            }
        }
    }
}
