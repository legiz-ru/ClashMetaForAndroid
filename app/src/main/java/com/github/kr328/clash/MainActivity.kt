package com.github.kr328.clash

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.AddProfileAction
import com.github.kr328.clash.design.compose.screen.MainScreen
import com.github.kr328.clash.design.compose.screen.SettingsNavTarget
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.update.UpdateChecker
import com.github.kr328.clash.util.importProfileFromUrl
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity() {
    private val isLoadingFlow = MutableStateFlow(true)
    private val runningFlow = MutableStateFlow(false)
    private val hasProfilesFlow = MutableStateFlow(false)
    private val activeProfileFlow = MutableStateFlow<Profile?>(null)
    private val appTitleFlow = MutableStateFlow("")
    private val appLogoUrlFlow = MutableStateFlow("")
    private val latencyTestingFlow = MutableStateFlow(false)
    private val proxyGroupsFlow = MutableStateFlow<List<Pair<String, ProxyGroup>>>(emptyList())
    private val useDotsFlow = MutableStateFlow(true)

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
                QRResult.QRMissingPermission -> toast(R.string.import_from_qr_no_permission)
                is QRResult.QRError -> toast(R.string.import_from_qr_exception)
            }
        }
    }

    override suspend fun main() {
        appTitleFlow.value = getString(R.string.application_name)
        runningFlow.value = clashRunning

        setContent {
            val isLoading by isLoadingFlow.collectAsStateWithLifecycle()
            val running by runningFlow.collectAsStateWithLifecycle()
            val hasProfiles by hasProfilesFlow.collectAsStateWithLifecycle()
            val activeProfile by activeProfileFlow.collectAsStateWithLifecycle()
            val appTitle by appTitleFlow.collectAsStateWithLifecycle()
            val appLogoUrl by appLogoUrlFlow.collectAsStateWithLifecycle()
            val latencyTesting by latencyTestingFlow.collectAsStateWithLifecycle()
            val proxyGroups by proxyGroupsFlow.collectAsStateWithLifecycle()
            val useDots by useDotsFlow.collectAsStateWithLifecycle()

            ClashTheme(variant = currentThemeVariant()) {
                MainScreen(
                    expanded = useDrawerNav(),
                    isTv = TvUtils.isTv(this),
                    clashRunning = running,
                    isLoading = isLoading,
                    hasProfiles = hasProfiles,
                    activeProfile = activeProfile,
                    appTitle = appTitle,
                    appLogoUrl = appLogoUrl,
                    latencyTesting = latencyTesting,
                    proxyGroups = proxyGroups,
                    useDots = useDots,
                    onPowerToggle = ::toggleStatus,
                    onUpdateProfile = ::updateActiveProfile,
                    onManageProfiles = { startActivity(ProfilesActivity::class.intent) },
                    onModeSelector = ::openModeSelector,
                    onOpenConnections = { startActivity(ConnectionsActivity::class.intent) },
                    onOpenProviders = { startActivity(ProvidersActivity::class.intent) },
                    onOpenSupport = ::openUrl,
                    onOpenWebPage = ::openUrl,
                    onAdd = ::add,
                    onNavigate = ::navigate,
                    onLatencyTest = ::latencyTestSimpleMode,
                    onDisconnect = { stopClashService() },
                    onSelectProxy = ::selectProxy,
                    onUrlTest = ::urlTestGroup,
                    onLogoTap = ::onLogoTap,
                )
            }
        }

        fetch()

        extractInstallConfigUrl(intent)?.let {
            importProfileFromUrl(it, forceAutoImport = true)
        }
        intent?.let { handleUpdateIntent(it) }
        if (UpdateChecker.shouldCheck(this)) {
            launch { runUpdateCheckSilent() }
        }

        val ticker = ticker(TimeUnit.SECONDS.toMillis(5))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart,
                        Event.ProfileLoaded, Event.ProfileChanged -> fetch()
                        else -> Unit
                    }
                }
                if (clashRunning) {
                    ticker.onReceive {
                        fetchProxyGroups()
                    }
                }
            }
        }
    }

    private suspend fun fetch() {
        runningFlow.value = clashRunning
        withProfile {
            val active = queryActive()
            activeProfileFlow.value = active
            hasProfilesFlow.value = queryAll().isNotEmpty()
            appTitleFlow.value = active?.profileTitle?.takeIf { it.isNotEmpty() }
                ?: getString(R.string.application_name)
            appLogoUrlFlow.value = active?.profileLogo.orEmpty()
        }
        if (clashRunning) {
            fetchProxyGroups()
        } else {
            proxyGroupsFlow.value = emptyList()
        }
        isLoadingFlow.value = false
    }

    private suspend fun fetchProxyGroups() {
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

                val knownNames = visibleGroups.map { it.first }.toHashSet()
                val nestedSmartNames = visibleGroups
                    .flatMap { it.second.proxies }
                    .filter { it.type == "Smart" && it.name !in knownNames }
                    .map { it.name }
                    .distinct()
                val nestedSmartGroups = nestedSmartNames.map { name ->
                    name to queryProxyGroup(name, uiStore.proxySort).copy(hidden = true)
                }

                proxyGroupsFlow.value = visibleGroups + nestedSmartGroups
                useDotsFlow.value = effectiveDots
            }
        } catch (_: Exception) {
            // Proxy groups may not be available yet
        }
    }

    private fun selectProxy(group: String, proxy: String) {
        launch {
            withClash { patchSelector(group, proxy) }
            fetchProxyGroups()
        }
    }

    private fun urlTestGroup(group: String) {
        launch {
            withClash { healthCheck(group) }
            fetchProxyGroups()
        }
    }

    private var logoTapCount = 0
    private var logoTapResetJob: kotlinx.coroutines.Job? = null

    // Easter egg: 15 taps on the logo unlocks the "Always Summer" theme.
    private fun onLogoTap() {
        logoTapResetJob?.cancel()
        logoTapCount++
        if (logoTapCount >= 15) {
            logoTapCount = 0
            uiStore.summerModeUnlocked = true
            Toast.makeText(
                this,
                "🥒 Всегда Лето разблокирован! Проверьте настройки темы",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            logoTapResetJob = lifecycleScope.launch {
                kotlinx.coroutines.delay(1000)
                logoTapCount = 0
            }
        }
    }

    private fun toggleStatus() {
        if (clashRunning) {
            stopClashService()
        } else {
            launch { startClash() }
        }
    }

    private suspend fun startClash() {
        val active = withProfile { queryActive() }
        if (active == null || !active.imported) {
            toast(R.string.no_profile_selected)
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
            toast(R.string.unable_to_start_vpn)
        }
    }

    private fun updateActiveProfile() {
        launch {
            val active = withProfile { queryActive() }
            if (active != null && active.imported && active.type != Profile.Type.File) {
                withProfile { update(active.uuid) }
            }
        }
    }

    private fun openModeSelector() {
        launch {
            val current = withClash {
                queryOverride(Clash.OverrideSlot.Session).mode ?: queryTunnelState().mode
            }
            // Resolve the choice from a suspending dialog and apply it from THIS
            // coroutine (prizrak structure). Patching from a launch{} spawned inside
            // the dialog's click listener did not take effect.
            val newMode = showModeDialog(current) ?: return@launch
            withClash {
                val o = queryOverride(Clash.OverrideSlot.Session)
                o.mode = newMode
                patchOverride(Clash.OverrideSlot.Session, o)
            }
            Toast.makeText(this@MainActivity, R.string.mode_switch_tips, Toast.LENGTH_SHORT).show()
            // Reflect the new mode immediately (the proxy view depends on it).
            fetchProxyGroups()
        }
    }

    private suspend fun showModeDialog(current: TunnelState.Mode?): TunnelState.Mode? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val modes = arrayOf(TunnelState.Mode.Rule, TunnelState.Mode.Global)
            val labels = arrayOf(
                getString(R.string.mode_rule_label),
                getString(R.string.mode_global_label),
            )
            val checked = modes.indexOf(current).coerceAtLeast(0)
            val dialog = MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.mode_selector_title)
                .setSingleChoiceItems(labels, checked) { d, which ->
                    if (cont.isActive) cont.resumeWith(Result.success(modes[which]))
                    d.dismiss()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> }
                .setOnDismissListener {
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }

    private fun latencyTestSimpleMode() {
        launch {
            val firstName = withClash {
                queryProxyGroupNames(uiStore.proxyExcludeNotSelectable).firstOrNull()
            }
            if (firstName != null) {
                latencyTestingFlow.value = true
                try {
                    withClash { healthCheck(firstName) }
                } finally {
                    latencyTestingFlow.value = false
                }
            }
        }
    }

    private fun add(action: AddProfileAction) {
        when (action) {
            AddProfileAction.Clipboard -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                if (text.isEmpty()) {
                    toast(R.string.empty_clipboard)
                } else {
                    launch { importProfileFromUrl(text) }
                }
            }
            AddProfileAction.ScanQr -> scanLauncher.launch(null)
            AddProfileAction.File -> launch {
                val uuid = withProfile { create(Profile.Type.File, getString(R.string.new_profile)) }
                startActivity(PropertiesActivity::class.intent.setUUID(uuid))
            }
            AddProfileAction.Manually -> launch {
                val uuid = withProfile { create(Profile.Type.Url, getString(R.string.new_profile)) }
                startActivity(PropertiesActivity::class.intent.setUUID(uuid))
            }
            AddProfileAction.TvImport -> startActivity(TvImportActivity::class.intent)
        }
    }

    private fun navigate(target: SettingsNavTarget) {
        when (target) {
            SettingsNavTarget.Home -> Unit // already home
            SettingsNavTarget.Profiles -> startActivity(ProfilesActivity::class.intent)
            SettingsNavTarget.Settings -> {
                startActivity(SettingsActivity::class.intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun openUrl(url: String) {
        if (url.isEmpty()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
    }

    private suspend fun runUpdateCheckSilent() {
        when (val result = UpdateChecker.check(this)) {
            is UpdateChecker.CheckResult.UpdateAvailable ->
                UpdateChecker.showUpdateNotification(this, result.tagName, result.downloadUrl)
            else -> Unit
        }
    }

    private fun handleUpdateIntent(intent: Intent) {
        if (intent.action != UpdateChecker.ACTION_SHOW_UPDATE) return
        val tag = intent.getStringExtra(UpdateChecker.EXTRA_TAG) ?: return
        val url = intent.getStringExtra(UpdateChecker.EXTRA_URL) ?: return
        showUpdateAvailableDialog(tag, url)
    }

    private fun showUpdateAvailableDialog(tagName: String, downloadUrl: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(getString(R.string.update_available_message, tagName))
            .setPositiveButton(R.string.update_download) { _, _ ->
                UpdateChecker.startDownload(this, downloadUrl, tagName)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
                registerForActivityResult(RequestPermission()) { _: Boolean -> }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
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

    private fun useDrawerNav(): Boolean {
        if (TvUtils.isTv(this)) return true
        val cfg = resources.configuration
        return cfg.smallestScreenWidthDp >= 600 &&
            cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
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
