package io.github.gitjacobbit.whoscalling.call

import android.telecom.Call
import android.telecom.CallScreeningService
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
            // The system gives us ~5 s to answer; the cache lookup is a single indexed query.
            val block = e164 != null && withTimeoutOrNull(2_000) { container.callers.shouldBlock(e164) } == true
            respondToCall(
                details,
                CallResponse.Builder()
                    .setDisallowCall(block)
                    .setRejectCall(block)
                    .setSkipNotification(block)
                    .build(),
            )
            if (e164 != null) container.callers.registerCall(e164, blocked = block)
            if (!block) container.calls.onIncomingCall(raw)
        }
    }
}
