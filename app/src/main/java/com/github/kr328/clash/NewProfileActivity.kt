package com.github.kr328.clash

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.github.kr328.clash.core.bridge.TemplatesBridge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.NewProfileDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withProfile
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.QRResult.QRError
import io.github.g00fy2.quickie.QRResult.QRMissingPermission
import io.github.g00fy2.quickie.QRResult.QRSuccess
import io.github.g00fy2.quickie.QRResult.QRUserCanceled
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume

class NewProfileActivity : BaseActivity<NewProfileDesign>() {
    private val self: NewProfileActivity
        get() = this

    private val scanLauncher = registerForActivityResult(ScanQRCode(), ::scanResultHandler)

    override suspend fun main() {
        val design = NewProfileDesign(this)

        design.patchProviders(queryProfileProviders())

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        is NewProfileDesign.Request.Create -> {
                            withProfile {
                                val name = getString(R.string.new_profile)

                                val uuid: UUID? = when (val p = it.provider) {
                                    is ProfileProvider.File ->
                                        create(Profile.Type.File, name)

                                    is ProfileProvider.Url ->
                                        create(Profile.Type.Url, name)

                                    is ProfileProvider.QR -> {
                                        null
                                    }

                                    is ProfileProvider.External -> {
                                        val data = p.get()

                                        if (data != null) {
                                            val (uri, initialName) = data

                                            create(
                                                Profile.Type.External,
                                                initialName ?: name,
                                                uri.toString()
                                            )
                                        } else {
                                            null
                                        }
                                    }
                                }

                                if (uuid != null) {
                                    val templateId = requestTemplateSelection()
                                    if (templateId != null) {
                                        writeInitialTemplate(uuid, templateId)
                                    }
                                    launchProperties(uuid)
                                }
                            }
                        }

                        is NewProfileDesign.Request.OpenDetail -> {
                            launchAppDetailed(it.provider)
                        }

                        is NewProfileDesign.Request.LaunchScanner -> {
                            scanLauncher.launch(null)
                        }
                    }
                }
            }
        }
    }



    private suspend fun requestTemplateSelection(): String? {
        val templates = TemplatesBridge.getAvailableTemplates()
        if (templates.isEmpty()) {
            design?.showExceptionToast(getString(R.string.error))
            return null
        }

        val names = templates.map { it.name }.toTypedArray()
        val ids = templates.map { it.id }

        return kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
            var selected = 0
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.template_select_title)
                .setSingleChoiceItems(names, 0) { _, which -> selected = which }
                .setPositiveButton(R.string.ok) { _, _ -> cont.resume(ids[selected]) }
                .setNegativeButton(R.string.cancel) { _, _ -> cont.resume("default") }
                .setOnCancelListener { if (!cont.isCompleted) cont.resume("default") }
                .create()

            cont.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
        }
    }

    private fun writeInitialTemplate(uuid: UUID, templateId: String) {
        val profileDir = filesDir.resolve("pending").resolve(uuid.toString())
        profileDir.mkdirs()
        profileDir.resolve("template.txt").writeText(templateId)

        if (templateId == "custom") {
            val customFile = profileDir.resolve("custom-template.yaml")
            if (!customFile.exists()) {
                val fallback = assets.open("templates/default.yaml").use { it.bufferedReader().readText() }
                customFile.writeText(fallback)
            }
        }
    }
    private fun launchAppDetailed(provider: ProfileProvider.External) {
        val data = Uri.fromParts(
            "package",
            provider.intent.component?.packageName ?: return,
            null
        )

        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(data))
    }

    private suspend fun launchProperties(uuid: UUID) {
        val r = startActivityForResult(
            ActivityResultContracts.StartActivityForResult(),
            PropertiesActivity::class.intent.setUUID(uuid)
        )

        if (r.resultCode == Activity.RESULT_OK)
            finish()
    }

    private suspend fun ProfileProvider.External.get(): Pair<Uri, String?>? {
        val result = startActivityForResult(
            ActivityResultContracts.StartActivityForResult(),
            intent
        )

        if (result.resultCode != RESULT_OK)
            return null

        val uri = result.data?.data
        val name = result.data?.getStringExtra(Intents.EXTRA_NAME)

        if (uri != null) {
            return uri to name
        }

        return null
    }

    private suspend fun queryProfileProviders(): List<ProfileProvider> {
        return withContext(Dispatchers.IO) {
            val providers = packageManager.queryIntentActivities(
                Intent(Intents.ACTION_PROVIDE_URL),
                0
            ).map {
                val activity = it.activityInfo

                val name = activity.applicationInfo.loadLabel(packageManager)
                val summary = activity.loadLabel(packageManager)
                val icon = activity.loadIcon(packageManager)
                val intent = Intent(Intents.ACTION_PROVIDE_URL)
                    .setComponent(
                        ComponentName(
                            activity.packageName,
                            activity.name
                        )
                    )

                ProfileProvider.External(name.toString(), summary.toString(), icon, intent)
            }

            listOf(
                ProfileProvider.File(self),
                ProfileProvider.Url(self),
                ProfileProvider.QR(self)
            ) + providers
        }
    }

    private fun scanResultHandler(result: QRResult) {
        lifecycleScope.launch {
            when (result) {
                is QRSuccess -> {
                    val url = result.content.rawValue
                        ?: result.content.rawBytes?.let { String(it) }.orEmpty()

                    createProfileByQrCode(url)
                }

                QRUserCanceled -> {}
                QRMissingPermission -> design?.showExceptionToast(getString(R.string.import_from_qr_no_permission))
                is QRError -> design?.showExceptionToast(getString(R.string.import_from_qr_exception))
            }
        }
    }

    private suspend fun createProfileByQrCode(url: String) {
        withProfile {
            val uuid = create(
                type = Profile.Type.Url,
                name = getString(R.string.new_profile),
                url,
            )

            val templateId = requestTemplateSelection() ?: "default"
            writeInitialTemplate(uuid, templateId)

            launchProperties(uuid)
        }
    }

}
