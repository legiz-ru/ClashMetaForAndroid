package com.github.kr328.clash.service.bypass

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress

enum class BypassPlatform(val relayMode: String, val urlMarker: String) {
    WBSTREAM("wbstream-headless-joiner", "wbstream://"),
    TELEMOST("telemost-headless-joiner", "telemost.yandex"),
    VK("vk-headless-joiner", ""),
    ;

    companion object {
        fun fromUrl(url: String): BypassPlatform = when {
            url.contains("wbstream://", ignoreCase = true) -> WBSTREAM
            url.contains("telemost.yandex", ignoreCase = true) -> TELEMOST
            else -> VK
        }
    }
}

enum class BypassStatus {
    IDLE, STARTING, CONNECTING, CONNECTED, LOST, ERROR
}

class BypassRelayController(
    private val context: Context,
    private val proxyConfig: BypassProxyConfig,
    var onStatus: (BypassStatus) -> Unit,
    var onLog: (String) -> Unit,
    var onCaptchaUrl: (String) -> Unit,
) {
    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    @Volatile private var running = false

    @Volatile var currentStatus: BypassStatus = BypassStatus.IDLE
        private set

    val isRunning get() = running

    fun start(platform: BypassPlatform) {
        if (running) return
        running = true

        val relayBin = File(context.applicationInfo.nativeLibraryDir, "librelay.so")
        if (!relayBin.exists()) {
            onLog("librelay.so not found at ${relayBin.absolutePath}")
            onStatus(BypassStatus.ERROR)
            running = false
            return
        }

        val cmd = mutableListOf(
            relayBin.absolutePath,
            "--mode", platform.relayMode,
            "--socks-port", proxyConfig.port.toString(),
        )
        if (proxyConfig.username.isNotEmpty()) {
            cmd += listOf("--socks-user", proxyConfig.username)
            cmd += listOf("--socks-pass", proxyConfig.password)
        }

        val proc = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        process = proc
        writer = OutputStreamWriter(proc.outputStream)

        currentStatus = BypassStatus.STARTING
        onStatus(BypassStatus.STARTING)

        Thread {
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            try {
                reader.forEachLine { line ->
                    onLog(line)
                    when {
                        line.startsWith("RESOLVE:") -> handleResolve(line.removePrefix("RESOLVE:").trim())
                        line.startsWith("STATUS:") -> handleStatus(line.removePrefix("STATUS:").trim())
                        line.startsWith("CAPTCHA:") -> onCaptchaUrl(line.removePrefix("CAPTCHA:").trim())
                    }
                }
            } finally {
                running = false
                currentStatus = BypassStatus.IDLE
                onStatus(BypassStatus.IDLE)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun sendAuth(joinLink: String, displayName: String = "Joiner", tunnelMode: String = "video",
                 vp8Fps: Int = 24, vp8Batch: Int = 30) {
        val platform = BypassPlatform.fromUrl(joinLink)
        when (platform) {
            BypassPlatform.WBSTREAM -> {
                val roomId = joinLink.removePrefix("wbstream://").trim()
                val json = JSONObject().apply {
                    put("roomId", roomId)
                    put("displayName", displayName.ifEmpty { "Joiner" })
                    put("tunnelMode", tunnelMode)
                    put("vp8Fps", vp8Fps)
                    put("vp8Batch", vp8Batch)
                }
                writeLine("JOIN:$json")
            }
            BypassPlatform.TELEMOST -> {
                val json = JSONObject().apply {
                    put("joinLink", joinLink)
                    put("displayName", displayName)
                    put("vp8Fps", vp8Fps)
                    put("vp8Batch", vp8Batch)
                }
                writeLine("JOIN:$json")
            }
            BypassPlatform.VK -> {
                val json = JSONObject().apply {
                    put("joinLink", joinLink)
                    put("displayName", displayName)
                    put("tunnelMode", tunnelMode)
                    put("vp8Fps", vp8Fps)
                    put("vp8Batch", vp8Batch)
                }
                writeLine("AUTH:$json")
            }
        }
    }

    fun stop() {
        running = false
        currentStatus = BypassStatus.IDLE
        try { writer?.close() } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        process = null
        writer = null
    }

    private fun handleResolve(hostname: String) {
        Thread {
            try {
                val addr = InetAddress.getAllByName(hostname).firstOrNull()?.hostAddress ?: ""
                writeLine(addr)
            } catch (_: Exception) {
                writeLine("")
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun handleStatus(status: String) {
        val s = when {
            status == "READY" -> BypassStatus.STARTING
            status == "CONNECTING" -> BypassStatus.CONNECTING
            status == "TUNNEL_CONNECTED" -> BypassStatus.CONNECTED
            status == "TUNNEL_LOST" -> BypassStatus.LOST
            else -> BypassStatus.CONNECTING
        }
        currentStatus = s
        onStatus(s)
    }

    private fun writeLine(line: String) {
        try {
            writer?.write("$line\n")
            writer?.flush()
        } catch (_: Exception) {}
    }
}
