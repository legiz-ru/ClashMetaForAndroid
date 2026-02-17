package com.github.kr328.clash.design

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.design.component.TvNavigationDrawer
import com.github.kr328.clash.design.databinding.DesignSettingsBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
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

        if (isTv) {
            // Replace scroll content with grid layout for TV
            binding.scrollRoot.removeAllViews()
            binding.scrollRoot.addView(buildTvSettingsGrid())
        } else {
            // Apply rounded backgrounds to settings items for mobile
            applyRoundedBackgrounds()

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

    private fun applyRoundedBackgrounds() {
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

    private data class SettingsItem(val iconRes: Int, val textRes: Int, val request: Request)

    private val settingsItems = listOf(
        SettingsItem(R.drawable.ic_baseline_settings, R.string.app, Request.StartApp),
        SettingsItem(R.drawable.ic_baseline_dns, R.string.network, Request.StartNetwork),
        SettingsItem(R.drawable.ic_baseline_extension, R.string.override, Request.StartOverride),
        SettingsItem(R.drawable.ic_baseline_meta, R.string.meta_features, Request.StartMetaFeature),
        SettingsItem(R.drawable.ic_baseline_swap_vertical_circle, R.string.providers, Request.StartProviders),
        SettingsItem(R.drawable.ic_baseline_assignment, R.string.logs, Request.StartLogs),
        SettingsItem(R.drawable.ic_baseline_help_center, R.string.help, Request.StartHelp),
        SettingsItem(R.drawable.ic_baseline_info, R.string.about, Request.StartAbout),
    )

    private fun buildTvSettingsGrid(): View {
        val dp = context.resources.displayMetrics.density
        val margin = (6 * dp).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding((16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt())
        }

        // Build 2-column rows from the items list
        for (i in settingsItems.indices step 2) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }

            val left = createTvSettingsItem(dp, settingsItems[i].iconRes, context.getString(settingsItems[i].textRes)) {
                requests.trySend(settingsItems[i].request)
            }
            left.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(margin, margin, margin, margin)
            }
            row.addView(left)

            if (i + 1 < settingsItems.size) {
                val right = createTvSettingsItem(dp, settingsItems[i + 1].iconRes, context.getString(settingsItems[i + 1].textRes)) {
                    requests.trySend(settingsItems[i + 1].request)
                }
                right.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(margin, margin, margin, margin)
                }
                row.addView(right)
            } else {
                // Placeholder for odd number of items
                val spacer = View(context)
                spacer.layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                    setMargins(margin, margin, margin, margin)
                }
                row.addView(spacer)
            }

            container.addView(row)
        }

        return container
    }

    private fun createTvSettingsItem(
        dp: Float,
        iconRes: Int,
        text: String,
        onClick: () -> Unit,
    ): View {
        val onSurfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val surfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSurfaceVariant)

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt())
            isFocusable = true
            isClickable = true
            background = createTvFocusableBackground(dp, surfaceVariantColor)
            setOnClickListener { onClick() }

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                    marginEnd = (12 * dp).toInt()
                }
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(onSurfaceVariantColor)
            }
            addView(icon)

            val textView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                this.text = text
                textSize = 15f
                setTextColor(onSurfaceColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(textView)
        }
    }

    private fun createTvFocusableBackground(dp: Float, fillColor: Int): StateListDrawable {
        val focusColor = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)

        val focusedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28 * dp
            setColor(fillColor)
            setStroke((2 * dp).toInt(), focusColor)
        }

        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28 * dp
            setColor(fillColor)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
