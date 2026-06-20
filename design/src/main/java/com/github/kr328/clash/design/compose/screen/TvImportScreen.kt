package com.github.kr328.clash.design.compose.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R

/**
 * TV / keyboard-free profile import (1:1 with the old activity_tv_import.xml,
 * restyled to MD3E). Shows a QR code + IP/port for the local import server, or
 * a status message (no Wi-Fi / received), with a cancel action.
 */
@Composable
fun TvImportScreen(
    qr: Bitmap?,
    ip: String,
    port: String,
    url: String,
    status: String?,
    onCancel: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    // Keep the cancel button reachable without scrolling (TV/D-pad) and focused
    // by default so the remote always has a target.
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.tv_import_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    status != null -> {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    qr == null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.tv_import_loading),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                        }
                    }
                    else -> {
                        // QR on the left, address/instructions on the right —
                        // wrap-content so the whole group stays centred (Box).
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            Image(
                                bitmap = remember(qr) { qr.asImageBitmap() },
                                contentDescription = stringResource(R.string.tv_import_qr_desc),
                                modifier = Modifier.size(256.dp),
                            )
                            Column(modifier = Modifier.widthIn(max = 360.dp)) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    ) {
                                        LabeledValue(stringResource(R.string.tv_import_ip_label), ip)
                                        LabeledValue(stringResource(R.string.tv_import_port_label), port)
                                    }
                                }
                                if (url.isNotEmpty()) {
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.tv_import_instruction),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .focusRequester(cancelFocus),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
