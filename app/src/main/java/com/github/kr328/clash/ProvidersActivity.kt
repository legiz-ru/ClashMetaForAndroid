package com.github.kr328.clash

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.ProviderItem
import com.github.kr328.clash.design.compose.screen.ProvidersScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

class ProvidersActivity : BaseActivity() {
    private val providersFlow = MutableStateFlow<List<ProviderItem>>(emptyList())
    private val tickFlow = MutableStateFlow(0)

    override suspend fun main() {
        val providers = withClash { queryProviders().sorted() }
        providersFlow.value = providers.map { ProviderItem(it, it.updatedAt, false) }

        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                val items by providersFlow.collectAsStateWithLifecycle()
                val tick by tickFlow.collectAsStateWithLifecycle()
                ProvidersScreen(
                    providers = items,
                    tick = tick,
                    onBack = { finish() },
                    onOpen = ::openDetail,
                    onUpdateAll = ::updateAll,
                )
            }
        }

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
                if (activityStarted) {
                    ticker.onReceive { tickFlow.value++ }
                }
            }
        }
    }

    private fun openDetail(provider: Provider) {
        startActivity(
            ProviderDetailActivity::class.intent.putExtra(
                ProviderDetailActivity.EXTRA_PROVIDER, provider
            )
        )
    }

    private fun updateAll() {
        providersFlow.value.forEachIndexed { index, item ->
            if (item.provider.vehicleType != Provider.VehicleType.Inline && !item.updating) {
                updateOne(index, item.provider)
            }
        }
    }

    private fun updateOne(index: Int, provider: Provider) {
        setItem(index) { it.copy(updating = true) }
        launch {
            try {
                withClash { updateProvider(provider.type, provider.name) }
                setItem(index) { it.copy(updating = false, updatedAt = System.currentTimeMillis()) }
            } catch (e: Exception) {
                Toast.makeText(
                    this@ProvidersActivity,
                    getString(R.string.format_update_provider_failure, provider.name, e.message),
                    Toast.LENGTH_LONG,
                ).show()
                setItem(index) { it.copy(updating = false) }
            }
        }
    }

    private fun setItem(index: Int, transform: (ProviderItem) -> ProviderItem) {
        providersFlow.value = providersFlow.value.toMutableList().also { list ->
            if (index in list.indices) list[index] = transform(list[index])
        }
    }

    private fun currentThemeVariant(): ClashThemeVariant {
        val cfg = resources.configuration
        return when (uiStore.darkMode) {
            DarkMode.Auto ->
                if (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    ClashThemeVariant.Dark
                } else {
                    ClashThemeVariant.Light
                }
            DarkMode.ForceLight -> ClashThemeVariant.Light
            DarkMode.ForceDark -> ClashThemeVariant.Dark
            DarkMode.AlwaysSummer -> ClashThemeVariant.Summer
        }
    }
}
