package com.github.kr328.clash.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Minimal coroutine-based HTTP server for TV profile import.
 * Listens on [port] and exposes endpoints under /Prizrak-BoxTVimport.
 */
class TvImportServer(val port: Int, private val html: String) {

    data class ImportData(
        /** "url", "yaml", or "text" */
        val type: String,
        /** URL or raw YAML/text content */
        val content: String,
        /** Optional profile name from sender */
        val name: String?,
    )

    private val importChannel = Channel<ImportData>(capacity = 1)

    /** Receive channel – caller suspends here until a profile arrives. */
    val imports: Channel<ImportData> get() = importChannel

    private var serverSocket: ServerSocket? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.IO) {
            val ss = ServerSocket(port)
            serverSocket = ss
            while (isActive) {
                try {
                    val client = ss.accept()
                    launch(Dispatchers.IO) { handleClient(client) }
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    fun stop() {
        serverSocket?.close()
        job?.cancel()
    }

    // ── HTTP handling ──────────────────────────────────────────────────────

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val input = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))
                val output = s.outputStream

                // Request line
                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val path = parts[1].split("?")[0]

                // Headers
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        headers[line.substring(0, idx).trim().lowercase()] =
                            line.substring(idx + 1).trim()
                    }
                }

                // Body
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (contentLength > 0) {
                    val chars = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = input.read(chars, read, contentLength - read)
                        if (n < 0) break
                        read += n
                    }
                    String(chars, 0, read)
                } else ""

                // CORS preflight
                if (method == "OPTIONS") {
                    sendResponse(output, 200, "text/plain", "")
                    return
                }

                when {
                    path == "/Prizrak-BoxTVimport" && method == "GET" ->
                        sendResponse(output, 200, "text/html; charset=utf-8", html)

                    path == "/Prizrak-BoxTVimport/api/ping" && method == "GET" ->
                        sendResponse(output, 200, "application/json", """{"status":"ok"}""")

                    path == "/Prizrak-BoxTVimport/submit" && method == "POST" ->
                        handleSubmit(output, body)

                    path == "/Prizrak-BoxTVimport/api/transfer" && method == "POST" ->
                        handleTransfer(output, body)

                    else ->
                        sendResponse(output, 404, "text/plain", "Not Found")
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun handleSubmit(output: OutputStream, body: String) {
        val content = parseJsonString(body, "content")
        if (content.isNullOrBlank()) {
            sendResponse(
                output, 400, "application/json",
                """{"status":"error","message":"Empty content"}"""
            )
            return
        }
        importChannel.trySend(ImportData("text", content, null))
        sendResponse(output, 200, "application/json", """{"status":"ok"}""")
    }

    private fun handleTransfer(output: OutputStream, body: String) {
        val type = parseJsonString(body, "type")
        if (type.isNullOrBlank()) {
            sendResponse(
                output, 400, "application/json",
                """{"status":"error","message":"Missing type"}"""
            )
            return
        }
        val name = parseJsonString(body, "name")
        val content = when (type) {
            "url" -> parseJsonString(body, "url")
            "yaml" -> parseJsonString(body, "content")
            else -> null
        }
        if (content.isNullOrBlank()) {
            sendResponse(
                output, 400, "application/json",
                """{"status":"error","message":"Missing content for type $type"}"""
            )
            return
        }
        importChannel.trySend(ImportData(type, content, name))
        sendResponse(output, 200, "application/json", """{"status":"ok"}""")
    }

    // ── HTTP response ──────────────────────────────────────────────────────

    private fun sendResponse(
        output: OutputStream,
        code: Int,
        contentType: String,
        body: String,
    ) {
        val statusText = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "OK"
        }
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $code $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Extracts a JSON string value without a full JSON parser. */
    private fun parseJsonString(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return regex.find(json)?.groupValues?.get(1)
            ?.replace("\\n", "\n")
            ?.replace("\\r", "\r")
            ?.replace("\\t", "\t")
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?.replace(Regex("""\\u([0-9a-fA-F]{4})""")) {
                it.groupValues[1].toInt(16).toChar().toString()
            }
    }

    companion object {
        /** Finds a free TCP port in [range] (default 40000–59999). */
        fun findPort(range: IntRange = 40000..59999): Int {
            val candidates = range.toMutableList().also { it.shuffle() }
            for (port in candidates) {
                try {
                    ServerSocket(port).close()
                    return port
                } catch (_: Exception) {
                }
            }
            throw RuntimeException("No available port found in range $range")
        }
    }
}
