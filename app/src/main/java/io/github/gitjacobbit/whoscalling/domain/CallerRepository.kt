package io.github.gitjacobbit.whoscalling.domain

import io.github.gitjacobbit.whoscalling.data.ai.AiVerdict
import io.github.gitjacobbit.whoscalling.data.ai.OpenAiClient
import io.github.gitjacobbit.whoscalling.data.db.CallLogEntity
import io.github.gitjacobbit.whoscalling.data.db.NumberDao
import io.github.gitjacobbit.whoscalling.data.db.PhoneNumberEntity
import io.github.gitjacobbit.whoscalling.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

data class LookupState(
    val local: LocalInfo,
    val entry: PhoneNumberEntity?,
    val loading: Boolean,
    val error: String? = null,
    /** True when the shown result came from the local database without asking the AI. */
    val fromCache: Boolean = false,
)

class CallerRepository(
    private val dao: NumberDao,
    private val settings: SettingsRepository,
    private val ai: OpenAiClient,
    private val analyzer: LocalAnalyzer,
) {
    fun normalize(raw: String): String = analyzer.analyze(raw).e164

    /** Decides in the screening service (≤5 s budget) whether to block, using only the local database. */
    suspend fun shouldBlock(e164: String): Boolean {
        val entry = dao.get(e164) ?: return false
        return when (entry.userOverride) {
            UserOverride.SPAM -> true
            UserOverride.SAFE -> false
            null -> settings.current().let { it.autoBlock && entry.aiChecked && entry.score >= it.autoBlockThreshold }
        }
    }

    suspend fun registerCall(e164: String, blocked: Boolean) {
        val now = System.currentTimeMillis()
        dao.insertCall(CallLogEntity(e164 = e164, timestamp = now, blocked = blocked))
        dao.registerCall(e164, now)
    }

    /** Emits the cached/local state immediately, then the AI result when a refresh is needed. */
    fun lookup(raw: String, force: Boolean = false): Flow<LookupState> = flow {
        val local = analyzer.analyze(raw)
        val cached = dao.get(local.e164)
        val prefs = settings.current()
        val maxAge = TimeUnit.DAYS.toMillis(prefs.cacheDays.toLong())
        val fresh = cached != null && cached.aiChecked &&
            System.currentTimeMillis() - cached.checkedAt < maxAge
        val apiKey = settings.apiKey()

        if (!force && (fresh || cached?.userOverride != null) || apiKey == null) {
            val entry = cached ?: withCallStats(localEntry(local), null).also { dao.upsert(it) }
            val error = if (apiKey == null && !fresh) "Brak klucza API – tylko analiza lokalna" else null
            emit(LookupState(local, entry, loading = false, error = error, fromCache = cached != null))
            return@flow
        }

        emit(LookupState(local, cached, loading = true))
        try {
            val verdict = withTimeout(AI_TIMEOUT_MS) { ai.investigate(apiKey, prefs.model, local.e164, local.describe()) }
            val entry = withCallStats(verdict.toEntity(local.e164, cached), cached)
            dao.upsert(entry)
            emit(LookupState(local, entry, loading = false))
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            val entry = cached ?: withCallStats(localEntry(local), null).also { dao.upsert(it) }
            val message = if (e is TimeoutCancellationException) "AI nie odpowiedziało na czas" else e.message
            emit(LookupState(local, entry, loading = false, error = message ?: "Błąd analizy"))
        }
    }

    suspend fun setOverride(e164: String, override: UserOverride?) = dao.setOverride(e164, override?.name)

    /** A new entry is created after the screening service logged the call, so take counters from the call log. */
    private suspend fun withCallStats(entry: PhoneNumberEntity, previous: PhoneNumberEntity?) =
        if (previous != null) entry
        else entry.copy(callCount = dao.countCalls(entry.e164), lastCallAt = dao.lastCallAt(entry.e164))

    private fun localEntry(local: LocalInfo) = PhoneNumberEntity(
        e164 = local.e164,
        verdict = when {
            local.riskScore >= 70 -> Verdict.REJECT
            local.riskScore >= 30 -> Verdict.CAUTION
            else -> Verdict.UNKNOWN
        },
        score = local.riskScore,
        category = CallerCategory.UNKNOWN,
        owner = null,
        summary = local.warnings.joinToString(". ").replaceFirstChar { it.uppercase() }
            .ifEmpty { "Brak informacji o numerze." },
        sources = emptyList(),
        checkedAt = System.currentTimeMillis(),
        aiChecked = false,
    )

    private fun AiVerdict.toEntity(e164: String, previous: PhoneNumberEntity?) = PhoneNumberEntity(
        e164 = e164,
        verdict = runCatching { Verdict.valueOf(verdict.uppercase()) }.getOrDefault(Verdict.UNKNOWN),
        score = score.coerceIn(0, 100),
        category = runCatching { CallerCategory.valueOf(category.uppercase()) }.getOrDefault(CallerCategory.UNKNOWN),
        owner = owner?.takeIf { it.isNotBlank() },
        summary = summary,
        sources = sources.take(5),
        checkedAt = System.currentTimeMillis(),
        aiChecked = true,
        callCount = previous?.callCount ?: 0,
        lastCallAt = previous?.lastCallAt,
        userOverride = previous?.userOverride,
        userNote = previous?.userNote,
    )

    private companion object {
        const val AI_TIMEOUT_MS = 45_000L
    }
}
