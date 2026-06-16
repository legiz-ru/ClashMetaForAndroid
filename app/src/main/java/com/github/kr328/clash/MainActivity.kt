package com.github.kr328.clash

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.importProfileFromUrl
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.core.bridge.*
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.update.UpdateChecker
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class MainActivity : BaseActivity<MainDesign>() {
    private fun extractInstallConfigUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null

        val data = intent.data ?: return null
        if (!data.host.equals("install-config", true)) return null

        return data.getQueryParameter("url")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private val scanLauncher = registerForActivityResult(ScanQRCode()) { result ->
        lifecycleScope.launch {
            when (result) {
                is QRResult.QRSuccess -> {
                    val url = result.content.rawValue
                        ?: result.content.rawBytes?.let { String(it) }.orEmpty()
                    if (url.isNotEmpty()) {
                        importProfileFromUrl(url)
                    }
                }
                QRResult.QRUserCanceled -> {}
                QRResult.QRMissingPermission -> {
                    design?.showToast(R.string.import_from_qr_no_permission, ToastDuration.Long)
                }
                is QRResult.QRError -> {
                    design?.showToast(R.string.import_from_qr_exception, ToastDuration.Long)
                }
            }
        }
    }

    override suspend fun main() {
        val design = MainDesign(this)

        setContentDesign(design)

        design.fetch()

        extractInstallConfigUrl(intent)?.let {
            importProfileFromUrl(it, forceAutoImport = true)
        }

        // Handle update deep-link from notification tap
        intent?.let { handleUpdateIntent(it, design) }

        // Periodic automatic update check (every 12 hours)
        if (UpdateChecker.shouldCheck(this)) {
            launch { runUpdateCheck(design, silent = true) }
        }

        val ticker = ticker(TimeUnit.SECONDS.toMillis(5))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart,
                        Event.ProfileLoaded, Event.ProfileChanged -> design.fetch()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        MainDesign.Request.ToggleStatus -> {
                            if (clashRunning)
                                stopClashService()
                            else
                                design.startClash()
                        }
                        MainDesign.Request.OpenProxy ->
                            startActivity(ProxyActivity::class.intent)
                        MainDesign.Request.OpenProfiles ->
                            startActivity(ProfilesActivity::class.intent)
                        MainDesign.Request.OpenProviders ->
                            startActivity(ProvidersActivity::class.intent)
                        MainDesign.Request.OpenSettings -> {
                            startActivity(SettingsActivity::class.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION))
                            overridePendingTransition(0, 0)
                        }
                        MainDesign.Request.ManageProfiles ->
                            startActivity(ProfilesActivity::class.intent)
                        MainDesign.Request.UpdateProfile -> {
                            val active = withProfile { queryActive() }
                            if (active != null && active.imported && active.type != Profile.Type.File) {
                                withProfile { update(active.uuid) }
                            }
                        }
                        MainDesign.Request.DeleteProfile -> {
                            val active = withProfile { queryActive() }
                            if (active != null) {
                                withProfile { delete(active.uuid) }
                            }
                        }
                        MainDesign.Request.AddFromClipboard -> {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                            if (clipText.isNotEmpty()) {
                                importProfileFromUrl(clipText)
                            } else {
                                design.showToast(R.string.empty_clipboard, ToastDuration.Long)
                            }
                        }
                        MainDesign.Request.ScanQrCode -> {
                            scanLauncher.launch(null)
                        }
                        MainDesign.Request.AddFromFile -> {
                            val uuid = withProfile {
                                create(Profile.Type.File, getString(R.string.new_profile))
                            }
                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                        MainDesign.Request.AddManually -> {
                            val uuid = withProfile {
                                create(Profile.Type.Url, getString(R.string.new_profile))
                            }
                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                        MainDesign.Request.TvImport ->
                            startActivity(TvImportActivity::class.intent)
                        MainDesign.Request.OpenHelp ->
                            startActivity(HelpActivity::class.intent)
                        MainDesign.Request.OpenAbout ->
                            design.showAbout(queryAppVersionName())
                        MainDesign.Request.CheckUpdate -> {
                            val pending = design.consumePendingUpdate()
                            if (pending != null) {
                                // User confirmed download from update dialog
                                UpdateChecker.startDownload(this@MainActivity, pending.second, pending.first)
                            } else {
                                // Manual check from About dialog
                                launch { runUpdateCheck(design, silent = false) }
                            }
                        }
                        MainDesign.Request.SelectProxy -> {
                            val (group, selected) = design.consumePendingProxySelection() ?: return@onReceive
                            withClash {
                                patchSelector(group, selected)
                            }
                            design.fetchProxyGroups()
                        }
                        MainDesign.Request.UrlTest -> {
                            val groupName = design.consumePendingUrlTestGroup() ?: return@onReceive
                            launch {
                                withClash {
                                    healthCheck(groupName)
                                }
                                design.fetchProxyGroups()
                            }
                        }
                        MainDesign.Request.UrlTestSimpleMode -> {
                            val firstName = withClash {
                                queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
                                    .firstOrNull()
                            }
                            if (firstName != null) {
                                launch {
                                    design.setLatencyTestRunning(true)
                                    try {
                                        withClash { healthCheck(firstName) }
                                    } finally {
                                        design.setLatencyTestRunning(false)
                                    }
                                    design.fetchProxyGroups()
                                }
                            }
                        }
                        MainDesign.Request.OpenConnections ->
                            startActivity(ConnectionsActivity::class.intent)
                        MainDesign.Request.OpenModeSelector -> {
                            launch {
                                val currentMode = withClash {
                                    queryOverride(Clash.OverrideSlot.Session).mode
                                        ?: queryTunnelState().mode
                                }
                                val newMode = design.showModeDialog(currentMode) ?: return@launch
                                if (newMode != currentMode) {
                                    withClash {
                                        val o = queryOverride(Clash.OverrideSlot.Session)
                                        o.mode = newMode
                                        patchOverride(Clash.OverrideSlot.Session, o)
                                    }
                                    design.setMode(newMode)
                                }
                            }
                        }
                    }
                }
                if (clashRunning) {
                    ticker.onReceive {
                        design.fetchProxyGroups()
                    }
                }
            }
        }
    }

    private suspend fun MainDesign.fetch() {
        setClashRunning(clashRunning)

        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }

        setMode(state.mode)
        setHasProviders(providers.isNotEmpty())

        withProfile {
            val active = queryActive()
            setProfileName(active?.name)
            setHasProfiles(queryAll().isNotEmpty())
            setActiveProfileInfo(active)
        }

        if (clashRunning) {
            fetchProxyGroups()
        }
    }

    private suspend fun MainDesign.fetchProxyGroups() {
        try {
            val activeLatencyDots = withProfile { queryActive()?.latencyDots ?: -1 }
            val effectiveDots = when (activeLatencyDots) {
                0 -> false
                1 -> true
                else -> uiStore.delayDisplayDots
            }
            withClash {
                val names = queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
                val visibleGroups = names.map { name ->
                    name to queryProxyGroup(name, uiStore.proxySort)
                }.filter { !it.second.hidden }

                // Smart subgroups referenced inside visible groups may themselves be
                // hidden (or excluded as not-selectable), so they don't appear in
                // `names`. Their per-proxy weight data is needed to render the weight
                // shield on the nested row. Query them explicitly so the data is
                // available — they are NOT added as visible cards, only as a data
                // source for setProxyGroups' lookup map.
                val knownNames = visibleGroups.map { it.first }.toHashSet()
                val nestedSmartNames = visibleGroups
                    .flatMap { it.second.proxies }
                    .filter { it.type == "Smart" && it.name !in knownNames }
                    .map { it.name }
                    .distinct()
                val nestedSmartGroups = nestedSmartNames.map { name ->
                    // Force hidden = true so these never render as standalone cards;
                    // they only feed the weight-lookup map in setProxyGroups.
                    name to queryProxyGroup(name, uiStore.proxySort).copy(hidden = true)
                }

                setProxyGroups(visibleGroups + nestedSmartGroups, effectiveDots)
            }
        } catch (_: Exception) {
            // Proxy groups may not be available yet
        }
    }

    private suspend fun MainDesign.startClash() {
        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            showToast(R.string.no_profile_selected, ToastDuration.Long) {
                setAction(R.string.profiles) {
                    startActivity(ProfilesActivity::class.intent)
                }
            }

            return
        }

        val vpnRequest = startClashService()

        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            design?.showToast(R.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" + Bridge.nativeCoreVersion().replace("_", "-")
        }
    }

    private suspend fun runUpdateCheck(design: MainDesign, silent: Boolean) {
        when (val result = UpdateChecker.check(this)) {
            is UpdateChecker.CheckResult.UpdateAvailable -> {
                if (silent) {
                    UpdateChecker.showUpdateNotification(this, result.tagName, result.downloadUrl)
                } else {
                    design.showUpdateAvailableDialog(result.tagName, result.downloadUrl)
                }
            }
            is UpdateChecker.CheckResult.UpToDate -> {
                if (!silent) design.showUpdateNotFoundDialog()
            }
            is UpdateChecker.CheckResult.Error -> {
                if (!silent) design.showUpdateErrorDialog(result.message)
            }
        }
    }

    private fun handleUpdateIntent(intent: Intent, design: MainDesign) {
        if (intent.action != UpdateChecker.ACTION_SHOW_UPDATE) return
        val tag = intent.getStringExtra(UpdateChecker.EXTRA_TAG) ?: return
        val url = intent.getStringExtra(UpdateChecker.EXTRA_URL) ?: return
        lifecycleScope.launch {
            design.showUpdateAvailableDialog(tag, url)
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val url = extractInstallConfigUrl(intent) ?: return

        lifecycleScope.launch {
            importProfileFromUrl(url, forceAutoImport = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()
                ) { isGranted: Boolean ->
                }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setupShortcuts()
    }

    private fun setupShortcuts() {
        if (uiStore.hideAppIcon) return

        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NO_ANIMATION

        val toggle = ShortcutInfoCompat.Builder(this, "toggle_clash")
            .setShortLabel(getString(R.string.shortcut_toggle_short))
            .setLongLabel(getString(R.string.shortcut_toggle_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_all))
            .setIntent(
                Intent(Intents.ACTION_TOGGLE_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(0)
            .build()

        val start = ShortcutInfoCompat.Builder(this, "start_clash")
            .setShortLabel(getString(R.string.shortcut_start_short))
            .setLongLabel(getString(R.string.shortcut_start_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_on))
            .setIntent(
                Intent(Intents.ACTION_START_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(1)
            .build()

        val stop = ShortcutInfoCompat.Builder(this, "stop_clash")
            .setShortLabel(getString(R.string.shortcut_stop_short))
            .setLongLabel(getString(R.string.shortcut_stop_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_off))
            .setIntent(
                Intent(Intents.ACTION_STOP_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(2)
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(toggle, start, stop))
    }
}
