package com.github.kr328.clash.design

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import com.github.kr328.clash.core.model.Rule
import com.github.kr328.clash.design.adapter.RuleAdapter
import com.github.kr328.clash.design.databinding.DesignRulesBinding
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RulesDesign(context: Context) : Design<RulesDesign.Request>(context) {

    sealed class Request {
        object Refresh : Request()
    }

    private val binding = DesignRulesBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val adapter = RuleAdapter(context)

    private var searchJob: Job? = null

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.activityBarLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val top = binding.activityBarLayout.height
            if (binding.recyclerList.paddingTop != top) {
                binding.recyclerList.setPadding(0, top, 0, binding.recyclerList.paddingBottom)
                binding.swipeRefresh.setProgressViewOffset(false, top, top + 96)
            }
        }

        binding.recyclerList.bindAppBarElevation(binding.activityBarLayout)
        binding.recyclerList.applyLinearAdapter(context, adapter)

        binding.swipeRefresh.setOnRefreshListener {
            requests.trySend(Request.Refresh)
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                searchJob?.cancel()
                searchJob = launch {
                    delay(500)
                    adapter.applyFilter(query)
                    updateStats(query)
                }
            }
        })
    }

    fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.refreshBtn.isEnabled = !loading
        if (loading) {
            binding.emptyView.visibility = View.GONE
            binding.errorView.visibility = View.GONE
            binding.searchBar.visibility = View.GONE
        }
    }

    fun setRules(rules: List<Rule>) {
        val query = binding.searchInput.text?.toString() ?: ""
        adapter.updateAll(rules, query)
        binding.searchBar.visibility = if (rules.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyView.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        binding.errorView.visibility = View.GONE
        updateStats(query)
    }

    fun setError(title: String, message: String) {
        binding.errorTitle.text = title
        binding.errorDesc.text = message
        binding.errorView.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.searchBar.visibility = View.GONE
        binding.statsText.text = ""
    }

    fun requestRefresh() {
        requests.trySend(Request.Refresh)
    }

    private fun updateStats(query: String) {
        val total = adapter.totalCount
        binding.statsText.text = if (query.isBlank()) {
            context.getString(R.string.rules_count, total)
        } else {
            context.getString(R.string.rules_found, adapter.filteredCount, total)
        }
    }
}
