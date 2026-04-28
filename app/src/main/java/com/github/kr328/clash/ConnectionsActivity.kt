package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.core.model.ConnectionSnapshot
import com.github.kr328.clash.design.ConnectionsDesign
import com.github.kr328.clash.design.model.ConnectionGroup
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val ConnectionsJson = Json { coerceInputValues = true; ignoreUnknownKeys = true }

class ConnectionsActivity : BaseActivity<ConnectionsDesign>() {
    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }

    override suspend fun main() {
        val design = ConnectionsDesign(this)

        setContentDesign(design)

        val poller = launch {
            while (isActive) {
                try {
                    val json = withClash { queryConnections() }
                    val snapshot = ConnectionsJson.decodeFromString(
                        ConnectionSnapshot.serializer(), json
                    )
                    design.updateSnapshot(snapshot)
                } catch (_: Exception) {}
                delay(1000)
            }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStop -> finish()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        is ConnectionsDesign.Request.OpenApp -> {
                            openAppConnections(it.group, it.showClosed)
                        }
                        is ConnectionsDesign.Request.KillAll -> {
                            launch {
                                try { withClash { closeAllConnections() } } catch (_: Exception) {}
                            }
                        }
                        is ConnectionsDesign.Request.CloseConnection -> {
                            launch {
                                try { withClash { closeConnection(it.id) } } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }

        poller.cancel()
    }

    private fun openAppConnections(group: ConnectionGroup, showClosed: Boolean) {
        val intent = AppConnectionsActivity::class.intent
        intent.putExtra(EXTRA_PACKAGE, group.packageName)
        intent.putExtra(AppConnectionsActivity.EXTRA_APP_NAME, group.appName)
        if (showClosed) {
            val closedJson = ConnectionsJson.encodeToString(
                ListSerializer(Connection.serializer()),
                group.connections
            )
            intent.putExtra(AppConnectionsActivity.EXTRA_CLOSED_JSON, closedJson)
        }
        startActivity(intent)
    }
}
