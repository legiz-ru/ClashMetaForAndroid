package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ConnectionGroup
import com.github.kr328.clash.design.util.toBytesString

class ConnectionGroupAdapter(
    private val context: Context,
    private val onGroupClicked: (ConnectionGroup) -> Unit,
) : RecyclerView.Adapter<ConnectionGroupAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.conn_group_icon)
        val name: TextView = view.findViewById(R.id.conn_group_name)
        val count: TextView = view.findViewById(R.id.conn_group_count)
        val speed: TextView = view.findViewById(R.id.conn_group_speed)
    }

    var groups: List<ConnectionGroup> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.adapter_connection_group, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val group = groups[position]

        if (group.icon != null) {
            holder.icon.setImageDrawable(group.icon)
        } else {
            holder.icon.setImageResource(R.drawable.ic_launcher_foreground)
        }

        holder.name.text = group.appName

        holder.count.text = group.connections.size.toString()

        val up = group.uploadSpeed.toBytesString()
        val down = group.downloadSpeed.toBytesString()
        holder.speed.text = "↑ $up/s  ↓ $down/s"

        holder.itemView.setOnClickListener { onGroupClicked(group) }
        holder.itemView.isFocusable = true
        holder.itemView.isClickable = true
    }

    override fun getItemCount(): Int = groups.size
}
