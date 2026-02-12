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


    // Cache for loaded icons
    private val iconCache = ConcurrentHashMap<String, Bitmap?>()
    private val iconExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingSelectGroup: String? = null
    private var pendingSelectName: String? = null
    private var pendingUrlTestGroup: String? = null

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

    private enum class GroupSheetSort {
        Default,
        Name,
        Delay,
    }

    private fun sortedProxies(group: ProxyGroup, sort: GroupSheetSort) = when (sort) {
        GroupSheetSort.Default -> group.proxies
        GroupSheetSort.Name -> group.proxies.sortedBy { it.title.ifEmpty { it.name }.lowercase(Locale.getDefault()) }
        GroupSheetSort.Delay -> group.proxies.sortedByDescending { it.delay }
    }

    private fun showProxyGroupSheet(
        groupName: String,
        group: ProxyGroup,
        groupMap: Map<String, ProxyGroup>,
        useDots: Boolean,
    ) {
        val dialog = AppBottomSheetDialog(context)
        val dp = context.resources.displayMetrics.density
        val onSurfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val surfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSurface)
        val surfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSurfaceVariant)

        fun resolveSelectedInfo(groupName: String, now: String, visited: MutableSet<String> = mutableSetOf()): Pair<String, Int> {
            if (!visited.add(groupName)) return now to 0

            val currentGroup = groupMap[groupName] ?: return now to 0
            val selected = currentGroup.proxies.find { it.name == now }
                ?: return now to 0

            val selectedDisplayName = selected.title.ifEmpty { selected.name }

            if (!selected.type.group) {
                return selectedDisplayName to selected.delay
            }

            val nestedGroup = groupMap[selected.name] ?: return selectedDisplayName to selected.delay
            val (finalProxyName, finalDelay) = resolveSelectedInfo(selected.name, nestedGroup.now, visited)
            return "$selectedDisplayName ➡️ $finalProxyName" to finalDelay
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), 0)
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        var currentSort = GroupSheetSort.Default
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = (8 * dp).toInt()
                bottomMargin = (16 * dp).toInt()
            }
        }

        fun rebuildList() {
            listContainer.removeAllViews()

            val proxies = sortedProxies(group, currentSort)
            proxies.forEachIndexed { index, proxy ->
                val isSelected = proxy.name == group.now
                val (proxyDisplayName, proxyDelay) = if (proxy.type.group) {
                    val nested = groupMap[proxy.name]
                    if (nested != null) resolveSelectedInfo(proxy.name, nested.now)
                    else proxy.title.ifEmpty { proxy.name } to proxy.delay
                } else {
                    proxy.title.ifEmpty { proxy.name } to proxy.delay
                }

                val row = MaterialCardView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (isSelected) bottomMargin = (8 * dp).toInt()
                    }
                    radius = 14 * dp
                    cardElevation = 0f
                    setCardBackgroundColor(if (isSelected) surfaceVariantColor else surfaceColor)
                    strokeWidth = if (isSelected) (1 * dp).toInt() else 0
                    strokeColor = onSurfaceVariantColor and 0x40FFFFFF
                    isClickable = true
                    isFocusable = true
                    background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
                    setOnClickListener {
                        requestProxySelection(groupName, proxy.name)
                        dialog.dismiss()
                    }
                }

                val rowContent = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
                }

                val proxyInfo = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val nameView = TextView(context).apply {
                    text = proxyDisplayName
                    textSize = 15f
                    setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(onSurfaceColor)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val subtitleView = TextView(context).apply {
                    text = proxy.subtitle.ifEmpty { proxy.type.name }
                    textSize = 12f
                    setTextColor(onSurfaceVariantColor)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val delayView = createDelayText(dp, proxyDelay, useDots)

                proxyInfo.addView(nameView)
                proxyInfo.addView(subtitleView)
                rowContent.addView(proxyInfo)
                rowContent.addView(delayView)
                row.addView(rowContent)
                listContainer.addView(row)

                if (!isSelected && index != proxies.lastIndex) {
                    listContainer.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (8 * dp).toInt(),
                        )
                    })
                }
            }
        }

        val sortButton = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt())
            setImageResource(R.drawable.ic_baseline_view_list)
            imageTintList = ColorStateList.valueOf(onSurfaceVariantColor)
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            isClickable = true
            isFocusable = true
            background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
            setOnClickListener { anchor ->
                androidx.appcompat.widget.PopupMenu(context, anchor).apply {
                    menu.add(0, 1, 0, R.string.default_)
                    menu.add(0, 2, 1, R.string.name)
                    menu.add(0, 3, 2, R.string.delay)

                    when (currentSort) {
                        GroupSheetSort.Default -> menu.findItem(1).isChecked = true
                        GroupSheetSort.Name -> menu.findItem(2).isChecked = true
                        GroupSheetSort.Delay -> menu.findItem(3).isChecked = true
                    }

                    menu.setGroupCheckable(0, true, true)
                    setOnMenuItemClickListener {
                        currentSort = when (it.itemId) {
                            2 -> GroupSheetSort.Name
                            3 -> GroupSheetSort.Delay
                            else -> GroupSheetSort.Default
                        }
                        rebuildList()
                        true
                    }
                    show()
                }
            }
        }

        val titleView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = groupName
            gravity = Gravity.CENTER
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onSurfaceColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val speedButton = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt())
            setImageResource(R.drawable.ic_baseline_speed)
            imageTintList = ColorStateList.valueOf(onSurfaceVariantColor)
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            isClickable = true
            isFocusable = true
            background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
            setOnClickListener {
                pendingUrlTestGroup = groupName
                requests.trySend(Request.UrlTest)
            }
        }

        titleRow.addView(sortButton)
        titleRow.addView(titleView)
        titleRow.addView(speedButton)
        root.addView(titleRow)
        root.addView(listContainer)

        rebuildList()

        dialog.setContentView(root)
        dialog.show()

        // Auto-test delays for this group as soon as the sheet opens.
        pendingUrlTestGroup = groupName
        requests.trySend(Request.UrlTest)
    }

    suspend fun setProxyGroups(groups: List<Pair<String, ProxyGroup>>, useDots: Boolean = true) {
        withContext(Dispatchers.Main) {
            val container = binding.proxyGroupsContainer
            container.removeAllViews()

            val dp = context.resources.displayMetrics.density
            val primaryColor = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
            val surfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSurface)
            val onSurfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

            val visibleGroups = groups.filter { !it.second.hidden }
            val groupMap = visibleGroups.toMap()

            fun resolveSelectedInfo(groupName: String, now: String, visited: MutableSet<String> = mutableSetOf()): Pair<String, Int> {
                if (!visited.add(groupName)) return now to 0

                val currentGroup = groupMap[groupName] ?: return now to 0
                val selected = currentGroup.proxies.find { it.name == now }
                    ?: return now to 0

                val selectedDisplayName = selected.title.ifEmpty { selected.name }
                if (!selected.type.group) {
                    return selectedDisplayName to selected.delay
                }

                val nestedGroup = groupMap[selected.name] ?: return selectedDisplayName to selected.delay
                val (finalProxyName, finalDelay) = resolveSelectedInfo(selected.name, nestedGroup.now, visited)
                return "$selectedDisplayName ➡️ $finalProxyName" to finalDelay
            }

            visibleGroups.forEach { (name, group) ->
                val (selectedInfoText, _) = resolveSelectedInfo(name, group.now)

                val card = MaterialCardView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        bottomMargin = (10 * dp).toInt()
                    }
                    radius = 16 * dp
                    cardElevation = if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) 0f else 1.5f * dp
                    setCardBackgroundColor(surfaceColor)
                    isClickable = true
                    isFocusable = true
                    background = context.getDrawable(R.drawable.bg_accordion_header_ripple)
                    setOnClickListener {
                        showProxyGroupSheet(name, group, groupMap, useDots)
                    }
                }

                val content = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((16 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt())
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

                nameColumn.addView(TextView(context).apply {
                    text = name
                    setTextColor(onSurfaceColor)
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })

                nameColumn.addView(TextView(context).apply {
                    text = selectedInfoText
                    setTextColor(onSurfaceVariantColor)
                    textSize = 13f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })

                val chevron = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt())
                    setImageResource(R.drawable.ic_mdi_chevron_right)
                    imageTintList = ColorStateList.valueOf(onSurfaceVariantColor)
                }

                content.addView(groupIcon)
                content.addView(nameColumn)
                content.addView(chevron)
                card.addView(content)
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

        // Easter egg: 15 taps on logo unlocks Summer mode
        binding.appLogo.setOnClickListener {
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
                // Reset counter if timeout
                logoTapTimeoutRunnable = Runnable {
                    logoTapCount = 0
                }.also {
                    mainHandler.postDelayed(it, logoTapTimeout)
                }
            }
        }

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
