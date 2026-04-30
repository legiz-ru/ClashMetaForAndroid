package com.github.kr328.clash.design

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.adapter.RuleEntryAdapter
import com.github.kr328.clash.design.databinding.DesignProviderDetailBinding
import com.github.kr328.clash.design.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProviderDetailDesign(
    context: Context,
    val provider: Provider,
) : Design<ProviderDetailDesign.Request>(context) {

    sealed class Request {
        object Update : Request()
    }

    private val binding = DesignProviderDetailBinding
        .inflate(context.layoutInflater, context.root, false)

    private val adapter = RuleEntryAdapter()

    private var allRules: List<String> = emptyList()
    private var matchPositions: List<Int> = emptyList()
    private var currentMatchIndex: Int = 0
    private var currentQuery: String = ""

    override val root: View
        get() = binding.root

    suspend fun showRules(rules: List<String>) {
        withContext(Dispatchers.Main) {
            allRules = rules
            adapter.updateRules(rules)
            binding.loadingBar.visibility = View.GONE
            if (provider.type == Provider.Type.Rule) {
                binding.searchBar.visibility = View.VISIBLE
            }
            if (currentQuery.isNotBlank()) {
                performSearch(currentQuery)
            }
        }
    }

    suspend fun setUpdating(updating: Boolean) {
        withContext(Dispatchers.Main) {
            binding.btnUpdate.isEnabled = !updating
        }
    }

    private fun performSearch(query: String) {
        currentQuery = query
        if (query.isBlank()) {
            matchPositions = emptyList()
            currentMatchIndex = 0
            adapter.updateSearch("", emptySet(), -1)
            binding.searchCounter.visibility = View.GONE
            return
        }
        matchPositions = allRules.indices.filter { idx ->
            allRules[idx].contains(query, ignoreCase = true)
        }
        currentMatchIndex = 0
        updateAdapterAndCounter()
        scrollToCurrent()
    }

    private fun updateAdapterAndCounter() {
        val matchSet = matchPositions.toSet()
        val currentPos = if (matchPositions.isNotEmpty()) matchPositions[currentMatchIndex] else -1
        adapter.updateSearch(currentQuery, matchSet, currentPos)
        binding.searchCounter.visibility = View.VISIBLE
        binding.searchCounter.text = if (matchPositions.isEmpty()) "0"
        else "${currentMatchIndex + 1}/${matchPositions.size}"
    }

    private fun scrollToCurrent() {
        if (matchPositions.isEmpty()) return
        val pos = matchPositions[currentMatchIndex]
        (binding.recyclerList.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(pos, 80)
    }

    private fun navigateNext() {
        if (matchPositions.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + 1) % matchPositions.size
        updateAdapterAndCounter()
        scrollToCurrent()
    }

    private fun navigatePrev() {
        if (matchPositions.isEmpty()) return
        currentMatchIndex = (currentMatchIndex - 1 + matchPositions.size) % matchPositions.size
        updateAdapterAndCounter()
        scrollToCurrent()
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.recyclerList.applyLinearAdapter(context, adapter)
        binding.recyclerList.bindAppBarElevation(binding.activityBarLayout)

        if (provider.vehicleType == Provider.VehicleType.Inline) {
            binding.btnUpdate.visibility = View.GONE
        } else {
            binding.btnUpdate.setOnClickListener {
                requests.trySend(Request.Update)
            }
        }

        binding.activityBarLayout.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            binding.recyclerList.setPaddingRelative(0, v.height, 0, binding.recyclerList.paddingBottom)
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s?.toString() ?: "")
            }
        })

        binding.btnNavNext.setOnClickListener { navigateNext() }
        binding.btnNavPrev.setOnClickListener { navigatePrev() }
    }
}
