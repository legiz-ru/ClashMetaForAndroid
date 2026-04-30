package com.github.kr328.clash.design.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.R as MaterialR

class RuleEntryAdapter : RecyclerView.Adapter<RuleEntryAdapter.Holder>() {

    private var allRules: List<String> = emptyList()
    private var query: String = ""
    private var matchSet: Set<Int> = emptySet()
    private var currentMatchPos: Int = -1

    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val text: TextView = root.findViewById(R.id.rule_entry_text)
    }

    fun updateRules(rules: List<String>) {
        allRules = rules
        query = ""
        matchSet = emptySet()
        currentMatchPos = -1
        notifyDataSetChanged()
    }

    fun updateSearch(newQuery: String, newMatchSet: Set<Int>, newCurrentPos: Int) {
        query = newQuery
        matchSet = newMatchSet
        currentMatchPos = newCurrentPos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rule_entry, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val rule = allRules[position]

        if (position == currentMatchPos) {
            val color = holder.itemView.context
                .resolveThemedColor(MaterialR.attr.colorPrimaryContainer)
            holder.itemView.setBackgroundColor(color)
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }

        if (query.isNotBlank() && position in matchSet) {
            val spannable = SpannableString(rule)
            val lowerRule = rule.lowercase()
            val lowerQuery = query.lowercase()
            var start = 0
            while (true) {
                val idx = lowerRule.indexOf(lowerQuery, start)
                if (idx < 0) break
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    idx, idx + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                start = idx + query.length
            }
            holder.text.text = spannable
        } else {
            holder.text.text = rule
        }
    }

    override fun getItemCount(): Int = allRules.size
}
