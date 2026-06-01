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

    private var rules: List<Rule> = emptyList()

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
        val rule = rules[position]

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

    override fun getItemCount(): Int = rules.size

    fun updateAll(newRules: List<Rule>) {
        rules = newRules
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
