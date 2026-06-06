package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.design.ConnectionsDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.toBytesString
import java.time.Duration
import java.time.Instant

class ConnectionAdapter(
    private val context: Context,
    private val onConnectionClicked: (Connection) -> Unit,
    private val onConnectionClosed: (Connection) -> Unit,
) : RecyclerView.Adapter<ConnectionAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val host: TextView = view.findViewById(R.id.conn_host)
        val typeChain: TextView = view.findViewById(R.id.conn_type_chain)
        val traffic: TextView = view.findViewById(R.id.conn_traffic)
        val closeBtn: ImageButton = view.findViewById(R.id.conn_close_btn)
    }

    var connections: List<Connection> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var visibleFields: Set<ConnectionsDesign.DisplayField> = ConnectionsDesign.visibleFields
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.adapter_connection, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val conn = connections[position]
        val meta = conn.metadata
        val vf = visibleFields

        // Line 1: Host
        if (ConnectionsDesign.DisplayField.HOST in vf) {
            val displayHost = meta.host.ifEmpty { meta.destinationIP }
            val port = meta.destinationPort
            holder.host.text = if (port.isNotEmpty()) "$displayHost:$port" else displayHost
            holder.host.visibility = View.VISIBLE
        } else {
            holder.host.visibility = View.GONE
        }

        // Line 2: Type and/or Chains
        val showType = ConnectionsDesign.DisplayField.TYPE in vf
        val showChains = ConnectionsDesign.DisplayField.CHAINS in vf
        if (showType || showChains) {
            val parts = buildList {
                if (showType) add(meta.network.uppercase())
                if (showChains) add(conn.chains.reversed().joinToString(" → "))
            }
            holder.typeChain.text = parts.joinToString("  ·  ")
            holder.typeChain.visibility = View.VISIBLE
        } else {
            holder.typeChain.visibility = View.GONE
        }

        // Line 3: Traffic / metadata fields
        val trafficParts = buildList {
            if (ConnectionsDesign.DisplayField.UPLOAD in vf)
                add("↑ ${conn.upload.toBytesString()}")
            if (ConnectionsDesign.DisplayField.DOWNLOAD in vf)
                add("↓ ${conn.download.toBytesString()}")
            if (ConnectionsDesign.DisplayField.RULE in vf && conn.rule.isNotEmpty())
                add(conn.rule)
            if (ConnectionsDesign.DisplayField.TIME in vf)
                add(formatDuration(conn.start))
            if (ConnectionsDesign.DisplayField.SOURCE_IP in vf && meta.sourceIP.isNotEmpty())
                add(meta.sourceIP)
        }
        if (trafficParts.isNotEmpty()) {
            holder.traffic.text = trafficParts.joinToString("   ")
            holder.traffic.visibility = View.VISIBLE
        } else {
            holder.traffic.visibility = View.GONE
        }

        holder.closeBtn.setOnClickListener { onConnectionClosed(conn) }
        holder.closeBtn.isFocusable = true

        holder.itemView.setOnClickListener { onConnectionClicked(conn) }
        holder.itemView.isFocusable = true
        holder.itemView.isClickable = true
    }

    override fun getItemCount(): Int = connections.size

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
}
