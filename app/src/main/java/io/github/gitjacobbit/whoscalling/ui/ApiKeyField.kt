package io.github.gitjacobbit.whoscalling.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.gitjacobbit.whoscalling.container
import io.github.gitjacobbit.whoscalling.ui.theme.Palette
import kotlinx.coroutines.launch

/** Validates the key against the API before storing it. */
@Composable
fun ApiKeyField(onSaved: () -> Unit) {
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim(); message = null },
            label = { Text("Klucz API OpenAI") },
            placeholder = { Text("sk-…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = key.length > 20 && !testing,
                onClick = {
                    testing = true
                    scope.launch {
                        val result = container.openAi.testKey(key)
                        testing = false
                        if (result.isSuccess) {
                            container.settings.setApiKey(key)
                            message = "Klucz działa i został zapisany" to true
                            key = ""
                            onSaved()
                        } else {
                            message = (result.exceptionOrNull()?.message ?: "Nie udało się sprawdzić klucza") to false
                        }
                    }
                },
            ) {
                if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sprawdź i zapisz")
            }
        }
        message?.let { (text, ok) ->
            Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) Palette.Answer else Palette.Reject)
        }
    }
}
