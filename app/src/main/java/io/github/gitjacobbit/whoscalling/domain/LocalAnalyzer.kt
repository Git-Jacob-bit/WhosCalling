package io.github.gitjacobbit.whoscalling.domain

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import java.util.Locale

data class LocalInfo(
    val e164: String,
    val formatted: String,
    val countryName: String?,
    val typeLabel: String?,
    val riskScore: Int,
    val warnings: List<String>,
) {
    fun describe(): String = buildList {
        countryName?.let { add("kraj: $it") }
        typeLabel?.let { add("typ: $it") }
        addAll(warnings)
    }.joinToString("; ").ifEmpty { "brak" }
}

/** Instant, offline heuristics shown before the AI answer arrives. */
class LocalAnalyzer(private val defaultRegion: () -> String) {
    private val util = PhoneNumberUtil.getInstance()

    fun analyze(raw: String): LocalInfo {
        val number = try {
            util.parse(raw, defaultRegion())
        } catch (e: NumberParseException) {
            return LocalInfo(raw, raw, null, null, 0, emptyList())
        }
        val region = util.getRegionCodeForNumber(number)
        val type = util.getNumberType(number)
        val warnings = mutableListOf<String>()
        var risk = 0

        if (number.countryCode in WANGIRI_COUNTRY_CODES) {
            warnings += "prefiks często używany w oszustwach typu wangiri (nie oddzwaniaj)"
            risk = maxOf(risk, 80)
        }
        if (type == PhoneNumberType.PREMIUM_RATE) {
            warnings += "numer o podwyższonej opłacie"
            risk = maxOf(risk, 70)
        }
        if (region != null && region != defaultRegion() && risk == 0) {
            warnings += "połączenie zagraniczne"
            risk = 30
        }

        return LocalInfo(
            e164 = util.format(number, PhoneNumberFormat.E164),
            formatted = util.format(number, PhoneNumberFormat.INTERNATIONAL),
            countryName = region?.let { Locale("", it).getDisplayCountry(Locale("pl")) },
            typeLabel = typeLabel(type),
            riskScore = risk,
            warnings = warnings,
        )
    }

    private fun typeLabel(type: PhoneNumberType): String? = when (type) {
        PhoneNumberType.MOBILE -> "komórkowy"
        PhoneNumberType.FIXED_LINE -> "stacjonarny"
        PhoneNumberType.FIXED_LINE_OR_MOBILE -> "stacjonarny lub komórkowy"
        PhoneNumberType.TOLL_FREE -> "infolinia bezpłatna"
        PhoneNumberType.SHARED_COST -> "infolinia (koszt dzielony)"
        PhoneNumberType.PREMIUM_RATE -> "premium"
        PhoneNumberType.VOIP -> "VoIP"
        else -> null
    }

    private companion object {
        // Country codes repeatedly reported for one-ring (wangiri) fraud in Poland.
        val WANGIRI_COUNTRY_CODES = setOf(216, 222, 225, 231, 232, 234, 235, 236, 237, 241, 242, 243, 252, 257, 261, 263, 269, 371, 381, 383, 591, 670, 675, 677, 678, 679, 682, 685, 686, 882, 883)
    }
}
