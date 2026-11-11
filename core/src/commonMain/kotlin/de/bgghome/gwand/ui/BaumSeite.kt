package de.bgghome.gwand.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.bgghome.webtrees.nativ.ui.tree.BaumAnsicht
import de.bgghome.webtrees.nativ.ui.tree.FamilyTreeView
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout

/**
 * Der Baum mit der geteilten Baumansicht: Tipp oeffnet das Profil, langer Druck setzt die Person in die Mitte,
 * die Symbole ueber und unter den Karten laden weitere Generationen.
 */
@Composable
fun BaumSeite(s: Zustand, viewModel: GwViewModel, ansicht: BaumAnsicht, kompakt: Boolean) {
    if (s.wurzel == null) return
    val layout = remember(s.ahnentafel, s.nachkommen, s.geschwister, s.zeigeCousins) {
        val oben = s.ahnentafel
        val unten = s.nachkommen
        if (oben != null && unten != null) {
            TreeLayout.build(oben, unten, canEdit = false, siblings = s.geschwister, cousins = s.zeigeCousins, cousinSpouses = s.zeigeCousins)
        } else null
    }
    FamilyTreeView(
        layout = layout,
        selected = s.profil?.handle,
        fullscreen = false,
        initialScale = if (kompakt) 0.8f else 1f,
        compact = kompakt,
        onToggleFullscreen = {},
        onPerson = { viewModel.profilOeffnen(it.xref) },
        onPlus = {},
        onExpand = { _, _ -> viewModel.mehrVorfahren() },
        onMakeRoot = viewModel::inDieMitte,
        onExpandDown = { viewModel.mehrNachkommen() },
        ansicht = ansicht,
        showFullscreen = false,
    )
}
