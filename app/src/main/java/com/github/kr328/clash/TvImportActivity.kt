package com.github.kr328.clash

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.compose.screen.TvImportScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.tv.TvImportServer
import com.github.kr328.clash.util.importProfileFromUrl
import com.github.kr328.clash.util.withProfile
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Displays a QR code pointing at the local TvImportServer so the user can
 * import a profile from a phone or browser over Wi-Fi – no keyboard needed.
 */
class TvImportActivity : BaseActivity() {
    private val qrFlow = MutableStateFlow<Bitmap?>(null)
    private val ipFlow = MutableStateFlow("")
    private val portFlow = MutableStateFlow("")
    private val urlFlow = MutableStateFlow("")
    private val statusFlow = MutableStateFlow<String?>(null)

    private var server: TvImportServer? = null

    override suspend fun main() {
        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                val qr by qrFlow.collectAsStateWithLifecycle()
                val ip by ipFlow.collectAsStateWithLifecycle()
                val port by portFlow.collectAsStateWithLifecycle()
                val url by urlFlow.collectAsStateWithLifecycle()
                val status by statusFlow.collectAsStateWithLifecycle()
                TvImportScreen(
                    qr = qr,
                    ip = ip,
                    port = port,
                    url = url,
                    status = status,
                    onCancel = { finish() },
                )
            }
        }

        setupServer()
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    private suspend fun setupServer() {
        val localIp = withContext(Dispatchers.IO) { getLocalIpAddress() }

        if (localIp == null) {
            statusFlow.value = getString(com.github.kr328.clash.design.R.string.tv_import_no_wifi)
            return
        }

        val port = withContext(Dispatchers.IO) { TvImportServer.findPort() }
        val html = withContext(Dispatchers.IO) {
            assets.open("tv_import_web.html").bufferedReader().readText()
        }

        // One-time token (auth) + AES-256-GCM key (payload encryption) carried only in the
        // QR code, so a LAN sniffer/MITM can't read or forge the phone → TV transfer.
        val token = com.github.kr328.clash.tv.TvCrypto.encodeB64(
            com.github.kr328.clash.tv.TvCrypto.randomBytes(16)
        )
        val keyBytes = com.github.kr328.clash.tv.TvCrypto.randomBytes(32)
        val keyB64 = com.github.kr328.clash.tv.TvCrypto.encodeB64(keyBytes)

        val tvServer = TvImportServer(port, html, token, keyBytes)
        server = tvServer
        tvServer.start(this)

        val baseUrl = "http://$localIp:$port/Prizrak-BoxTVimport"
        // QR carries the secrets (consumed by the phone app); the displayed URL stays clean
        // for manual browser entry (the browser uses /submit and ignores the query).
        val qrUrl = "$baseUrl?v=1&t=$token&k=$keyB64"
        val qrBitmap = withContext(Dispatchers.Default) { generateQrCode(qrUrl, 512) }

        ipFlow.value = localIp
        portFlow.value = port.toString()
        urlFlow.value = baseUrl
        qrFlow.value = qrBitmap

        // Wait for profile data – suspends until the server receives something
        val importData = tvServer.imports.receive()
        tvServer.stop()

        statusFlow.value = getString(com.github.kr328.clash.design.R.string.tv_import_received)

        delay(1500)
        processImport(importData)
        finish()
    }

    private suspend fun processImport(data: TvImportServer.ImportData) {
        when (data.type) {
            "url" -> importProfileFromUrl(
                data.content,
                forceAutoImport = true,
                ageSecretKey = data.ageSecretKey.orEmpty(),
            )

            "yaml" -> {
                val name = data.name
                    ?: getString(com.github.kr328.clash.design.R.string.new_profile)
                val tmpFile = withContext(Dispatchers.IO) {
                    File(cacheDir, "tv_import_${System.currentTimeMillis()}.yaml")
                        .also { it.writeText(data.content) }
                }
                val uuid = withProfile { create(com.github.kr328.clash.service.model.Profile.Type.File, name) }
                withProfile { patch(uuid, name, tmpFile.absolutePath, 0L, data.ageSecretKey.orEmpty()) }
                try {
                    withProfile { commit(uuid, null) }
                    withProfile { queryByUUID(uuid)?.let { setActive(it) } }
                    startActivity(
                        MainActivity::class.intent
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                } catch (_: Exception) {
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
            }

            else -> importProfileFromUrl(data.content) // "text" – URL or proxy-link
        }
    }

    private fun getLocalIpAddress(): String? =
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.filterIsInstance<Inet4Address>()
            ?.filterNot { it.isLoopbackAddress }
            ?.firstOrNull()
            ?.hostAddress

    private fun generateQrCode(content: String, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
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
