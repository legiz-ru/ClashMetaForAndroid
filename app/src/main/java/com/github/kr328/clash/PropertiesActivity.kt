package com.github.kr328.clash

import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.design.PropertiesDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.TemplateManager
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.util.withProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.github.kr328.clash.design.R
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
                            design.selectAndApplyTemplate()
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

    /**
     * Shows a template-selection dialog for Converted profiles, writes the chosen template id
     * to the pending profile directory, then re-commits with the new template.
     */
    private suspend fun PropertiesDesign.selectAndApplyTemplate() {
        if (profile.type != Profile.Type.Converted) return

        // Ensure a pending record and directory exist (creates one from the imported profile if absent).
        withProfile { patch(profile.uuid, profile.name, profile.source, profile.interval) }

        val pendingProfileDir = pendingDir.resolve(profile.uuid.toString())
        val currentTemplateId = TemplateManager.getSelectedTemplateId(pendingProfileDir)
        val pxaTemplateUrl = TemplateManager.getPxaTemplateUrl(pendingProfileDir)

        // Build flat lists of ids and display names.
        // "Шаблон из подписки" is prepended when a pxa-template URL is available.
        val templateIds = mutableListOf<String>()
        val displayNames = mutableListOf<String>()

        if (!pxaTemplateUrl.isNullOrBlank()) {
            templateIds.add(TemplateManager.PXA_SUBSCRIPTION_TEMPLATE_ID)
            displayNames.add(context.getString(R.string.template_pxa_subscription))
        }

        val builtinTemplates = TemplateManager.Template.entries.toList()
        builtinTemplates.forEach { t ->
            templateIds.add(t.id)
            displayNames.add(context.getString(
                when (t) {
                    TemplateManager.Template.Default       -> R.string.template_default
                    TemplateManager.Template.RuBundle      -> R.string.template_ru_bundle
                    TemplateManager.Template.Ultimate      -> R.string.template_ultimate
                    TemplateManager.Template.DefaultSmart  -> R.string.template_default_smart
                    TemplateManager.Template.RuBundleSmart -> R.string.template_ru_bundle_smart
                    TemplateManager.Template.UltimateSmart -> R.string.template_ultimate_smart
                    TemplateManager.Template.ChinaSmart    -> R.string.template_china_smart
                    TemplateManager.Template.Custom        -> R.string.template_custom
                }
            ))
        }

        val currentIndex = templateIds.indexOfFirst { it == currentTemplateId }.coerceAtLeast(0)

        val selectedIndex = suspendCancellableCoroutine<Int?> { continuation ->
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.select_template)
                .setSingleChoiceItems(displayNames.toTypedArray(), currentIndex) { dlg, which ->
                    dlg.dismiss()
                    continuation.resume(which)
                }
                .setNegativeButton(R.string.cancel) { _, _ -> continuation.resume(null) }
                .setOnCancelListener { if (!continuation.isCompleted) continuation.resume(null) }
                .show()
            continuation.invokeOnCancellation { dialog.dismiss() }
        } ?: return

        val selectedId = templateIds[selectedIndex]

        // "Шаблон из подписки" — just save the special id; pxa URL is already in meta.
        if (selectedId == TemplateManager.PXA_SUBSCRIPTION_TEMPLATE_ID) {
            TemplateManager.saveSelectedTemplateId(pendingProfileDir, selectedId)
        } else {
            val selectedTemplate = builtinTemplates.first { it.id == selectedId }

            // For Custom template, let the user pick a YAML file to use as the template.
            if (selectedTemplate == TemplateManager.Template.Custom) {
                val uri = this@PropertiesActivity.startActivityForResult(
                    ActivityResultContracts.GetContent(), "*/*"
                )
                if (uri == null) return // User cancelled the file picker
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                if (content.isNullOrBlank()) return
                val customFile = context.filesDir.resolve("custom_template.yaml")
                withContext(Dispatchers.IO) { customFile.writeText(content, Charsets.UTF_8) }
                TemplateManager.setCustomTemplatePath(context, customFile.absolutePath)
            }

            TemplateManager.saveSelectedTemplateId(pendingProfileDir, selectedTemplate.id)
        }

        // Re-commit so the new template is applied.
        verifyAndCommit()
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
}