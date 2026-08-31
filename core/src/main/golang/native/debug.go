//go:build debug
// +build debug

package main

import (
	"net/http"
	_ "net/http/pprof"

	"github.com/metacubex/mihomo/log"
)

func init() {
	go func() {
		// Loopback only: goroutine/heap dumps must not be reachable from the
		// rest of the local network, let alone through the tunnel once it's up.
		log.Debugln("pprof service listen at: 127.0.0.1:8888")

		_ = http.ListenAndServe("127.0.0.1:8888", nil)
	}()
}
