package com.github.kr328.clash.design

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.component.TvNavigationDrawer
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.databinding.DesignSheetAddProfileBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.databinding.OnRebindCallback
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
        TvImport,
        OpenHelp,
        OpenAbout,
        CheckUpdate,
        SelectProxy,
        UrlTest,
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    val isTv = TvUtils.isTv(context)

    // Use the side-rail navigation drawer on TV and on tablet/foldable devices
    // in landscape mode (smallest screen dimension ≥ 600 dp — not a phone).
    private val useDrawerNav: Boolean = isTv || run {
        val cfg = context.resources.configuration
        cfg.smallestScreenWidthDp >= 600 &&
            cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private val tvDrawer: TvNavigationDrawer? = if (useDrawerNav) {
        TvNavigationDrawer(context, TvNavigationDrawer.NavItem.Home).apply {
            onNavigate = { item ->
                when (item) {
                    TvNavigationDrawer.NavItem.Home -> {} // Already on home
                    TvNavigationDrawer.NavItem.Profiles -> requests.trySend(Request.OpenProfiles)
                    TvNavigationDrawer.NavItem.Settings -> requests.trySend(Request.OpenSettings)
                }
            }
            onToggleStatus = { requests.trySend(Request.ToggleStatus) }
        }
    } else null

    private val rootView: View = if (useDrawerNav) {
        // Drawer layout: hide bottom nav and FAB — drawer provides navigation and toggle
        binding.bottomNav.visibility = View.GONE
        binding.disconnectFab.visibility = View.GONE
        tvDrawer!!.wrapContent(binding.root)
    } else {
        binding.root
    }

    override val root: View
        get() = rootView

    val bottomNav: BottomNavigationView
        get() = binding.bottomNav

    private enum class GroupSheetSort {
        Default,
        Name,
        Delay,
    }

    // Cache for loaded icons
    private val iconCache = ConcurrentHashMap<String, Bitmap?>()
    private val iconExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingSelectGroup: String? = null
    private var pendingSelectName: String? = null
    private var pendingUrlTestGroup: String? = null
    var pendingUpdateTag: String? = null
    var pendingUpdateUrl: String? = null

    private var latestProxyGroups: Map<String, ProxyGroup> = emptyMap()
    private var latestUseDots: Boolean = true
    private var openedProxyGroupName: String? = null
    private var openedProxyGroupSort: GroupSheetSort = GroupSheetSort.Default
    private var proxyGroupDialog: AppBottomSheetDialog? = null
    private var proxyGroupRecyclerView: RecyclerView? = null

    // Easter egg: tap counter for summer mode
    private var logoTapCount = 0
    private val logoTapTimeout = 2000L // 2 seconds timeout between taps
    private var logoTapTimeoutRunnable: Runnable? = null

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            if (!running) {
                // Clear stale proxy groups so they don't flash when VPN restarts
                latestProxyGroups = emptyMap()
                proxyGroupRecyclerView = null
                proxyGroupDialog?.dismiss()
                val container = binding.proxyGroupsContainer
                container.removeAllViews()
                // Add a centered spinner as placeholder (shown when container becomes visible on next start)
                val dp = context.resources.displayMetrics.density
                val spinner = android.widget.ProgressBar(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (48 * dp).toInt(),
                        (48 * dp).toInt(),
                    ).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        topMargin = (32 * dp).toInt()
                        bottomMargin = (32 * dp).toInt()
                    }
                }
                container.gravity = Gravity.CENTER_HORIZONTAL
                container.addView(spinner)
            }
            binding.clashRunning = running
            tvDrawer?.isClashRunning = running
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
                delay == 65535 -> {
                    text = context.getString(R.string.timeout)
                    setTextColor(0xFFF44336.toInt())
                }
                else -> {
                    text = context.getString(R.string.format_delay_ms, delay)
                    setTextColor(delayColor(delay))
                }
            }
        }
    }

    private fun resolveDelay(
        groupMap: Map<String, ProxyGroup>,
        groupName: String,
        now: String,
        visited: MutableSet<String> = mutableSetOf(),
    ): Int {
        if (!visited.add(groupName)) return 0
        val currentGroup = groupMap[groupName] ?: return 0
        val selected = currentGroup.proxies.find { it.name == now } ?: return 0
        if (!selected.type.group) return selected.delay
        val nestedGroup = groupMap[selected.name] ?: return selected.delay
        if (nestedGroup.type == com.github.kr328.clash.core.model.Proxy.Type.LoadBalance) {
            return nestedGroup.proxies.filter { it.delay in 1..65534 }
                .minOfOrNull { it.delay } ?: 0
        }
        return resolveDelay(groupMap, selected.name, nestedGroup.now, visited)
    }

    private fun resolveSelectedInfo(
        groupMap: Map<String, ProxyGroup>,
        groupName: String,
        now: String,
    ): Pair<String, Int> {
        val currentGroup = groupMap[groupName] ?: return now to 0
        val selected = currentGroup.proxies.find { it.name == now }
            ?: return now to 0

        val selectedDisplayName = selected.title.ifEmpty { selected.name }

        if (!selected.type.group) {
            return selectedDisplayName to selected.delay
        }

        // For nested groups, resolve delay from the chain but always display the group name only
        val nestedGroup = groupMap[selected.name] ?: return selectedDisplayName to selected.delay
        val finalDelay = resolveDelay(groupMap, selected.name, nestedGroup.now)
        return selectedDisplayName to finalDelay
    }

    private fun sortedProxies(group: ProxyGroup): List<com.github.kr328.clash.core.model.Proxy> {
        val proxies = group.proxies.toList()

        return when (openedProxyGroupSort) {
            GroupSheetSort.Default -> proxies
            GroupSheetSort.Name -> proxies.sortedBy { it.title.ifEmpty { it.name }.lowercase(Locale.getDefault()) }
            GroupSheetSort.Delay -> proxies.sortedBy {
                val delay = it.delay
                if (delay > 0) delay else Int.MAX_VALUE
            }
        }
    }

    private fun openSortPicker(onPicked: () -> Unit) {
        val options = arrayOf(
            context.getString(R.string.default_),
            context.getString(R.string.name),
            context.getString(R.string.delay),
        )

        val initial = when (openedProxyGroupSort) {
            GroupSheetSort.Default -> 0
            GroupSheetSort.Name -> 1
            GroupSheetSort.Delay -> 2
        }

        var selected = initial

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.sort)
            .setSingleChoiceItems(options, initial) { _, which ->
                selected = which
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                openedProxyGroupSort = when (selected) {
                    1 -> GroupSheetSort.Name
                    2 -> GroupSheetSort.Delay
                    else -> GroupSheetSort.Default
                }
                onPicked()
            }
            .show()
    }

    private fun buildProxyGroupSheet(
        groupName: String,
        group: ProxyGroup,
        groupMap: Map<String, ProxyGroup>,
        useDots: Boolean,
    ): View {
        val dp = context.resources.displayMetrics.density
        val maxSheetHeight = (context.resources.displayMetrics.heightPixels * 0.7f).toInt()
        val topBarHeight = (56 * dp).toInt()
        val listHeight = (maxSheetHeight - topBarHeight).coerceAtLeast((180 * dp).toInt())
        val primaryColor = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        val secondaryContainerColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSecondaryContainer)
        val onSecondaryContainerColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
        val onSurfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

        val isSelectorGroup = group.type == com.github.kr328.clash.core.model.Proxy.Type.Selector
        val isSmartGroup = group.type == com.github.kr328.clash.core.model.Proxy.Type.Smart

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            val topBar = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            }

            val sortButton = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt(), Gravity.START or Gravity.CENTER_VERTICAL)
                setImageResource(R.drawable.ic_baseline_sort)
                imageTintList = ColorStateList.valueOf(onSurfaceVariantColor)
                background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
                setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    openSortPicker {
                        refreshOpenedProxyGroupSheet()
                    }
                }
            }

            val titleView = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                )
                text = groupName
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(onSurfaceColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val urlTestButton = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt(), Gravity.END or Gravity.CENTER_VERTICAL)
                setImageResource(R.drawable.ic_baseline_speed)
                imageTintList = ColorStateList.valueOf(onSurfaceVariantColor)
                background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
                setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    pendingUrlTestGroup = groupName
                    requests.trySend(Request.UrlTest)
                }
            }

            topBar.addView(sortButton)
            topBar.addView(titleView)
            topBar.addView(urlTestButton)
            addView(topBar)

            // Build each proxy row as a standalone view.
            // Using unique item view types prevents RecyclerView from recycling
            // views across different positions — safe for pre-built complex rows.
            fun buildProxyRow(proxy: com.github.kr328.clash.core.model.Proxy): View {
                val isSelected = proxy.name == group.now
                val proxyDisplayName = proxy.title.ifEmpty { proxy.name }
                val proxyDelay = if (proxy.type.group) {
                    val nestedGroup = groupMap[proxy.name]
                    if (nestedGroup != null) {
                        if (proxy.type == com.github.kr328.clash.core.model.Proxy.Type.LoadBalance) {
                            nestedGroup.proxies.filter { it.delay in 1..65534 }
                                .minOfOrNull { it.delay } ?: 0
                        } else {
                            resolveDelay(groupMap, proxy.name, nestedGroup.now)
                        }
                    } else {
                        proxy.delay
                    }
                } else {
                    proxy.delay
                }

                val nestedSmartGroup62 =
                    if (!isSmartGroup && proxy.type == com.github.kr328.clash.core.model.Proxy.Type.Smart)
                        groupMap[proxy.name] else null
                val hasSmartData62 = nestedSmartGroup62?.proxies?.any { it.rank.isNotEmpty() && it.weight > 0.0 } == true

                val weightDialogMsg: (() -> String)? = when {
                    isSmartGroup -> {
                        {
                            val weight = (proxy.weight * 100).toInt()
                            when (proxy.rank) {
                                "MostUsed" -> context.getString(R.string.proxies_smart_most_used_tip, weight)
                                "OccasionalUsed" -> context.getString(R.string.proxies_smart_occasional_used_tip, weight)
                                "RarelyUsed" -> context.getString(R.string.proxies_smart_rarely_used_tip, weight)
                                else -> context.getString(R.string.proxies_smart_no_data)
                            }
                        }
                    }
                    nestedSmartGroup62 != null -> {
                        {
                            if (!hasSmartData62) {
                                context.getString(R.string.proxies_smart_no_data)
                            } else {
                                nestedSmartGroup62.proxies
                                    .filter { it.rank.isNotEmpty() }
                                    .joinToString("\n") { p ->
                                        val rankLabel = when (p.rank) {
                                            "MostUsed" -> context.getString(R.string.proxies_smart_most_used)
                                            "OccasionalUsed" -> context.getString(R.string.proxies_smart_occasional_used)
                                            "RarelyUsed" -> context.getString(R.string.proxies_smart_rarely_used)
                                            else -> p.rank
                                        }
                                        "${p.title.ifEmpty { p.name }}: $rankLabel (${(p.weight * 100).toInt()})"
                                    }.ifEmpty { context.getString(R.string.proxies_smart_no_data) }
                            }
                        }
                    }
                    else -> null
                }

                fun showWeightDialog() {
                    val msg = weightDialogMsg?.invoke() ?: return
                    MaterialAlertDialogBuilder(context)
                        .setMessage(msg)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }

                val rowCard = MaterialCardView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        bottomMargin = (6 * dp).toInt()
                    }
                    radius = 14 * dp
                    cardElevation = 0f
                    if (isSelected) {
                        setCardBackgroundColor(secondaryContainerColor)
                        strokeWidth = (1 * dp).toInt()
                        strokeColor = primaryColor
                    } else {
                        setCardBackgroundColor(0x00000000)
                        strokeWidth = 0
                    }
                }

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
                    isClickable = true
                    isFocusable = true
                    background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
                    setOnClickListener {
                        if (isSelectorGroup) {
                            requestProxySelection(groupName, proxy.name)
                            proxyGroupDialog?.dismiss()
                        } else {
                            MaterialAlertDialogBuilder(context)
                                .setMessage("Данная группа является автоматической, в ней запрещён ручной выбор")
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                    if (weightDialogMsg != null) {
                        setOnLongClickListener {
                            showWeightDialog()
                            true
                        }
                    }
                }

                val infoColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val nameView = TextView(context).apply {
                    text = proxyDisplayName
                    textSize = 16f
                    setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(if (isSelected) onSecondaryContainerColor else onSurfaceColor)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val subtitleView = TextView(context).apply {
                    text = proxy.subtitle.ifEmpty { proxy.type.name }
                    textSize = 12f
                    setTextColor(if (isSelected) onSecondaryContainerColor else onSurfaceVariantColor)
                    alpha = if (isSelected) 0.85f else 1f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                infoColumn.addView(nameView)
                infoColumn.addView(subtitleView)

                val rightColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                }

                if (isSmartGroup) {
                    val iconRes = when (proxy.rank) {
                        "MostUsed" -> R.drawable.ic_mdi_shield
                        "OccasionalUsed" -> R.drawable.ic_mdi_shield_half_full
                        "RarelyUsed" -> R.drawable.ic_mdi_shield_outline
                        else -> R.drawable.ic_mdi_timelapse
                    }
                    val rankIcon = ImageView(context).apply {
                        val sz = (16 * dp).toInt()
                        layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                            marginEnd = (4 * dp).toInt()
                        }
                        setImageResource(iconRes)
                        imageTintList = ColorStateList.valueOf(
                            if (isSelected) onSecondaryContainerColor else onSurfaceVariantColor
                        )
                        isClickable = true
                        isFocusable = true
                        background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
                        setOnClickListener { showWeightDialog() }
                        setOnLongClickListener { showWeightDialog(); true }
                    }
                    rightColumn.addView(rankIcon)
                }

                if (nestedSmartGroup62 != null) {
                    val shieldIconRes = if (hasSmartData62)
                        R.drawable.ic_mdi_shield_check_outline
                    else
                        R.drawable.ic_mdi_timelapse
                    val shieldIcon = ImageView(context).apply {
                        val sz = (16 * dp).toInt()
                        layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                            marginEnd = (4 * dp).toInt()
                        }
                        setImageResource(shieldIconRes)
                        imageTintList = ColorStateList.valueOf(
                            if (isSelected) onSecondaryContainerColor else onSurfaceVariantColor
                        )
                        isClickable = true
                        isFocusable = true
                        background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
                        setOnClickListener { showWeightDialog() }
                        setOnLongClickListener { showWeightDialog(); true }
                    }
                    rightColumn.addView(shieldIcon)
                }

                rightColumn.addView(createDelayText(dp, proxyDelay, useDots))
                row.addView(infoColumn)
                row.addView(rightColumn)
                rowCard.addView(row)
                return rowCard
            }

            val proxyRows = sortedProxies(group).map { buildProxyRow(it) }

            val rv = RecyclerView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    listHeight,
                )
                layoutManager = LinearLayoutManager(context)
                clipToPadding = false
                setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), (12 * dp).toInt())
                // Each position gets a unique view type so RecyclerView never
                // tries to rebind a pre-built view into the wrong slot.
                adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun getItemCount() = proxyRows.size
                    override fun getItemViewType(position: Int) = position
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                        object : RecyclerView.ViewHolder(proxyRows[viewType]) {}
                    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
                }
            }
            proxyGroupRecyclerView = rv
            addView(rv)
        }
    }

    private fun refreshOpenedProxyGroupSheet() {
        val groupName = openedProxyGroupName ?: return
        val dialog = proxyGroupDialog ?: return

        if (!dialog.isShowing) return

        val group = latestProxyGroups[groupName] ?: run {
            dialog.dismiss()
            return
        }

        // Save RecyclerView scroll position (first visible item + pixel offset within it).
        val lm = proxyGroupRecyclerView?.layoutManager as? LinearLayoutManager
        val savedFirstPos = lm?.findFirstVisibleItemPosition() ?: 0
        val savedFirstOffset = lm?.findViewByPosition(savedFirstPos)?.top ?: 0

        dialog.setContentView(buildProxyGroupSheet(groupName, group, latestProxyGroups, latestUseDots))

        // Restore scroll position before the first layout pass so RecyclerView
        // never renders at position 0 first. LinearLayoutManager stores the
        // pending scroll and applies it during onLayoutChildren — no visible flash.
        if (savedFirstPos > 0 || savedFirstOffset < 0) {
            (proxyGroupRecyclerView?.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(savedFirstPos, savedFirstOffset)
        }

        // On TV: update the dialog focus trap after content replacement so UP/DOWN
        // keys that hit the list boundary stay inside the dialog instead of jumping
        // to the sidebar toggle button.
        if (isTv) {
            dialog.focusTrapView = proxyGroupRecyclerView
            proxyGroupRecyclerView?.post { proxyGroupRecyclerView?.requestFocus() }
        }
    }

    private fun openProxyGroupSheet(groupName: String) {
        val group = latestProxyGroups[groupName] ?: return

        openedProxyGroupName = groupName

        val dialog = proxyGroupDialog ?: AppBottomSheetDialog(context, forceExpanded = true).also {
            proxyGroupDialog = it
            it.setOnDismissListener {
                openedProxyGroupName = null
                // On TV: clear the dialog focus target so the drawer redirect logic
                // stops trying to return focus to the (now closed) dialog.
                if (isTv) tvDrawer?.dialogFocusTarget = null
            }
        }

        dialog.setContentView(buildProxyGroupSheet(groupName, group, latestProxyGroups, latestUseDots))
        if (!dialog.isShowing) dialog.show()

        // On TV: register the dialog's RecyclerView as the active focus target so
        // the drawer redirect logic (TvNavigationDrawer) returns focus here when
        // focus accidentally escapes to the sidebar. Also trap UP/DOWN keys.
        if (isTv) {
            tvDrawer?.dialogFocusTarget = { proxyGroupRecyclerView?.takeIf { it.isAttachedToWindow } }
            dialog.focusTrapView = proxyGroupRecyclerView
            proxyGroupRecyclerView?.post { proxyGroupRecyclerView?.requestFocus() }
        }

        pendingUrlTestGroup = groupName
        requests.trySend(Request.UrlTest)
    }

    suspend fun setProxyGroups(
        groups: List<Pair<String, ProxyGroup>>,
        useDots: Boolean = true,
    ) {
        withContext(Dispatchers.Main) {
            val container = binding.proxyGroupsContainer
            container.removeAllViews()
            container.gravity = Gravity.TOP

            val dp = context.resources.displayMetrics.density
            val isDarkTheme =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val primaryColor = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
            val surfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSurface)
            val surfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSurfaceVariant)
            val onSurfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

            val visibleGroups = groups.filter { !it.second.hidden }
            val groupMap = visibleGroups.toMap()
            latestProxyGroups = groupMap
            latestUseDots = useDots
            refreshOpenedProxyGroupSheet()

            for ((name, group) in visibleGroups) {
                val (selectedInfoText, _) = resolveSelectedInfo(groupMap, name, group.now)

                val card = MaterialCardView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (8 * dp).toInt()
                    }
                    radius = 16 * dp
                    cardElevation = if (isDarkTheme) 0f else 2 * dp
                    setCardBackgroundColor(if (isDarkTheme) surfaceVariantColor else surfaceColor)
                }

                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((16 * dp).toInt(), (18 * dp).toInt(), (12 * dp).toInt(), (18 * dp).toInt())
                    isClickable = true
                    isFocusable = true
                    background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
                }

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

                val nameColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val groupNameView = TextView(context).apply {
                    text = name
                    setTextColor(onSurfaceColor)
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val selectedInfo = TextView(context).apply {
                    text = selectedInfoText
                    setTextColor(onSurfaceVariantColor)
                    textSize = 12f
                    maxLines = 1
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                }

                nameColumn.addView(groupNameView)
                nameColumn.addView(selectedInfo)

                val chevron = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt())
                    setImageResource(R.drawable.ic_mdi_chevron_right)
                    imageTintList = ColorStateList.valueOf(onSurfaceVariantColor)
                }

                headerRow.addView(groupIcon)
                headerRow.addView(nameColumn)
                headerRow.addView(chevron)
                headerRow.setOnClickListener {
                    openProxyGroupSheet(name)
                }

                card.addView(headerRow)
                container.addView(card)
            }
        }
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            MaterialAlertDialogBuilder(context)
                .setView(binding.root)
                .setPositiveButton(R.string.check_for_updates) { _, _ ->
                    requests.trySend(Request.CheckUpdate)
                }
                .show()
        }
    }

    suspend fun showUpdateAvailableDialog(tagName: String, downloadUrl: String) {
        withContext(Dispatchers.Main) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.update_available_title)
                .setMessage(context.getString(R.string.update_available_message, tagName))
                .setPositiveButton(R.string.update_download) { _, _ ->
                    pendingUpdateTag = tagName
                    pendingUpdateUrl = downloadUrl
                    requests.trySend(Request.CheckUpdate)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    suspend fun showUpdateNotFoundDialog() {
        withContext(Dispatchers.Main) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.update_not_found_title)
                .setMessage(R.string.update_not_found_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    suspend fun showUpdateErrorDialog(message: String) {
        withContext(Dispatchers.Main) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.update_error_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    fun showAddProfileSheet() {
        val dialog = AppBottomSheetDialog(context)

        val sheetBinding = DesignSheetAddProfileBinding
            .inflate(context.layoutInflater, dialog.window?.decorView as ViewGroup?, false)

        sheetBinding.master = this
        sheetBinding.dialog = dialog
        sheetBinding.isTv = isTv

        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    init {
        binding.self = this

        binding.colorClashStarted = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)

        // Easter egg: 15 taps on logo unlocks Summer mode (touch-only, no D-pad)
        binding.appLogo.isFocusable = false
        binding.appLogo.isFocusableInTouchMode = false
        binding.appLogo.setOnTouchListener { _, event ->
            // Only handle touchscreen input, ignore D-pad
            if (event.source and android.view.InputDevice.SOURCE_TOUCHSCREEN != 0) {
                if (event.action == MotionEvent.ACTION_UP) {
                    logoTapTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }

                    logoTapCount++

                    if (logoTapCount >= 15) {
                        val uiStore = com.github.kr328.clash.design.store.UiStore(context)
                        uiStore.summerModeUnlocked = true
                        logoTapCount = 0

                        android.widget.Toast.makeText(
                            context,
                            "🥒 Всегда Лето разблокирован! Проверьте настройки темы",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        logoTapTimeoutRunnable = Runnable {
                            logoTapCount = 0
                        }.also {
                            mainHandler.postDelayed(it, logoTapTimeout)
                        }
                    }
                }
                true // consume all touchscreen events to receive full DOWN→UP sequence
            } else {
                false
            }
        }

        binding.hasProfiles = false
        binding.hasTrafficInfo = false
        binding.hasExpireInfo = false

        // Drawer layout (TV or tablet/foldable landscape): D-pad focus + hide FAB on rebind
        if (useDrawerNav) {
            (binding.profileCard as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

            // Prevent data binding from re-showing the FAB after rebind
            binding.addOnRebindCallback(object : OnRebindCallback<DesignMainBinding>() {
                override fun onBound(binding: DesignMainBinding?) {
                    binding?.disconnectFab?.visibility = View.GONE
                }
            })
        }

        if (!useDrawerNav) {
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

        // When using the drawer, skip landscape content-width constraining — the drawer
        // already divides the screen and the computed padding (based on full screen width)
        // would make the content area unnecessarily narrow.
        if (useDrawerNav && context is androidx.appcompat.app.AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener(adaptLandscape = false) {
                if (surface.insets != it) {
                    surface.insets = it
                }
            }
        }
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

    fun consumePendingUpdate(): Pair<String, String>? {
        val tag = pendingUpdateTag ?: return null
        val url = pendingUpdateUrl ?: return null
        pendingUpdateTag = null
        pendingUpdateUrl = null
        return tag to url
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
