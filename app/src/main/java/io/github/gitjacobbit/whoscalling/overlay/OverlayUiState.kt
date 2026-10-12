package io.github.gitjacobbit.whoscalling.overlay

import io.github.gitjacobbit.whoscalling.domain.CallerCategory
import io.github.gitjacobbit.whoscalling.domain.LookupState
import io.github.gitjacobbit.whoscalling.domain.UserOverride
import io.github.gitjacobbit.whoscalling.domain.Verdict

data class OverlayUiState(
    val e164: String?,
    val displayNumber: String,
    val subtitle: String?,
    val verdict: Verdict = Verdict.UNKNOWN,
    val score: Int? = null,
    val category: CallerCategory = CallerCategory.UNKNOWN,
    val owner: String? = null,
    val summary: String? = null,
    val callCount: Int = 0,
    val fromCache: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val userOverride: UserOverride? = null,
    val expanded: Boolean = true,
) {
    /** The user's own rating always wins over the AI. */
    val effectiveVerdict: Verdict
        get() = when (userOverride) {
            UserOverride.SAFE -> Verdict.ANSWER
            UserOverride.SPAM -> Verdict.REJECT
            null -> verdict
        }

    fun withLookup(state: LookupState): OverlayUiState {
        val entry = state.entry
        return copy(
            e164 = state.local.e164,
            displayNumber = state.local.formatted,
            subtitle = listOfNotNull(state.local.countryName, state.local.typeLabel).joinToString(" · ").ifEmpty { null },
            verdict = entry?.verdict ?: verdict,
            score = entry?.score ?: score,
            category = entry?.category ?: category,
            owner = entry?.owner,
            summary = entry?.summary,
            callCount = entry?.callCount ?: callCount,
            fromCache = state.fromCache,
            loading = state.loading,
            error = state.error,
            userOverride = entry?.userOverride ?: userOverride,
        )
    }

    companion object {
        fun initial(raw: String) = OverlayUiState(e164 = null, displayNumber = raw, subtitle = null, loading = true)

        fun privateNumber() = OverlayUiState(
            e164 = null,
            displayNumber = "Numer zastrzeżony",
            subtitle = "Dzwoniący ukrył swój numer",
            summary = "Nie da się sprawdzić numeru zastrzeżonego. Banki i urzędy rzadko dzwonią z ukrytych numerów.",
        )

        fun preview() = OverlayUiState(
            e164 = null,
            displayNumber = "+48 22 123 45 67",
            subtitle = "Polska · stacjonarny",
            verdict = Verdict.REJECT,
            score = 92,
            category = CallerCategory.TELEMARKETING,
            owner = "Przykładowa Energia Sp. z o.o.",
            summary = "47 zgłoszeń: natarczywe oferty fotowoltaiki, dzwonią kilka razy dziennie.",
            callCount = 3,
            fromCache = true,
        )
    }
}
