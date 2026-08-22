package tunnel

import (
	"sync/atomic"
	"time"
)

// networkSettleWindow is how long a probe holds off after a network change,
// on the assumption the connection is still shaky right after one.
//
// This is the Android-only half of network-settle handling: upstream
// (Mrvibecodic/clod-clash-android) pairs this with a small mihomo core patch
// (constant.SetProbeHoldUntil/ProbeHolding, checked from adapter.go and
// groupbase.go) that also stops a probe FAILING during this window from
// marking a node dead. That part needs a patch to the moshen fork itself,
// which is out of reach from this repo — so here the window only delays WHEN
// our own post-network-change probes run ([ProbeCurrentNodes],
// [RecoverDeadNodes]); a probe that starts after the wait and still fails is
// recorded as dead exactly as it always was. Reduced, not equivalent: a probe
// fired by something else entirely (the user tapping a delay badge, a manual
// health check) is not held back and is not protected either.
const networkSettleWindow = 5 * time.Second

// networkReadyGrace is how much longer to wait after the network reports
// itself validated — DNS and routes can lag a confirmed connection by a beat.
const networkReadyGrace = time.Second

var settleUntil atomic.Int64

// NoteNetworkChange marks the network as unsettled from now for
// [networkSettleWindow]. Called at the top of [OnNetworkChanged].
func NoteNetworkChange() {
	settleUntil.Store(time.Now().Add(networkSettleWindow).UnixNano())
}

// NoteNetworkReady shortens the wait once the network the phone is actually
// using has confirmed it has internet — but only ever shortens it: a ready
// signal for a network that isn't current anymore, or a stale one arriving
// after a newer change already pushed the deadline out further, must not pull
// the window back in.
func NoteNetworkReady() {
	until := time.Now().Add(networkReadyGrace).UnixNano()

	for {
		cur := settleUntil.Load()
		if cur <= until {
			return
		}

		if settleUntil.CompareAndSwap(cur, until) {
			return
		}
	}
}

// waitNetworkSettled blocks the calling goroutine until the settle window
// closes. Bounded by construction ([NoteNetworkChange] only ever sets a
// bounded deadline), so this takes no context: the longest a caller waits is
// [networkSettleWindow].
func waitNetworkSettled() {
	for {
		wait := time.Until(time.Unix(0, settleUntil.Load()))
		if wait <= 0 {
			return
		}

		time.Sleep(wait)
	}
}
