package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class SettingsDesign(context: Context) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp, StartNetwork, StartOverride, StartMetaFeature,
        StartProviders, StartLogs, StartHelp, StartAbout,
        GoHome,
    }

    private val binding = DesignSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.self = this

        // Select settings tab by default
        binding.bottomNav.selectedItemId = R.id.nav_settings

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    requests.trySend(Request.GoHome)
                    false
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
