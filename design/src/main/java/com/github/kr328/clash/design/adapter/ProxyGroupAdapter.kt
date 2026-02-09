package com.github.kr328.clash.design.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.databinding.AdapterProxyGroupBinding
import com.github.kr328.clash.design.util.layoutInflater

class ProxyGroupAdapter(
    private val config: ProxyViewConfig,
    private val groupNames: List<String>,
    private val adapters: List<ProxyAdapter>,
    private val onGroupToggled: (Int) -> Unit,
) : RecyclerView.Adapter<ProxyGroupAdapter.Holder>() {
    class Holder(val binding: AdapterProxyGroupBinding) : RecyclerView.ViewHolder(binding.root)

    private val expandedStates = MutableList(groupNames.size) { index -> index == 0 }
    private val proxyPool = RecyclerView.RecycledViewPool()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterProxyGroupBinding.inflate(parent.context.layoutInflater, parent, false)
        binding.proxyList.apply {
            layoutManager = GridLayoutManager(context, 6).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val grids = when (config.proxyLine) {
                            2 -> 3
                            3 -> 2
                            else -> 6
                        }
                        return if (config.proxyLine == 1) 6 else grids
                    }
                }
            }
            setRecycledViewPool(proxyPool)
            clipToPadding = false
            isNestedScrollingEnabled = false
        }
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val binding = holder.binding
        val expanded = expandedStates[position]

        binding.groupTitle = groupNames[position]
        binding.proxyList.adapter = adapters[position]
        binding.proxyList.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.expandIcon.rotation = if (expanded) 90f else 0f

        binding.groupHeader.setOnClickListener {
            val next = !expandedStates[position]
            expandedStates[position] = next
            notifyItemChanged(position)
            onGroupToggled(position)
        }
    }

    override fun getItemCount(): Int = groupNames.size
}
