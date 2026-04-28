package com.github.kr328.clash.design.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R

class RuleEntryAdapter(private val rules: List<String>) :
    RecyclerView.Adapter<RuleEntryAdapter.Holder>() {

    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val text: TextView = root.findViewById(R.id.rule_entry_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rule_entry, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.text.text = rules[position]
    }

    override fun getItemCount(): Int = rules.size
}
