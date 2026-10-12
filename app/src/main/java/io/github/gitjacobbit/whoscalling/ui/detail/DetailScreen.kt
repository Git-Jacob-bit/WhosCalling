package io.github.gitjacobbit.whoscalling.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.gitjacobbit.whoscalling.container
import io.github.gitjacobbit.whoscalling.domain.UserOverride
import io.github.gitjacobbit.whoscalling.overlay.OverlayCard
import io.github.gitjacobbit.whoscalling.overlay.OverlayUiState
import io.github.gitjacobbit.whoscalling.ui.relativeTime
import io.github.gitjacobbit.whoscalling.ui.theme.Palette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(e164: String, forceLookup: Boolean, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val dao = container.database.numberDao()
    val scope = rememberCoroutineScope()
    val entry by dao.observe(e164).collectAsState(initial = null)
    val calls by dao.observeCallsFor(e164).collectAsState(initial = emptyList())
    var lookup by remember { mutableStateOf<OverlayUiState?>(null) }

    fun runLookup(force: Boolean) {
        scope.launch {
            container.callers.lookup(e164, force).collect { state ->
                lookup = (lookup ?: OverlayUiState.initial(e164)).withLookup(state)
            }
        }
    }
    LaunchedEffect(e164) { runLookup(forceLookup) }

    Scaffold(
        containerColor = Palette.Background,
        topBar = {
            TopAppBar(
                title = { Text(e164) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz") } },
                actions = {
                    if (lookup?.loading == true) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { runLookup(true) }) { Icon(Icons.Rounded.Refresh, "Sprawdź ponownie") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            lookup?.let { state ->
                OverlayCard(
                    state = state.copy(userOverride = entry?.userOverride, expanded = true),
                    onClose = onBack,
                    onToggle = {},
                    onOverride = { override ->
                        val next = override.takeIf { it != entry?.userOverride }
                        scope.launch { container.callers.setOverride(e164, next) }
                    },
                    onDrag = {},
                )
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entry?.let { e ->
                    if (e.userOverride != null) {
                        FilterChip(
                            selected = true,
                            onClick = { scope.launch { container.callers.setOverride(e164, null) } },
                            label = { Text(if (e.userOverride == UserOverride.SPAM) "Twoja ocena: spam · usuń" else "Twoja ocena: bezpieczny · usuń") },
                        )
                    }
                    Text(
                        (if (e.aiChecked) "Sprawdzone przez AI " else "Analiza lokalna ") + relativeTime(e.checkedAt),
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary,
                    )
                    if (e.sources.isNotEmpty()) Sources(e.sources)
                }
                if (calls.isNotEmpty()) {
                    Section("Połączenia (${calls.size})")
                    calls.forEach { call ->
                        Text(
                            relativeTime(call.timestamp) + if (call.blocked) " · zablokowane" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (call.blocked) Palette.Reject else Palette.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Sources(sources: List<String>) {
    val uriHandler = LocalUriHandler.current
    Section("Źródła")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sources.forEach { url ->
            Text(
                url,
                color = Palette.Accent,
                style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.Underline,
                maxLines = 2,
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(url) } },
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Row { Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.TextSecondary) }
}
