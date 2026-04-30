package com.github.kr328.clash

import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun loadProviderRules(provider: Provider, cacheDir: File): List<String> {
    if (provider.type != Provider.Type.Rule) return emptyList()
    return withContext(Dispatchers.IO) {
        val filePath = withClash { queryRuleProviderFilePath(provider.name) }
        when {
            filePath.isBlank() || isMrsFile(filePath) -> dumpToTextFile(provider.name, cacheDir)
            else -> readRuleProviderContent(filePath)
        }
    }
}

private fun isMrsFile(path: String): Boolean {
    val file = File(path)
    if (!file.exists() || file.length() < 4) return false
    val header = ByteArray(4)
    file.inputStream().use { it.read(header) }
    return header[0] == 0x28.toByte() && header[1] == 0xB5.toByte() &&
        header[2] == 0x2F.toByte() && header[3] == 0xFD.toByte()
}

private suspend fun dumpToTextFile(providerName: String, cacheDir: File): List<String> {
    val tempFile = File(cacheDir, "pr_${providerName.hashCode()}.txt")
    return try {
        val errMsg = withClash { dumpRuleProviderToText(providerName, tempFile.absolutePath) }
        if (errMsg.isNotBlank()) emptyList()
        else tempFile.readLines().filter { it.isNotBlank() }
    } finally {
        tempFile.delete()
    }
}

private fun readRuleProviderContent(filePath: String): List<String> {
    val file = File(filePath)
    if (!file.exists()) return emptyList()
    val bytes = file.readBytes()
    if (bytes.size < 4) return emptyList()
    if (bytes[0] == 0x28.toByte() && bytes[1] == 0xB5.toByte() &&
        bytes[2] == 0x2F.toByte() && bytes[3] == 0xFD.toByte()
    ) return emptyList()
    return parseRuleText(bytes.toString(Charsets.UTF_8))
}

private fun parseRuleText(text: String): List<String> {
    val result = mutableListOf<String>()
    var inList = false
    for (rawLine in text.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
        if (line == "payload:" || line == "rules:") { inList = true; continue }
        if (inList && line.startsWith("- ")) {
            var entry = line.removePrefix("- ").trim()
            if (entry.startsWith("'") && entry.endsWith("'"))
                entry = entry.substring(1, entry.length - 1)
            else if (entry.startsWith("\"") && entry.endsWith("\""))
                entry = entry.substring(1, entry.length - 1)
            if (entry.isNotEmpty()) result.add(entry)
        } else if (!inList) {
            result.add(line)
        }
    }
    return result
}
