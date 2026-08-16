package tunnel

import (
	"github.com/metacubex/mihomo/component/iface"
	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

// OnNetworkChanged is what has to happen once the phone moved from one network
// to another.
//
// The core never learns about it on its own: the only network monitor lives in
// the sing-tun listener and is started for auto-route / auto-detect-interface
// only, while we hand the core a ready tunnel file descriptor. And the core
// itself tears nothing down on a network change — there is no "close every
// connection" function in it at all, only the REST endpoint nobody calls here.
// So after a Wi-Fi -> LTE move the connections opened over the interface that
// is gone keep waiting for the OS timeout: minutes for TCP, up to a minute for
// UDP. The user sees it as "the internet is back but everything hangs".
//
// This is not a core patch: all three calls below are public mihomo functions,
// and they are exactly the ones the core itself makes in its DELETE /connections
// handler (hub/route/connections.go).
//
// closeConnections tells whether live connections should be torn down. A
// connection over a vanished interface is dead either way, the only difference
// is whether the app learns about it now or after a timeout; but for an app
// without resume support "now" means a broken download, so the decision is left
// to the user via a toggle.
func OnNetworkChanged(closeConnections bool) {
	// The interface cache lives for twenty seconds, and all that time the core
	// resolves routes over an interface that no longer exists.
	iface.FlushCache()

	// Connections to DNS servers are kept apart from the user ones and survive
	// a network change just as badly: names stop resolving before anything else.
	resolver.ResetConnection()

	if !closeConnections {
		log.Infoln("Network changed: interface cache and DNS connections reset")

		return
	}

	closed := 0

	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		_ = c.Close()

		closed++

		return true
	})

	log.Infoln("Network changed: interface cache and DNS connections reset, %d connection(s) closed", closed)
}
