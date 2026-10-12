package io.github.gitjacobbit.whoscalling.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.gitjacobbit.whoscalling.container
import io.github.gitjacobbit.whoscalling.data.db.CallWithNumber
import io.github.gitjacobbit.whoscalling.domain.CallerCategory
import io.github.gitjacobbit.whoscalling.domain.UserOverride
import io.github.gitjacobbit.whoscalling.domain.Verdict
import io.github.gitjacobbit.whoscalling.overlay.icon
import io.github.gitjacobbit.whoscalling.ui.relativeTime
import io.github.gitjacobbit.whoscalling.ui.theme.Palette
import io.github.gitjacobbit.whoscalling.ui.theme.color
import io.github.gitjacobbit.whoscalling.ui.theme.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenNumber: (e164: String, force: Boolean) -> Unit, onOpenSettings: () -> Unit) {
    val container = LocalContext.current.container
    val history by container.database.numberDao().observeHistory().collectAsState(initial = null)
    var checkDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Palette.Background,
        topBar = {
            TopAppBar(
                title = { Text("WhosCalling", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Ustawienia") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { checkDialog = true },
                icon = { Icon(Icons.Rounded.Search, null) },
                text = { Text("Sprawdź numer") },
            )
        },
    ) { padding ->
        val items = history
        when {
            items == null -> Unit
            items.isEmpty() -> EmptyState(Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { call -> CallRow(call) { onOpenNumber(call.e164, false) } }
            }
        }
    }

    if (checkDialog) {
        CheckNumberDialog(
            onDismiss = { checkDialog = false },
            onCheck = { raw ->
                checkDialog = false
                onOpenNumber(container.callers.normalize(raw), true)
            },
        )
    }
}

@Composable
private fun CallRow(call: CallWithNumber, onClick: () -> Unit) {
    val override = call.userOverride?.let(UserOverride::valueOf)
    val verdict = when (override) {
        UserOverride.SAFE -> Verdict.ANSWER
        UserOverride.SPAM -> Verdict.REJECT
        null -> call.verdict?.let(Verdict::valueOf) ?: Verdict.UNKNOWN
    }
    val category = call.category?.let(CallerCategory::valueOf) ?: CallerCategory.UNKNOWN
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(verdict.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(category.icon, null, tint = verdict.color, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(call.owner ?: call.e164, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            val details = listOfNotNull(
                call.e164.takeIf { call.owner != null },
                category.label.takeIf { category != CallerCategory.UNKNOWN },
                relativeTime(call.timestamp),
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (call.blocked) "Zablokowany" else verdict.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (call.blocked) Palette.Reject else verdict.color,
            )
            call.score?.let { Text("ryzyko $it", style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary) }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Tu pojawią się połączenia z nieznanych numerów.\nMożesz też sprawdzić numer ręcznie.",
            textAlign = TextAlign.Center,
            color = Palette.TextSecondary,
        )
    }
}

@Composable
private fun CheckNumberDialog(onDismiss: () -> Unit, onCheck: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sprawdź numer") },
        text = {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Numer telefonu") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        },
        confirmButton = { TextButton(enabled = number.count(Char::isDigit) >= 6, onClick = { onCheck(number) }) { Text("Sprawdź") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
