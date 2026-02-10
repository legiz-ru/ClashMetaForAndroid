package com.github.kr328.clash.design

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

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
        AddFromFile,
        AddManually,
        OpenHelp,
        OpenAbout,
        SelectProxy,
        UrlTest,
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    val bottomNav: BottomNavigationView
        get() = binding.bottomNav

    // Track expanded state of accordion groups
    private val expandedGroups = mutableSetOf<String>()

    // Track URL testing state for each group
    private val urlTestingGroups = mutableSetOf<String>()

    // Cache for loaded icons
    private val iconCache = ConcurrentHashMap<String, Bitmap?>()
    private val iconExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingSelectGroup: String? = null
    private var pendingSelectName: String? = null
    private var pendingUrlTestGroup: String? = null

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

                val elapsed = System.currentTimeMillis() - profile.updatedAt
                binding.profileUpdated = elapsed.elapsedIntervalString(context)

                binding.profileSupportUrl = profile.supportUrl
                binding.profileWebPageUrl = profile.profileWebPageUrl
                binding.profileAnnounce = profile.announce.replace("\\n", "\n")
                binding.profileLogoUrl = profile.profileLogo
                binding.profileTitleOverride = profile.profileTitle

                // Wire click listeners for support/webpage icons
                binding.profileSupportIcon?.setOnClickListener {
                    if (profile.supportUrl.isNotEmpty()) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(profile.supportUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
                binding.profileWebpageIcon?.setOnClickListener {
                    if (profile.profileWebPageUrl.isNotEmpty()) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(profile.profileWebPageUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }

                // Override logo and title from profile headers
                if (profile.profileLogo.isNotEmpty()) {
                    loadIconAsync(profile.profileLogo, binding.appLogo)
                } else {
                    binding.appLogo.setImageResource(R.drawable.ic_clash)
                    binding.appLogo.imageTintList = null
                }
                if (profile.profileTitle.isNotEmpty()) {
                    binding.appTitle.text = profile.profileTitle
                } else {
                    binding.appTitle.setText(R.string.application_name)
                }
            } else {
                binding.hasTrafficInfo = false
                binding.hasExpireInfo = false
                binding.profileTrafficUsed = null
                binding.profileTrafficTotal = null
                binding.profileExpire = null
                binding.profileUpdated = null
                binding.profileSupportUrl = null
                binding.profileWebPageUrl = null
                binding.profileAnnounce = null
                binding.profileLogoUrl = null
                binding.profileTitleOverride = null
                // Reset logo and title to defaults
                binding.appLogo.setImageResource(R.drawable.ic_clash)
                binding.appLogo.imageTintList = null
                binding.appTitle.setText(R.string.application_name)
            }
        }
    }

    private fun loadIconAsync(url: String, imageView: ImageView) {
        val cached = iconCache[url]
        if (cached != null) {
            imageView.setImageBitmap(cached)
            imageView.imageTintList = null
            return
        }

        iconExecutor.execute {
            try {
                val bitmap = if (url.startsWith("file://")) {
                    val path = url.removePrefix("file://")
                    val file = java.io.File(path)
                    if (file.exists()) BitmapFactory.decodeFile(path) else null
                } else {
                    val stream = URL(url).openStream()
                    val b = BitmapFactory.decodeStream(stream)
                    stream.close()
                    b
                }
                if (bitmap != null) {
                    iconCache[url] = bitmap
                    mainHandler.post {
                        imageView.setImageBitmap(bitmap)
                        imageView.imageTintList = null
                    }
                }
            } catch (_: Exception) {
                // Keep default icon
            }
        }
    }

    private fun delayColor(delay: Int): Int {
        return when {
            delay <= 0 -> 0x00000000
            delay < 200 -> 0xFF4CAF50.toInt() // green
            delay < 500 -> 0xFFFF9800.toInt() // orange
            else -> 0xFFF44336.toInt() // red
        }
    }

    private fun createDelayDot(dp: Float, delay: Int): View {
        val size = (10 * dp).toInt()
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = (4 * dp).toInt()
            }
            if (delay > 0) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(delayColor(delay))
                }
            } else {
                visibility = View.GONE
            }
        }
    }

    private fun createDelayText(dp: Float, delay: Int, useDots: Boolean): View {
        if (useDots) return createDelayDot(dp, delay)
        return TextView(context).apply {
            textSize = 13f
            when {
                delay <= 0 -> visibility = View.GONE
                else -> {
                    text = context.getString(R.string.format_delay_ms, delay)
                    setTextColor(delayColor(delay))
                }
            }
        }
    }

    suspend fun setProxyGroups(groups: List<Pair<String, ProxyGroup>>, useDots: Boolean = true) {
        withContext(Dispatchers.Main) {
            val container = binding.proxyGroupsContainer
            container.removeAllViews()

            val dp = context.resources.displayMetrics.density
            val primaryColor = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
            val secondaryContainerColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSecondaryContainer)
            val onSecondaryContainerColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
            val surfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSurfaceVariant)
            val onSurfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

            // Filter hidden groups on Kotlin side as safety net
            val visibleGroups = groups.filter { !it.second.hidden }
            val groupMap = visibleGroups.toMap()

            fun resolveSelectedInfo(groupName: String, now: String, visited: MutableSet<String> = mutableSetOf()): Pair<String, Int> {
                if (!visited.add(groupName)) return now to 0

                val currentGroup = groupMap[groupName] ?: return now to 0
                val selected = currentGroup.proxies.find { it.name == now }
                    ?: return now to 0

                val selectedDisplayName = selected.title.ifEmpty { selected.name }

                if (!selected.type.group) {
                    // This is a final proxy, not a group - just show its name
                    return selectedDisplayName to selected.delay
                }

                // This is a nested group - recurse to find the final proxy
                val nestedGroupName = selected.name
                val nestedGroup = groupMap[nestedGroupName] ?: return selectedDisplayName to selected.delay
                val (finalProxyName, finalDelay) = resolveSelectedInfo(nestedGroupName, nestedGroup.now, visited)

                // Show: "SelectedGroupName ➡️ FinalProxy"
                val displayText = "$selectedDisplayName ➡️ $finalProxyName"
                return displayText to finalDelay
            }

            for ((name, group) in visibleGroups) {
                val isExpanded = expandedGroups.contains(name)

                // Resolve selected info (supports nested groups)
                val (selectedInfoText, groupDelay) = resolveSelectedInfo(name, group.now)

                val card = MaterialCardView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (8 * dp).toInt()
                    }
                    radius = 16 * dp
                    cardElevation = 0f
                    setCardBackgroundColor(if (isExpanded) secondaryContainerColor else surfaceVariantColor)
                }

                val cardContent = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }

                // ======= COLLAPSED HEADER (always visible) =======
                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((16 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt())
                    isClickable = true
                    isFocusable = true
                    background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
                }

                // Group icon (from URL or default)
                val groupIcon = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).apply {
                        marginEnd = (12 * dp).toInt()
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                if (group.icon.isNotEmpty()) {
                    groupIcon.setImageResource(R.drawable.ic_baseline_vpn_lock)
                    groupIcon.imageTintList = ColorStateList.valueOf(primaryColor)
                    loadIconAsync(group.icon, groupIcon)
                } else {
                    groupIcon.visibility = View.GONE
                }

                // Group name and selected proxy info
                val nameColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val groupNameView = TextView(context).apply {
                    text = name
                    setTextColor(if (isExpanded) onSecondaryContainerColor else onSurfaceColor)
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val selectedInfo = TextView(context).apply {
                    text = selectedInfoText
                    setTextColor(if (isExpanded) onSecondaryContainerColor else onSurfaceVariantColor)
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                nameColumn.addView(groupNameView)
                nameColumn.addView(selectedInfo)

                // Group delay indicator (delay of the currently selected proxy)
                val groupDelayView = createDelayText(dp, groupDelay, useDots)
                groupDelayView.layoutParams = (groupDelayView.layoutParams as? LinearLayout.LayoutParams
                    ?: LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )).apply {
                    marginEnd = (8 * dp).toInt()
                }

                // URL Test button (tests delay for this group)
                val urlTestButton = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                        marginEnd = (8 * dp).toInt()
                    }
                    setImageResource(R.drawable.ic_baseline_speed)
                    imageTintList = ColorStateList.valueOf(if (isExpanded) onSecondaryContainerColor else onSurfaceVariantColor)
                    isClickable = true
                    isFocusable = true
                    background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
                    setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                    setOnClickListener {
                        pendingUrlTestGroup = name
                        requests.trySend(Request.UrlTest)
                    }
                }

                // Chevron indicator (rotates when expanded)
                val chevron = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt())
                    setImageResource(R.drawable.ic_mdi_chevron_right)
                    imageTintList = ColorStateList.valueOf(if (isExpanded) onSecondaryContainerColor else onSurfaceVariantColor)
                    rotation = if (isExpanded) 90f else 0f
                }

                headerRow.addView(groupIcon)
                headerRow.addView(nameColumn)
                headerRow.addView(groupDelayView)
                headerRow.addView(urlTestButton)
                headerRow.addView(chevron)
                cardContent.addView(headerRow)

                // ======= EXPANDED PROXY LIST (accordion content) =======
                val proxyListContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (isExpanded) View.VISIBLE else View.GONE
                }

                // Divider
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                    ).apply {
                        marginStart = (16 * dp).toInt()
                        marginEnd = (16 * dp).toInt()
                    }
                    setBackgroundColor(onSurfaceVariantColor and 0x1FFFFFFF)
                }
                proxyListContainer.addView(divider)

                for (proxy in group.proxies) {
                    val isSelected = proxy.name == group.now
                    val (proxyDisplayName, proxyDelay) = if (proxy.type.group) {
                        val nestedGroup = groupMap[proxy.name]
                        if (nestedGroup != null) {
                            resolveSelectedInfo(proxy.name, nestedGroup.now)
                        } else {
                            (proxy.title.ifEmpty { proxy.name }) to proxy.delay
                        }
                    } else {
                        (proxy.title.ifEmpty { proxy.name }) to proxy.delay
                    }

                    val proxyRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
                        isClickable = true
                        isFocusable = true
                        background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
                    }

                    // Checkmark for selected
                    val checkIcon = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply {
                            marginEnd = (8 * dp).toInt()
                        }
                        if (isSelected) {
                            setImageResource(R.drawable.ic_baseline_check)
                            imageTintList = ColorStateList.valueOf(primaryColor)
                        } else {
                            visibility = View.INVISIBLE
                        }
                    }

                    // Proxy name + subtitle column
                    val proxyInfo = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val proxyNameView = TextView(context).apply {
                        text = proxyDisplayName
                        textSize = 14f
                        setTextColor(if (isSelected) primaryColor else onSurfaceColor)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }

                    val proxySubtitle = TextView(context).apply {
                        text = proxy.subtitle.ifEmpty { proxy.type.name }
                        textSize = 11f
                        setTextColor(onSurfaceVariantColor)
                        maxLines = 1
                    }

                    proxyInfo.addView(proxyNameView)
                    proxyInfo.addView(proxySubtitle)

                    // Delay indicator
                    val delayView = createDelayText(dp, proxyDelay, useDots)

                    proxyRow.addView(checkIcon)
                    proxyRow.addView(proxyInfo)
                    proxyRow.addView(delayView)

                    proxyRow.setOnClickListener {
                        requestProxySelection(name, proxy.name)
                    }

                    // Row separator
                    if (proxy != group.proxies.last()) {
                        val rowDivider = View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, 1
                            ).apply {
                                marginStart = (44 * dp).toInt()
                                marginEnd = (16 * dp).toInt()
                            }
                            setBackgroundColor(onSurfaceVariantColor and 0x0FFFFFFF)
                        }
                        proxyListContainer.addView(proxyRow)
                        proxyListContainer.addView(rowDivider)
                    } else {
                        proxyListContainer.addView(proxyRow)
                    }
                }

                cardContent.addView(proxyListContainer)

                // Toggle accordion on header click
                headerRow.setOnClickListener {
                    if (expandedGroups.contains(name)) {
                        expandedGroups.remove(name)
                        proxyListContainer.visibility = View.GONE
                        chevron.animate().rotation(0f).setDuration(200).start()
                        card.setCardBackgroundColor(surfaceVariantColor)
                        groupNameView.setTextColor(onSurfaceColor)
                        selectedInfo.setTextColor(onSurfaceVariantColor)
                        chevron.imageTintList = ColorStateList.valueOf(onSurfaceVariantColor)
                        urlTestButton.imageTintList = ColorStateList.valueOf(onSurfaceVariantColor)
                    } else {
                        expandedGroups.add(name)
                        proxyListContainer.visibility = View.VISIBLE
                        chevron.animate().rotation(90f).setDuration(200).start()
                        card.setCardBackgroundColor(secondaryContainerColor)
                        groupNameView.setTextColor(onSecondaryContainerColor)
                        selectedInfo.setTextColor(onSecondaryContainerColor)
                        chevron.imageTintList = ColorStateList.valueOf(onSecondaryContainerColor)
                        urlTestButton.imageTintList = ColorStateList.valueOf(onSecondaryContainerColor)
                    }
                }

                card.addView(cardContent)
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
        sheetBinding.dialog = dialog

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

        // Position disconnect FAB above bottom nav dynamically after layout
        binding.bottomNav.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.bottomNav.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val dp = context.resources.displayMetrics.density
                val params = binding.disconnectFab.layoutParams as CoordinatorLayout.LayoutParams
                params.bottomMargin = binding.bottomNav.height + (12 * dp).toInt()
                binding.disconnectFab.layoutParams = params
            }
        })
    }

    fun requestSheet(dialog: Dialog, request: Request) {
        dialog.dismiss()
        requests.trySend(request)
    }

    private fun requestProxySelection(group: String, name: String) {
        pendingSelectGroup = group
        pendingSelectName = name
        requests.trySend(Request.SelectProxy)
    }

    fun consumePendingProxySelection(): Pair<String, String>? {
        val group = pendingSelectGroup ?: return null
        val name = pendingSelectName ?: return null

        pendingSelectGroup = null
        pendingSelectName = null

        return group to name
    }

    fun consumePendingUrlTestGroup(): String? {
        val group = pendingUrlTestGroup ?: return null
        pendingUrlTestGroup = null
        return group
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
