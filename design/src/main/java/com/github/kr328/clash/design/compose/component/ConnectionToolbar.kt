package com.github.kr328.clash.design.compose.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.theme.ClashTheme

/**
 * Floating toolbar shown on phones while Clash is running. Uses the native MD3
 * Expressive [HorizontalFloatingToolbar] and replaces the previous disconnect +
 * latency-test FAB row.
 *
 * @param visible           usually `clashRunning`
 * @param showLatencyTest   usually `clashRunning && profileSimpleMode`
 * @param latencyTesting    shows an inline loading indicator and disables the test button
 *
 * Targets Material 3 1.4.x (alpha). If a toolbar API signature changes in a later
 * alpha, the adjustment is local to this file.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        ) {
            if (showLatencyTest) {
                FilledIconButton(
                    onClick = onLatencyTest,
                    enabled = !latencyTesting,
                ) {
                    if (latencyTesting) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_speed),
                            contentDescription = stringResource(R.string.delay_test),
                        )
                    }
                }
            }

            Button(onClick = onDisconnect) {
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
