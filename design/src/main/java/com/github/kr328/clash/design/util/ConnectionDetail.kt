package com.github.kr328.clash.design.util

import android.content.Context
import android.view.View
import android.widget.TextView
import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import java.time.Duration
import java.time.Instant

fun showConnectionDetailSheet(context: Context, connection: Connection) {
    val dialog = AppBottomSheetDialog(context)
    val sheetView = dialog.layoutInflater.inflate(R.layout.sheet_connection_detail, null)
    dialog.setContentView(sheetView)

    val meta = connection.metadata

    sheetView.findViewById<TextView>(R.id.detail_header).text =
        meta.host.ifEmpty { meta.destinationIP }.let {
            val port = meta.destinationPort
            if (port.isNotEmpty()) "$it:$port" else it
        }

    fun row(id: Int, label: String, value: String) {
        val row = sheetView.findViewById<View>(id)
        if (value.isEmpty()) { row.visibility = View.GONE; return }
        row.visibility = View.VISIBLE
        row.findViewById<TextView>(R.id.detail_label).text = label
        row.findViewById<TextView>(R.id.detail_value).text = value
    }

    val dur = try {
        val start = Instant.parse(connection.start)
        val s = Duration.between(start, Instant.now()).seconds.coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    } catch (_: Exception) { "—" }

    row(R.id.row_upload_speed,    context.getString(R.string.conn_upload_speed),   "—")
    row(R.id.row_download_speed,  context.getString(R.string.conn_download_speed), "—")
    row(R.id.row_upload,          context.getString(R.string.conn_upload),          connection.upload.toBytesString())
    row(R.id.row_download,        context.getString(R.string.conn_download),        connection.download.toBytesString())
    row(R.id.row_duration,        context.getString(R.string.conn_duration),        dur)

    val ruleText = if (connection.rulePayload.isNotEmpty())
        "${connection.rule}  ${connection.rulePayload}" else connection.rule
    row(R.id.row_rule,   context.getString(R.string.conn_rule),        ruleText)
    row(R.id.row_chain,  context.getString(R.string.conn_proxy_chain), connection.chains.reversed().joinToString(" → "))
    row(R.id.row_type,   context.getString(R.string.conn_type),        "${meta.network.uppercase()}(${meta.type})")

    row(R.id.row_host,        context.getString(R.string.conn_host),        meta.host)
    row(R.id.row_sniff_host,  context.getString(R.string.conn_sniff_host),  meta.sniffHost)
    row(R.id.row_dest_ip,     context.getString(R.string.conn_dest_ip),     meta.destinationIP)
    row(R.id.row_dest_geoip,  context.getString(R.string.conn_dest_geoip), meta.destinationGeoIP.joinToString(", "))
    row(R.id.row_dest_asn,    context.getString(R.string.conn_dest_asn),    meta.destinationIPASN)
    row(R.id.row_src_ip,      context.getString(R.string.conn_src_ip),      meta.sourceIP)
    row(R.id.row_src_geoip,   context.getString(R.string.conn_src_geoip),   meta.sourceGeoIP.joinToString(", "))
    row(R.id.row_src_asn,     context.getString(R.string.conn_src_asn),     meta.sourceIPASN)
    row(R.id.row_src_port,    context.getString(R.string.conn_src_port),    meta.sourcePort)
    row(R.id.row_dest_port,   context.getString(R.string.conn_dest_port),   meta.destinationPort)
    row(R.id.row_remote_dest, context.getString(R.string.conn_remote_dest), meta.remoteDestination)

    val isInner = meta.type.equals("inner", ignoreCase = true)
    sheetView.findViewById<View>(R.id.section_process).visibility =
        if (isInner) View.GONE else View.VISIBLE
    if (!isInner) {
        val procText = if (meta.uid > 0) "${meta.process} (uid:${meta.uid})" else meta.process
        row(R.id.row_process,      context.getString(R.string.conn_process),      procText)
        row(R.id.row_process_path, context.getString(R.string.conn_process_path), meta.processPath)
    }

    val hasInbound = meta.inboundIP.isNotEmpty() || meta.inboundPort.isNotEmpty() ||
        meta.inboundName.isNotEmpty() || meta.inboundUser.isNotEmpty()
    sheetView.findViewById<View>(R.id.section_inbound).visibility =
        if (hasInbound) View.VISIBLE else View.GONE
    row(R.id.row_inbound_ip,   context.getString(R.string.conn_inbound_ip),   meta.inboundIP)
    row(R.id.row_inbound_port, context.getString(R.string.conn_inbound_port), meta.inboundPort)
    row(R.id.row_inbound_name, context.getString(R.string.conn_inbound_name), meta.inboundName)
    row(R.id.row_inbound_user, context.getString(R.string.conn_inbound_user), meta.inboundUser)

    val hasOther = meta.dnsMode.isNotEmpty() || meta.specialProxy.isNotEmpty() ||
        meta.specialRules.isNotEmpty() || meta.dscp > 0
    sheetView.findViewById<View>(R.id.section_other).visibility =
        if (hasOther) View.VISIBLE else View.GONE
    row(R.id.row_dns_mode,      context.getString(R.string.conn_dns_mode),      meta.dnsMode)
    row(R.id.row_special_proxy, context.getString(R.string.conn_special_proxy), meta.specialProxy)
    row(R.id.row_special_rules, context.getString(R.string.conn_special_rules), meta.specialRules)
    row(R.id.row_dscp,          context.getString(R.string.conn_dscp),          if (meta.dscp > 0) meta.dscp.toString() else "")

    dialog.show()
}
