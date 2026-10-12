package io.github.gitjacobbit.whoscalling.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.gitjacobbit.whoscalling.domain.Verdict

object Palette {
    val Background = Color(0xFF0B0D12)
    val Surface = Color(0xFF151821)
    val SurfaceHigh = Color(0xFF1E2230)
    val Outline = Color(0x1FFFFFFF)
    val TextPrimary = Color(0xFFF3F4F8)
    val TextSecondary = Color(0xFF9AA0B4)
    val Accent = Color(0xFF7C5CFF)

    val Answer = Color(0xFF2ED47A)
    val Caution = Color(0xFFFFB547)
    val Reject = Color(0xFFFF4D67)
    val Unknown = Color(0xFF7C5CFF)
}

val Verdict.color: Color
    get() = when (this) {
        Verdict.ANSWER -> Palette.Answer
        Verdict.CAUTION -> Palette.Caution
        Verdict.REJECT -> Palette.Reject
        Verdict.UNKNOWN -> Palette.Unknown
    }

val Verdict.label: String
    get() = when (this) {
        Verdict.ANSWER -> "Możesz odebrać"
        Verdict.CAUTION -> "Ostrożnie"
        Verdict.REJECT -> "Nie odbieraj"
        Verdict.UNKNOWN -> "Brak danych"
    }
