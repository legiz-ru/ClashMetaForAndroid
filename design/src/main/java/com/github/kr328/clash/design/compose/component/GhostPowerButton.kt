package com.github.kr328.clash.design.compose.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.theme.ClashTheme

/**
 * Big circular start/stop control, styled after the adns power toggle but using the
 * app's ghost logo instead of a shield.
 *
 * - stopped: muted surface-variant circle with a monochrome ghost
 * - running: filled primary circle with a subtle breathing pulse
 *
 * Behaviour is delegated to [onClick] (wired to `Request.ToggleStatus` on integration).
 */
@Composable
fun GhostPowerButton(
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 120.dp,
) {
    val scheme = MaterialTheme.colorScheme

    val targetBackground = if (running) scheme.primary else scheme.surfaceVariant
    val background by animateColorAsState(targetBackground, label = "ghostPowerBackground")

    val contentColor: Color = if (running) scheme.onPrimary else scheme.onSurfaceVariant

    // Subtle "breathing" pulse while connected.
    val infinite = rememberInfiniteTransition(label = "ghostPowerPulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (running) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ghostPowerScale",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_prizrak_ghost),
            contentDescription = stringResource(if (running) R.string.stop else R.string.start),
            tint = contentColor,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Preview
@Composable
private fun GhostPowerButtonStoppedPreview() {
    ClashTheme(darkTheme = false) {
        GhostPowerButton(running = false, onClick = {})
    }
}

@Preview
@Composable
private fun GhostPowerButtonRunningPreview() {
    ClashTheme(darkTheme = true) {
        GhostPowerButton(running = true, onClick = {})
    }
}
