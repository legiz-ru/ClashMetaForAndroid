package com.github.kr328.clash

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.ProxyScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ProxyActivity : BaseActivity() {
    private val groupMapFlow = MutableStateFlow<Map<String, ProxyGroup>>(emptyMap())
    private val testingFlow = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Proxies whose delay is being measured right now, by proxy name.
     *
     * The core keeps one object per name, so a name is enough to key this: the
     * same node shown in two groups is the same measurement.
     */
    private val testingProxiesFlow = MutableStateFlow<Set<String>>(emptySet())

    private lateinit var names: List<String>
    private val reloadLock = Semaphore(10)

    // UUID of the active profile and its remembered sort; the sort is persisted per profile.
    private var profileUuid: String = ""
    private var proxySort: ProxySort = ProxySort.Default

    override suspend fun main() {
        val mode = withClash { queryOverride(Clash.OverrideSlot.Session).mode }
        names = withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
        val active = withProfile { queryActive() }
        profileUuid = active?.uuid?.toString() ?: ""
        proxySort = uiStore.getProxySort(profileUuid)
        val activeProfileLatencyDots = active?.latencyDots ?: -1
        val useDots = when (activeProfileLatencyDots) {
            0 -> false
            1 -> true
            else -> uiStore.delayDisplayDots
        }

        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                val groupMap by groupMapFlow.collectAsStateWithLifecycle()
                val testing by testingFlow.collectAsStateWithLifecycle()
                val testingProxies by testingProxiesFlow.collectAsStateWithLifecycle()
                ProxyScreen(
                    groupNames = names,
                    groupMap = groupMap,
                    useDots = useDots,
                    mode = mode,
                    sort = proxySort,
                    excludeNotSelectable = uiStore.proxyExcludeNotSelectable,
                    testingGroups = testing,
                    testingProxies = testingProxies,
                    initialGroup = uiStore.proxyLastGroup,
                    onBack = { finish() },
                    onSelect = ::selectProxy,
                    onUrlTest = ::urlTestGroup,
                    onTestProxy = ::testProxy,
                    onSetSort = ::setSort,
                    onSetMode = ::setMode,
                    onToggleExclude = ::toggleExclude,
                    onGroupChanged = { uiStore.proxyLastGroup = it },
                )
            }
        }

        reloadAll()

        while (isActive) {
            when (events.receive()) {
                Event.ProfileLoaded -> {
                    val newNames = withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
                    if (newNames != names) relaunch()
                }
                else -> Unit
            }
        }
    }

    private fun reloadAll() {
        names.forEach { reloadGroup(it) }
    }

    private fun reloadGroup(name: String) {
        launch {
            val group = reloadLock.withPermit {
                withClash { queryProxyGroup(name, proxySort) }
            }
            groupMapFlow.value = groupMapFlow.value.toMutableMap().apply { put(name, group) }
        }
    }

    private fun selectProxy(group: String, proxy: String) {
        launch {
            withClash { patchSelector(group, proxy) }
            // Optimistic update for instant feedback, then re-query for the real state.
            groupMapFlow.value = groupMapFlow.value.toMutableMap().apply {
                this[group]?.let { put(group, it.copy(now = proxy)) }
            }
            reloadGroup(group)
        }
    }

    private fun urlTestGroup(group: String) {
        launch {
            val members = groupMapFlow.value[group]?.proxies?.map { it.name }?.toSet().orEmpty()

            testingFlow.value = testingFlow.value + group
            testingProxiesFlow.value = testingProxiesFlow.value + members
            try {
                withClash { healthCheck(group) }
                val refreshed = reloadLock.withPermit {
                    withClash { queryProxyGroup(group, proxySort) }
                }
                groupMapFlow.value = groupMapFlow.value.toMutableMap().apply { put(group, refreshed) }
            } finally {
                testingProxiesFlow.value = testingProxiesFlow.value - members
                testingFlow.value = testingFlow.value - group
            }
        }
    }

    /** Measure one proxy, triggered by a tap on its delay badge. */
    private fun testProxy(group: String, name: String) {
        launch {
            testingProxiesFlow.value = testingProxiesFlow.value + name
            try {
                withClash { healthCheckProxy(group, name) }
                // Inline rather than reloadGroup(): that one launches its own
                // coroutine, and the spinner would clear before the new delay
                // landed in the map.
                val refreshed = reloadLock.withPermit {
                    withClash { queryProxyGroup(group, proxySort) }
                }
                groupMapFlow.value = groupMapFlow.value.toMutableMap().apply { put(group, refreshed) }
            } finally {
                testingProxiesFlow.value = testingProxiesFlow.value - name
            }
        }
    }

    private fun setSort(sort: ProxySort) {
        proxySort = sort
        uiStore.setProxySort(profileUuid, sort)
        reloadAll()
    }

    private fun setMode(mode: TunnelState.Mode?) {
        launch {
            Toast.makeText(this@ProxyActivity, R.string.mode_switch_tips, Toast.LENGTH_LONG).show()
            withClash {
                val o = queryOverride(Clash.OverrideSlot.Session)
                o.mode = mode
                patchOverride(Clash.OverrideSlot.Session, o)
            }
            relaunch()
        }
    }

    private fun toggleExclude(exclude: Boolean) {
        uiStore.proxyExcludeNotSelectable = exclude
        relaunch()
    }

    private fun relaunch() {
        startActivity(ProxyActivity::class.intent)
        finish()
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
