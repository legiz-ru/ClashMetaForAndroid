package com.github.kr328.clash

import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.core.model.ConnectionSnapshot
import com.github.kr328.clash.design.AppConnectionsDesign
import com.github.kr328.clash.design.model.ConnectionGroup
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val ConnectionsJson = Json { coerceInputValues = true; ignoreUnknownKeys = true }

class AppConnectionsActivity : BaseActivity<AppConnectionsDesign>() {
    companion object {
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_CLOSED_JSON = "extra_closed_json"
    }

    override suspend fun main() {
        val packageName = intent.getStringExtra(ConnectionsActivity.EXTRA_PACKAGE) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val closedJson = intent.getStringExtra(EXTRA_CLOSED_JSON)

        val initialConnections: List<Connection> = if (closedJson != null) {
            runCatching {
                ConnectionsJson.decodeFromString(ListSerializer(Connection.serializer()), closedJson)
            }.getOrElse { emptyList() }
        } else emptyList()

        val initialGroup = ConnectionGroup(packageName, appName, null, initialConnections, 0, 0)
        val design = AppConnectionsDesign(this, initialGroup)

        setContentDesign(design)

        if (closedJson != null) {
            // Static snapshot of closed connections — no live polling
            while (isActive) {
                select<Unit> {
                    events.onReceive {
                        when (it) {
                            Event.ClashStop -> finish()
                            else -> Unit
                        }
                    }
                    design.requests.onReceive { /* close-connection not applicable for closed */ }
                }
            }
        } else {
            // Active connections — poll every second
            val poller = launch {
                while (isActive) {
                    try {
                        val json = withClash { queryConnections() }
                        val snapshot = ConnectionsJson.decodeFromString(
                            ConnectionSnapshot.serializer(), json
                        )
                        val filtered = if (packageName.isEmpty())
                            snapshot.connections.filter { it.metadata.process.isEmpty() }
                        else
                            snapshot.connections.filter { it.metadata.process == packageName }
                        design.updateConnections(filtered)
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
                            is AppConnectionsDesign.Request.CloseConnection -> {
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
    }
}
