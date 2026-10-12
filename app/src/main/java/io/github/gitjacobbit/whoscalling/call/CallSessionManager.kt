package io.github.gitjacobbit.whoscalling.call

import android.content.Context
import android.provider.Settings
import android.util.Log
import io.github.gitjacobbit.whoscalling.domain.CallerRepository
import io.github.gitjacobbit.whoscalling.domain.UserOverride
import io.github.gitjacobbit.whoscalling.overlay.OverlayController
import io.github.gitjacobbit.whoscalling.overlay.OverlayUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Glue between an incoming call, the lookup pipeline and the overlay.
 * The lookup keeps running after the overlay is hidden so the result still lands in the database.
 */
class CallSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val callers: CallerRepository,
    private val overlay: OverlayController,
) {
    private var lookupJob: Job? = null

    suspend fun onIncomingCall(raw: String?) {
        if (!Settings.canDrawOverlays(context)) return
        withContext(Dispatchers.Main) {
            overlay.show(
                if (raw == null) OverlayUiState.privateNumber()
                else OverlayUiState.initial(raw),
                actions(),
            )
            // On Android 15 a foreground service may only start from the background while an overlay is visible,
            // so the overlay has to be on screen before this call.
            runCatching { CallOverlayService.start(context) }
                .onFailure { Log.w(TAG, "Foreground service not started, overlay runs without it", it) }
        }
        if (raw == null) return
        lookupJob?.cancel()
        lookupJob = scope.launch {
            callers.lookup(raw).collect { state ->
                withContext(Dispatchers.Main) { overlay.update { it.withLookup(state) } }
            }
        }
    }

    /** Shows the overlay with sample data so the look can be checked without a real call. */
    fun preview() {
        if (!Settings.canDrawOverlays(context)) return
        overlay.show(OverlayUiState.preview(), actions())
    }

    fun onCallAnswered() = overlay.update { it.copy(expanded = false) }

    fun onCallEnded() {
        overlay.hide()
        CallOverlayService.stop(context)
    }

    private fun actions() = OverlayController.Actions(
        onClose = ::onCallEnded,
        onOverride = { e164, override: UserOverride ->
            scope.launch { callers.setOverride(e164, override) }
            overlay.update { it.copy(userOverride = override) }
        },
    )

    private companion object {
        const val TAG = "CallSession"
    }
}
