package com.github.kr328.clash.design

import android.content.Context
import android.content.pm.PackageManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
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
            if (confirmKillAll()) {
                requests.trySend(Request.KillAll())
            }
        }
    }

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
        else groups.filter { it.appName.contains(currentFilter, ignoreCase = true) ||
            it.packageName.contains(currentFilter, ignoreCase = true) ||
            it.connections.any { c -> matchesFilter(c, currentFilter) } }

        withContext(Dispatchers.Main) {
            activeAdapter.groups = filtered
        }
    }

    private suspend fun refreshClosed() {
        val groups = buildGroups(closedConnections)
        val filtered = if (currentFilter.isEmpty()) groups
        else groups.filter { it.appName.contains(currentFilter, ignoreCase = true) ||
            it.connections.any { c -> matchesFilter(c, currentFilter) } }

        withContext(Dispatchers.Main) {
            closedAdapter.groups = filtered
        }
    }

    private suspend fun buildGroups(connections: List<Connection>): List<ConnectionGroup> {
        return withContext(Dispatchers.Default) {
            connections
                .groupBy { c -> if (c.metadata.process.isEmpty() || c.metadata.process == appPackage) "" else c.metadata.process }
                .map { (pkg, conns) ->
                    val isPrizrak = pkg.isEmpty()
                    val icon = if (isPrizrak) null else runCatching {
                        pm.getApplicationInfo(pkg, 0).loadIcon(pm)
                    }.getOrNull()
                    val name = if (isPrizrak) context.getString(R.string.prizrak_core_name)
                    else runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrElse { pkg }

                    val upSpeed = conns.sumOf { it.upload }
                    val downSpeed = conns.sumOf { it.download }

                    ConnectionGroup(pkg, name, icon, conns, upSpeed, downSpeed)
                }
                .sortedWith(
                    compareByDescending<ConnectionGroup> { it.packageName.isEmpty() }
                        .thenByDescending { it.uploadSpeed + it.downloadSpeed }
                )
        }
    }

    fun showConnectionDetail(connection: Connection) {
        showConnectionDetailSheet(context, connection)
    }

    private fun matchesFilter(c: Connection, q: String): Boolean {
        val m = c.metadata
        return listOf(m.host, m.destinationIP, m.sourceIP, m.process,
            m.sniffHost, m.remoteDestination, c.rule, c.rulePayload,
            c.chains.firstOrNull() ?: "")
            .any { it.contains(q, ignoreCase = true) }
    }

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)

        // Hide title — it is shown only in Settings
        binding.activityBarLayout.findViewById<TextView>(R.id.activity_bar_title_view)?.visibility = View.GONE

        // Set dynamic paddingTop on the list to match the toolbar height
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
    }
}
