package com.github.kr328.clash

import android.content.ClipboardManager
import android.content.Context
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.ProfilesDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.importProfileFromUrl
import com.github.kr328.clash.util.sendProfileToTv
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import androidx.activity.result.contract.ActivityResultContracts
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class ProfilesActivity : BaseActivity<ProfilesDesign>() {
    companion object {
        const val EXTRA_SCAN_QR_ON_START = "scan_qr_on_start"
    }

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
        val design = ProfilesDesign(this)

        setContentDesign(design)

        design.setClashRunning(clashRunning)

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart -> {
                            design.fetch()
                            if (intent.getBooleanExtra(EXTRA_SCAN_QR_ON_START, false)) {
                                intent.removeExtra(EXTRA_SCAN_QR_ON_START)
                                scanLauncher.launch(null)
                            }
                        }
                        Event.ProfileChanged -> {
                            design.fetch()
                        }
                        Event.ClashStart, Event.ClashStop -> {
                            design.setClashRunning(clashRunning)
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProfilesDesign.Request.Create -> {}
                        ProfilesDesign.Request.AddFromClipboard -> {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                            if (clipText.isNotEmpty()) {
                                importProfileFromUrl(clipText)
                            } else {
                                design.showToast(R.string.empty_clipboard, ToastDuration.Long)
                            }
                        }
                        ProfilesDesign.Request.ScanQrCode -> {
                            scanLauncher.launch(null)
                        }
                        ProfilesDesign.Request.AddFromFile -> {
                            val uuid = withProfile {
                                create(Profile.Type.File, getString(R.string.new_profile))
                            }
                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                        ProfilesDesign.Request.AddManually -> {
                            val uuid = withProfile {
                                create(Profile.Type.Url, getString(R.string.new_profile))
                            }
                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                        ProfilesDesign.Request.UpdateAll ->
                            withProfile {
                                try {
                                    queryAll().forEach { p ->
                                        if (p.imported && p.type != Profile.Type.File)
                                            update(p.uuid)
                                    }
                                }
                                finally {
                                    withContext(Dispatchers.Main) {
                                        design.finishUpdateAll();
                                    }
                                }
                            }
                        is ProfilesDesign.Request.Update ->
                            withProfile { update(it.profile.uuid) }
                        is ProfilesDesign.Request.Delete ->
                            withProfile { delete(it.profile.uuid) }
                        is ProfilesDesign.Request.Edit ->
                            startActivity(PropertiesActivity::class.intent.setUUID(it.profile.uuid))
                        is ProfilesDesign.Request.Active -> {
                            withProfile {
                                if (it.profile.imported)
                                    setActive(it.profile)
                                else
                                    design.requestSave(it.profile)
                            }
                        }
                        is ProfilesDesign.Request.Duplicate -> {
                            val uuid = withProfile { clone(it.profile.uuid) }

                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                        is ProfilesDesign.Request.OpenUrl -> {
                            try {
                                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it.url)))
                            } catch (_: Exception) {}
                        }
                        is ProfilesDesign.Request.ShowAnnounce -> {
                            design.showAnnounceSheet(it.profile)
                        }
                        ProfilesDesign.Request.GoHome -> {
                            startActivity(
                                MainActivity::class.intent
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            )
                            finish()
                            overridePendingTransition(0, 0)
                        }
                        ProfilesDesign.Request.OpenSettings ->
                            startActivity(SettingsActivity::class.intent)
                        ProfilesDesign.Request.TvImport ->
                            startActivity(TvImportActivity::class.intent)
                        ProfilesDesign.Request.ToggleStatus -> {
                            if (clashRunning) {
                                stopClashService()
                            } else {
                                toggleClashOn(design)
                            }
                        }
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    private suspend fun toggleClashOn(design: ProfilesDesign) {
        val active = withProfile { queryActive() }
        if (active == null || !active.imported) {
            design.showToast(R.string.no_profile_selected, ToastDuration.Long)
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
            design.showToast(R.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    private suspend fun ProfilesDesign.fetch() {
        withProfile {
            patchProfiles(queryAll())
        }
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        super.onProfileUpdateCompleted(uuid)
        if (uuid != null) {
            launch { design?.markProfileFinished(uuid) }
        }
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if(uuid == null)
            return;
        launch {
            design?.markProfileFinished(uuid)
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(R.string.toast_profile_updated_failed, name, reason),
                ToastDuration.Long
            ){
                setAction(R.string.edit) {
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
            }
        }
    }
}
