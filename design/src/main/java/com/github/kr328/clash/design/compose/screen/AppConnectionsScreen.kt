package com.github.kr328.clash.design.compose.screen

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.toBytesString
import java.time.Duration
import java.time.Instant

/**
 * Per-app connection list (1:1 with AppConnectionsDesign, restyled to MD3E).
 * Lists a single app's connections (host, network/chain, traffic + duration);
 * tapping opens the shared detail sheet, and active connections expose a close
 * button. Polling/closing lives in AppConnectionsActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppConnectionsScreen(
    appName: String,
    appIcon: Drawable?,
    connections: List<Connection>,
    closable: Boolean,
    onBack: () -> Unit,
    onConnectionClick: (Connection) -> Unit,
    onClose: (Connection) -> Unit,
) {
    val iconPainter = remember(appIcon) {
        appIcon?.let { BitmapPainter(it.toBitmap(96, 96).asImageBitmap()) }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (iconPainter != null) {
                                Image(
                                    painter = iconPainter,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(28.dp),
                                )
                            }
                            Text(appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        ) { inner ->
            if (connections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(inner)) {
                    items(items = connections, key = { it.id }) { conn ->
                        ConnectionRow(
                            connection = conn,
                            closable = closable,
                            onClick = { onConnectionClick(conn) },
                            onClose = { onClose(conn) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionRow(
    connection: Connection,
    closable: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val meta = connection.metadata
    val host = remember(connection) {
        val h = meta.host.ifEmpty { meta.destinationIP }
        if (meta.destinationPort.isNotEmpty()) "$h:${meta.destinationPort}" else h
    }
    val typeChain = remember(connection) {
        "${meta.network.uppercase()}  ·  ${connection.chains.reversed().joinToString(" → ")}"
    }
    val traffic = remember(connection) {
        "↑ ${connection.upload.toBytesString()}  ↓ ${connection.download.toBytesString()}    ${formatDuration(connection.start)}"
    }
    ListItem(
        headlineContent = { Text(host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(typeChain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(traffic, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = if (closable) {
            {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_clear_all),
                        contentDescription = null,
                    )
                }
            }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

private fun formatDuration(startIso: String): String {
    return try {
        val start = Instant.parse(startIso)
        val seconds = Duration.between(start, Instant.now()).seconds.coerceAtLeast(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    } catch (_: Exception) {
        "00:00"
    }
}
