package com.github.kr328.clash.design

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.databinding.DesignSheetAddProfileBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenProxy,
        OpenProfiles,
        OpenProviders,
        OpenSettings,
        ManageProfiles,
        UpdateProfile,
        DeleteProfile,
        AddFromClipboard,
        ScanQrCode,
        AddManually,
        OpenHelp,
        OpenAbout,
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    val bottomNav: BottomNavigationView
        get() = binding.bottomNav

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            binding.clashRunning = running
        }
    }

    suspend fun setHasProfiles(has: Boolean) {
        withContext(Dispatchers.Main) {
            binding.hasProfiles = has
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            binding.mode = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                else -> context.getString(R.string.rule_mode)
            }
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            binding.hasProviders = has
        }
    }

    suspend fun setActiveProfileInfo(profile: Profile?) {
        withContext(Dispatchers.Main) {
            if (profile != null) {
                val hasTraffic = profile.total > 1
                val hasExpire = profile.expire > 0

                binding.hasTrafficInfo = hasTraffic
                binding.hasExpireInfo = hasExpire

                if (hasTraffic) {
                    binding.profileTrafficUsed = (profile.download + profile.upload).toBytesString()
                    binding.profileTrafficTotal = profile.total.toBytesString()
                }

                if (hasExpire) {
                    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    binding.profileExpire = sdf.format(Date(profile.expire))
                }

                val updateSdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                binding.profileUpdated = updateSdf.format(Date(profile.updatedAt))
            } else {
                binding.hasTrafficInfo = false
                binding.hasExpireInfo = false
                binding.profileTrafficUsed = null
                binding.profileTrafficTotal = null
                binding.profileExpire = null
                binding.profileUpdated = null
            }
        }
    }

    suspend fun setProxyGroups(groups: List<Pair<String, ProxyGroup>>) {
        withContext(Dispatchers.Main) {
            val container = binding.proxyGroupsContainer
            container.removeAllViews()

            val dp = context.resources.displayMetrics.density
            val primaryColor = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
            val surfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSurfaceVariant)
            val onSurfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

            for ((name, group) in groups) {
                val card = MaterialCardView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (8 * dp).toInt()
                    }
                    radius = 16 * dp
                    cardElevation = 0f
                    setCardBackgroundColor(surfaceVariantColor)
                }

                val cardContent = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
                }

                // Group header row
                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val groupIcon = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply {
                        marginEnd = (8 * dp).toInt()
                    }
                    setImageResource(R.drawable.ic_baseline_vpn_lock)
                    imageTintList = ColorStateList.valueOf(primaryColor)
                }

                val groupName = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = name
                    setTextColor(onSurfaceColor)
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }

                val selectedLabel = TextView(context).apply {
                    text = group.now
                    setTextColor(primaryColor)
                    textSize = 12f
                }

                headerRow.addView(groupIcon)
                headerRow.addView(groupName)
                headerRow.addView(selectedLabel)
                cardContent.addView(headerRow)

                // Show proxies (limit to first 6 to keep it compact)
                val proxiesToShow = group.proxies.take(6)
                if (proxiesToShow.isNotEmpty()) {
                    val proxyGrid = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = (8 * dp).toInt()
                        }
                    }

                    // Two proxies per row
                    for (i in proxiesToShow.indices step 2) {
                        val row = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = (4 * dp).toInt()
                            }
                        }

                        for (j in 0..1) {
                            val idx = i + j
                            if (idx < proxiesToShow.size) {
                                val proxy = proxiesToShow[idx]
                                val isSelected = proxy.name == group.now

                                val proxyChip = LinearLayout(context).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = Gravity.CENTER_VERTICAL
                                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                        if (j == 0) marginEnd = (4 * dp).toInt()
                                        else marginStart = (4 * dp).toInt()
                                    }
                                    setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
                                    background = context.getDrawable(R.drawable.bg_proxy_item)?.mutate()?.apply {
                                        if (isSelected) {
                                            setTint(primaryColor)
                                        }
                                    }
                                }

                                val proxyName = TextView(context).apply {
                                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                    text = proxy.title.ifEmpty { proxy.name }
                                    textSize = 11f
                                    maxLines = 1
                                    setTextColor(if (isSelected) ContextCompat.getColor(context, android.R.color.white) else onSurfaceVariantColor)
                                }

                                val delayText = TextView(context).apply {
                                    textSize = 10f
                                    when {
                                        proxy.delay <= 0 -> {
                                            text = context.getString(R.string.no_delay)
                                            setTextColor(onSurfaceVariantColor)
                                        }
                                        proxy.delay < 200 -> {
                                            text = context.getString(R.string.format_delay_ms, proxy.delay)
                                            setTextColor(if (isSelected) ContextCompat.getColor(context, android.R.color.white) else 0xFF4CAF50.toInt())
                                        }
                                        proxy.delay < 500 -> {
                                            text = context.getString(R.string.format_delay_ms, proxy.delay)
                                            setTextColor(if (isSelected) ContextCompat.getColor(context, android.R.color.white) else 0xFFFF9800.toInt())
                                        }
                                        else -> {
                                            text = context.getString(R.string.format_delay_ms, proxy.delay)
                                            setTextColor(if (isSelected) ContextCompat.getColor(context, android.R.color.white) else 0xFFF44336.toInt())
                                        }
                                    }
                                }

                                proxyChip.addView(proxyName)
                                proxyChip.addView(delayText)
                                row.addView(proxyChip)
                            } else {
                                // Empty spacer
                                row.addView(View(context).apply {
                                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                                })
                            }
                        }
                        proxyGrid.addView(row)
                    }

                    cardContent.addView(proxyGrid)

                    // "N more..." label if there are more proxies
                    if (group.proxies.size > 6) {
                        val moreLabel = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = (4 * dp).toInt()
                            }
                            text = "+${group.proxies.size - 6} ${context.getString(R.string.more)}"
                            textSize = 11f
                            setTextColor(onSurfaceVariantColor)
                            gravity = Gravity.CENTER
                        }
                        cardContent.addView(moreLabel)
                    }
                }

                card.addView(cardContent)

                card.setOnClickListener {
                    requests.trySend(Request.OpenProxy)
                }

                container.addView(card)
            }
        }
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            AlertDialog.Builder(context)
                .setView(binding.root)
                .show()
        }
    }

    fun showAddProfileSheet() {
        val dialog = AppBottomSheetDialog(context)

        val sheetBinding = DesignSheetAddProfileBinding
            .inflate(context.layoutInflater, dialog.window?.decorView as ViewGroup?, false)

        sheetBinding.master = this

        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    init {
        binding.self = this

        binding.colorClashStarted = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)

        binding.hasProfiles = false
        binding.hasTrafficInfo = false
        binding.hasExpireInfo = false

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_settings -> {
                    requests.trySend(Request.OpenSettings)
                    false
                }
                else -> false
            }
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
