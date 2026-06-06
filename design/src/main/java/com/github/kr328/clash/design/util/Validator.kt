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
            key.startsWith("AGE-SECRET-KEY-1", ignoreCase = true) && key.length > 20
        }
    }
}