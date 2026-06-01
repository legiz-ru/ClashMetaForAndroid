package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.core.model.Rule
import com.github.kr328.clash.design.adapter.RuleAdapter
import com.github.kr328.clash.design.databinding.DesignRulesBinding
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class RulesDesign(context: Context) : Design<RulesDesign.Request>(context) {

    sealed class Request

    private val binding = DesignRulesBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val adapter = RuleAdapter(context)

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.activityBarLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val top = binding.activityBarLayout.height
            if (binding.recyclerList.paddingTop != top) {
                binding.recyclerList.setPadding(0, top, 0, binding.recyclerList.paddingBottom)
            }
        }

        binding.recyclerList.bindAppBarElevation(binding.activityBarLayout)
        binding.recyclerList.applyLinearAdapter(context, adapter)
    }

    fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            binding.emptyView.visibility = View.GONE
            binding.errorView.visibility = View.GONE
            binding.rulesCountText.text = ""
        }
    }

    fun setRules(rules: List<Rule>) {
        adapter.updateAll(rules)
        binding.progressBar.visibility = View.GONE
        binding.emptyView.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        binding.errorView.visibility = View.GONE
        binding.rulesCountText.text = rules.size.toString()
    }

    fun setError(title: String, message: String) {
        binding.progressBar.visibility = View.GONE
        binding.errorTitle.text = title
        binding.errorDesc.text = message
        binding.errorView.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.rulesCountText.text = ""
    }
}
