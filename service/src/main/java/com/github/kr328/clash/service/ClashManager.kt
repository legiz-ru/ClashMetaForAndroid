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
import com.github.kr328.clash.service.util.importedDir
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

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> {
        val names = Clash.queryGroupNames(excludeNotSelectable)
        val hidden = loadHiddenProxyGroups()

        if (hidden.isEmpty()) return names

        return names.filterNot { it in hidden }
    }

    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup {
        return Clash.queryGroup(name, proxySort)
    }

    override fun queryConfiguration(): UiConfiguration {
        return Clash.queryConfiguration()
    }

    override fun queryProviders(): ProviderList {
        return ProviderList(Clash.queryProviders())
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

    private fun loadHiddenProxyGroups(): Set<String> {
        val activeProfile = store.activeProfile ?: return emptySet()
        val configFile = context.importedDir
            .resolve(activeProfile.toString())
            .resolve("config.yaml")

        if (!configFile.exists()) return emptySet()

        return parseHiddenProxyGroups(configFile.readLines())
    }

    private fun parseHiddenProxyGroups(lines: List<String>): Set<String> {
        val hiddenGroups = mutableSetOf<String>()
        var inProxyGroups = false
        var currentName: String? = null
        var currentHidden = false

        fun commitCurrent() {
            val name = currentName?.takeIf { it.isNotBlank() } ?: return
            if (currentHidden) hiddenGroups.add(name)
        }

        for (line in lines) {
            if (line.isBlank() || line.trimStart().startsWith("#")) continue

            val isTopLevel = line.indexOfFirst { !it.isWhitespace() } == 0
            val trimmed = line.trimStart()

            if (isTopLevel) {
                if (trimmed.startsWith("proxy-groups:")) {
                    inProxyGroups = true
                    currentName = null
                    currentHidden = false
                    continue
                }

                if (inProxyGroups) {
                    commitCurrent()
                    break
                }
            }

            if (!inProxyGroups) continue

            if (trimmed.startsWith("-")) {
                commitCurrent()
                currentName = null
                currentHidden = false

                val remainder = trimmed.removePrefix("-").trim()
                if (remainder.startsWith("name:")) {
                    currentName = parseYamlValue(remainder.removePrefix("name:"))
                } else if (remainder.startsWith("hidden:")) {
                    currentHidden = parseYamlValue(remainder.removePrefix("hidden:"))
                        .equals("true", ignoreCase = true)
                }
                continue
            }

            if (trimmed.startsWith("name:")) {
                currentName = parseYamlValue(trimmed.removePrefix("name:"))
                continue
            }

            if (trimmed.startsWith("hidden:")) {
                currentHidden = parseYamlValue(trimmed.removePrefix("hidden:"))
                    .equals("true", ignoreCase = true)
            }
        }

        if (inProxyGroups) {
            commitCurrent()
        }

        return hiddenGroups
    }

    private fun parseYamlValue(value: String): String {
        return value.trim().trim('"', '\'')
    }
}
