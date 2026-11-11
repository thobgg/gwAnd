package de.bgghome.gwand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.menu_appearance
import de.bgghome.gwand.res.appearance_classic
import de.bgghome.gwand.res.appearance_light
import de.bgghome.gwand.res.search_close
import de.bgghome.webtrees.nativ.res.Res as WRes
import de.bgghome.webtrees.nativ.res.menu_language
import de.bgghome.gwand.res.menu_logout
import de.bgghome.gwand.res.menu_about
import de.bgghome.gwand.res.offline_banner
import de.bgghome.gwand.res.menu_export
import de.bgghome.gwand.res.menu_chat
import de.bgghome.gwand.res.menu_more
import de.bgghome.gwand.res.menu_reload
import de.bgghome.gwand.res.more_about
import de.bgghome.gwand.res.nav_more
import de.bgghome.gwand.res.nav_people
import de.bgghome.gwand.res.nav_photos
import de.bgghome.gwand.res.nav_start
import de.bgghome.gwand.res.nav_tree
import de.bgghome.gwand.res.setting_cousins
import de.bgghome.gwand.res.setting_reminder
import de.bgghome.gwand.res.setting_siblings
import de.bgghome.webtrees.nativ.ui.PhotoViewer
import de.bgghome.webtrees.nativ.ui.tree.BaumAnsicht
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Ab dieser Breite steht das Profil neben dem Baum (Tablet, Handy quer). */
private val TABLET_BREITE = 840.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HauptBildschirm(s: Zustand, viewModel: GwViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(s.meldung) {
        s.meldung?.let { snackbar.showSnackbar(it); viewModel.meldungGezeigt() }
    }
    BackHandler(enabled = s.pdf != null || s.chat != null || s.gesichter != null || s.betrachter != null || s.detail != null || s.profil != null || s.ausgewaehlt != null || s.bereich != Bereich.Baum || s.baumVollbild || s.wurzelVerlauf.isNotEmpty()) {
        viewModel.zurueck()
    }
    val ansicht = remember { BaumAnsicht() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val breit = maxWidth >= TABLET_BREITE
        val profilDaneben = breit && s.bereich == Bereich.Baum && s.profil != null && s.detail == null

        when {
            s.gesichter != null -> GesichterSeite(s.gesichter, s, viewModel)
            s.chat != null -> ChatSeite(s.chat, viewModel)
            // Vollbild-Seiten am Handy: Detail und Profil decken alles ab, mit eigener Kopfzeile und Zurueck.
            s.detail != null -> DetailSeite(s.detail, s, viewModel)
            s.profil != null && !profilDaneben -> ProfilSeite(s.profil, s, viewModel)
            // Baum im Vollbild: ohne Kopfzeile und untere Leiste
            s.bereich == Bereich.Baum && s.baumVollbild -> Box(Modifier.fillMaxSize().systemBarsPadding()) {
                BaumSeite(s, viewModel, ansicht, kompakt = !breit)
            }
            else -> Scaffold(
                topBar = { Kopfzeile(s, viewModel) },
                bottomBar = { UntereLeiste(s.bereich, viewModel::bereich) },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { innen ->
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(innen)) {
                if (s.offline) Text(stringResource(Res.string.offline_banner), Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Box(Modifier.fillMaxSize()) {
                    when (s.bereich) {
                        Bereich.Start -> Startseite(s, viewModel)
                        Bereich.Personen -> PersonenSeite(s, viewModel)
                        Bereich.Fotos -> FotoSeite(s, viewModel)
                        Bereich.Mehr -> MehrSeite(s, viewModel)
                        Bereich.Baum -> if (profilDaneben) {
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxHeight()) { BaumSeite(s, viewModel, ansicht, kompakt = false) }
                                VerticalDivider()
                                Box(Modifier.width(440.dp).fillMaxHeight()) { ProfilSeite(s.profil!!, s, viewModel, eingebettet = true) }
                            }
                        } else BaumSeite(s, viewModel, ansicht, kompakt = !breit)
                    }
                }
                }
            }
        }
        if (s.gesichter != null || s.detail != null || (s.profil != null && !profilDaneben)) {
            SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
        }
    }

    BearbeitenDialoge(s, viewModel)
    when (s.einstellungDialog) {
        EinstellungDialog.Darstellung -> DarstellungDialog(s.darstellung, viewModel)
        EinstellungDialog.Sprache -> de.bgghome.webtrees.nativ.ui.SpracheDialog(
            onDismiss = { viewModel.einstellungDialog(null) }, onChosen = viewModel::spracheGewaehlt,
        )
        null -> {}
    }
    s.texterkennung?.let { TexterkennungDialog(it, s, viewModel) }

    // Der Betrachter liegt ueber allem.
    s.betrachter?.let { b ->
        PhotoViewer(viewer = b, onIndex = viewModel::betrachterIndex, onClose = viewModel::betrachterSchliessen, onOpenWeb = viewModel::oeffnen)
    }
    s.pdf?.let { PdfAnsicht(it, onClose = viewModel::pdfSchliessen) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Kopfzeile(s: Zustand, viewModel: GwViewModel) {
    var menue by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(s.baumName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = kopfFarben(),
        actions = {
            IconButton(onClick = {
                when (s.bereich) {
                    Bereich.Start -> viewModel.startseiteLaden()
                    else -> viewModel.neuLaden()
                }
            }) { Icon(Icons.Default.Refresh, stringResource(Res.string.menu_reload)) }
            Box {
                IconButton(onClick = { menue = true }) { Icon(Icons.Default.MoreVert, stringResource(Res.string.menu_more)) }
                DropdownMenu(menue, onDismissRequest = { menue = false }) {
                    if (s.kannErinnern) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.setting_reminder)) },
                            leadingIcon = { Checkbox(s.erinnerung, onCheckedChange = null) },
                            onClick = { menue = false; viewModel.erinnerung(!s.erinnerung) },
                        )
                        HorizontalDivider()
                    }
                    DropdownMenuItem(text = { Text(stringResource(Res.string.menu_appearance)) }, onClick = { menue = false; viewModel.einstellungDialog(EinstellungDialog.Darstellung) })
                    DropdownMenuItem(text = { Text(stringResource(WRes.string.menu_language)) }, onClick = { menue = false; viewModel.einstellungDialog(EinstellungDialog.Sprache) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(Res.string.menu_export)) }, onClick = { menue = false; viewModel.exportDialog() })
                    if (s.chatMoeglich) DropdownMenuItem(text = { Text(stringResource(Res.string.menu_chat)) }, onClick = { menue = false; viewModel.chatOeffnen() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(Res.string.menu_logout)) }, onClick = { menue = false; viewModel.abmelden() })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.menu_about)) }, onClick = { menue = false; viewModel.ueber() })
                }
            }
        },
    )
}

@Composable
private fun UntereLeiste(aktiv: Bereich, waehlen: (Bereich) -> Unit) {
    NavigationBar {
        for ((b, symbol, text) in listOf(
            Triple(Bereich.Start, Icons.Default.Home, Res.string.nav_start),
            Triple(Bereich.Baum, BaumSymbol, Res.string.nav_tree),
            Triple(Bereich.Personen, Icons.Default.Search, Res.string.nav_people),
            Triple(Bereich.Fotos, FotoSymbol, Res.string.nav_photos),
            Triple(Bereich.Mehr, Icons.Default.List, Res.string.nav_more),
        )) {
            Eintrag(b == aktiv, symbol, text) { waehlen(b) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Eintrag(aktiv: Boolean, symbol: ImageVector, text: StringResource, onClick: () -> Unit) {
    NavigationBarItem(
        selected = aktiv, onClick = onClick,
        icon = { Icon(symbol, null) },
        label = { Text(stringResource(text), maxLines = 1) },
    )
}

/** Symbole, die das Material-Grundset nicht hat: Baum (drei Kaesten) und Foto. */
private val BaumSymbol: ImageVector by lazy {
    ImageVector.Builder("baum", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                "M3,3h8v5H3z M13,3h8v5h-8z M8,16h8v5H8z M7,8v3h10V8h-1.5v1.5h-7V8z M11.25,11h1.5v5h-1.5z",
            ).toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        )
    }.build()
}

private val FotoSymbol: ImageVector by lazy {
    ImageVector.Builder("foto", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z",
            ).toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        )
    }.build()
}

/** Darstellung waehlen; wirkt sofort, der Dialog bleibt zum Vergleichen offen. */
@Composable
private fun DarstellungDialog(aktuell: Darstellung, viewModel: GwViewModel) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { viewModel.einstellungDialog(null) },
        title = { Text(stringResource(Res.string.menu_appearance).trimEnd('…', ' ')) },
        text = {
            androidx.compose.foundation.layout.Column {
                listOf(Darstellung.Klassisch to Res.string.appearance_classic, Darstellung.Hell to Res.string.appearance_light).forEach { (d, text) ->
                    Row(Modifier.fillMaxWidth().clickable { viewModel.darstellung(d) }.padding(vertical = 6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(aktuell == d, onClick = { viewModel.darstellung(d) })
                        Text(stringResource(text))
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { viewModel.einstellungDialog(null) }) { Text(stringResource(Res.string.search_close)) } },
    )
}
