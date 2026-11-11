package de.bgghome.gwand.ui

import androidx.compose.foundation.layout.Box
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
    val layout = remember(s.ahnentafel, s.nachkommen, s.geschwister, s.zeigeCousins, s.darfBearbeiten) {
        val oben = s.ahnentafel
        val unten = s.nachkommen
        if (oben != null && unten != null) {
            TreeLayout.build(oben, unten, canEdit = s.darfBearbeiten, siblings = s.geschwister, cousins = s.zeigeCousins, cousinSpouses = s.zeigeCousins)
        } else null
    }
    Box(Modifier.fillMaxSize()) {
        if (s.baumArt == BaumArt.Faecher) {
            s.ahnentafel?.let { FaecherAnsicht(it, onPerson = { p -> viewModel.profilOeffnen(p.xref) }) }
        } else {
            FamilyTreeView(
                layout = layout,
                selected = s.profil?.handle,
                fullscreen = false,
                initialScale = if (kompakt) 0.8f else 1f,
                compact = kompakt,
                onToggleFullscreen = {},
                onPerson = { viewModel.profilOeffnen(it.xref) },
                onPlus = { viewModel.verwandterDialog(it) },
                onExpand = { _, _ -> viewModel.mehrVorfahren() },
                onMakeRoot = viewModel::inDieMitte,
                onExpandDown = { viewModel.mehrNachkommen() },
                ansicht = ansicht,
                showFullscreen = false,
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
