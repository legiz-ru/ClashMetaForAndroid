package com.github.kr328.clash

import android.text.InputType
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.core.bridge.TemplatesBridge
import com.github.kr328.clash.design.PropertiesDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.DialogTextFieldBinding
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.util.withProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

class PropertiesActivity : BaseActivity<PropertiesDesign>() {
    private var canceled: Boolean = false
    private lateinit var original: Profile

    override suspend fun main() {
        setResult(RESULT_CANCELED)

        val uuid = intent.uuid ?: return finish()
        val design = PropertiesDesign(this)

        original = withProfile { queryByUUID(uuid) } ?: return finish()

        design.profile = original
        design.selectedTemplateName = getTemplateDisplayName(readCurrentTemplateId(uuid))

        setContentDesign(design)

        defer {
            canceled = true

            withProfile { release(uuid) }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStop -> {
                            val profile = design.profile

                            if (!canceled && profile != original) {
                                withProfile {
                                    patch(profile.uuid, profile.name, profile.source, profile.interval)
                                }
                            }
                        }

                        Event.ServiceRecreated -> {
                            finish()
                        }

                        else -> Unit
                    }
                }

                design.requests.onReceive {
                    when (it) {
                        PropertiesDesign.Request.BrowseFiles -> {
                            startActivity(FilesActivity::class.intent.setUUID(uuid))
                        }

                        PropertiesDesign.Request.Commit -> {
                            design.verifyAndCommit()
                        }

                        PropertiesDesign.Request.SelectTemplate -> {
                            selectTemplate(uuid, design)
                        }

                        PropertiesDesign.Request.EditCustomTemplate -> {
                            editCustomTemplate(uuid)
                        }
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        design?.apply {
            launch {
                if (!progressing) {
                    if (original == profile || requestExitWithoutSaving())
                        finish()
                }
            }
        } ?: return super.onBackPressed()
    }

    private suspend fun PropertiesDesign.verifyAndCommit() {
        when {
            profile.name.isBlank() -> {
                showToast(R.string.empty_name, ToastDuration.Long)
            }

            profile.type != Profile.Type.File && profile.source.isBlank() -> {
                showToast(R.string.invalid_url, ToastDuration.Long)
            }

            else -> {
                try {
                    withProcessing { updateStatus ->
                        withProfile {
                            patch(profile.uuid, profile.name, profile.source, profile.interval)

                            coroutineScope {
                                commit(profile.uuid) {
                                    launch {
                                        updateStatus(it)
                                    }
                                }
                            }
                        }
                    }

                    // Auto-activate the imported profile
                    withProfile {
                        queryByUUID(profile.uuid)?.let { setActive(it) }
                    }

                    setResult(RESULT_OK)

                    finish()
                } catch (e: Exception) {
                    showExceptionToast(e)
                }
            }
        }
    }

    private suspend fun selectTemplate(uuid: UUID, design: PropertiesDesign) {
        val templates = TemplatesBridge.getAvailableTemplates()
        if (templates.isEmpty()) {
            showToast(R.string.error, ToastDuration.Long)
            return
        }

        val currentId = readCurrentTemplateId(uuid)
        val names = templates.map { it.name }.toTypedArray()
        val ids = templates.map { it.id }
        val selected = suspendCancellableCoroutine<String?> { cont ->
            var selectedIndex = ids.indexOf(currentId).coerceAtLeast(0)
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.template_select_title)
                .setSingleChoiceItems(names, selectedIndex) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton(R.string.ok) { _, _ ->
                    cont.resume(ids[selectedIndex])
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    cont.resume(null)
                }
                .setOnCancelListener {
                    if (!cont.isCompleted) cont.resume(null)
                }
                .create()

            cont.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
        } ?: return

        writeTemplateId(uuid, selected)
        design.selectedTemplateName = getTemplateDisplayName(selected)
        showToast(R.string.template_saved, ToastDuration.Short)
    }

    private suspend fun editCustomTemplate(uuid: UUID) {
        val profileDir = ensureWritableProfileDir(uuid)
        val customFile = profileDir.resolve("custom-template.yaml")
        if (!customFile.exists()) {
            val fallback = assets.open("templates/default.yaml").use { it.bufferedReader().readText() }
            customFile.writeText(fallback)
        }

        val edited = suspendCancellableCoroutine<String?> { cont ->
            val binding = DialogTextFieldBinding.inflate(layoutInflater, findViewById(android.R.id.content), false)
            binding.textLayout.hint = "YAML"
            binding.textField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            binding.textField.minLines = 12
            binding.textField.maxLines = 20
            binding.textField.setText(customFile.readText())
            binding.textField.setSelection(0)

            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_custom_template)
                .setView(binding.root)
                .setPositiveButton(R.string.ok) { _, _ ->
                    cont.resume(binding.textField.text?.toString())
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    cont.resume(null)
                }
                .setOnCancelListener {
                    if (!cont.isCompleted) cont.resume(null)
                }
                .create()

            cont.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
        } ?: return

        TemplatesBridge.validateTemplateYAML(edited)
        customFile.writeText(edited)
        showToast(R.string.custom_template_saved, ToastDuration.Short)
    }

    private suspend fun ensureWritableProfileDir(uuid: UUID) = withProfile {
        val profile = queryByUUID(uuid) ?: throw IllegalStateException("Profile not found")
        patch(profile.uuid, profile.name, profile.source, profile.interval)
        pendingDir.resolve(uuid.toString())
    }

    private suspend fun writeTemplateId(uuid: UUID, templateId: String) {
        val profileDir = ensureWritableProfileDir(uuid)
        profileDir.resolve("template.txt").writeText(templateId)
    }

    private fun readCurrentTemplateId(uuid: UUID): String {
        val pendingFile = pendingDir.resolve(uuid.toString()).resolve("template.txt")
        if (pendingFile.exists()) return pendingFile.readText().trim()

        val importedFile = importedDir.resolve(uuid.toString()).resolve("template.txt")
        if (importedFile.exists()) return importedFile.readText().trim()

        return "default"
    }

    private fun getTemplateDisplayName(templateId: String): String {
        return when (templateId) {
            "rubundle" -> getString(R.string.template_rubundle)
            "davoyan" -> getString(R.string.template_davoyan)
            "custom" -> getString(R.string.template_custom)
            else -> getString(R.string.template_default)
        }
    }
}
