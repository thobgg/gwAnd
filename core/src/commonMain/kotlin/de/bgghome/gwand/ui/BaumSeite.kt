package de.bgghome.gwand.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.bgghome.gwand.res.setting_cousins
import de.bgghome.gwand.res.setting_siblings
import de.bgghome.webtrees.nativ.res.Res as WRes
import de.bgghome.webtrees.nativ.res.action_back
import de.bgghome.webtrees.nativ.res.tree_ancestors_head
import de.bgghome.webtrees.nativ.res.tree_descendants_head
import de.bgghome.webtrees.nativ.res.tree_descendants_none
import de.bgghome.webtrees.nativ.res.tree_generations_n
import org.jetbrains.compose.resources.pluralStringResource
import de.bgghome.webtrees.nativ.res.tree_home
import de.bgghome.webtrees.nativ.res.tree_settings
import de.bgghome.webtrees.nativ.res.tree_show_cousin_spouses
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.tree_fan
import de.bgghome.gwand.res.tree_hourglass
import de.bgghome.webtrees.nativ.ui.tree.BaumAnsicht
import de.bgghome.webtrees.nativ.ui.tree.FamilyTreeView
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout
import org.jetbrains.compose.resources.stringResource

/**
 * Der Baum: als Sanduhr mit der geteilten Baumansicht (Tipp oeffnet das Profil, langer Druck setzt die Person in die
 * Mitte, die Symbole ueber und unter den Karten laden weitere Generationen) oder als Faecher der Vorfahren.
 */
@Composable
fun BaumSeite(s: Zustand, viewModel: GwViewModel, ansicht: BaumAnsicht, kompakt: Boolean) {
    if (s.wurzel == null) return
    val layout = remember(s.ahnentafel, s.nachkommen, s.geschwister, s.zeigeCousins, s.zeigeCousinPartner, s.darfBearbeiten) {
        val oben = s.ahnentafel
        val unten = s.nachkommen
        if (oben != null && unten != null) {
            TreeLayout.build(oben, unten, canEdit = s.darfBearbeiten, siblings = s.geschwister, cousins = s.zeigeCousins,
                cousinSpouses = s.zeigeCousins && s.zeigeCousinPartner)
        } else null
    }
    Column(Modifier.fillMaxSize()) {
    if (!s.baumVollbild) {
        BaumLeiste(s, viewModel, kurz = kompakt)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    Box(Modifier.fillMaxSize()) {
        if (s.baumArt == BaumArt.Faecher) {
            s.ahnentafel?.let { FaecherAnsicht(it, onPerson = { p -> viewModel.profilOeffnen(p.xref) }) }
        } else {
            FamilyTreeView(
                layout = layout,
                selected = s.profil?.handle,
                fullscreen = s.baumVollbild,
                initialScale = if (kompakt) 0.8f else 1f,
                compact = kompakt,
                onToggleFullscreen = { viewModel.baumVollbild(!s.baumVollbild) },
                onPerson = { viewModel.profilOeffnen(it.xref) },
                onPlus = { viewModel.verwandterDialog(it) },
                onExpand = { _, _ -> viewModel.mehrVorfahren() },
                onMakeRoot = viewModel::inDieMitte,
                onExpandDown = { viewModel.mehrNachkommen() },
                ansicht = ansicht,
                showFullscreen = true,
            )
        }
        val arten = listOf(BaumArt.Sanduhr to Res.string.tree_hourglass, BaumArt.Faecher to Res.string.tree_fan)
        // Unten links, neben den runden Knoepfen der Baumansicht: oben wuerde er Karten verdecken
        SingleChoiceSegmentedButtonRow(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 20.dp).width(220.dp)) {
            arten.forEachIndexed { i, (art, text) ->
                SegmentedButton(selected = s.baumArt == art, onClick = { viewModel.baumArt(art) }, shape = SegmentedButtonDefaults.itemShape(i, arten.size)) {
                    Text(stringResource(text), maxLines = 1)
                }
            }
        }
    }
}
}

/** Leiste ueber dem Baum: zurueck zur vorigen Mitte, zur Startperson, Generationen, Baumansicht. */
@Composable
private fun BaumLeiste(s: Zustand, viewModel: GwViewModel, kurz: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.wurzelVerlauf.isNotEmpty()) IconButton(onClick = viewModel::vorigeMitte) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(WRes.string.action_back))
            }
            val start = s.startperson
            if (start != null && start != s.wurzel) IconButton(onClick = viewModel::zurStartperson) {
                Icon(Icons.Default.Person, stringResource(WRes.string.tree_home))
            }
            Spacer(Modifier.weight(1f))
            Generationen(s, viewModel, kurz)
            Baumansicht(s, viewModel)
        }
    }
}

@Composable
private fun Generationen(s: Zustand, viewModel: GwViewModel, kurz: Boolean) {
    var offen by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { offen = true }) {
            Text(if (kurz) "${s.ahnenGenerationen}" else generationenText(s.ahnenGenerationen), maxLines = 1, softWrap = false)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(offen, onDismissRequest = { offen = false }) {
            Ueberschrift(stringResource(WRes.string.tree_ancestors_head))
            (2..6).forEach { n ->
                DropdownMenuItem(
                    text = { Text(generationenText(n)) },
                    leadingIcon = { RadioButton(s.ahnenGenerationen == n, onClick = null) },
                    onClick = { offen = false; viewModel.generationen(ahnen = n) },
                )
            }
            if (s.baumArt == BaumArt.Sanduhr) {
                HorizontalDivider()
                Ueberschrift(stringResource(WRes.string.tree_descendants_head))
                (0..4).forEach { n ->
                    DropdownMenuItem(
                        text = { Text(if (n == 0) stringResource(WRes.string.tree_descendants_none) else generationenText(n)) },
                        leadingIcon = { RadioButton(s.nachkommenGenerationen == n, onClick = null) },
                        onClick = { offen = false; viewModel.generationen(nachkommen = n) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Ueberschrift(text: String) = Text(text, Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Zahnrad: Geschwister, Cousins und deren Partner. */
@Composable
private fun Baumansicht(s: Zustand, viewModel: GwViewModel) {
    var offen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { offen = true }) { Icon(Icons.Default.Settings, stringResource(WRes.string.tree_settings)) }
        DropdownMenu(offen, onDismissRequest = { offen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.setting_siblings)) },
                leadingIcon = { Checkbox(s.zeigeGeschwister, onCheckedChange = null) },
                onClick = { offen = false; viewModel.zeigeGeschwister(!s.zeigeGeschwister) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.setting_cousins)) },
                leadingIcon = { Checkbox(s.zeigeCousins && s.zeigeGeschwister, onCheckedChange = null, enabled = s.zeigeGeschwister) },
                enabled = s.zeigeGeschwister,
                onClick = { offen = false; viewModel.zeigeCousins(!s.zeigeCousins) },
            )
            val cousins = s.zeigeGeschwister && s.zeigeCousins
            DropdownMenuItem(
                text = { Text(stringResource(WRes.string.tree_show_cousin_spouses)) },
                leadingIcon = { Checkbox(s.zeigeCousinPartner && cousins, onCheckedChange = null, enabled = cousins) },
                enabled = cousins,
                onClick = { offen = false; viewModel.zeigeCousinPartner(!s.zeigeCousinPartner) },
            )
        }
    }
}

@Composable
private fun generationenText(n: Int) = pluralStringResource(WRes.plurals.tree_generations_n, n, n)
