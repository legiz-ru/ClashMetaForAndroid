package com.github.kr328.clash.design.util

import com.github.kr328.clash.common.util.PatternFileName

typealias Validator = (String) -> Boolean

val ValidatorAcceptAll: Validator = {
    true
}

val ValidatorFileName: Validator = {
    PatternFileName.matches(it) && it.isNotBlank()
}

val ValidatorNotBlank: Validator = {
    it.isNotBlank()
}

val ValidatorHttpUrl: Validator = {
    it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
}

val ValidatorAutoUpdateInterval: Validator = {
    it.isEmpty() || (it.toLongOrNull() ?: 0) >= 15
}

val ValidatorAgeSecretKey: Validator = {
    it.isEmpty() || it.lines().filter { l -> l.trim().isNotEmpty() }.all { l ->
        l.trim().let { key ->
            // Accept all age secret-key variants: classic X25519 (AGE-SECRET-KEY-1…),
            // hybrid / post-quantum (AGE-SECRET-KEY-HYBRID-1…, AGE-SECRET-KEY-PQ-…).
            // The native core (verifySecretKeys) performs the authoritative validation.
            key.startsWith("AGE-SECRET-KEY-", ignoreCase = true) && key.length > 20
        }
    }
}