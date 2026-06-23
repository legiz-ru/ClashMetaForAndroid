package com.github.kr328.clash.service.util

/**
 * Parses tun.include-package and tun.exclude-package from a Clash YAML config string.
 * Returns (includePackages, excludePackages). Both sets are empty when the tun section
 * is absent or contains no package lists.
 */
fun parseTunPackageLists(yaml: String): Pair<Set<String>, Set<String>> {
    val lines = yaml.lines()

    val tunIdx = lines.indexOfFirst { line ->
        val t = line.trimEnd()
        t == "tun:" || (t.startsWith("tun:") && t.length > 4 && t[4].isWhitespace())
    }
    if (tunIdx < 0) return Pair(emptySet(), emptySet())

    var tunChildIndent = -1
    for (i in tunIdx + 1 until lines.size) {
        val line = lines[i]
        if (line.isBlank()) continue
        val indent = line.length - line.trimStart().length
        if (indent == 0) return Pair(emptySet(), emptySet())
        tunChildIndent = indent
        break
    }
    if (tunChildIndent < 0) return Pair(emptySet(), emptySet())

    var tunEnd = lines.size
    for (i in tunIdx + 1 until lines.size) {
        val line = lines[i]
        if (line.isBlank()) continue
        if (line.length - line.trimStart().length == 0) { tunEnd = i; break }
    }

    fun collectList(keyName: String): Set<String> {
        val result = mutableSetOf<String>()
        val keyIdx = (tunIdx + 1 until tunEnd).firstOrNull { i ->
            lines[i].trimStart().startsWith("$keyName:")
        } ?: return result
        val keyIndent = lines[keyIdx].length - lines[keyIdx].trimStart().length
        val afterColon = lines[keyIdx].trimStart().removePrefix("$keyName:").trim()
        if (afterColon.startsWith("[")) {
            afterColon.trim('[', ']').split(",")
                .map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { result.add(it) }
        } else {
            for (i in keyIdx + 1 until tunEnd) {
                val line = lines[i]
                if (line.isBlank()) continue
                val stripped = line.trimStart()
                if (stripped.startsWith("- ") && line.length - stripped.length >= keyIndent)
                    result.add(stripped.removePrefix("- ").trim())
                else break
            }
        }
        return result
    }

    return Pair(collectList("include-package"), collectList("exclude-package"))
}
