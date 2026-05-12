package com.github.kr328.clash

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.databinding.DialogWhitelistBypassBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.service.bypass.BypassPlatform
import com.github.kr328.clash.service.bypass.BypassRelayController
import com.github.kr328.clash.service.bypass.BypassStatus
import com.github.kr328.clash.service.bypass.parseBypassProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

class BypassTunnelActivity : BaseActivity<BypassTunnelActivity.EmptyDesign>() {

    class EmptyDesign(context: android.content.Context) : Design<Nothing>(context) {
        override val root: View = View(context)
    }

    companion object {
        const val EXTRA_PROFILE_UUID = "profile_uuid"
    }

    private var controller: BypassRelayController? = null

    override suspend fun main() {
        val uuidStr = intent.getStringExtra(EXTRA_PROFILE_UUID) ?: return finish()
        val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: return finish()

        val binding = withContext(Dispatchers.Main) {
            DialogWhitelistBypassBinding.inflate(LayoutInflater.from(this@BypassTunnelActivity))
        }

        withContext(Dispatchers.Main) {
            binding.platformWbstream.isChecked = true
            updateStatus(binding, BypassStatus.IDLE)

            binding.btnStart.setOnClickListener {
                val joinLink = binding.joinLinkInput.text?.toString()?.trim() ?: ""
                if (joinLink.isEmpty()) {
                    Toast.makeText(
                        this@BypassTunnelActivity,
                        getString(com.github.kr328.clash.design.R.string.whitelist_bypass_join_link_hint),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                launch {
                    val proxyConfig = withContext(Dispatchers.IO) {
                        parseBypassProxyConfig(this@BypassTunnelActivity, uuid)
                    }
                    if (proxyConfig == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@BypassTunnelActivity,
                                getString(com.github.kr328.clash.design.R.string.whitelist_bypass_proxy_not_found),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }
                    val platform = withContext(Dispatchers.Main) { selectedPlatform(binding, joinLink) }
                    stopController()
                    controller = BypassRelayController(
                        context = this@BypassTunnelActivity,
                        proxyConfig = proxyConfig,
                        onStatus = { status -> runOnUiThread { updateStatus(binding, status) } },
                        onLog = { line -> runOnUiThread { binding.logText = line } },
                        onCaptchaUrl = { url -> runOnUiThread { openCaptchaUrl(url) } }
                    ).also { ctrl ->
                        ctrl.start(platform)
                        ctrl.sendAuth(joinLink)
                    }
                }
            }

            binding.btnStop.setOnClickListener {
                stopController()
                updateStatus(binding, BypassStatus.IDLE)
            }

            suspendCancellableCoroutine { cont ->
                val dialog = AppBottomSheetDialog(this@BypassTunnelActivity).apply {
                    setContentView(binding.root)
                    setOnDismissListener {
                        stopController()
                        if (cont.isActive) cont.resume(Unit)
                    }
                    show()
                }
                cont.invokeOnCancellation { dialog.dismiss() }
            }
        }

        finish()
    }

    private fun selectedPlatform(binding: DialogWhitelistBypassBinding, joinLink: String): BypassPlatform {
        return when {
            binding.platformWbstream.isChecked -> BypassPlatform.WBSTREAM
            binding.platformTelemost.isChecked -> BypassPlatform.TELEMOST
            binding.platformVk.isChecked -> BypassPlatform.VK
            else -> BypassPlatform.fromUrl(joinLink)
        }
    }

    private fun updateStatus(binding: DialogWhitelistBypassBinding, status: BypassStatus) {
        val (label, color) = when (status) {
            BypassStatus.IDLE       -> getString(com.github.kr328.clash.design.R.string.whitelist_bypass_status_idle)       to Color.GRAY
            BypassStatus.STARTING   -> getString(com.github.kr328.clash.design.R.string.whitelist_bypass_status_starting)   to Color.parseColor("#FF9800")
            BypassStatus.CONNECTING -> getString(com.github.kr328.clash.design.R.string.whitelist_bypass_status_connecting) to Color.parseColor("#2196F3")
            BypassStatus.CONNECTED  -> getString(com.github.kr328.clash.design.R.string.whitelist_bypass_status_connected)  to Color.parseColor("#4CAF50")
            BypassStatus.LOST       -> getString(com.github.kr328.clash.design.R.string.whitelist_bypass_status_lost)       to Color.parseColor("#F44336")
            BypassStatus.ERROR      -> getString(com.github.kr328.clash.design.R.string.whitelist_bypass_status_error)      to Color.parseColor("#F44336")
        }
        binding.status = label
        binding.statusColor = color
        binding.btnStart.isEnabled = status in listOf(BypassStatus.IDLE, BypassStatus.LOST, BypassStatus.ERROR)
        binding.btnStop.isEnabled = status != BypassStatus.IDLE
    }

    private fun openCaptchaUrl(url: String) {
        runCatching {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }
    }

    private fun stopController() {
        controller?.stop()
        controller = null
    }

    override fun onDestroy() {
        stopController()
        super.onDestroy()
    }
}
