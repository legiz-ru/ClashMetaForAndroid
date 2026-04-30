package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.ProvidersDesign
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class ProvidersActivity : BaseActivity<ProvidersDesign>() {
    override suspend fun main() {
        val providers = withClash { queryProviders().sorted() }
        val design = ProvidersDesign(this, providers)

        setContentDesign(design)

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ProfileLoaded -> {
                            val newList = withClash { queryProviders().sorted() }

                            if (newList != providers) {
                                startActivity(ProvidersActivity::class.intent)
                                finish()
                            }
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        is ProvidersDesign.Request.Update -> {
                            launch {
                                try {
                                    withClash {
                                        updateProvider(it.provider.type, it.provider.name)
                                    }
                                    design.notifyChanged(it.index)
                                } catch (e: Exception) {
                                    design.showExceptionToast(
                                        getString(
                                            R.string.format_update_provider_failure,
                                            it.provider.name,
                                            e.message
                                        )
                                    )
                                    design.notifyUpdated(it.index)
                                }
                            }
                        }
                        is ProvidersDesign.Request.ViewContent -> {
                            launch {
                                try {
                                    val filePath = withClash {
                                        queryRuleProviderFilePath(it.provider.name)
                                    }
                                    val rules = if (filePath.isBlank()) {
                                        emptyList()
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            readRuleProviderContent(filePath)
                                        }
                                    }
                                    design.showRuleContent(it.provider, rules)
                                } catch (e: Exception) {
                                    design.showExceptionToast(e)
                                }
                            }
                        }
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    private fun readRuleProviderContent(filePath: String): List<String> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()

        val bytes = file.readBytes()
        if (bytes.size < 4) return emptyList()

        // MRS files are zstd-compressed — unreadable as text
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

            if (line == "payload:" || line == "rules:") {
                inList = true
                continue
            }

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
}
