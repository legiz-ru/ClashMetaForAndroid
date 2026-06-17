package com.github.kr328.clash

import android.Manifest.permission.INTERNET
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.design.compose.screen.AccessControlScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.util.toAppInfo
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccessControlActivity : BaseActivity() {
    private val appsFlow = MutableStateFlow<List<AppInfo>>(emptyList())
    private val selectedFlow = MutableStateFlow<Set<String>>(emptySet())
    private val sortFlow = MutableStateFlow(AppInfoSort.Label)
    private val reverseFlow = MutableStateFlow(false)
    private val systemAppsFlow = MutableStateFlow(false)

    override suspend fun main() {
        val service = ServiceStore(this)

        sortFlow.value = uiStore.accessControlSort
        reverseFlow.value = uiStore.accessControlReverse
        systemAppsFlow.value = uiStore.accessControlSystemApp

        selectedFlow.value = withContext(Dispatchers.IO) {
            service.accessControlPackages.toSet()
        }

        defer {
            withContext(Dispatchers.IO) {
                val selected = selectedFlow.value
                val changed = selected != service.accessControlPackages
                service.accessControlPackages = selected
                if (clashRunning && changed) {
                    stopClashService()
                    while (clashRunning) {
                        delay(200)
                    }
                    startClashService()
                }
            }
        }

        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                val apps by appsFlow.collectAsStateWithLifecycle()
                val selected by selectedFlow.collectAsStateWithLifecycle()
                val sort by sortFlow.collectAsStateWithLifecycle()
                val reverse by reverseFlow.collectAsStateWithLifecycle()
                val systemApps by systemAppsFlow.collectAsStateWithLifecycle()
                AccessControlScreen(
                    apps = apps,
                    selected = selected,
                    sort = sort,
                    reverse = reverse,
                    systemApps = systemApps,
                    onBack = { finish() },
                    onToggle = ::toggle,
                    onSelectAll = ::selectAll,
                    onSelectNone = ::selectNone,
                    onSelectInvert = ::selectInvert,
                    onImport = ::importFromClipboard,
                    onExport = ::exportToClipboard,
                    onSetSort = ::setSort,
                    onSetReverse = ::setReverse,
                    onSetSystemApps = ::setSystemApps,
                )
            }
        }

        appsFlow.value = loadApps(selectedFlow.value)

        while (isActive) {
            events.receive()
        }
    }

    private fun toggle(pkg: String) {
        val current = selectedFlow.value
        selectedFlow.value = if (pkg in current) current - pkg else current + pkg
    }

    private fun selectAll() {
        selectedFlow.value = appsFlow.value.map(AppInfo::packageName).toSet()
    }

    private fun selectNone() {
        selectedFlow.value = emptySet()
    }

    private fun selectInvert() {
        selectedFlow.value = appsFlow.value.map(AppInfo::packageName).toSet() - selectedFlow.value
    }

    private fun importFromClipboard() {
        val clipboard = getSystemService<ClipboardManager>()
        val data = clipboard?.primaryClip
        if (data != null && data.itemCount > 0) {
            val packages = data.getItemAt(0).text.split("\n").toSet()
            selectedFlow.value = appsFlow.value.map(AppInfo::packageName).intersect(packages)
        }
    }

    private fun exportToClipboard() {
        val clipboard = getSystemService<ClipboardManager>()
        val data = ClipData.newPlainText("packages", selectedFlow.value.joinToString("\n"))
        clipboard?.setPrimaryClip(data)
    }

    private fun setSort(sort: AppInfoSort) {
        uiStore.accessControlSort = sort
        sortFlow.value = sort
        reload()
    }

    private fun setReverse(reverse: Boolean) {
        uiStore.accessControlReverse = reverse
        reverseFlow.value = reverse
        reload()
    }

    private fun setSystemApps(systemApps: Boolean) {
        uiStore.accessControlSystemApp = systemApps
        systemAppsFlow.value = systemApps
        reload()
    }

    private fun reload() {
        launch {
            appsFlow.value = loadApps(selectedFlow.value)
        }
    }

    private suspend fun loadApps(selected: Set<String>): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val reverse = uiStore.accessControlReverse
            val sort = uiStore.accessControlSort
            val systemApp = uiStore.accessControlSystemApp

            val base = compareByDescending<AppInfo> { it.packageName in selected }
            val comparator = if (reverse) base.thenDescending(sort) else base.then(sort)

            val pm = packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            packages.asSequence()
                .filter {
                    it.packageName != packageName
                }
                .filter {
                    it.applicationInfo != null
                }
                .filter {
                    it.requestedPermissions?.contains(INTERNET) == true || it.applicationInfo!!.uid < android.os.Process.FIRST_APPLICATION_UID
                }
                .filter {
                    systemApp || !it.isSystemApp
                }
                .map {
                    it.toAppInfo(pm)
                }
                .sortedWith(comparator)
                .toList()
        }

    private val PackageInfo.isSystemApp: Boolean
        get() {
            return applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0
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
