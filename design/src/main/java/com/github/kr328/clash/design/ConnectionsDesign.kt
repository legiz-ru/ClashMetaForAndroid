package com.github.kr328.clash.design

import android.content.Context
import android.content.pm.PackageManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.core.model.ConnectionSnapshot
import com.github.kr328.clash.design.adapter.ConnectionGroupAdapter
import com.github.kr328.clash.design.databinding.DesignConnectionsBinding
import com.github.kr328.clash.design.model.ConnectionGroup
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ConnectionsDesign(context: Context) : Design<ConnectionsDesign.Request>(context) {

    enum class DisplayField(val labelRes: Int) {
        HOST(R.string.conn_host),
        TYPE(R.string.conn_type),
        CHAINS(R.string.conn_proxy_chain),
        RULE(R.string.conn_rule),
        UPLOAD(R.string.conn_upload),
        DOWNLOAD(R.string.conn_download),
        TIME(R.string.conn_duration),
        SOURCE_IP(R.string.conn_src_ip);

        companion object {
            val DEFAULTS: Set<DisplayField> = setOf(HOST, TYPE, CHAINS, UPLOAD, DOWNLOAD, TIME)
        }
    }

    enum class SortField(val labelRes: Int) {
        SPEED(R.string.connections_sort_speed),
        UPLOAD(R.string.conn_upload),
        DOWNLOAD(R.string.conn_download),
        HOST(R.string.conn_host),
        CHAINS(R.string.conn_proxy_chain),
        RULE(R.string.conn_rule),
        TYPE(R.string.conn_type),
        TIME(R.string.conn_duration),
        SOURCE_IP(R.string.conn_src_ip);

        companion object {
            val DEFAULT = SPEED
        }
    }

    enum class SortDirection { ASC, DESC }

    companion object {
        var visibleFields: Set<DisplayField> = DisplayField.DEFAULTS
        var sortField: SortField = SortField.DEFAULT
        var sortDirection: SortDirection = SortDirection.DESC

        fun sortConnectionList(connections: List<Connection>): List<Connection> {
            val comparator: Comparator<Connection> = when (sortField) {
                SortField.SPEED   -> compareBy { it.upload + it.download }
                SortField.UPLOAD  -> compareBy { it.upload }
                SortField.DOWNLOAD -> compareBy { it.download }
                SortField.HOST    -> compareBy { it.metadata.host.ifEmpty { it.metadata.destinationIP } }
                SortField.CHAINS  -> compareBy { it.chains.firstOrNull() ?: "" }
                SortField.RULE    -> compareBy { it.rule }
                SortField.TYPE    -> compareBy { it.metadata.network }
                SortField.TIME    -> compareBy { it.start }
                SortField.SOURCE_IP -> compareBy { it.metadata.sourceIP }
            }
            return if (sortDirection == SortDirection.DESC)
                connections.sortedWith(comparator.reversed())
            else
                connections.sortedWith(comparator)
        }
    }

    sealed class Request {
        data class OpenApp(val group: ConnectionGroup, val showClosed: Boolean) : Request()
        class KillAll : Request()
        data class CloseConnection(val id: String) : Request()
    }

    private val binding = DesignConnectionsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View get() = binding.root

    private val pm: PackageManager = context.packageManager
    private val appPackage = context.packageName

    private val activeAdapter = ConnectionGroupAdapter(context) { requests.trySend(Request.OpenApp(it, false)) }
    private val closedAdapter = ConnectionGroupAdapter(context) { requests.trySend(Request.OpenApp(it, true)) }

    private var isPaused = false
    private var isCachingClosed = false
    private var currentFilter = ""
    private var showingClosed = false

    private var lastSnapshot: ConnectionSnapshot = ConnectionSnapshot()
    private var closedConnections: List<Connection> = emptyList()
    private val closedIds = mutableSetOf<String>()

    // ─── Pause / Kill ────────────────────────────────────────────────────────

    fun togglePause() {
        isPaused = !isPaused
        binding.pauseBtn.setImageResource(
            if (isPaused) R.drawable.ic_baseline_play_arrow else R.drawable.ic_baseline_pause
        )
        binding.pauseBtn.contentDescription = context.getString(
            if (isPaused) R.string.connections_resume else R.string.connections_pause
        )
    }

    fun toggleCacheClosed() {
        isCachingClosed = !isCachingClosed
        val tint = if (isCachingClosed)
            context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        else
            context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        binding.cacheToggleBtn.setColorFilter(tint)
        if (!isCachingClosed) {
            closedConnections = emptyList()
            closedIds.clear()
            launch { refreshClosed() }
        }
        val msgRes = if (isCachingClosed) R.string.connections_cache_enabled
        else R.string.connections_cache_disabled
        launch { showToast(msgRes, ToastDuration.Short) }
    }

    fun requestKillAll() {
        launch {
            if (confirmKillAll()) requests.trySend(Request.KillAll())
        }
    }

    // ─── Filter dialog ───────────────────────────────────────────────────────

    fun showFilterDialog() {
        val fields = DisplayField.values()
        val labels = fields.map { context.getString(it.labelRes) }.toTypedArray()
        val checked = BooleanArray(fields.size) { fields[it] in visibleFields }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.connections_display_fields)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val selected = fields.filterIndexed { i, _ -> checked[i] }.toMutableSet()
                if (selected.isEmpty()) selected.addAll(DisplayField.DEFAULTS)
                visibleFields = selected
                updateFilterBtnTint()
                launch { if (showingClosed) refreshClosed() else refreshActive(lastSnapshot.connections) }
            }
            .show()
    }

    fun onFilterLongClick(): Boolean {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.connections_display_fields)
            .setMessage(R.string.connections_reset_display_fields)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.connections_reset_to_defaults) { _, _ ->
                visibleFields = DisplayField.DEFAULTS
                updateFilterBtnTint()
                launch { if (showingClosed) refreshClosed() else refreshActive(lastSnapshot.connections) }
            }
            .show()
        return true
    }

    // ─── Sort dialog ─────────────────────────────────────────────────────────

    fun showSortDialog() {
        val fields = SortField.values()
        val labels = fields.map { context.getString(it.labelRes) }.toTypedArray()
        var selectedIdx = fields.indexOf(sortField).coerceAtLeast(0)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.connections_sort_by)
            .setSingleChoiceItems(labels, selectedIdx) { _, which -> selectedIdx = which }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.connections_sort_asc) { _, _ ->
                sortField = fields[selectedIdx]
                sortDirection = SortDirection.ASC
                updateSortBtnIcon()
                launch { if (showingClosed) refreshClosed() else refreshActive(lastSnapshot.connections) }
            }
            .setPositiveButton(R.string.connections_sort_desc) { _, _ ->
                sortField = fields[selectedIdx]
                sortDirection = SortDirection.DESC
                updateSortBtnIcon()
                launch { if (showingClosed) refreshClosed() else refreshActive(lastSnapshot.connections) }
            }
            .show()
    }

    fun onSortLongClick(): Boolean {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.connections_sort_by)
            .setMessage(R.string.connections_reset_sort)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.connections_reset_to_defaults) { _, _ ->
                sortField = SortField.DEFAULT
                sortDirection = SortDirection.DESC
                updateSortBtnIcon()
                launch { if (showingClosed) refreshClosed() else refreshActive(lastSnapshot.connections) }
            }
            .show()
        return true
    }

    // ─── Icon state ──────────────────────────────────────────────────────────

    private fun updateFilterBtnTint() {
        val isDefault = visibleFields == DisplayField.DEFAULTS
        val color = if (isDefault)
            context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        else
            context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.filterBtn.setColorFilter(color)
    }

    private fun updateSortBtnIcon() {
        val isDefault = sortField == SortField.DEFAULT && sortDirection == SortDirection.DESC
        binding.sortBtn.setImageResource(
            if (sortDirection == SortDirection.ASC) R.drawable.ic_baseline_swap_vert
            else R.drawable.ic_baseline_sort
        )
        val color = if (isDefault)
            context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        else
            context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.sortBtn.setColorFilter(color)
    }

    // ─── Data ────────────────────────────────────────────────────────────────

    private suspend fun confirmKillAll(): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.connections_kill_all_confirm_title)
                .setMessage(R.string.connections_kill_all_confirm_msg)
                .setPositiveButton(R.string.yes) { _, _ -> cont.resume(true) }
                .setNegativeButton(R.string.no) { _, _ -> cont.resume(false) }
                .setOnCancelListener { if (cont.isActive) cont.resume(false) }
                .show()
        }
    }

    suspend fun updateSnapshot(snapshot: ConnectionSnapshot) {
        if (isPaused) return

        val activeIds = snapshot.connections.map { it.id }.toSet()

        if (isCachingClosed) {
            val newlyClosed = lastSnapshot.connections
                .filter { it.id !in activeIds && it.id !in closedIds }
            if (newlyClosed.isNotEmpty()) {
                closedIds.addAll(newlyClosed.map { it.id })
                closedConnections = (closedConnections + newlyClosed).takeLast(300)
                refreshClosed()
            }
        }

        lastSnapshot = snapshot

        val up = snapshot.uploadTotal.toBytesString()
        val down = snapshot.downloadTotal.toBytesString()

        withContext(Dispatchers.Main) {
            binding.speedView.text = "↑ $up/s  ↓ $down/s"
        }

        refreshActive(snapshot.connections)
    }

    private suspend fun refreshActive(connections: List<Connection>) {
        val groups = buildGroups(connections)
        val filtered = if (currentFilter.isEmpty()) groups
        else groups.filter {
            it.appName.contains(currentFilter, ignoreCase = true) ||
                it.packageName.contains(currentFilter, ignoreCase = true) ||
                it.connections.any { c -> matchesFilter(c, currentFilter) }
        }
        withContext(Dispatchers.Main) {
            activeAdapter.groups = filtered
        }
    }

    private suspend fun refreshClosed() {
        val groups = buildGroups(closedConnections)
        val filtered = if (currentFilter.isEmpty()) groups
        else groups.filter {
            it.appName.contains(currentFilter, ignoreCase = true) ||
                it.connections.any { c -> matchesFilter(c, currentFilter) }
        }
        withContext(Dispatchers.Main) {
            closedAdapter.groups = filtered
        }
    }

    private suspend fun buildGroups(connections: List<Connection>): List<ConnectionGroup> {
        return withContext(Dispatchers.Default) {
            connections
                .groupBy { c ->
                    if (c.metadata.process.isEmpty() || c.metadata.process == appPackage) ""
                    else c.metadata.process
                }
                .map { (pkg, conns) ->
                    val isPrizrak = pkg.isEmpty()
                    val icon = if (isPrizrak) null else runCatching {
                        pm.getApplicationInfo(pkg, 0).loadIcon(pm)
                    }.getOrNull()
                    val name = if (isPrizrak) context.getString(R.string.prizrak_core_name)
                    else runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrElse { pkg }

                    val upSpeed = conns.sumOf { it.upload }
                    val downSpeed = conns.sumOf { it.download }
                    val sortedConns = sortConnectionList(conns)

                    ConnectionGroup(pkg, name, icon, sortedConns, upSpeed, downSpeed)
                }
                .sortedWith(buildGroupComparator())
        }
    }

    private fun buildGroupComparator(): Comparator<ConnectionGroup> {
        val coreFirst = compareByDescending<ConnectionGroup> { it.packageName.isEmpty() }

        val byField: Comparator<ConnectionGroup> = when (sortField) {
            SortField.SPEED   ->
                if (sortDirection == SortDirection.DESC)
                    compareByDescending { it.uploadSpeed + it.downloadSpeed }
                else
                    compareBy { it.uploadSpeed + it.downloadSpeed }

            SortField.UPLOAD  ->
                if (sortDirection == SortDirection.DESC)
                    compareByDescending { it.uploadSpeed }
                else
                    compareBy { it.uploadSpeed }

            SortField.DOWNLOAD ->
                if (sortDirection == SortDirection.DESC)
                    compareByDescending { it.downloadSpeed }
                else
                    compareBy { it.downloadSpeed }

            else -> {
                val selector: (ConnectionGroup) -> String? = { g ->
                    g.connections.firstOrNull()?.let { c ->
                        when (sortField) {
                            SortField.HOST   -> c.metadata.host.ifEmpty { c.metadata.destinationIP }
                            SortField.CHAINS -> c.chains.firstOrNull() ?: ""
                            SortField.RULE   -> c.rule
                            SortField.TYPE   -> c.metadata.network
                            SortField.TIME   -> c.start
                            SortField.SOURCE_IP -> c.metadata.sourceIP
                            else -> ""
                        }
                    }
                }
                if (sortDirection == SortDirection.DESC)
                    compareByDescending(selector)
                else
                    compareBy(selector)
            }
        }

        return coreFirst.thenComparing(byField)
    }

    fun showConnectionDetail(connection: Connection) {
        showConnectionDetailSheet(context, connection)
    }

    private fun matchesFilter(c: Connection, q: String): Boolean {
        val m = c.metadata
        return listOf(
            m.host, m.destinationIP, m.sourceIP, m.process,
            m.sniffHost, m.remoteDestination, c.rule, c.rulePayload,
            c.chains.firstOrNull() ?: ""
        ).any { it.contains(q, ignoreCase = true) }
    }

    // ─── Init ────────────────────────────────────────────────────────────────

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)

        binding.activityBarLayout.findViewById<TextView>(R.id.activity_bar_title_view)
            ?.visibility = View.GONE

        binding.activityBarLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val top = binding.activityBarLayout.height
            if (binding.recyclerList.paddingTop != top) {
                binding.recyclerList.setPadding(0, top, 0, binding.recyclerList.paddingBottom)
            }
        }

        binding.recyclerList.bindAppBarElevation(binding.activityBarLayout)
        binding.recyclerList.applyLinearAdapter(context, activeAdapter)

        val tabActive = binding.tabLayout.newTab().setText(context.getString(R.string.connections_active))
        val tabClosed = binding.tabLayout.newTab().setText(context.getString(R.string.connections_closed))
        binding.tabLayout.addTab(tabActive)
        binding.tabLayout.addTab(tabClosed)

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showingClosed = tab.position == 1
                binding.recyclerList.adapter =
                    if (showingClosed) closedAdapter else activeAdapter
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentFilter = s?.toString() ?: ""
                launch {
                    if (showingClosed) refreshClosed() else refreshActive(lastSnapshot.connections)
                }
            }
        })

        binding.filterBtn.setOnLongClickListener { onFilterLongClick() }
        binding.sortBtn.setOnLongClickListener { onSortLongClick() }

        // Sync icon states with current (possibly already changed) companion state
        updateFilterBtnTint()
        updateSortBtnIcon()
    }
}
