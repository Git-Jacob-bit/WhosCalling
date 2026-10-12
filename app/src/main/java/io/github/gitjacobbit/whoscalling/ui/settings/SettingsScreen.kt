package io.github.gitjacobbit.whoscalling.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.gitjacobbit.whoscalling.BuildConfig
import io.github.gitjacobbit.whoscalling.container
import io.github.gitjacobbit.whoscalling.data.UpdateChecker
import io.github.gitjacobbit.whoscalling.ui.ApiKeyField
import io.github.gitjacobbit.whoscalling.ui.PermissionsChecklist
import io.github.gitjacobbit.whoscalling.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContext.current.container
    val settings by container.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val current = settings ?: return

    Scaffold(
        containerColor = Palette.Background,
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card("OpenAI") {
                if (current.hasApiKey) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Klucz zapisany", color = Palette.Answer, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { scope.launch { container.settings.setApiKey(null) } }) { Text("Usuń") }
                    }
                } else {
                    ApiKeyField(onSaved = {})
                }
                var model by remember(current.model) { mutableStateOf(current.model) }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model (musi obsługiwać web search)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (model.isNotBlank() && model != current.model) {
                    FilledTonalButton(onClick = { scope.launch { container.settings.setModel(model) } }) { Text("Zapisz model") }
                }
            }

            Card("Blokowanie") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatycznie blokuj spam")
                        Text(
                            "Odrzuca połączenia z numerów sprawdzonych wcześniej przez AI z ryzykiem ≥ ${current.autoBlockThreshold}. " +
                                "Numery oznaczone przez Ciebie jako spam są blokowane zawsze.",
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary,
                        )
                    }
                    Switch(checked = current.autoBlock, onCheckedChange = { scope.launch { container.settings.setAutoBlock(it) } })
                }
                if (current.autoBlock) {
                    var threshold by remember(current.autoBlockThreshold) { mutableStateOf(current.autoBlockThreshold.toFloat()) }
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        onValueChangeFinished = { scope.launch { container.settings.setAutoBlockThreshold(threshold.roundToInt()) } },
                        valueRange = 50f..100f,
                    )
                }
            }

            Card("Baza") {
                var days by remember(current.cacheDays) { mutableStateOf(current.cacheDays.toFloat()) }
                Text("Sprawdzaj ponownie po ${days.roundToInt()} dniach")
                Slider(
                    value = days,
                    onValueChange = { days = it },
                    onValueChangeFinished = { scope.launch { container.settings.setCacheDays(days.roundToInt()) } },
                    valueRange = 1f..180f,
                )
            }

            Card("Nakładka") {
                FilledTonalButton(onClick = { container.calls.preview() }) { Text("Pokaż podgląd nakładki") }
                PermissionsChecklist()
            }

            Card("Aplikacja") {
                var updateMessage by remember { mutableStateOf<String?>(null) }
                var updateUrl by remember { mutableStateOf<String?>(null) }
                var checking by remember { mutableStateOf(false) }
                LaunchedEffect(checking) {
                    if (!checking) return@LaunchedEffect
                    UpdateChecker().check()
                        .onSuccess { update ->
                            updateMessage = update?.let { "Dostępna wersja ${it.version}" } ?: "Masz najnowszą wersję"
                            updateUrl = update?.url
                        }
                        .onFailure { updateMessage = "Nie udało się sprawdzić: ${it.message}" }
                    checking = false
                }
                Text("Wersja ${BuildConfig.VERSION_NAME}", color = Palette.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !checking, onClick = { checking = true }) { Text("Sprawdź aktualizacje") }
                    updateUrl?.let { url -> FilledTonalButton(onClick = { uriHandler.openUri(url) }) { Text("Pobierz") } }
                }
                updateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier)
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
