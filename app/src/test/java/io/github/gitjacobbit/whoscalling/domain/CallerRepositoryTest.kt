package io.github.gitjacobbit.whoscalling.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.gitjacobbit.whoscalling.data.ai.AiVerdict
import io.github.gitjacobbit.whoscalling.data.ai.OpenAiException
import io.github.gitjacobbit.whoscalling.data.db.AppDatabase
import io.github.gitjacobbit.whoscalling.data.db.PhoneNumberEntity
import io.github.gitjacobbit.whoscalling.data.settings.Settings
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class CallerRepositoryTest {
    private class FakeSettings(var settings: Settings, var key: String? = "sk-test") : LookupSettings {
        override suspend fun current() = settings
        override suspend fun apiKey() = key
    }

    private lateinit var db: AppDatabase
    private val settings = FakeSettings(
        Settings(
            hasApiKey = true, model = "gpt-5-mini", autoBlock = true, autoBlockThreshold = 85,
            cacheDays = 30, onboardingDone = true,
        )
    )
    private var aiCalls = 0
    private var ai: suspend (e164: String) -> AiVerdict = { verdict(score = 10) }
    private lateinit var repo: CallerRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = CallerRepository(
            db, settings,
            { _, _, e164, _ -> aiCalls++; ai(e164) },
            LocalAnalyzer { "PL" },
        )
    }

    @After
    fun tearDown() = db.close()

    // shouldBlock

    @Test
    fun `unknown number is never blocked`() = runBlocking {
        assertFalse(repo.shouldBlock(NUMBER))
    }

    @Test
    fun `user spam rating blocks even with auto block off`() = runBlocking {
        settings.settings = settings.settings.copy(autoBlock = false)
        insert(aiEntry(score = 0, userOverride = UserOverride.SPAM))
        assertTrue(repo.shouldBlock(NUMBER))
    }

    @Test
    fun `user safe rating wins over a high AI score`() = runBlocking {
        insert(aiEntry(score = 99, userOverride = UserOverride.SAFE))
        assertFalse(repo.shouldBlock(NUMBER))
    }

    @Test
    fun `fresh AI verdict above threshold blocks only with auto block on`() = runBlocking {
        insert(aiEntry(score = 90))
        assertTrue(repo.shouldBlock(NUMBER))
        settings.settings = settings.settings.copy(autoBlock = false)
        assertFalse(repo.shouldBlock(NUMBER))
    }

    @Test
    fun `AI verdict below threshold does not block`() = runBlocking {
        insert(aiEntry(score = 84))
        assertFalse(repo.shouldBlock(NUMBER))
    }

    @Test
    fun `expired AI verdict lets the call through so it can be refreshed`() = runBlocking {
        insert(aiEntry(score = 99, checkedAt = daysAgo(31)))
        assertFalse(repo.shouldBlock(NUMBER))
    }

    @Test
    fun `local-only risk score never blocks`() = runBlocking {
        insert(aiEntry(score = 99).copy(aiChecked = false))
        assertFalse(repo.shouldBlock(NUMBER))
    }

    // lookup

    @Test
    fun `without an API key only the local analysis is saved`() = runBlocking {
        settings.key = null
        val states = repo.lookup(NUMBER).toList()

        assertEquals(1, states.size)
        val state = states.single()
        assertFalse(state.loading)
        assertFalse(state.fromCache)
        assertNotNull(state.error)
        assertEquals(0, aiCalls)
        val saved = db.numberDao().get(NUMBER)!!
        assertFalse(saved.aiChecked)
    }

    @Test
    fun `new number emits loading then the AI result`() = runBlocking {
        ai = { verdict(score = 95, verdict = "reject", category = "telemarketing", owner = "Firma X") }
        val states = repo.lookup("22 123 45 67").toList()

        assertEquals(listOf(true, false), states.map { it.loading })
        val result = states.last().entry!!
        assertEquals(NUMBER, result.e164)
        assertEquals(Verdict.REJECT, result.verdict)
        assertEquals(CallerCategory.TELEMARKETING, result.category)
        assertEquals("Firma X", result.owner)
        assertTrue(result.aiChecked)
        assertEquals(result, db.numberDao().get(NUMBER))
    }

    @Test
    fun `fresh cached verdict is reused without asking the AI`() = runBlocking {
        insert(aiEntry(score = 40))
        val state = repo.lookup(NUMBER).toList().single()

        assertTrue(state.fromCache)
        assertEquals(40, state.entry!!.score)
        assertEquals(0, aiCalls)
    }

    @Test
    fun `user rating skips the AI even when the verdict is stale`() = runBlocking {
        insert(aiEntry(score = 40, checkedAt = daysAgo(60), userOverride = UserOverride.SAFE))
        repo.lookup(NUMBER).toList()
        assertEquals(0, aiCalls)
    }

    @Test
    fun `stale verdict and force both ask the AI again`() = runBlocking {
        insert(aiEntry(score = 40, checkedAt = daysAgo(31)))
        repo.lookup(NUMBER).toList()
        assertEquals(1, aiCalls)

        repo.lookup(NUMBER, force = true).toList()
        assertEquals(2, aiCalls)
    }

    @Test
    fun `AI scores and unknown enum values are sanitized`() = runBlocking {
        ai = { verdict(score = 250, verdict = "maybe", category = "aliens", owner = " ") }
        val entry = repo.lookup(NUMBER).toList().last().entry!!

        assertEquals(100, entry.score)
        assertEquals(Verdict.UNKNOWN, entry.verdict)
        assertEquals(CallerCategory.UNKNOWN, entry.category)
        assertNull(entry.owner)
    }

    @Test
    fun `AI failure keeps the cached entry and reports the error`() = runBlocking {
        insert(aiEntry(score = 40, checkedAt = daysAgo(31)))
        ai = { throw OpenAiException("Nieprawidłowy klucz API") }
        val state = repo.lookup(NUMBER).toList().last()

        assertFalse(state.loading)
        assertEquals("Nieprawidłowy klucz API", state.error)
        assertEquals(40, state.entry!!.score)
    }

    @Test
    fun `rating given while the AI runs survives the AI result`() = runBlocking {
        ai = { e164 ->
            repo.setOverride(e164, UserOverride.SPAM)
            verdict(score = 5)
        }
        val entry = repo.lookup(NUMBER).toList().last().entry!!

        assertEquals(UserOverride.SPAM, entry.userOverride)
        assertEquals(5, entry.score)
    }

    // registerCall

    @Test
    fun `calls logged before the first lookup are counted once`() = runBlocking {
        repo.registerCall(NUMBER, blocked = false)
        val first = repo.lookup(NUMBER).toList().last().entry!!
        assertEquals(1, first.callCount)

        repo.registerCall(NUMBER, blocked = false)
        assertEquals(2, db.numberDao().get(NUMBER)!!.callCount)
        assertEquals(2, db.numberDao().countCalls(NUMBER))
    }

    private suspend fun insert(entry: PhoneNumberEntity) = db.numberDao().insertIfAbsent(entry)

    private fun aiEntry(
        score: Int,
        checkedAt: Long = System.currentTimeMillis(),
        userOverride: UserOverride? = null,
    ) = PhoneNumberEntity(
        e164 = NUMBER, verdict = Verdict.CAUTION, score = score, category = CallerCategory.COMPANY,
        owner = null, summary = "", sources = emptyList(), checkedAt = checkedAt, aiChecked = true,
        userOverride = userOverride,
    )

    private fun verdict(
        score: Int,
        verdict: String = "answer",
        category: String = "company",
        owner: String? = null,
    ) = AiVerdict(verdict, score, category, owner, "Podsumowanie.", listOf("https://example.com"))

    private fun daysAgo(days: Long) = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)

    private companion object {
        const val NUMBER = "+48221234567"
    }
}
