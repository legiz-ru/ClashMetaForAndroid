package com.github.kr328.clash

import com.github.kr328.clash.core.model.ConnectionSnapshot
import com.github.kr328.clash.design.AppConnectionsDesign
import com.github.kr328.clash.design.model.ConnectionGroup
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json

private val ConnectionsJson = Json { coerceInputValues = true; ignoreUnknownKeys = true }

class AppConnectionsActivity : BaseActivity<AppConnectionsDesign>() {
    companion object {
        const val EXTRA_APP_NAME = "extra_app_name"
    }

    override suspend fun main() {
        val packageName = intent.getStringExtra(ConnectionsActivity.EXTRA_PACKAGE) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName

        val initialGroup = ConnectionGroup(packageName, appName, null, emptyList(), 0, 0)
        val design = AppConnectionsDesign(this, initialGroup)

        setContentDesign(design)

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
