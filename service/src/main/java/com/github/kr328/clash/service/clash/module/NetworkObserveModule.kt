package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import android.net.*
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.asSocketAddressText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class NetworkObserveModule(service: Service) : Module<Network>(service) {
    private val connectivity = service.getSystemService<ConnectivityManager>()!!
    private val networks: Channel<Network> = Channel(Channel.UNLIMITED)
    private val request = NetworkRequest.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)
        }
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }.build()

    private data class NetworkInfo(
        @Volatile var losingMs: Long = 0,
        @Volatile var dnsList: List<InetAddress> = emptyList()
    ) {
        fun isAvailable(): Boolean = losingMs < System.currentTimeMillis()
    }

    private val networkInfos = ConcurrentHashMap<Network, NetworkInfo>()

    @Volatile
    private var curDnsList = emptyList<String>()

    private val store = ServiceStore(service)

    /**
     * A network change: the callbacks drop a signal in here, the module loop
     * handles it. The channel is conflated — on a Wi-Fi -> LTE move the system
     * fires callbacks in a burst, and the work has to be done once.
     */
    private val networkChanges: Channel<Unit> = Channel(Channel.CONFLATED)

    /**
     * The network we consider current. Compared by object: the system hands out
     * a new [Network] for every connection, so even coming back to the same
     * Wi-Fi after a drop is a network change and cannot skip the reset.
     */
    @Volatile
    private var currentNetwork: Network? = null

    /**
     * The first network we see is not a change: there is nothing to tear down
     * when the service starts, and the probe would only wake the radio for
     * nothing.
     */
    @Volatile
    private var networkKnown = false

    /** When the last reset happened, on the clock that ignores sleep. */
    @Volatile
    private var lastResetAt = 0L

    /** The screen was off at the moment of the change — the probe has to catch up. */
    @Volatile
    private var probePending = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i("NetworkObserve onAvailable network=$network")
            networkInfos[network] = NetworkInfo()

            onNetworkMaybeChanged(network)
        }

        /**
         * The network confirmed there is internet behind it.
         *
         * An interface showing up means nothing yet: Wi-Fi can accept the
         * connection and let nothing past the captive portal, and LTE can take
         * seconds to come up. `NET_CAPABILITY_VALIDATED` is the only signal the
         * system gives meaning "checked, the internet is here", and that is what
         * to act on.
         */
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                return
            }

            onNetworkMaybeChanged(network)
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            Log.i("NetworkObserve onLosing network=$network")
            networkInfos[network]?.losingMs = System.currentTimeMillis() + maxMsToLive
            notifyDnsChange()

            networks.trySend(network)
        }

        override fun onLost(network: Network) {
            Log.i("NetworkObserve onLost network=$network")
            networkInfos.remove(network)
            notifyDnsChange()

            // THE MOST COMMON CASE: Wi-Fi went away while LTE was already up in
            // the background. There will be no new onAvailable for it — the
            // system reported it long ago — and without this branch the network
            // change would go unnoticed.
            if (network == currentNetwork) {
                preferredNetwork()?.let(::onNetworkMaybeChanged)
            }

            networks.trySend(network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            Log.i("NetworkObserve onLinkPropertiesChanged network=$network $linkProperties")
            networkInfos[network]?.dnsList = linkProperties.dnsServers
            notifyDnsChange()

            networks.trySend(network)
        }

        override fun onUnavailable() {
            Log.i("NetworkObserve onUnavailable")
        }
    }

    private fun register(): Boolean {
        Log.i("NetworkObserve start register")
        return try {
            connectivity.registerNetworkCallback(request, callback)

            true
        } catch (e: Exception) {
            Log.w("NetworkObserve register failed", e)

            false
        }
    }

    private fun unregister(): Boolean {
        Log.i("NetworkObserve start unregister")
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w("NetworkObserve unregister failed", e)
        }

        return false
    }

    private fun networkToInt(entry: Map.Entry<Network, NetworkInfo>): Int {
        val capabilities = connectivity.getNetworkCapabilities(entry.key)
        // calculate priority based on transport type, available state
        // lower value means higher priority
        // wifi > ethernet > usb tethering > bluetooth tethering > cellular > satellite > other
        return when {
            capabilities == null -> 100
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> 90
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> 2
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> 3
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 4
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE) -> 5
            // TRANSPORT_LOWPAN / TRANSPORT_THREAD / TRANSPORT_WIFI_AWARE are not for general internet access, which will not set as default route.
            else -> 20
        } + (if (entry.value.isAvailable()) 0 else 10)
    }

    /**
     * The network looks changed — tell the module loop about it.
     *
     * The "did this network become the preferred one" check filters out the
     * background: the phone holds Wi-Fi and LTE at the same time, and a second
     * network showing up next to a live first one changes nothing for us.
     */
    private fun onNetworkMaybeChanged(network: Network) {
        if (preferredNetwork()?.equals(network) == false) {
            return
        }

        if (currentNetwork == network) {
            return
        }

        currentNetwork = network

        if (!networkKnown) {
            networkKnown = true

            return
        }

        Log.i("NetworkObserve network changed to $network")

        networkChanges.trySend(Unit)
    }

    /**
     * The network changed: reset the core state and, when the screen is on,
     * check the current node.
     *
     * The reset is cheap and needs no network — it is always done. The probe
     * costs a request, so with the screen off it is deferred until it turns on:
     * waking the radio for a number nobody is there to see is pointless.
     */
    private fun handleNetworkChanged() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastResetAt < RESET_THROTTLE_MS) {
            Log.d("NetworkObserve reset throttled")

            return
        }

        lastResetAt = now

        Clash.notifyNetworkChanged(store.resetConnectionsOnNetworkChange)

        if (isInteractive()) {
            Clash.probeCurrentNodes()
        } else {
            probePending = true
        }
    }

    private fun isInteractive(): Boolean =
        service.getSystemService<PowerManager>()?.isInteractive ?: true

    /**
     * The network the phone is using right now: the one with the lowest weight
     * by [networkToInt]. There is always more than one — Wi-Fi and LTE live side
     * by side.
     */
    private fun preferredNetwork(): Network? =
        networkInfos.asSequence().minByOrNull { networkToInt(it) }?.key

    private fun notifyDnsChange() {
        val dnsList = (networkInfos.asSequence().minByOrNull { networkToInt(it) }?.value?.dnsList
            ?: emptyList()).map { x -> x.asSocketAddressText(53) }
        val prevDnsList = curDnsList
        if (dnsList.isNotEmpty() && prevDnsList != dnsList) {
            Log.i("notifyDnsChange $prevDnsList -> $dnsList")
            curDnsList = dnsList
            Clash.notifyDnsChanged(dnsList)
        }
    }

    override suspend fun run() {
        register()

        val screenOn = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON)
        }

        try {
            while (true) {
                select<Unit> {
                    networks.onReceive {
                        enqueueEvent(it)
                    }
                    networkChanges.onReceive {
                        handleNetworkChanged()
                    }
                    screenOn.onReceive {
                        if (probePending) {
                            probePending = false

                            Log.i("NetworkObserve deferred probe after screen on")

                            Clash.probeCurrentNodes()
                        }
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                unregister()

                Log.i("NetworkObserve dns = []")
                Clash.notifyDnsChanged(emptyList())
            }
        }
    }

    companion object {
        /**
         * The system shows a move from network to network as a burst of
         * callbacks within a fraction of a second. Five seconds on the leading
         * edge: the first signal fires right away, the rest of the burst is
         * skipped.
         */
        private const val RESET_THROTTLE_MS = 5_000L
    }
}