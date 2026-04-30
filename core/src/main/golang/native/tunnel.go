package main

//#include "bridge.h"
import "C"

import (
	"unsafe"

	"cfa/native/app"
	"cfa/native/tunnel"
)

//export queryTunnelState
func queryTunnelState() *C.char {
	mode := tunnel.QueryMode()

	response := &struct {
		Mode string `json:"mode"`
	}{mode}

	return marshalJson(response)
}

//export queryNow
func queryNow(upload, download *C.uint64_t) {
	up, down := tunnel.Now()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryTotal
func queryTotal(upload, download *C.uint64_t) {
	up, down := tunnel.Total()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryGroupNames
func queryGroupNames(excludeNotSelectable C.int) *C.char {
	return marshalJson(tunnel.QueryProxyGroupNames(excludeNotSelectable != 0))
}

//export queryGroup
func queryGroup(name C.c_string, sortMode C.c_string) *C.char {
	n := C.GoString(name)
	s := C.GoString(sortMode)

	mode := tunnel.Default

	switch s {
	case "Title":
		mode = tunnel.Title
	case "Delay":
		mode = tunnel.Delay
	}

	response := tunnel.QueryProxyGroup(n, mode, app.SubtitlePattern())

	if response == nil {
		return nil
	}

	return marshalJson(response)
}

//export healthCheck
func healthCheck(completable unsafe.Pointer, name C.c_string) {
	go func(name string) {
		tunnel.HealthCheck(name)

		C.complete(completable, nil)
	}(C.GoString(name))
}

//export healthCheckAll
func healthCheckAll() {
	tunnel.HealthCheckAll()
}

//export patchSelector
func patchSelector(selector, name C.c_string) C.int {
	s := C.GoString(selector)
	n := C.GoString(name)

	if tunnel.PatchSelector(s, n) {
		return 1
	}

	return 0
}

//export queryProviders
func queryProviders() *C.char {
	return marshalJson(tunnel.QueryProviders())
}

//export queryRuleProviderFilePath
func queryRuleProviderFilePath(name C.c_string) *C.char {
	path := tunnel.QueryRuleProviderFilePath(C.GoString(name))
	if path == "" {
		return nil
	}
	return C.CString(path)
}

//export dumpRuleProviderToText
func dumpRuleProviderToText(name C.c_string, outputPath C.c_string) *C.char {
	errMsg := tunnel.DumpRuleProviderToText(C.GoString(name), C.GoString(outputPath))
	if errMsg == "" {
		return nil
	}
	return C.CString(errMsg)
}

//export updateProvider
func updateProvider(completable unsafe.Pointer, pType C.c_string, name C.c_string) {
	go func(pType, name string) {
		C.complete(completable, marshalString(tunnel.UpdateProvider(pType, name)))

		C.release_object(completable)
	}(C.GoString(pType), C.GoString(name))
}

//export suspend
func suspend(suspended C.int) {
	tunnel.Suspend(suspended != 0)
}

//export queryConnections
func queryConnections() *C.char {
	return marshalJson(tunnel.QueryConnections())
}

//export closeConnection
func closeConnection(id C.c_string) C.int {
	s := C.GoString(id)
	if tunnel.CloseConnection(s) {
		return 1
	}
	return 0
}

//export closeAllConnections
func closeAllConnections() {
	tunnel.CloseAllConnections()
}
