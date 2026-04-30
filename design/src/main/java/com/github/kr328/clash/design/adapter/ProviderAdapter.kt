package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProviderState
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.type

class ProviderAdapter(
    private val context: Context,
    providers: List<Provider>,
    private val onClick: (Int, Provider) -> Unit,
) : RecyclerView.Adapter<ProviderAdapter.Holder>() {

    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val name: TextView = root.findViewById(R.id.provider_name)
        val subtitle: TextView = root.findViewById(R.id.provider_subtitle)
    }

    val states = providers.map { ProviderState(it, it.updatedAt, false) }

    fun updateElapsed() {
        notifyItemRangeChanged(0, states.size)
    }

    fun notifyUpdated(index: Int) {
        states[index].updating = false
        notifyItemChanged(index)
    }

    fun notifyChanged(index: Int) {
        states[index].apply {
            updating = false
            updatedAt = System.currentTimeMillis()
        }
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_provider, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val state = states[position]
        val provider = state.provider

        holder.name.text = provider.name
        holder.subtitle.text = buildSubtitle(provider, state.updatedAt)
        holder.itemView.setOnClickListener { onClick(position, provider) }
    }

    override fun getItemCount(): Int = states.size

    private fun buildSubtitle(provider: Provider, updatedAt: Long): String {
        val typeStr = provider.type(context)
        return when (provider.vehicleType) {
            Provider.VehicleType.Inline -> typeStr
            else -> {
                val elapsed = (System.currentTimeMillis() - updatedAt).elapsedIntervalString(context)
                "$typeStr • $elapsed"
            }
        }
    }
}
