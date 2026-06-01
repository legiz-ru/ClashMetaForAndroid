package com.github.kr328.clash

import com.github.kr328.clash.core.model.Rule
import com.github.kr328.clash.design.RulesDesign
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val RulesJson = Json { ignoreUnknownKeys = true }

class RulesActivity : BaseActivity<RulesDesign>() {
    override suspend fun main() {
        val design = RulesDesign(this)

        setContentDesign(design)

        loadRules(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ProfileLoaded -> loadRules(design)
                        else -> Unit
                    }
                }
                design.requests.onReceive {}
            }
        }
    }

    private suspend fun loadRules(design: RulesDesign) {
        design.setLoading(true)
        try {
            val json = withClash { queryRules() }
            val rules = RulesJson.decodeFromString(ListSerializer(Rule.serializer()), json)
            design.setRules(rules)
        } catch (e: Exception) {
            design.setError(
                getString(com.github.kr328.clash.design.R.string.no_rules),
                e.message ?: e.javaClass.simpleName
            )
        }
    }
}
