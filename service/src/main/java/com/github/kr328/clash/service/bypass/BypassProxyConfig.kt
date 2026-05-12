package com.github.kr328.clash.service.bypass

import android.content.Context
import com.github.kr328.clash.service.util.importedDir
import java.util.UUID

data class BypassProxyConfig(
    val port: Int,
    val username: String,
    val password: String,
)

/**
 * Parses the active profile YAML and extracts SOCKS5 config from the proxy
 * named "whitelist-bypass". Handles both top-level `proxies:` list and
 * `proxy-providers` with `type: inline`.
 */
fun parseBypassProxyConfig(context: Context, profileUuid: UUID): BypassProxyConfig? {
    val configFile = context.importedDir.resolve("${profileUuid}/config.yaml")
    if (!configFile.exists()) return null
    val yaml = configFile.readText()
    return parseBypassProxyFromYaml(yaml)
}

fun parseBypassProxyFromYaml(yaml: String): BypassProxyConfig? {
    // Collect all YAML blocks that start an item named "whitelist-bypass"
    val lines = yaml.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        // Match list item that declares name: whitelist-bypass
        if (line.trimStart().let { it == "- name: whitelist-bypass" || it == "- name: \"whitelist-bypass\"" || it == "- name: 'whitelist-bypass'" }) {
            val block = extractBlock(lines, i)
            val cfg = parseProxyBlock(block)
            if (cfg != null) return cfg
        }
        i++
    }
    return null
}

/** Extracts a YAML list-item block starting at lineIndex. */
private fun extractBlock(lines: List<String>, start: Int): List<String> {
    val result = mutableListOf(lines[start])
    val baseIndent = lines[start].length - lines[start].trimStart().length
    var i = start + 1
    while (i < lines.size) {
        val l = lines[i]
        if (l.isBlank()) { i++; continue }
        val indent = l.length - l.trimStart().length
        // Next list item at same or lower indent → stop
        if (indent <= baseIndent && l.trimStart().startsWith("- ")) break
        if (indent <= baseIndent && !l.trimStart().startsWith(" ")) break
        result.add(l)
        i++
    }
    return result
}

private fun parseProxyBlock(block: List<String>): BypassProxyConfig? {
    val map = mutableMapOf<String, String>()
    for (line in block) {
        val stripped = line.trimStart().removePrefix("- ")
        val colon = stripped.indexOf(':')
        if (colon < 0) continue
        val key = stripped.substring(0, colon).trim()
        val value = stripped.substring(colon + 1).trim().removeSurrounding("\"").removeSurrounding("'")
        map[key] = value
    }
    val type = map["type"] ?: return null
    if (type != "socks5") return null
    val port = map["port"]?.toIntOrNull() ?: return null
    val username = map["username"] ?: map["user"] ?: ""
    val password = map["password"] ?: map["pass"] ?: ""
    return BypassProxyConfig(port, username, password)
}
