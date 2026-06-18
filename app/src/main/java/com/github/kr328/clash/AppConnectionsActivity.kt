package com.github.kr328.clash

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.core.model.ConnectionSnapshot
import com.github.kr328.clash.design.compose.component.ConnectionDetailSheet
import com.github.kr328.clash.design.compose.screen.AppConnectionsScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val ConnectionsJson = Json { coerceInputValues = true; ignoreUnknownKeys = true }

class AppConnectionsActivity : BaseActivity() {
    companion object {
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_CLOSED_JSON = "extra_closed_json"
    }

    private val connectionsFlow = MutableStateFlow<List<Connection>>(emptyList())
    private val detailFlow = MutableStateFlow<Connection?>(null)

    override suspend fun main() {
        val packageName = intent.getStringExtra(ConnectionsActivity.EXTRA_PACKAGE) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val closedJson = intent.getStringExtra(EXTRA_CLOSED_JSON)

        val initialConnections: List<Connection> = if (closedJson != null) {
            runCatching {
                ConnectionsJson.decodeFromString(ListSerializer(Connection.serializer()), closedJson)
            }.getOrElse { emptyList() }
        } else emptyList()
        connectionsFlow.value = initialConnections

        val appIcon: Drawable? = if (packageName.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    packageManager.getApplicationInfo(packageName, 0).loadIcon(packageManager)
                }.getOrNull()
            }
        } else null

        val isLive = closedJson == null

        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                val connections by connectionsFlow.collectAsStateWithLifecycle()
                val detail by detailFlow.collectAsStateWithLifecycle()
                AppConnectionsScreen(
                    appName = appName,
                    appIcon = appIcon,
                    connections = connections,
                    closable = isLive,
                    onBack = { finish() },
                    onConnectionClick = { detailFlow.value = it },
                    onClose = ::closeConn,
                )
                detail?.let {
                    ConnectionDetailSheet(
                        connection = it,
                        onDismiss = { detailFlow.value = null },
                    )
                }
            }
        }

        if (isLive) {
            val poller = launch {
                while (isActive) {
                    try {
                        val json = withClash { queryConnections() }
                        val snapshot = ConnectionsJson.decodeFromString(
                            ConnectionSnapshot.serializer(), json
                        )
                        connectionsFlow.value = if (packageName.isEmpty())
                            snapshot.connections.filter { it.metadata.process.isEmpty() }
                        else
                            snapshot.connections.filter { it.metadata.process == packageName }
                    } catch (_: Exception) {}
                    delay(1000)
                }
            }

            while (isActive) {
                when (events.receive()) {
                    Event.ClashStop -> finish()
                    else -> Unit
                }
            }

            poller.cancel()
        } else {
            while (isActive) {
                when (events.receive()) {
                    Event.ClashStop -> finish()
                    else -> Unit
                }
            }
        }
    }

    private fun closeConn(connection: Connection) {
        launch {
            try { withClash { closeConnection(connection.id) } } catch (_: Exception) {}
        }
    }

    private fun currentThemeVariant(): ClashThemeVariant {
        val cfg = resources.configuration
        return when (uiStore.darkMode) {
            DarkMode.Auto ->
                if (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    ClashThemeVariant.Dark
                } else {
                    ClashThemeVariant.Light
                }
            DarkMode.ForceLight -> ClashThemeVariant.Light
            DarkMode.ForceDark -> ClashThemeVariant.Dark
            DarkMode.AlwaysSummer -> ClashThemeVariant.Summer
        }
    }
}
