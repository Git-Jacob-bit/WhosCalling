package io.github.gitjacobbit.whoscalling

import android.app.Application
import android.content.Context
import android.telephony.TelephonyManager
import io.github.gitjacobbit.whoscalling.call.CallSessionManager
import io.github.gitjacobbit.whoscalling.data.ai.OpenAiClient
import io.github.gitjacobbit.whoscalling.data.db.AppDatabase
import io.github.gitjacobbit.whoscalling.data.settings.SettingsRepository
import io.github.gitjacobbit.whoscalling.domain.CallerRepository
import io.github.gitjacobbit.whoscalling.domain.LocalAnalyzer
import io.github.gitjacobbit.whoscalling.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WhosCallingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency graph; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val openAi = OpenAiClient()
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    val analyzer = LocalAnalyzer {
        telephony?.networkCountryIso?.takeIf { it.isNotBlank() }?.uppercase() ?: "PL"
    }
    val callers = CallerRepository(database.numberDao(), settings, openAi, analyzer)
    val overlay = OverlayController(context)
    val calls = CallSessionManager(context, appScope, callers, overlay)
}

val Context.container: AppContainer get() = (applicationContext as WhosCallingApp).container
