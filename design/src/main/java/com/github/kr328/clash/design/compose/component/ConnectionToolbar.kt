package com.github.kr328.clash.design.compose.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.theme.ClashTheme

/**
 * Floating controls shown on phones while Clash is running: an optional
 * latency-test mini FAB (simple-mode profiles) plus a "disconnect" extended FAB.
 * Mirrors the FAB row from the old design_main.xml.
 *
 * @param visible           usually `clashRunning`
 * @param showLatencyTest   usually `clashRunning && profileSimpleMode`
 * @param latencyTesting    shows an inline progress indicator and disables the test button
 */
@Composable
fun ConnectionToolbar(
    visible: Boolean,
    showLatencyTest: Boolean,
    latencyTesting: Boolean,
    onDisconnect: () -> Unit,
    onLatencyTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showLatencyTest) {
                SmallFloatingActionButton(
                    onClick = { if (!latencyTesting) onLatencyTest() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    if (latencyTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_speed),
                            contentDescription = stringResource(R.string.delay_test),
                        )
                    }
                }
            }

            ExtendedFloatingActionButton(onClick = onDisconnect) {
                Icon(
                    painter = painterResource(R.drawable.ic_mdi_power),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.disconnect),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ConnectionToolbarPreview() {
    ClashTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            ConnectionToolbar(
                visible = true,
                showLatencyTest = true,
                latencyTesting = false,
                onDisconnect = {},
                onLatencyTest = {},
            )
        }
    }
}
