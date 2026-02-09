package com.github.kr328.clash

import android.content.ClipboardManager
import android.content.Context
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.ProfilesDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withProfile
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
    private val scanLauncher = registerForActivityResult(ScanQRCode()) { result ->
        lifecycleScope.launch {
            when (result) {
                is QRResult.QRSuccess -> {
                    val url = result.content.rawValue
                        ?: result.content.rawBytes?.let { String(it) }.orEmpty()
                    if (url.isNotEmpty()) {
                        val uuid = withProfile {
                            create(Profile.Type.Url, getString(R.string.new_profile)).also {
                                patch(it, getString(R.string.new_profile), url, 0)
                            }
                        }
                        startActivity(PropertiesActivity::class.intent.setUUID(uuid))
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

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ProfileChanged -> {
                            design.fetch()
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
                                val uuid = withProfile {
                                    create(Profile.Type.Url, getString(R.string.new_profile)).also {
                                        patch(it, getString(R.string.new_profile), clipText, 0)
                                    }
                                }
                                startActivity(PropertiesActivity::class.intent.setUUID(uuid))
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

    private suspend fun ProfilesDesign.fetch() {
        withProfile {
            patchProfiles(queryAll())
        }
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(R.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }
    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if(uuid == null)
            return;
        launch {
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