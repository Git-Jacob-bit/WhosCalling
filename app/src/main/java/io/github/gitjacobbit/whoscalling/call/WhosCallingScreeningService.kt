package io.github.gitjacobbit.whoscalling.call

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import io.github.gitjacobbit.whoscalling.container
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Invoked by the system for incoming calls from numbers outside the contacts. */
class WhosCallingScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val container = applicationContext.container
        val raw = details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }

        container.appScope.launch {
            val e164 = raw?.let(container.callers::normalize)
            // The system gives us ~5 s to answer. Any failure fails open: a missed block beats a lost call.
            val block = e164 != null && runCatching {
                withTimeoutOrNull(2_000) { container.callers.shouldBlock(e164) } == true
            }.onFailure { Log.e(TAG, "Block decision failed", it) }.getOrDefault(false)
            respondToCall(
                details,
                CallResponse.Builder()
                    .setDisallowCall(block)
                    .setRejectCall(block)
                    .setSkipNotification(block)
                    .build(),
            )
            // Logged before the lookup starts so a new number's row picks up this call in its counter.
            if (e164 != null) {
                runCatching { container.callers.registerCall(e164, blocked = block) }
                    .onFailure { Log.e(TAG, "Call log write failed", it) }
            }
            if (!block) container.calls.onIncomingCall(raw)
        }
    }

    private companion object {
        const val TAG = "Screening"
    }
}
