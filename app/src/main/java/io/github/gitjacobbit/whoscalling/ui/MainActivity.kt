package io.github.gitjacobbit.whoscalling.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.gitjacobbit.whoscalling.container
import io.github.gitjacobbit.whoscalling.ui.detail.DetailScreen
import io.github.gitjacobbit.whoscalling.ui.history.HistoryScreen
import io.github.gitjacobbit.whoscalling.ui.onboarding.OnboardingScreen
import io.github.gitjacobbit.whoscalling.ui.settings.SettingsScreen
import io.github.gitjacobbit.whoscalling.ui.theme.WhosCallingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhosCallingTheme {
                var start by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    start = if (container.settings.current().onboardingDone) "history" else "onboarding"
                }
                val startDestination = start ?: return@WhosCallingTheme
                val nav = rememberNavController()
                NavHost(nav, startDestination = startDestination) {
                    composable("onboarding") {
                        OnboardingScreen(onFinished = {
                            nav.navigate("history") { popUpTo("onboarding") { inclusive = true } }
                        })
                    }
                    composable("history") {
                        HistoryScreen(
                            onOpenNumber = { e164, force -> nav.navigate("number/${Uri.encode(e164)}?force=$force") },
                            onOpenSettings = { nav.navigate("settings") },
                        )
                    }
                    composable(
                        "number/{e164}?force={force}",
                        arguments = listOf(
                            navArgument("e164") { type = NavType.StringType },
                            navArgument("force") { type = NavType.BoolType; defaultValue = false },
                        ),
                    ) { entry ->
                        DetailScreen(
                            e164 = entry.arguments?.getString("e164").orEmpty(),
                            forceLookup = entry.arguments?.getBoolean("force") == true,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}
