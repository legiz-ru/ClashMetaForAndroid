package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.AccessControlMode

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

/**
 * Merges UI access-control packages (union) into the tun.include-package /
 * tun.exclude-package lists of a Clash YAML config string.
 *
 * Rules:
 *  - AcceptAll  → no change
 *  - AcceptSelected → union into tun.include-package
 *  - DenySelected   → union into tun.exclude-package
 *
 * If the target key is absent from the tun section it is appended.
 * The opposite key (e.g. exclude-package when mode is AcceptSelected) is left untouched.
 * Returns the original string when no modification is required.
 */
fun patchTunPackages(
    yaml: String,
    mode: AccessControlMode,
    uiPackages: Set<String>,
): String {
    if (mode == AccessControlMode.AcceptAll || uiPackages.isEmpty()) return yaml

    val targetKey = when (mode) {
        AccessControlMode.AcceptSelected -> "include-package"
        AccessControlMode.DenySelected   -> "exclude-package"
        AccessControlMode.AcceptAll      -> return yaml
    }

    val lines = yaml.lines().toMutableList()

    // Find tun: at indent 0
    val tunIdx = lines.indexOfFirst { line ->
        val t = line.trimEnd()
        t == "tun:" || (t.startsWith("tun:") && t.length > 4 && t[4].isWhitespace())
    }
    if (tunIdx < 0) return yaml

    // Find tun-child indent (first non-blank line after tun:)
    var tunChildIndent = -1
    for (i in tunIdx + 1 until lines.size) {
        val line = lines[i]
        if (line.isBlank()) continue
        val indent = line.length - line.trimStart().length
        if (indent == 0) return yaml  // tun section is empty
        tunChildIndent = indent
        break
    }
    if (tunChildIndent < 0) return yaml

    // Find end of tun section (first non-blank line back at indent 0)
    var tunEnd = lines.size
    for (i in tunIdx + 1 until lines.size) {
        val line = lines[i]
        if (line.isBlank()) continue
        if (line.length - line.trimStart().length == 0) {
            tunEnd = i
            break
        }
    }

    // Find targetKey inside the tun section
    var keyIdx = -1
    for (i in tunIdx + 1 until tunEnd) {
        if (lines[i].trimStart().startsWith("$targetKey:")) {
            keyIdx = i
            break
        }
    }

    if (keyIdx >= 0) {
        val keyIndent  = lines[keyIdx].length - lines[keyIdx].trimStart().length
        val afterColon = lines[keyIdx].trimStart().removePrefix("$targetKey:").trim()
        val existingPkgs = mutableSetOf<String>()

        if (afterColon.startsWith("[")) {
            // Inline list: include-package: [pkg1, pkg2]
            afterColon.trim('[', ']').split(",")
                .map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { existingPkgs.add(it) }

            val missing = uiPackages - existingPkgs
            if (missing.isNotEmpty()) {
                val allPkgs = existingPkgs.toList() + missing.toList()
                val pad = " ".repeat(keyIndent)
                lines[keyIdx] = "$pad$targetKey: [${allPkgs.joinToString(", ")}]"
            }
        } else {
            // Block list: items may be at same indent as key or deeper
            var listItemIndent = keyIndent
            var listEnd = keyIdx + 1

            for (i in keyIdx + 1 until tunEnd) {
                val line = lines[i]
                if (line.isBlank()) { listEnd = i + 1; continue }
                val indent  = line.length - line.trimStart().length
                val stripped = line.trimStart()
                if (stripped.startsWith("- ") && indent >= keyIndent) {
                    listItemIndent = indent
                    existingPkgs.add(stripped.removePrefix("- ").trim())
                    listEnd = i + 1
                } else {
                    break
                }
            }

            val missing = uiPackages - existingPkgs
            if (missing.isNotEmpty()) {
                val pad = " ".repeat(listItemIndent)
                lines.addAll(listEnd, missing.map { "$pad- $it" })
            }
        }
    } else {
        // Key absent — append before the end of the tun section
        val pad = " ".repeat(tunChildIndent)
        val toInsert = mutableListOf("$pad$targetKey:")
        uiPackages.forEach { toInsert.add("$pad- $it") }
        lines.addAll(tunEnd, toInsert)
    }

    return lines.joinToString("\n")
}
