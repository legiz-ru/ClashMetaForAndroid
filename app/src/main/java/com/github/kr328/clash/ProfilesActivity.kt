package com.github.kr328.clash

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.AddProfileAction
import com.github.kr328.clash.design.compose.screen.ProfilesScreen
import com.github.kr328.clash.design.compose.screen.SettingsNavTarget
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.importProfileFromUrl
import com.github.kr328.clash.util.sendProfileToTv
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProfilesActivity : BaseActivity() {
    companion object {
        const val EXTRA_SCAN_QR_ON_START = "scan_qr_on_start"
    }

    private val profilesFlow = MutableStateFlow<List<Profile>>(emptyList())
    private val nowFlow = MutableStateFlow(System.currentTimeMillis())
    private val updatingFlow = MutableStateFlow<Set<UUID>>(emptySet())
    private val allUpdatingFlow = MutableStateFlow(false)
    private val runningFlow = MutableStateFlow(false)

    private val scanLauncher = registerForActivityResult(ScanQRCode()) { result ->
        lifecycleScope.launch {
            when (result) {
                is QRResult.QRSuccess -> {
                    val url = result.content.rawValue
                        ?: result.content.rawBytes?.let { String(it) }.orEmpty()
                    if (url.isNotEmpty()) {
                        if (url.contains("/Prizrak-BoxTVimport")) {
                            sendProfileToTv(url)
                        } else {
                            importProfileFromUrl(url)
                        }
                    }
                }
                QRResult.QRUserCanceled -> {}
                QRResult.QRMissingPermission ->
                    toast(R.string.import_from_qr_no_permission)
                is QRResult.QRError ->
                    toast(R.string.import_from_qr_exception)
            }
        }
    }

    override suspend fun main() {
        runningFlow.value = clashRunning

        setContent {
            val profiles by profilesFlow.collectAsStateWithLifecycle()
            val now by nowFlow.collectAsStateWithLifecycle()
            val updating by updatingFlow.collectAsStateWithLifecycle()
            val allUpdating by allUpdatingFlow.collectAsStateWithLifecycle()
            val running by runningFlow.collectAsStateWithLifecycle()

            ClashTheme(variant = currentThemeVariant()) {
                ProfilesScreen(
                    expanded = useDrawerNav(),
                    clashRunning = running,
                    isTv = TvUtils.isTv(this),
                    profiles = profiles,
                    now = now,
                    updatingUuids = updating,
                    allUpdating = allUpdating,
                    onBack = { finish() },
                    onUpdateAll = ::updateAll,
                    onAdd = ::add,
                    onProfileClick = ::activate,
                    onProfileUpdate = ::updateProfile,
                    onProfileAnnounce = ::showAnnounce,
                    onProfileSupport = { openUrl(it.supportUrl) },
                    onProfileWebPage = { openUrl(it.profileWebPageUrl) },
                    onProfileEdit = { startActivity(PropertiesActivity::class.intent.setUUID(it.uuid)) },
                    onProfileDelete = ::confirmDelete,
                    onNavigate = ::navigate,
                    onToggleStatus = ::toggleStatus,
                )
            }
        }

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart -> {
                            fetch()
                            if (intent.getBooleanExtra(EXTRA_SCAN_QR_ON_START, false)) {
                                intent.removeExtra(EXTRA_SCAN_QR_ON_START)
                                scanLauncher.launch(null)
                            }
                        }
                        Event.ProfileChanged -> fetch()
                        Event.ClashStart, Event.ClashStop -> runningFlow.value = clashRunning
                        else -> Unit
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        nowFlow.value = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    private suspend fun fetch() {
        withProfile { profilesFlow.value = queryAll() }
    }

    private fun updateAll() {
        if (allUpdatingFlow.value) {
            showAlreadyUpdatingDialog()
            return
        }
        allUpdatingFlow.value = true
        launch {
            withProfile {
                try {
                    queryAll().forEach { p ->
                        if (p.imported && p.type != Profile.Type.File) update(p.uuid)
                    }
                } finally {
                    allUpdatingFlow.value = false
                }
            }
        }
    }

    private fun updateProfile(profile: Profile) {
        if (profile.uuid in updatingFlow.value || allUpdatingFlow.value) {
            showAlreadyUpdatingDialog()
            return
        }
        updatingFlow.value = updatingFlow.value + profile.uuid
        launch { withProfile { update(profile.uuid) } }
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

    private fun activate(profile: Profile) {
        launch {
            if (profile.imported) {
                withProfile { setActive(profile) }
            } else {
                showUnsavedTips(profile)
            }
        }
    }

    private fun confirmDelete(profile: Profile) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.profile_delete_confirm, profile.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                launch { withProfile { delete(profile.uuid) } }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAnnounce(profile: Profile) {
        if (profile.announce.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(profile.name)
            .setMessage(profile.announce.replace("\\n", "\n"))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun openUrl(url: String) {
        if (url.isEmpty()) return
        try {
            startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            )
        } catch (_: Exception) {
        }
    }

    private fun navigate(target: SettingsNavTarget) {
        when (target) {
            SettingsNavTarget.Home -> {
                startActivity(
                    MainActivity::class.intent
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
                finish()
                overridePendingTransition(0, 0)
            }
            SettingsNavTarget.Settings -> startActivity(SettingsActivity::class.intent)
            SettingsNavTarget.Profiles -> Unit // already here
        }
    }

    private fun toggleStatus() {
        if (clashRunning) {
            stopClashService()
        } else {
            launch { toggleClashOn() }
        }
    }

    private suspend fun toggleClashOn() {
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

    private fun showUnsavedTips(profile: Profile) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.active_unsaved_tips)
            .setPositiveButton(R.string.edit) { _, _ ->
                startActivity(PropertiesActivity::class.intent.setUUID(profile.uuid))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAlreadyUpdatingDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_updating_title)
            .setMessage(R.string.profile_updating_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        super.onProfileUpdateCompleted(uuid)
        if (uuid != null) {
            updatingFlow.value = updatingFlow.value - uuid
        }
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if (uuid == null) return
        updatingFlow.value = updatingFlow.value - uuid
        launch {
            val name = withProfile { queryByUUID(uuid)?.name }
            MaterialAlertDialogBuilder(this@ProfilesActivity)
                .setMessage(getString(R.string.toast_profile_updated_failed, name, reason))
                .setPositiveButton(R.string.edit) { _, _ ->
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
                .setNegativeButton(R.string.ok, null)
                .show()
        }
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
