package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.design.component.TvNavigationDrawer
import com.github.kr328.clash.design.databinding.DesignSettingsBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.view.ActionLabel

class SettingsDesign(context: Context) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp, StartNetwork, StartOverride, StartMetaFeature,
        StartProviders, StartLogs, StartHelp, StartAbout,
        GoHome, OpenProfiles, ToggleStatus,
    }

    private val binding = DesignSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    private val isTv = TvUtils.isTv(context)

    private val tvDrawer: TvNavigationDrawer? = if (isTv) {
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

    private val rootView: View = if (isTv) {
        binding.bottomNav.visibility = View.GONE
        tvDrawer!!.wrapContent(binding.root)
    } else {
        binding.root
    }

    override val root: View
        get() = rootView

    fun setClashRunning(running: Boolean) {
        tvDrawer?.isClashRunning = running
    }

    init {
        binding.self = this

        if (!isTv) {
            // Apply rounded ripple backgrounds for mobile
            applyMobileRoundedBackgrounds()

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

    private fun applyMobileRoundedBackgrounds() {
        val scrollContent = binding.scrollRoot.getChildAt(0) as? LinearLayout ?: return
        val dp = context.resources.displayMetrics.density

        for (i in 0 until scrollContent.childCount) {
            val child = scrollContent.getChildAt(i)
            if (child is ActionLabel) {
                // ActionLabel is a FrameLayout; its first child is the inner clickable LinearLayout
                val innerLayout = (child as ViewGroup).getChildAt(0)
                innerLayout?.background = context.getDrawable(R.drawable.bg_settings_item_rounded)

                // Add horizontal margins for the rounded pill look
                val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
                params.marginStart = (12 * dp).toInt()
                params.marginEnd = (12 * dp).toInt()
                child.layoutParams = params
            }
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
