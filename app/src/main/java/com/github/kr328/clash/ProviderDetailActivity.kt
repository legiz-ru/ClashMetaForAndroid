package com.github.kr328.clash

import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.ProviderDetailDesign
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class ProviderDetailActivity : BaseActivity<ProviderDetailDesign>() {
    companion object {
        const val EXTRA_PROVIDER = "provider"
    }

    override suspend fun main() {
        @Suppress("DEPRECATION")
        val provider = intent.getParcelableExtra<Provider>(EXTRA_PROVIDER)!!
        title = provider.name

        val design = ProviderDetailDesign(this, provider)
        setContentDesign(design)

        launch {
            val rules = loadProviderRules(provider, cacheDir)
            design.showRules(rules)
        }

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStop -> finish()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProviderDetailDesign.Request.Update -> {
                            launch {
                                design.setUpdating(true)
                                try {
                                    withClash { updateProvider(provider.type, provider.name) }
                                } catch (e: Exception) {
                                    design.showExceptionToast(e)
                                } finally {
                                    try {
                                        val newRules = loadProviderRules(provider, cacheDir)
                                        design.showRules(newRules)
                                    } finally {
                                        design.setUpdating(false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
