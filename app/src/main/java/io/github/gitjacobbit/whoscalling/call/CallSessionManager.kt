package io.github.gitjacobbit.whoscalling.call

import android.content.Context
import android.provider.Settings
import android.util.Log
import io.github.gitjacobbit.whoscalling.domain.CallerRepository
import io.github.gitjacobbit.whoscalling.domain.UserOverride
import io.github.gitjacobbit.whoscalling.overlay.OverlayController
import io.github.gitjacobbit.whoscalling.overlay.OverlayUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Glue between an incoming call, the lookup pipeline and the overlay.
 * All session state is touched on the main thread only. Each call gets a session id so a late
 * lookup result from an earlier call never lands on the current overlay; lookups themselves keep
 * running after their overlay is gone so the result still reaches the database.
 */
class CallSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val callers: CallerRepository,
    private val overlay: OverlayController,
) {
    private var session = 0
    private var cleanupJob: Job? = null

    suspend fun onIncomingCall(raw: String?) {
        if (!Settings.canDrawOverlays(context)) return
        val id = withContext(Dispatchers.Main) {
            val id = ++session
            overlay.show(
                if (raw == null) OverlayUiState.privateNumber() else OverlayUiState.initial(raw),
                actions(),
                // On Android 15 a foreground service may only start from the background while an overlay
                // is visible, so wait until the overlay has drawn its first frame.
                onVisible = { if (id == session) startService(id) },
            )
            // Independent of the service, which may fail to start or never see the call state.
            cleanupJob?.cancel()
            cleanupJob = scope.launch(Dispatchers.Main) {
                delay(MAX_SESSION_MS)
                onCallEnded(id)
            }
            id
        }
        if (raw == null) return
        scope.launch {
            try {
                callers.lookup(raw).collect { state ->
                    withContext(Dispatchers.Main) {
                        if (id == session) overlay.update { it.withLookup(state) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Lookup failed", e)
                withContext(Dispatchers.Main) {
                    if (id == session) overlay.update { it.copy(loading = false, error = "Błąd analizy numeru") }
                }
            }
        }
    }

    /** Shows the overlay with sample data so the look can be checked without a real call. */
    fun preview() {
        if (!Settings.canDrawOverlays(context)) return
        onCallEnded()
        overlay.show(OverlayUiState.preview(), actions())
    }

    fun onCallAnswered(id: Int) {
        if (id == session) overlay.update { it.copy(expanded = false) }
    }

    /** [id] guards against stale timers and callbacks; null means the user closed the overlay. */
    fun onCallEnded(id: Int? = null) {
        if (id != null && id != session) return
        session++
        cleanupJob?.cancel()
        overlay.hide()
        CallOverlayService.stop(context)
    }

    private fun startService(id: Int) {
        runCatching { CallOverlayService.start(context, id) }
            .onFailure { Log.w(TAG, "Foreground service not started, overlay runs without it", it) }
    }

    private fun actions() = OverlayController.Actions(
        onClose = { onCallEnded() },
        onOverride = { e164, override: UserOverride ->
            scope.launch { callers.setOverride(e164, override) }
            overlay.update { it.copy(userOverride = override) }
        },
    )

    private companion object {
        const val TAG = "CallSession"
        const val MAX_SESSION_MS = 3 * 60_000L
    }
}
