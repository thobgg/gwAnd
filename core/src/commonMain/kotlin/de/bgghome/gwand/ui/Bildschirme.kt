package de.bgghome.gwand.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.app_name
import de.bgghome.gwand.res.login_oidc
import de.bgghome.gwand.res.login_or
import de.bgghome.gwand.res.app_subtitle
import de.bgghome.gwand.res.login_button
import de.bgghome.gwand.res.login_note
import de.bgghome.gwand.res.login_password
import de.bgghome.gwand.res.login_server
import de.bgghome.gwand.res.login_server_hint
import de.bgghome.gwand.res.login_user
import de.bgghome.gwand.res.menu_logout
import de.bgghome.gwand.res.menu_more
import de.bgghome.gwand.res.menu_reload
import de.bgghome.gwand.res.menu_search
import de.bgghome.gwand.res.menu_start_person
import de.bgghome.gwand.res.person_born
import de.bgghome.gwand.res.person_center
import de.bgghome.gwand.res.person_died
import de.bgghome.gwand.res.search_close
import de.bgghome.gwand.res.search_count
import de.bgghome.gwand.res.search_hint
import de.bgghome.webtrees.nativ.api.EventJson
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.ui.Avatar
import de.bgghome.webtrees.nativ.ui.tree.BaumAnsicht
import de.bgghome.webtrees.nativ.ui.tree.FamilyTreeView
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Wurzel der Oberflaeche: Theme in den Farben des Baums, dann der passende Bildschirm. */
@Composable
fun GwRoot(viewModel: GwViewModel) {
    val s by viewModel.state.collectAsState()
    GwTheme(farben = s.farben) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (s.bildschirm) {
                Bildschirm.Laden -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Bildschirm.Anmeldung -> AnmeldeBildschirm(s, viewModel)
                Bildschirm.Haupt -> HauptBildschirm(s, viewModel)
            }
        }
    }
}

@Composable
fun AnmeldeBildschirm(s: Zustand, viewModel: GwViewModel) {
    s.oidcFenster?.let { OidcAnmeldung(it, viewModel); return }
    var adresse by rememberSaveable { mutableStateOf(s.adresse) }
    var name by rememberSaveable { mutableStateOf(s.benutzername) }
    var passwort by remember { mutableStateOf("") }
    val senden = { if (adresse.isNotBlank() && name.isNotBlank() && passwort.isNotEmpty()) viewModel.anmelden(adresse, name, passwort) }

    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.app_subtitle), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                adresse, { adresse = it; viewModel.oidcPruefen(it) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(Res.string.login_server)) },
                placeholder = { Text(stringResource(Res.string.login_server_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            val nurOidc = s.oidc?.nurOidc == true
            if (!nurOidc) OutlinedTextField(
                name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(Res.string.login_user)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            if (!nurOidc) OutlinedTextField(
                passwort, { passwort = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(Res.string.login_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { senden() }),
            )
            s.fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (!nurOidc) Button(onClick = { senden() }, enabled = !s.beschaeftigt, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                if (s.beschaeftigt) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(Res.string.login_button))
            }
            s.oidc?.let { k ->
                if (!nurOidc) Text(
                    stringResource(Res.string.login_or), Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                k.providers.forEach { a ->
                    OutlinedButton(
                        onClick = { viewModel.oidcStarten(adresse, a) }, enabled = !s.beschaeftigt && adresse.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (s.beschaeftigt && nurOidc) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(Res.string.login_oidc, a.name.ifBlank { a.id }))
                    }
                }
            }
            if (!nurOidc) Text(stringResource(Res.string.login_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

