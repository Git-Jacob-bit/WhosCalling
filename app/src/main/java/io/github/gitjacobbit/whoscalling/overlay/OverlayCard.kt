package io.github.gitjacobbit.whoscalling.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gitjacobbit.whoscalling.domain.CallerCategory
import io.github.gitjacobbit.whoscalling.domain.UserOverride
import io.github.gitjacobbit.whoscalling.ui.theme.Palette
import io.github.gitjacobbit.whoscalling.ui.theme.color
import io.github.gitjacobbit.whoscalling.ui.theme.label

private val CardShape = RoundedCornerShape(28.dp)

@Composable
fun OverlayCard(
    state: OverlayUiState,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onOverride: (UserOverride) -> Unit,
    onDrag: (Float) -> Unit,
) {
    val verdict = state.effectiveVerdict
    val accent by animateColorAsState(if (state.loading) Palette.Accent else verdict.color, tween(600), label = "accent")
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (state.loading) 1600 else 6000, easing = LinearEasing)),
        label = "angle",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> onDrag(dy) } }
            .shadow(24.dp, CardShape, ambientColor = accent, spotColor = accent)
            .clip(CardShape)
            .background(Brush.verticalGradient(listOf(Color(0xF21A1D28), Color(0xF20E1016))))
            .border(1.5.dp, Brush.sweepGradient(listOf(accent, accent.copy(alpha = 0.05f), accent)), CardShape)
            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent), radius = 700f, center = Offset(0f, 0f)))
    ) {
        Column(Modifier.padding(18.dp)) {
            Header(state, accent, angle, onToggle, onClose)
            AnimatedVisibility(state.expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    VerdictRow(state, accent)
                    Spacer(Modifier.height(10.dp))
                    Summary(state, accent)
                    if (state.e164 != null && !state.loading) {
                        Spacer(Modifier.height(14.dp))
                        OverrideButtons(state.userOverride, onOverride)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: OverlayUiState, accent: Color, angle: Float, onToggle: () -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(46.dp).rotate(angle)) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, accent)),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(state.category.icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(onClick = onToggle)) {
            Text(
                state.owner ?: state.displayNumber,
                color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val secondLine = listOfNotNull(
                state.displayNumber.takeIf { state.owner != null },
                state.subtitle,
            ).joinToString(" · ")
            if (secondLine.isNotEmpty()) {
                Text(secondLine, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!state.expanded) VerdictPill(state, accent)
        Icon(
            Icons.Rounded.Close, "Zamknij", tint = Palette.TextSecondary,
            modifier = Modifier.padding(start = 6.dp).size(32.dp).clip(CircleShape).clickable(onClick = onClose).padding(6.dp),
        )
    }
}

@Composable
private fun VerdictRow(state: OverlayUiState, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VerdictPill(state, accent)
        Spacer(Modifier.width(8.dp))
        if (state.category != CallerCategory.UNKNOWN) Chip(state.category.label)
        if (state.callCount > 1) {
            Spacer(Modifier.width(6.dp))
            Chip("Dzwonił ${state.callCount}×")
        }
        Spacer(Modifier.weight(1f))
        state.score?.takeIf { !state.loading }?.let { ScoreRing(it, accent) }
    }
}

@Composable
private fun VerdictPill(state: OverlayUiState, accent: Color) {
    val text = if (state.loading) "Analizuję…" else state.effectiveVerdict.label.uppercase()
    Text(
        text,
        color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.15f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Chip(text: String) {
    Text(
        text, color = Palette.TextSecondary, fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Outline).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun ScoreRing(score: Int, accent: Color) {
    val progress by animateFloatAsState(score / 100f, tween(900), label = "score")
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(44.dp)) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Palette.Outline, -90f, 360f, false, style = stroke)
            drawArc(accent, -90f, 360f * progress, false, style = stroke)
        }
        Text("$score", color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Summary(state: OverlayUiState, accent: Color) {
    if (state.loading) {
        val pulse = rememberInfiniteTransition(label = "pulse")
        val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
        Text(
            state.summary ?: "Przeszukuję internet w poszukiwaniu opinii o tym numerze…",
            color = Palette.TextSecondary.copy(alpha = alpha), fontSize = 14.sp,
        )
        return
    }
    state.summary?.let { Text(it, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp) }
    val footnote = state.error ?: "Z lokalnej bazy".takeIf { state.fromCache }
    footnote?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = if (state.error != null) Palette.Caution else accent.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

@Composable
private fun OverrideButtons(current: UserOverride?, onOverride: (UserOverride) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OverrideButton("Spam", Icons.Rounded.Block, Palette.Reject, current == UserOverride.SPAM, Modifier.weight(1f)) {
            onOverride(UserOverride.SPAM)
        }
        OverrideButton("Bezpieczny", Icons.Rounded.VerifiedUser, Palette.Answer, current == UserOverride.SAFE, Modifier.weight(1f)) {
            onOverride(UserOverride.SAFE)
        }
    }
}

@Composable
private fun OverrideButton(text: String, icon: ImageVector, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) color.copy(alpha = 0.25f) else Palette.Outline)
            .border(1.dp, if (selected) color else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

val CallerCategory.icon: ImageVector
    get() = when (this) {
        CallerCategory.TELEMARKETING -> Icons.Rounded.Campaign
        CallerCategory.SCAM -> Icons.Rounded.GppBad
        CallerCategory.COURIER -> Icons.Rounded.Inventory2
        CallerCategory.BANK -> Icons.Rounded.AccountBalanceWallet
        CallerCategory.COMPANY -> Icons.Rounded.Business
        CallerCategory.INSTITUTION -> Icons.Rounded.AccountBalance
        CallerCategory.PRIVATE -> Icons.Rounded.Person
        CallerCategory.UNKNOWN -> Icons.Rounded.QuestionMark
    }
