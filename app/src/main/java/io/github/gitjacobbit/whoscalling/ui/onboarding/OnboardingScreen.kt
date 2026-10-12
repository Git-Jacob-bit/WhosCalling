package io.github.gitjacobbit.whoscalling.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gitjacobbit.whoscalling.container
import io.github.gitjacobbit.whoscalling.ui.ApiKeyField
import io.github.gitjacobbit.whoscalling.ui.PermissionsChecklist
import io.github.gitjacobbit.whoscalling.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var keySaved by remember { mutableStateOf(false) }
    var essentialGranted by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.Accent.copy(alpha = 0.18f), Palette.Background, Palette.Background)))
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        StepDots(step, total = 3)
        Spacer(Modifier.height(32.dp))
        AnimatedContent(step, label = "step", modifier = Modifier.weight(1f)) { current ->
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (current) {
                    0 -> {
                        Title("Kto dzwoni?")
                        Body(
                            "WhosCalling sprawdza nieznane numery w trakcie dzwonienia: przeszukuje internet " +
                                "za pomocą AI i pokazuje krótką ocenę, czy warto odebrać. Każdy wynik zostaje " +
                                "w lokalnej bazie na telefonie, więc przy kolejnym połączeniu werdykt jest od razu."
                        )
                        Body(
                            "Prywatność: numer dzwoniącego jest wysyłany do OpenAI z Twoim własnym kluczem API " +
                                "i na Twój koszt. Kontakty, historia i baza nie opuszczają telefonu."
                        )
                    }
                    1 -> {
                        Title("Twój klucz OpenAI")
                        Body(
                            "Klucz utworzysz na platform.openai.com → API keys. Jest szyfrowany kluczem z Android " +
                                "Keystore i nigdy nie opuszcza urządzenia poza zapytaniami do OpenAI."
                        )
                        ApiKeyField(onSaved = { keySaved = true })
                        if (!keySaved) {
                            Body("Bez klucza aplikacja działa w trybie podstawowym: kraj, typ numeru i podejrzane prefiksy.")
                        }
                    }
                    else -> {
                        Title("Uprawnienia")
                        Body("Dwa pierwsze są wymagane, żeby nakładka pojawiała się podczas dzwonienia.")
                        PermissionsChecklist(onStatus = { essentialGranted = it.essentialGranted })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Wstecz") } else Spacer(Modifier)
            when (step) {
                0 -> Button(onClick = { step = 1 }) { Text("Zaczynamy") }
                1 -> Button(onClick = { step = 2 }) { Text(if (keySaved) "Dalej" else "Pomiń") }
                else -> Button(
                    enabled = essentialGranted,
                    onClick = {
                        scope.launch {
                            container.settings.setOnboardingDone()
                            onFinished()
                        }
                    },
                ) { Text("Gotowe") }
            }
        }
    }
}

@Composable
private fun StepDots(step: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(width = if (i == step) 24.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= step) Palette.Accent else Palette.Outline)
            )
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

@Composable
private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
