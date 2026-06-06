package com.github.kr328.clash.design

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.design.adapter.ProxyLinkAdapter
import com.github.kr328.clash.design.databinding.DesignPropertiesBinding
import com.github.kr328.clash.design.dialog.ModelProgressBarConfigure
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.dialog.requestMultilineTextInput
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class PropertiesDesign(context: Context) : Design<PropertiesDesign.Request>(context) {
    sealed class Request {
        object Commit : Request()
        object BrowseFiles : Request()
        object SelectTemplate : Request()
        object ScanQrForLinks : Request()
    }

    private val binding = DesignPropertiesBinding
        .inflate(context.layoutInflater, context.root, false)

    private val proxyLinkAdapter = ProxyLinkAdapter(
        onEdit = { index, url -> launch { editProxyLink(index, url) } },
        onDelete = { index -> deleteProxyLink(index) },
    )

    override val root: View
        get() = binding.root

    var profile: Profile
        get() = binding.profile!!
        set(value) {
            binding.profile = value
            if (value.type == Profile.Type.Converted) {
                proxyLinkAdapter.links = parseLinks(value.source)
            }
            updateAgeKeyBtnTint(value.ageSecretKey)
        }

    val progressing: Boolean
        get() = binding.processing

    suspend fun withProcessing(executeTask: suspend (suspend (FetchStatus) -> Unit) -> Unit) {
        try {
            binding.processing = true

            context.withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = context.getString(R.string.initializing)
                }

                executeTask {
                    configure {
                        applyFrom(it)
                    }
                }
            }
        } finally {
            binding.processing = false
        }
    }

    suspend fun requestExitWithoutSaving(): Boolean {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { ctx ->
                val dialog = MaterialAlertDialogBuilder(context)
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

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        binding.proxyLinksRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = proxyLinkAdapter
        }
    }

    fun inputName() {
        if (profile.profileTitle.isNotEmpty()) return
        launch {
            val name = context.requestModelTextInput(
                initial = profile.name,
                title = context.getText(R.string.name),
                hint = context.getText(R.string.properties),
                error = context.getText(R.string.should_not_be_blank),
                validator = ValidatorNotBlank
            )

            if (name != profile.name) {
                profile = profile.copy(name = name)
            }
        }
    }

    fun inputUrl() {
        if (profile.type == Profile.Type.External)
            return

        launch {
            // Converted profiles accept proxy-link text or HTTP(S) URLs; everything else
            // requires a proper http/https URL.
            val validator = if (profile.type == Profile.Type.Converted) ValidatorNotBlank else ValidatorHttpUrl

            val url = context.requestModelTextInput(
                initial = profile.source,
                title = context.getText(R.string.url),
                hint = context.getText(if (profile.type == Profile.Type.Converted) R.string.converted_profile_hint else R.string.profile_url),
                error = context.getText(R.string.accept_http_content),
                validator = validator
            )

            if (url != profile.source) {
                profile = profile.copy(source = url)
            }
        }
    }

    fun inputInterval() {
        if (profile.profileUpdateInterval > 0) return
        launch {
            var minutes = TimeUnit.MILLISECONDS.toMinutes(profile.interval)

            minutes = context.requestModelTextInput(
                initial = if (minutes == 0L) "" else minutes.toString(),
                title = context.getText(R.string.auto_update),
                hint = context.getText(R.string.auto_update_minutes),
                error = context.getText(R.string.at_least_15_minutes),
                validator = ValidatorAutoUpdateInterval
            ).toLongOrNull() ?: 0

            val interval = TimeUnit.MINUTES.toMillis(minutes)

            if (interval != profile.interval) {
                profile = profile.copy(interval = interval)
            }
        }
    }

    fun addProxyLinks() {
        launch {
            val choice = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val options = arrayOf(
                        context.getString(R.string.paste_links),
                        context.getString(R.string.scan_qr_code),
                    )
                    val dlg = MaterialAlertDialogBuilder(context)
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
                1 -> requests.trySend(Request.ScanQrForLinks)
            }
        }
    }

    fun appendProxyLinksFromText(text: String) {
        val newLinks = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (newLinks.isNotEmpty()) {
            val combined = proxyLinkAdapter.links + newLinks
            profile = profile.copy(source = combined.joinToString("\n"))
        }
    }

    private suspend fun pasteProxyLinks() {
        val text = context.requestMultilineTextInput(
            initial = "",
            title = context.getText(R.string.add_proxy_links),
            hint = context.getText(R.string.add_proxy_links_hint),
        )
        appendProxyLinksFromText(text)
    }

    private suspend fun editProxyLink(index: Int, url: String) {
        val newUrl = context.requestModelTextInput(
            initial = url,
            title = context.getText(R.string.proxy_link_edit),
            hint = context.getText(R.string.proxy_link_edit_hint),
            error = context.getText(R.string.should_not_be_blank),
            validator = ValidatorNotBlank,
        )
        if (newUrl != url) {
            val updated = proxyLinkAdapter.links.toMutableList().also { it[index] = newUrl }
            profile = profile.copy(source = updated.joinToString("\n"))
        }
    }

    private fun deleteProxyLink(index: Int) {
        val updated = proxyLinkAdapter.links.toMutableList().also { it.removeAt(index) }
        profile = profile.copy(source = updated.joinToString("\n"))
    }

    private fun parseLinks(source: String): List<String> =
        source.lines().map { it.trim() }.filter { it.isNotBlank() }

    fun inputAgeKey() {
        launch {
            val key = context.requestModelTextInput(
                initial = profile.ageSecretKey,
                title = context.getText(R.string.age_secret_key),
                hint = context.getText(R.string.age_secret_key_hint),
                error = context.getText(R.string.age_secret_key_error),
                validator = ValidatorAgeSecretKey
            )

            if (key != profile.ageSecretKey) {
                profile = profile.copy(ageSecretKey = key)
            }
        }
    }

    private fun updateAgeKeyBtnTint(ageSecretKey: String) {
        val color = if (ageSecretKey.isNotEmpty()) {
            context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        } else {
            context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        }
        binding.ageKeyBtn.imageTintList = ColorStateList.valueOf(color)
    }

    fun requestCommit() {
        requests.trySend(Request.Commit)
    }

    fun requestBrowseFiles() {
        requests.trySend(Request.BrowseFiles)
    }

    fun requestSelectTemplate() {
        requests.trySend(Request.SelectTemplate)
    }

    private fun ModelProgressBarConfigure.applyFrom(status: FetchStatus) {
        when (status.action) {
            FetchStatus.Action.FetchConfiguration -> {
                text = context.getString(R.string.format_fetching_configuration, status.args[0])
                isIndeterminate = true
            }
            FetchStatus.Action.FetchProviders -> {
                text = context.getString(R.string.format_fetching_provider, status.args[0])
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
            FetchStatus.Action.FetchIcons -> {
                text = context.getString(R.string.fetching_icons)
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
            FetchStatus.Action.Verifying -> {
                text = context.getString(R.string.verifying)
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
        }
    }
}