package com.github.kr328.clash.design

import android.content.Context
import android.view.View
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
        binding.mainList.recyclerList.bindAppBarElevation(binding.activityBarLayout)
        binding.mainList.recyclerList.applyLinearAdapter(context, adapter)

        binding.appName.text = group.appName
        if (group.icon != null) {
            binding.appIcon.setImageDrawable(group.icon)
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
