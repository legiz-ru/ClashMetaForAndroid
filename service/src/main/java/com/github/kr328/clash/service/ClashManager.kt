package com.github.kr328.clash.service

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.*
import com.github.kr328.clash.service.data.Selection
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.ILogObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.sendOverrideChanged
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel

class ClashManager(private val context: Context) : IClashManager,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val store = ServiceStore(context)
    private var logReceiver: ReceiveChannel<LogMessage>? = null

    override fun queryTunnelState(): TunnelState {
        return Clash.queryTunnelState()
    }

    override fun queryTrafficTotal(): Long {
        return Clash.queryTrafficTotal()
    }

    // Whether the active config has a user-defined GLOBAL override group.
    // Updated by queryProxyGroupNames(); read by queryProxyGroup() to suppress the hidden flag.
    @Volatile private var hasGlobalOverride = false

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> {
        // Always fetch all group names to detect a GLOBAL override group.
        val allNames = Clash.queryGroupNames(false)
        val globalName = allNames.firstOrNull { it.equals("GLOBAL", ignoreCase = true) }

        if (globalName != null) {
            hasGlobalOverride = true

            // Determine the effective tunnel mode (session override takes precedence).
            val effectiveMode = runCatching {
                Clash.queryOverride(Clash.OverrideSlot.Session).mode
                    ?: Clash.queryTunnelState().mode
            }.getOrElse {
                runCatching { Clash.queryTunnelState().mode }.getOrDefault(TunnelState.Mode.Rule)
            }

            return if (effectiveMode == TunnelState.Mode.Global) {
                // Global mode: show GLOBAL first, then all other groups.
                listOf(globalName) + allNames.filter { !it.equals(globalName, ignoreCase = true) }
            } else {
                // Rules / Direct / Script mode: show all groups except GLOBAL.
                allNames.filter { !it.equals(globalName, ignoreCase = true) }
            }
        }

        hasGlobalOverride = false
        return Clash.queryGroupNames(excludeNotSelectable)
    }

    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup {
        val group = Clash.queryGroup(name, proxySort)
        // When a GLOBAL override group is present, suppress the hidden flag so that
        // sub-groups (which the engine may mark hidden) are still visible in the UI.
        if (hasGlobalOverride) return group.copy(hidden = false)
        return group
    }

    override fun queryConfiguration(): UiConfiguration {
        return Clash.queryConfiguration()
    }

    override fun queryProviders(): ProviderList {
        return ProviderList(Clash.queryProviders())
    }

    override fun dumpRuleProviderToText(name: String, outputPath: String): String {
        return Clash.dumpRuleProviderToText(name, outputPath)
    }

    override fun queryRuleProviderFilePath(name: String): String {
        return Clash.queryRuleProviderFilePath(name) ?: ""
    }

    override fun queryOverride(slot: Clash.OverrideSlot): ConfigurationOverride {
        return Clash.queryOverride(slot)
    }

    override fun patchSelector(group: String, name: String): Boolean {
        return Clash.patchSelector(group, name).also {
            val current = store.activeProfile ?: return@also

            if (it) {
                SelectionDao().setSelected(Selection(current, group, name))
            } else {
                SelectionDao().removeSelected(current, group)
            }
        }
    }

    override fun patchOverride(slot: Clash.OverrideSlot, configuration: ConfigurationOverride) {
        Clash.patchOverride(slot, configuration)

        context.sendOverrideChanged()
    }

    override fun clearOverride(slot: Clash.OverrideSlot) {
        Clash.clearOverride(slot)
    }

    override suspend fun healthCheck(group: String) {
        return Clash.healthCheck(group).await()
    }

    override suspend fun updateProvider(type: Provider.Type, name: String) {
        return Clash.updateProvider(type, name).await()
    }

    override fun queryConnections(): String {
        return com.github.kr328.clash.core.bridge.Bridge.nativeQueryConnections()
    }

    override fun closeConnection(id: String): Boolean {
        return Clash.closeConnection(id)
    }

    override fun closeAllConnections() {
        com.github.kr328.clash.core.bridge.Bridge.nativeCloseAllConnections()
    }

    override fun setLogObserver(observer: ILogObserver?) {
        synchronized(this) {
            logReceiver?.apply {
                cancel()

                Clash.forceGc()
            }

            if (observer != null) {
                logReceiver = Clash.subscribeLogcat().also { c ->
                    launch {
                        try {
                            while (isActive) {
                                observer.newItem(c.receive())
                            }
                        } catch (e: CancellationException) {
                            // intended behavior
                            // ignore
                        } catch (e: Exception) {
                            Log.w("UI crashed", e)
                        } finally {
                            withContext(NonCancellable) {
                                c.cancel()

                                Clash.forceGc()
                            }
                        }
                    }
                }
            }
        }
    }
}
