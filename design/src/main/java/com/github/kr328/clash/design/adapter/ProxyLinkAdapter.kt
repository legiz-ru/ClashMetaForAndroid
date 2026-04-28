package com.github.kr328.clash.design.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import java.net.URLDecoder

class ProxyLinkAdapter(
    private val onEdit: (index: Int, url: String) -> Unit,
    private val onDelete: (index: Int) -> Unit,
) : RecyclerView.Adapter<ProxyLinkAdapter.Holder>() {

    var links: List<String> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val alias: TextView = root.findViewById(R.id.link_alias)
        val deleteBtn: ImageView = root.findViewById(R.id.btn_delete_link)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_proxy_link, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val url = links[position]
        holder.alias.text = extractAlias(url)
        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) onEdit(pos, links[pos])
        }
        holder.deleteBtn.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) onDelete(pos)
        }
    }

    override fun getItemCount(): Int = links.size

    private fun extractAlias(url: String): String {
        val afterHash = url.substringAfterLast("#", "").trim()
        if (afterHash.isNotBlank()) {
            return try {
                URLDecoder.decode(afterHash, "UTF-8")
            } catch (_: Exception) {
                afterHash
            }
        }
        return try {
            url.substringAfter("://").substringBefore("?").substringBefore("/").ifBlank { url }
        } catch (_: Exception) { url }
    }
}
