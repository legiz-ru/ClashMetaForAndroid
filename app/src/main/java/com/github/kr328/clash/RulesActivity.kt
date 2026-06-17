package com.github.kr328.clash

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.core.model.Rule
import com.github.kr328.clash.design.compose.screen.RulesScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val RulesJson = Json { ignoreUnknownKeys = true }

class RulesActivity : BaseActivity() {
    private val rulesFlow = MutableStateFlow<List<Rule>>(emptyList())
    private val loadingFlow = MutableStateFlow(true)
    private val errorFlow = MutableStateFlow<Pair<String, String>?>(null)

    override suspend fun main() {
        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                val rules by rulesFlow.collectAsStateWithLifecycle()
                val loading by loadingFlow.collectAsStateWithLifecycle()
                val error by errorFlow.collectAsStateWithLifecycle()
                RulesScreen(
                    rules = rules,
                    loading = loading,
                    error = error,
                    onBack = { finish() },
                )
            }
        }

        loadRules()

        while (isActive) {
            when (events.receive()) {
                Event.ProfileLoaded -> loadRules()
                else -> Unit
            }
        }
    }

    private suspend fun loadRules() {
        loadingFlow.value = true
        errorFlow.value = null
        try {
            val json = withClash { queryRules() }
            val rules = RulesJson.decodeFromString(ListSerializer(Rule.serializer()), json)
            rulesFlow.value = rules
            errorFlow.value = null
        } catch (e: Exception) {
            errorFlow.value = getString(com.github.kr328.clash.design.R.string.no_rules) to
                (e.message ?: e.javaClass.simpleName)
        } finally {
            loadingFlow.value = false
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
