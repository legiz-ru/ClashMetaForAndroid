package main

//#include "bridge.h"
import "C"

import (
	"encoding/json"
	"strings"

	"github.com/metacubex/age"
)

//export verifySecretKeys
func verifySecretKeys(keys C.c_string) C.int {
	s := C.GoString(keys)
	for _, line := range strings.Split(s, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		if _, err := age.ParseX25519Identity(line); err != nil {
			return 0
		}
	}
	return 1
}

//export generateAgeKeyPair
func generateAgeKeyPair() *C.char {
	identity, err := age.GenerateX25519Identity()
	if err != nil {
		return C.CString("{}")
	}
	result, err := json.Marshal(map[string]string{
		"secretKey": identity.String(),
		"publicKey": identity.Recipient().String(),
	})
	if err != nil {
		return C.CString("{}")
	}
	return C.CString(string(result))
}
