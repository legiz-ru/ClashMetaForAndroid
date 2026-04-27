package com.github.kr328.clash.design

import android.content.Context
import android.content.res.Configuration
import android.view.View
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.design.component.TvNavigationDrawer
import com.github.kr328.clash.design.databinding.DesignSettingsBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class SettingsDesign(context: Context) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp, StartNetwork, StartOverride, StartMetaFeature,
        StartProviders, StartConnections, StartLogs, StartAbout,
        GoHome, OpenProfiles, ToggleStatus,
    }

    private val binding = DesignSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    private val isTv = TvUtils.isTv(context)

    private val useDrawerNav: Boolean = isTv || run {
        val cfg = context.resources.configuration
        cfg.smallestScreenWidthDp >= 600 &&
            cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private val tvDrawer: TvNavigationDrawer? = if (useDrawerNav) {
        TvNavigationDrawer(context, TvNavigationDrawer.NavItem.Settings).apply {
            onNavigate = { item ->
                when (item) {
                    TvNavigationDrawer.NavItem.Home -> requests.trySend(Request.GoHome)
                    TvNavigationDrawer.NavItem.Profiles -> requests.trySend(Request.OpenProfiles)
                    TvNavigationDrawer.NavItem.Settings -> {} // Already on settings
                }
            }
            onToggleStatus = { requests.trySend(Request.ToggleStatus) }
        }
    } else null

    private val rootView: View = if (useDrawerNav) {
        binding.bottomNav.visibility = View.GONE
        tvDrawer!!.wrapContent(binding.root)
    } else {
        binding.root
    }

    override val root: View
        get() = rootView

    fun setClashRunning(running: Boolean) {
        tvDrawer?.isClashRunning = running
        binding.clashRunning = running
    }

    init {
        binding.self = this

        if (!useDrawerNav) {
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
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
