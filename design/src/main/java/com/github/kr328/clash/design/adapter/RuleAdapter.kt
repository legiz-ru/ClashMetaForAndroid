package com.github.kr328.clash.design.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Rule
import com.github.kr328.clash.design.R

class RuleAdapter(private val context: Context) :
    RecyclerView.Adapter<RuleAdapter.Holder>() {

    private var allRules: List<Rule> = emptyList()
    private var filteredRules: List<Rule> = emptyList()

    val totalCount: Int get() = allRules.size
    val filteredCount: Int get() = filteredRules.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val type: TextView = view.findViewById(R.id.rule_type)
        val proxy: TextView = view.findViewById(R.id.rule_proxy)
        val payload: TextView = view.findViewById(R.id.rule_payload)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_rule, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val rule = filteredRules[position]

        holder.type.text = rule.type
        holder.proxy.text = rule.proxy
        holder.proxy.setTextColor(proxyColor(rule.proxy))

        if (rule.payload.isNotEmpty()) {
            holder.payload.text = rule.payload
            holder.payload.visibility = View.VISIBLE
        } else {
            holder.payload.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = filteredRules.size

    fun updateAll(rules: List<Rule>, query: String = "") {
        allRules = rules
        applyFilter(query)
    }

    fun applyFilter(query: String) {
        filteredRules = if (query.isBlank()) {
            allRules
        } else {
            val q = query.lowercase()
            allRules.filter { rule ->
                rule.type.lowercase().contains(q) ||
                    rule.payload.lowercase().contains(q) ||
                    rule.proxy.lowercase().contains(q)
            }
        }
        notifyDataSetChanged()
    }

    private fun proxyColor(proxy: String): Int {
        return when (proxy.uppercase()) {
            "DIRECT" -> Color.parseColor("#4CAF50")
            "REJECT", "REJECT-DROP" -> Color.parseColor("#F44336")
            else -> context.obtainStyledAttributes(
                intArrayOf(android.R.attr.textColorSecondary)
            ).use { it.getColor(0, Color.GRAY) }
        }
    }
}
