package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.adapter.ProviderAdapter
import com.github.kr328.clash.design.adapter.RuleEntryAdapter
import com.github.kr328.clash.design.databinding.DesignProvidersBinding
import com.github.kr328.clash.design.util.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProvidersDesign(
    context: Context,
    providers: List<Provider>,
) : Design<ProvidersDesign.Request>(context) {
    sealed class Request {
        data class Update(val index: Int, val provider: Provider) : Request()
        data class ViewContent(val index: Int, val provider: Provider) : Request()
    }

    private val binding = DesignProvidersBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val adapter = ProviderAdapter(
        context,
        providers,
        requestUpdate = { index, provider ->
            requests.trySend(Request.Update(index, provider))
        },
        requestViewContent = { index, provider ->
            requests.trySend(Request.ViewContent(index, provider))
        },
    )

    fun updateElapsed() {
        adapter.updateElapsed()
    }

    suspend fun notifyUpdated(index: Int) {
        withContext(Dispatchers.Main) {
            adapter.notifyUpdated(index)
        }
    }

    suspend fun notifyChanged(index: Int) {
        withContext(Dispatchers.Main) {
            adapter.notifyChanged(index)
        }
    }

    suspend fun showRuleContent(provider: Provider, rules: List<String>) {
        withContext(Dispatchers.Main) {
            val recycler = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = RuleEntryAdapter(rules)
            }

            val title = "${provider.name} (${rules.size})"

            MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(recycler)
                .setPositiveButton(R.string.close) { _, _ -> }
                .show()
        }
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.bindAppBarElevation(binding.activityBarLayout)
        binding.mainList.recyclerList.applyLinearAdapter(context, adapter)
    }

    fun requestUpdateAll() {
        adapter.states.filter { !it.updating }.forEachIndexed { index, state ->
            state.updating = true
            if (state.provider.vehicleType != Provider.VehicleType.Inline) {
                requests.trySend(Request.Update(index, state.provider))
            }
        }
    }
}
