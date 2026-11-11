package de.bgghome.gwand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.chat_hint
import de.bgghome.gwand.res.chat_send
import de.bgghome.gwand.res.menu_chat
import de.bgghome.gwand.res.profile_back
import org.jetbrains.compose.resources.stringResource

/** Der KI-Assistent des Servers (nur, wenn der Betreiber ihn eingerichtet hat). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSeite(c: Chat, viewModel: GwViewModel) {
    var frage by remember { mutableStateOf("") }
    val liste = rememberLazyListState()
    LaunchedEffect(c.verlauf.size) { if (c.verlauf.isNotEmpty()) liste.animateScrollToItem(c.verlauf.lastIndex) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.menu_chat)) },
                navigationIcon = { IconButton(onClick = viewModel::chatSchliessen) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.profile_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innen ->
        Column(Modifier.fillMaxSize().padding(innen).imePadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = liste, verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                items(c.verlauf) { (rolle, text) ->
                    val ich = rolle == "human"
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (ich) Alignment.CenterEnd else Alignment.CenterStart) {
                        SelectionContainer {
                            Text(text, Modifier.widthIn(max = 320.dp)
                                .background(if (ich) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (c.laeuft) item { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(frage, { frage = it }, Modifier.weight(1f), placeholder = { Text(stringResource(Res.string.chat_hint)) }, maxLines = 4)
                IconButton(onClick = { viewModel.chatFragen(frage); frage = "" }, enabled = frage.isNotBlank() && !c.laeuft) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(Res.string.chat_send))
                }
            }
        }
    }
}
