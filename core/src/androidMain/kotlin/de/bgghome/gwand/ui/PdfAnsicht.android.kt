package de.bgghome.gwand.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.res.Res as WRes
import de.bgghome.webtrees.nativ.res.action_close
import de.bgghome.webtrees.nativ.res.pdf_error
import de.bgghome.webtrees.nativ.res.pdf_pages
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** Ein geoeffnetes PDF. PdfRenderer zeichnet nur eine Seite zur Zeit - darum das Schloss. */
private class Dokument(private val datei: File) {
    private val fd = ParcelFileDescriptor.open(datei, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val schloss = Mutex()
    val seiten: Int = renderer.pageCount
    val verhaeltnis: List<Float> = (0 until seiten).map { i -> renderer.openPage(i).use { it.width.toFloat() / it.height } }

    suspend fun zeichnen(i: Int, breite: Int): Bitmap = schloss.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(i).use { s ->
                val hoehe = (breite / (s.width.toFloat() / s.height)).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(AColor.WHITE)
                    s.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    fun schliessen() {
        runCatching { renderer.close() }; runCatching { fd.close() }; datei.delete()
    }
}

@Composable
actual fun PdfAnsicht(pdf: PdfAnzeige, onClose: () -> Unit) {
    val cache = LocalContext.current.cacheDir
    var dokument by remember { mutableStateOf<Dokument?>(null) }
    var fehler by remember { mutableStateOf(false) }
    val datei = pdf.datei
    LaunchedEffect(datei) {
        if (datei == null) return@LaunchedEffect
        dokument = withContext(Dispatchers.IO) {
            runCatching {
                // Nur fuer die Dauer der Anzeige im privaten Cache der App; beim Schliessen geloescht
                val f = File(cache, "pdf").apply { mkdirs() }.let { File(it, "ansicht-${System.nanoTime()}.pdf") }
                f.writeBytes(datei.daten)
                Dokument(f)
            }.getOrNull()
        }
        fehler = dokument == null
    }
    DisposableEffect(dokument) {
        val d = dokument
        onDispose { d?.schliessen() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(WRes.string.action_close)) }
            Column(Modifier.weight(1f)) {
                Text(pdf.titel, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                dokument?.let {
                    Text(stringResource(WRes.string.pdf_pages, it.seiten), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val d = dokument
        when {
            fehler -> Text(stringResource(WRes.string.pdf_error), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val breite = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
                // Solange eine Seite vergroessert ist, schiebt der Finger die Seite, nicht die Liste
                var gezoomt by remember { mutableStateOf(-1) }
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), userScrollEnabled = gezoomt < 0) {
                    items(d.seiten) { i -> Seite(d, i, breite) { an -> gezoomt = if (an) i else if (gezoomt == i) -1 else gezoomt } }
                }
            }
        }
    }
}

@Composable
private fun Seite(d: Dokument, i: Int, breite: Int, onZoom: (Boolean) -> Unit) {
    val bild by produceState<Bitmap?>(null, d, i) { value = runCatching { d.zeichnen(i, breite) }.getOrNull() }
    var faktor by remember { mutableFloatStateOf(1f) }
    var versatz by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(d.verhaeltnis[i]).padding(bottom = 4.dp).background(androidx.compose.ui.graphics.Color.White)
            .pointerInput(i) {
                detectTapGestures(onDoubleTap = {
                    if (faktor > 1f) { faktor = 1f; versatz = Offset.Zero; onZoom(false) } else { faktor = 2.5f; onZoom(true) }
                })
            }
            .pointerInput(i, faktor > 1f) {
                // Zwei Finger zoomen; vergroessert schiebt ein Finger die Seite
                detectTransformGestures { _, pan, zoom, _ ->
                    val neu = (faktor * zoom).coerceIn(1f, 4f)
                    faktor = neu
                    versatz = if (neu <= 1f) Offset.Zero else Offset(
                        (versatz.x + pan.x).coerceIn(-size.width * (neu - 1) / 2, size.width * (neu - 1) / 2),
                        (versatz.y + pan.y).coerceIn(-size.height * (neu - 1) / 2, size.height * (neu - 1) / 2),
                    )
                    onZoom(neu > 1f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val b = bild
        if (b == null) CircularProgressIndicator()
        else Image(b.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer {
            scaleX = faktor; scaleY = faktor; translationX = versatz.x; translationY = versatz.y
        })
    }
}
