package com.github.kr328.clash.design

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.widget.TextView
import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.design.adapter.ConnectionAdapter
import com.github.kr328.clash.design.databinding.DesignAppConnectionsBinding
import com.github.kr328.clash.design.model.ConnectionGroup
import com.github.kr328.clash.design.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppConnectionsDesign(
    context: Context,
    group: ConnectionGroup,
) : Design<AppConnectionsDesign.Request>(context) {
    sealed class Request {
        data class CloseConnection(val id: String) : Request()
    }

    private val binding = DesignAppConnectionsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View get() = binding.root

    private val adapter = ConnectionAdapter(
        context,
        onConnectionClicked = { showConnectionDetailSheet(context, it) },
        onConnectionClosed = { requests.trySend(Request.CloseConnection(it.id)) }
    )

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)

        // Hide generic title — app name is shown via app_name TextView instead
        binding.activityBarLayout.findViewById<TextView>(R.id.activity_bar_title_view)?.visibility = View.GONE

        // Dynamic paddingTop so the list doesn't go behind the toolbar
        binding.activityBarLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val top = binding.activityBarLayout.height
            if (binding.recyclerList.paddingTop != top) {
                binding.recyclerList.setPadding(0, top, 0, binding.recyclerList.paddingBottom)
            }
        }

        binding.recyclerList.bindAppBarElevation(binding.activityBarLayout)
        binding.recyclerList.applyLinearAdapter(context, adapter)

        binding.appName.text = group.appName

        val icon = group.icon ?: if (group.packageName.isNotEmpty()) {
            runCatching {
                context.packageManager.getApplicationInfo(group.packageName, 0).loadIcon(context.packageManager)
            }.getOrNull()
        } else null

        if (icon != null) {
            binding.appIcon.setImageDrawable(icon)
        } else {
            binding.appIcon.setImageResource(R.drawable.ic_launcher_foreground)
        }

        adapter.connections = group.connections
    }

    suspend fun updateConnections(connections: List<Connection>) {
        withContext(Dispatchers.Main) {
            adapter.connections = connections
        }
    }
}
