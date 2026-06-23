package com.github.kr328.clash

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.PropertiesScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.dialog.ModelProgressBarConfigure
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.dialog.requestMultilineTextInput
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.util.ValidatorAgeSecretKey
import com.github.kr328.clash.design.util.ValidatorAutoUpdateInterval
import com.github.kr328.clash.design.util.ValidatorHttpUrl
import com.github.kr328.clash.design.util.ValidatorNotBlank
import com.github.kr328.clash.service.ProfileProcessor
import com.github.kr328.clash.service.TemplateManager
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.util.GetContentCompat
import com.github.kr328.clash.util.withProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class PropertiesActivity : BaseActivity() {
    private var canceled: Boolean = false
    private lateinit var original: Profile

    private val profileFlow = MutableStateFlow<Profile?>(null)
    private val proxyLinksFlow = MutableStateFlow<List<String>>(emptyList())
    private val processingFlow = MutableStateFlow(false)

    private val profile: Profile
        get() = profileFlow.value!!

    private val qrScanLauncher = registerForActivityResult(ScanQRCode()) { result ->
        when (result) {
            is QRResult.QRSuccess -> {
                val text = result.content.rawValue?.trim() ?: return@registerForActivityResult
                if (text.isNotEmpty()) appendProxyLinksFromText(text)
            }
            else -> Unit
        }
    }

    override suspend fun main() {
        setResult(RESULT_CANCELED)

        val uuid = intent.uuid ?: return finish()

        original = withProfile { queryByUUID(uuid) } ?: return finish()

        updateProfile(original)

        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                val current by profileFlow.collectAsStateWithLifecycle()
                val proxyLinks by proxyLinksFlow.collectAsStateWithLifecycle()
                val processing by processingFlow.collectAsStateWithLifecycle()
                current?.let { p ->
                    PropertiesScreen(
                        profile = p,
                        proxyLinks = proxyLinks,
                        processing = processing,
                        onBack = { onBackPressed() },
                        onSave = { launch { verifyAndCommit() } },
                        onEditName = { launch { inputName() } },
                        onEditUrl = { launch { inputUrl() } },
                        onEditInterval = { launch { inputInterval() } },
                        onEditAgeKey = { launch { inputAgeKey() } },
                        onBrowseFiles = { startActivity(FilesActivity::class.intent.setUUID(uuid)) },
                        onSelectTemplate = { launch { selectAndApplyTemplate() } },
                        onAddProxyLinks = { launch { addProxyLinks() } },
                        onEditProxyLink = { index, url -> launch { editProxyLink(index, url) } },
                        onDeleteProxyLink = { index -> deleteProxyLink(index) },
                    )
                }
            }
        }

        defer {
            canceled = true

            withProfile { release(uuid) }
        }

        while (isActive) {
            when (events.receive()) {
                Event.ActivityStop -> {
                    if (!canceled && profile != original) {
                        withProfile {
                            patch(profile.uuid, profile.name, profile.source, profile.interval, profile.ageSecretKey)
                        }
                    }
                }
                Event.ServiceRecreated -> finish()
                else -> Unit
            }
        }
    }

    override fun onBackPressed() {
        if (profileFlow.value == null) return super.onBackPressed()
        launch {
            if (!processingFlow.value) {
                if (original == profile || requestExitWithoutSaving())
                    finish()
            }
        }
    }

    private fun updateProfile(value: Profile) {
        profileFlow.value = value
        if (value.type == Profile.Type.Converted) {
            proxyLinksFlow.value = parseLinks(value.source)
        }
    }

    private fun parseLinks(source: String): List<String> =
        source.lines().map { it.trim() }.filter { it.isNotBlank() }

    private suspend fun inputName() {
        if (profile.profileTitle.isNotEmpty()) return
        val name = requestModelTextInput(
            initial = profile.name,
            title = getText(R.string.name),
            hint = getText(R.string.properties),
            error = getText(R.string.should_not_be_blank),
            validator = ValidatorNotBlank,
        )
        if (name != profile.name) {
            updateProfile(profile.copy(name = name))
        }
    }

    private suspend fun inputUrl() {
        if (profile.type == Profile.Type.External) return

        // Converted profiles accept proxy-link text or HTTP(S) URLs; everything else
        // requires a proper http/https URL.
        val validator = if (profile.type == Profile.Type.Converted) ValidatorNotBlank else ValidatorHttpUrl

        val url = requestModelTextInput(
            initial = profile.source,
            title = getText(R.string.url),
            hint = getText(if (profile.type == Profile.Type.Converted) R.string.converted_profile_hint else R.string.profile_url),
            error = getText(R.string.accept_http_content),
            validator = validator,
        )
        if (url != profile.source) {
            updateProfile(profile.copy(source = url))
        }
    }

    private suspend fun inputInterval() {
        if (profile.profileUpdateInterval > 0) return
        var minutes = TimeUnit.MILLISECONDS.toMinutes(profile.interval)

        minutes = requestModelTextInput(
            initial = if (minutes == 0L) "" else minutes.toString(),
            title = getText(R.string.auto_update),
            hint = getText(R.string.auto_update_minutes),
            error = getText(R.string.at_least_15_minutes),
            validator = ValidatorAutoUpdateInterval,
        ).toLongOrNull() ?: 0

        val interval = TimeUnit.MINUTES.toMillis(minutes)
        if (interval != profile.interval) {
            updateProfile(profile.copy(interval = interval))
        }
    }

    private suspend fun inputAgeKey() {
        val key = requestModelTextInput(
            initial = profile.ageSecretKey,
            title = getText(R.string.age_secret_key),
            hint = getText(R.string.age_secret_key_hint),
            error = getText(R.string.age_secret_key_error),
            validator = ValidatorAgeSecretKey,
        )
        if (key != profile.ageSecretKey) {
            updateProfile(profile.copy(ageSecretKey = key))
        }
    }

    private suspend fun addProxyLinks() {
        val choice = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val options = arrayOf(
                    getString(R.string.paste_links),
                    getString(R.string.scan_qr_code),
                )
                val dlg = MaterialAlertDialogBuilder(this@PropertiesActivity)
                    .setTitle(R.string.add_proxy_links)
                    .setItems(options) { _, which -> if (!cont.isCompleted) cont.resume(which) }
                    .setNegativeButton(R.string.cancel) { _, _ -> if (!cont.isCompleted) cont.resume(-1) }
                    .setOnDismissListener { if (!cont.isCompleted) cont.resume(-1) }
                    .show()
                cont.invokeOnCancellation { dlg.dismiss() }
            }
        }
        when (choice) {
            0 -> pasteProxyLinks()
            1 -> qrScanLauncher.launch(null)
        }
    }

    private fun appendProxyLinksFromText(text: String) {
        val newLinks = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (newLinks.isNotEmpty()) {
            val combined = proxyLinksFlow.value + newLinks
            updateProfile(profile.copy(source = combined.joinToString("\n")))
        }
    }

    private suspend fun pasteProxyLinks() {
        val text = requestMultilineTextInput(
            initial = "",
            title = getText(R.string.add_proxy_links),
            hint = getText(R.string.add_proxy_links_hint),
        )
        appendProxyLinksFromText(text)
    }

    private suspend fun editProxyLink(index: Int, url: String) {
        val newUrl = requestModelTextInput(
            initial = url,
            title = getText(R.string.proxy_link_edit),
            hint = getText(R.string.proxy_link_edit_hint),
            error = getText(R.string.should_not_be_blank),
            validator = ValidatorNotBlank,
        )
        if (newUrl != url) {
            val updated = proxyLinksFlow.value.toMutableList().also { it[index] = newUrl }
            updateProfile(profile.copy(source = updated.joinToString("\n")))
        }
    }

    private fun deleteProxyLink(index: Int) {
        val updated = proxyLinksFlow.value.toMutableList().also { it.removeAt(index) }
        updateProfile(profile.copy(source = updated.joinToString("\n")))
    }

    private suspend fun requestExitWithoutSaving(): Boolean {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { ctx ->
                val dialog = MaterialAlertDialogBuilder(this@PropertiesActivity)
                    .setTitle(R.string.exit_without_save)
                    .setMessage(R.string.exit_without_save_warning)
                    .setCancelable(true)
                    .setPositiveButton(R.string.ok) { _, _ -> ctx.resume(true) }
                    .setNegativeButton(R.string.cancel) { _, _ -> }
                    .setOnDismissListener { if (!ctx.isCompleted) ctx.resume(false) }
                    .show()

                ctx.invokeOnCancellation { dialog.dismiss() }
            }
        }
    }

    private suspend fun withProcessing(executeTask: suspend (suspend (FetchStatus) -> Unit) -> Unit) {
        try {
            processingFlow.value = true

            withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = getString(R.string.initializing)
                }

                executeTask {
                    configure {
                        applyFrom(it)
                    }
                }
            }
        } finally {
            processingFlow.value = false
        }
    }

    /**
     * Shows a template-selection dialog for Converted profiles, writes the chosen template id
     * to the pending profile directory, then re-commits with the new template.
     */
    private suspend fun selectAndApplyTemplate() {
        if (profile.type != Profile.Type.Converted) return

        // Ensure a pending record and directory exist (creates one from the imported profile if absent).
        withProfile { patch(profile.uuid, profile.name, profile.source, profile.interval, profile.ageSecretKey) }

        val pendingProfileDir = pendingDir.resolve(profile.uuid.toString())
        val currentTemplateId = TemplateManager.getSelectedTemplateId(pendingProfileDir)
        val pxaTemplateUrl = TemplateManager.getPxaTemplateUrl(pendingProfileDir)

        // Build flat lists of ids and display names.
        // "Шаблон из подписки" is prepended when a pxa-template URL is available.
        val templateIds = mutableListOf<String>()
        val displayNames = mutableListOf<String>()

        if (!pxaTemplateUrl.isNullOrBlank()) {
            templateIds.add(TemplateManager.PXA_SUBSCRIPTION_TEMPLATE_ID)
            displayNames.add(getString(R.string.template_pxa_subscription))
        }

        val builtinTemplates = TemplateManager.Template.entries.toList()
        builtinTemplates.forEach { t ->
            templateIds.add(t.id)
            displayNames.add(getString(
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
            val dialog = MaterialAlertDialogBuilder(this)
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
                val uri = startActivityForResult(GetContentCompat(), "*/*")
                if (uri == null) return // User cancelled the file picker
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                if (content.isNullOrBlank()) return
                val customFile = filesDir.resolve("custom_template.yaml")
                withContext(Dispatchers.IO) { customFile.writeText(content, Charsets.UTF_8) }
                TemplateManager.setCustomTemplatePath(this, customFile.absolutePath)
            }

            TemplateManager.saveSelectedTemplateId(pendingProfileDir, selectedTemplate.id)
        }

        // Re-commit so the new template is applied.
        verifyAndCommit()
    }

    private suspend fun verifyAndCommit() {
        when {
            profile.name.isBlank() -> {
                Toast.makeText(this, R.string.empty_name, Toast.LENGTH_LONG).show()
            }
            profile.type != Profile.Type.File && profile.source.isBlank() -> {
                Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_LONG).show()
            }
            else -> {
                try {
                    withProcessing { updateStatus ->
                        withProfile {
                            patch(profile.uuid, profile.name, profile.source, profile.interval, profile.ageSecretKey)

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
                    if (isAgeKeyRequired(e)) {
                        showAgeKeyRequiredDialog()
                    } else {
                        val (issue, supportUrl) = resolveHwidIssue(e)
                        when (issue) {
                            HwidIssue.NotSupported -> showHwidNotSupportedDialog()
                            HwidIssue.MaxDevicesReached -> showHwidMaxDevicesDialog(supportUrl)
                            HwidIssue.None -> Toast.makeText(this, e.message ?: "Unknown", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private enum class HwidIssue {
        None,
        NotSupported,
        MaxDevicesReached,
    }

    private fun isAgeKeyRequired(exception: Throwable): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (current is ProfileProcessor.AgeKeyRequiredException) return true
            if (current.message.orEmpty().contains("AGE_KEY_REQUIRED", ignoreCase = true)) return true
            current = current.cause
        }
        return false
    }

    private suspend fun showAgeKeyRequiredDialog() {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val dialog = MaterialAlertDialogBuilder(this@PropertiesActivity)
                    .setTitle(R.string.age_key_required_title)
                    .setMessage(R.string.age_key_required_msg)
                    .setPositiveButton(R.string.ok) { _, _ -> if (cont.isActive) cont.resume(Unit) }
                    .setOnCancelListener { if (cont.isActive) cont.resume(Unit) }
                    .show()
                cont.invokeOnCancellation { dialog.dismiss() }
            }
        }
    }

    private fun resolveHwidIssue(exception: Throwable): Pair<HwidIssue, String> {
        var current: Throwable? = exception

        while (current != null) {
            when (current) {
                is ProfileProcessor.HwidNotSupportedException -> return HwidIssue.NotSupported to ""
                is ProfileProcessor.HwidMaxDevicesReachedException -> return HwidIssue.MaxDevicesReached to current.supportUrl
            }

            val message = current.message.orEmpty()
            if (message.contains("HWID_NOT_SUPPORTED", ignoreCase = true)) {
                return HwidIssue.NotSupported to ""
            }

            if (message.contains("HWID_MAX_DEVICES_REACHED", ignoreCase = true)) {
                return HwidIssue.MaxDevicesReached to ""
            }

            current = current.cause
        }

        return HwidIssue.None to ""
    }

    private suspend fun showHwidNotSupportedDialog() {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val dialog = MaterialAlertDialogBuilder(this@PropertiesActivity)
                    .setTitle(R.string.hwid_not_supported_title)
                    .setMessage(R.string.hwid_not_supported_msg)
                    .setPositiveButton(R.string.ok) { _, _ -> cont.resume(Unit) }
                    .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(Unit) }
                    .setOnCancelListener { if (cont.isActive) cont.resume(Unit) }
                    .show()

                cont.invokeOnCancellation { dialog.dismiss() }
            }
        }
    }

    private suspend fun showHwidMaxDevicesDialog(supportUrl: String) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val builder = MaterialAlertDialogBuilder(this@PropertiesActivity)
                    .setTitle(R.string.hwid_max_devices_title)
                    .setMessage(R.string.hwid_max_devices_msg)
                    .setPositiveButton(R.string.ok) { _, _ -> cont.resume(Unit) }
                    .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(Unit) }
                    .setOnCancelListener { if (cont.isActive) cont.resume(Unit) }
                if (supportUrl.isNotEmpty()) {
                    builder.setNeutralButton(R.string.hwid_support_btn) { _, _ ->
                        cont.resume(Unit)
                        try {
                            startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {}
                    }
                }
                val dialog = builder.show()
                cont.invokeOnCancellation { dialog.dismiss() }
            }
        }
    }

    private fun ModelProgressBarConfigure.applyFrom(status: FetchStatus) {
        when (status.action) {
            FetchStatus.Action.FetchConfiguration -> {
                text = getString(R.string.format_fetching_configuration, status.args[0])
                isIndeterminate = true
            }
            FetchStatus.Action.FetchProviders -> {
                text = getString(R.string.format_fetching_provider, status.args[0])
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
            FetchStatus.Action.FetchIcons -> {
                text = getString(R.string.fetching_icons)
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
            FetchStatus.Action.Verifying -> {
                text = getString(R.string.verifying)
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
        }
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
