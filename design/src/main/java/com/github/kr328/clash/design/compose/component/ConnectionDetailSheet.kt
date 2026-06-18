package com.github.kr328.clash.design.compose.component

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.showHostMenu
import com.github.kr328.clash.design.util.showIpMenu
import com.github.kr328.clash.design.util.showProcessMenu
import com.github.kr328.clash.design.util.toBytesString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

private val PrettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

/**
 * Compose connection-detail bottom sheet (replaces the View-based
 * AppBottomSheetDialog + sheet_connection_detail.xml). Shows the traffic,
 * routing, network, process, inbound and other metadata for a single
 * connection, with tappable IP/host/process rows and a copy-as-JSON action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetailSheet(
    connection: Connection,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val context = LocalContext.current
        val meta = connection.metadata

        val header = remember(connection) {
            meta.host.ifEmpty { meta.destinationIP }.let {
                if (meta.destinationPort.isNotEmpty()) "$it:${meta.destinationPort}" else it
            }
        }
        val duration = remember(connection) { formatConnDuration(connection.start) }
        val isInner = meta.type.equals("inner", ignoreCase = true)
        val hasInbound = meta.inboundIP.isNotEmpty() || meta.inboundPort.isNotEmpty() ||
            meta.inboundName.isNotEmpty() || meta.inboundUser.isNotEmpty()
        val hasOther = meta.dnsMode.isNotEmpty() || meta.specialProxy.isNotEmpty() ||
            meta.specialRules.isNotEmpty() || meta.dscp > 0

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val json = runCatching {
                        PrettyJson.encodeToString(Connection.serializer(), connection)
                    }.getOrElse { connection.toString() }
                    context.getSystemService<ClipboardManager>()
                        ?.setPrimaryClip(ClipData.newPlainText("connection_json", json))
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_content_copy),
                        contentDescription = null,
                    )
                }
            }

            Section(stringResource(R.string.conn_detail_traffic)) {
                DetailRow(stringResource(R.string.conn_upload_speed), "—")
                DetailRow(stringResource(R.string.conn_download_speed), "—")
                DetailRow(stringResource(R.string.conn_upload), connection.upload.toBytesString())
                DetailRow(stringResource(R.string.conn_download), connection.download.toBytesString())
                DetailRow(stringResource(R.string.conn_duration), duration)
            }

            val ruleText = if (connection.rulePayload.isNotEmpty())
                "${connection.rule}  ${connection.rulePayload}" else connection.rule
            Section(stringResource(R.string.conn_detail_routing)) {
                DetailRow(stringResource(R.string.conn_rule), ruleText)
                DetailRow(
                    stringResource(R.string.conn_proxy_chain),
                    connection.chains.reversed().joinToString(" → "),
                )
                DetailRow(
                    stringResource(R.string.conn_type),
                    "${meta.network.uppercase()}(${meta.type})",
                )
            }

            Section(stringResource(R.string.conn_detail_network)) {
                DetailRow(stringResource(R.string.conn_host), meta.host) {
                    showHostMenu(context, meta.host)
                }
                DetailRow(stringResource(R.string.conn_sniff_host), meta.sniffHost) {
                    showHostMenu(context, meta.sniffHost)
                }
                DetailRow(stringResource(R.string.conn_dest_ip), meta.destinationIP) {
                    showIpMenu(context, meta.destinationIP)
                }
                DetailRow(stringResource(R.string.conn_dest_geoip), meta.destinationGeoIP.joinToString(", "))
                DetailRow(stringResource(R.string.conn_dest_asn), meta.destinationIPASN)
                DetailRow(stringResource(R.string.conn_src_ip), meta.sourceIP) {
                    showIpMenu(context, meta.sourceIP)
                }
                DetailRow(stringResource(R.string.conn_src_geoip), meta.sourceGeoIP.joinToString(", "))
                DetailRow(stringResource(R.string.conn_src_asn), meta.sourceIPASN)
                DetailRow(stringResource(R.string.conn_src_port), meta.sourcePort)
                DetailRow(stringResource(R.string.conn_dest_port), meta.destinationPort)
                DetailRow(stringResource(R.string.conn_remote_dest), meta.remoteDestination) {
                    showIpMenu(context, meta.remoteDestination)
                }
            }

            if (!isInner) {
                Section(stringResource(R.string.conn_detail_process)) {
                    val procText = if (meta.uid > 0) "${meta.process} (uid:${meta.uid})" else meta.process
                    DetailRow(stringResource(R.string.conn_process), procText) {
                        if (meta.process.isNotEmpty()) showProcessMenu(context, meta.process)
                    }
                    DetailRow(stringResource(R.string.conn_process_path), meta.processPath)
                }
            }

            if (hasInbound) {
                Section(stringResource(R.string.conn_detail_inbound)) {
                    DetailRow(stringResource(R.string.conn_inbound_ip), meta.inboundIP)
                    DetailRow(stringResource(R.string.conn_inbound_port), meta.inboundPort)
                    DetailRow(stringResource(R.string.conn_inbound_name), meta.inboundName)
                    DetailRow(stringResource(R.string.conn_inbound_user), meta.inboundUser)
                }
            }

            if (hasOther) {
                Section(stringResource(R.string.conn_detail_other)) {
                    DetailRow(stringResource(R.string.conn_dns_mode), meta.dnsMode)
                    DetailRow(stringResource(R.string.conn_special_proxy), meta.specialProxy)
                    DetailRow(stringResource(R.string.conn_special_rules), meta.specialRules)
                    DetailRow(
                        stringResource(R.string.conn_dscp),
                        if (meta.dscp > 0) meta.dscp.toString() else "",
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 2.dp),
    )
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    if (value.isBlank()) return
    val base = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(vertical = 2.dp)
    Row(modifier = base) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatConnDuration(start: String): String = try {
    val s = Duration.between(Instant.parse(start), Instant.now()).seconds.coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
} catch (_: Exception) {
    "—"
}
