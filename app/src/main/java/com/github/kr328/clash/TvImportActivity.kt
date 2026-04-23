package com.github.kr328.clash

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.tv.TvImportServer
import com.github.kr328.clash.util.importProfileFromUrl
import com.github.kr328.clash.util.withProfile
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Displays a QR code pointing at the local TvImportServer so the user can
 * import a profile from a phone or browser over Wi-Fi – no keyboard needed.
 */
class TvImportActivity : BaseActivity<TvImportActivity.EmptyDesign>() {

    /** Placeholder design – we manage the view directly via setContentView. */
    class EmptyDesign(context: android.content.Context) : Design<Nothing>(context) {
        override val root: View = View(context)
    }

    private var server: TvImportServer? = null

    override suspend fun main() {
        // Set content view on the main thread
        withContext(Dispatchers.Main) {
            setContentView(com.github.kr328.clash.design.R.layout.activity_tv_import)
            findViewById<View>(com.github.kr328.clash.design.R.id.cancel_button)
                .setOnClickListener { finish() }
        }

        setupServer()
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    // ── Setup ──────────────────────────────────────────────────────────────

    private suspend fun setupServer() {
        val localIp = withContext(Dispatchers.IO) { getLocalIpAddress() }

        if (localIp == null) {
            withContext(Dispatchers.Main) {
                statusText().apply {
                    text = getString(com.github.kr328.clash.design.R.string.tv_import_no_wifi)
                    visibility = View.VISIBLE
                }
                instructionText().visibility = View.GONE
            }
            return
        }

        val port = withContext(Dispatchers.IO) { TvImportServer.findPort() }
        val html = withContext(Dispatchers.IO) {
            assets.open("tv_import_web.html").bufferedReader().readText()
        }

        val tvServer = TvImportServer(port, html)
        server = tvServer
        tvServer.start(this)

        val url = "http://$localIp:$port/Prizrak-BoxTVimport"
        val qrBitmap = withContext(Dispatchers.Default) { generateQrCode(url, 512) }

        withContext(Dispatchers.Main) {
            qrImage().setImageBitmap(qrBitmap)
            ipValueText().text = localIp
            portValueText().text = port.toString()
            ipPortCard().visibility = View.VISIBLE
            urlText().text = url
            instructionText().text =
                getString(com.github.kr328.clash.design.R.string.tv_import_instruction)
        }

        // Wait for profile data – suspends until the server receives something
        val importData = tvServer.imports.receive()
        tvServer.stop()

        withContext(Dispatchers.Main) {
            statusText().apply {
                text = getString(com.github.kr328.clash.design.R.string.tv_import_received)
                visibility = View.VISIBLE
            }
        }

        delay(1500)
        processImport(importData)
        finish()
    }

    // ── Import routing ─────────────────────────────────────────────────────

    private suspend fun processImport(data: TvImportServer.ImportData) {
        when (data.type) {
            "url" -> importProfileFromUrl(data.content)

            "yaml" -> {
                val name = data.name
                    ?: getString(com.github.kr328.clash.design.R.string.new_profile)
                val tmpFile = withContext(Dispatchers.IO) {
                    File(cacheDir, "tv_import_${System.currentTimeMillis()}.yaml")
                        .also { it.writeText(data.content) }
                }
                val uuid = withProfile { create(com.github.kr328.clash.service.model.Profile.Type.File, name) }
                withProfile { patch(uuid, name, tmpFile.absolutePath, 0L) }
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

    // ── Helpers ────────────────────────────────────────────────────────────

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

    private fun qrImage() = findViewById<ImageView>(com.github.kr328.clash.design.R.id.qr_image)
    private fun urlText() = findViewById<TextView>(com.github.kr328.clash.design.R.id.url_text)
    private fun statusText() = findViewById<TextView>(com.github.kr328.clash.design.R.id.status_text)
    private fun instructionText() = findViewById<TextView>(com.github.kr328.clash.design.R.id.instruction_text)
    private fun ipPortCard() = findViewById<MaterialCardView>(com.github.kr328.clash.design.R.id.ip_port_card)
    private fun ipValueText() = findViewById<TextView>(com.github.kr328.clash.design.R.id.ip_value_text)
    private fun portValueText() = findViewById<TextView>(com.github.kr328.clash.design.R.id.port_value_text)
}
