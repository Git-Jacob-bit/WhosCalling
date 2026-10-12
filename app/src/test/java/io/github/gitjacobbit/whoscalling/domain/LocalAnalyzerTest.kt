package io.github.gitjacobbit.whoscalling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAnalyzerTest {
    private val analyzer = LocalAnalyzer { "PL" }

    @Test
    fun `normalizes polish numbers to E164`() {
        assertEquals("+48221234567", analyzer.analyze("22 123 45 67").e164)
        assertEquals("+48221234567", analyzer.analyze("0048221234567").e164)
    }

    @Test
    fun `domestic mobile has no warnings`() {
        val info = analyzer.analyze("+48 501 234 567")
        assertEquals("komórkowy", info.typeLabel)
        assertEquals(0, info.riskScore)
    }

    @Test
    fun `flags wangiri prefixes`() {
        val info = analyzer.analyze("+225 07 12 34 56 78")
        assertTrue(info.riskScore >= 80)
        assertTrue(info.warnings.any { "wangiri" in it })
    }

    @Test
    fun `unparseable input is passed through`() {
        assertEquals("abc", analyzer.analyze("abc").e164)
    }
}
