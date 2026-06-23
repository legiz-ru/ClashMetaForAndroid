package main

//#include "bridge.h"
import "C"

import (
	"encoding/json"
	"strings"

	"github.com/metacubex/age"
	mihomoage "github.com/metacubex/mihomo/component/age"
)

//export setAgeSecretKeys
func setAgeSecretKeys(keys C.c_string) {
	s := C.GoString(keys)
	parsed := make([]string, 0)
	for _, line := range strings.Split(s, "\n") {
		line = strings.TrimSpace(line)
		if line != "" {
			parsed = append(parsed, line)
		}
	}
	// The core decrypts age-encrypted configs via age.DecryptBytes, which reads
	// identities only from this process-global slice (no key file is consulted).
	mihomoage.SetGlobalSecretKeys(parsed...)
}

//export decryptConfig
func decryptConfig(content C.c_string, keys C.c_string) *C.char {
	s := C.GoString(content)
	k := C.GoString(keys)

	parsed := make([]string, 0)
	for _, line := range strings.Split(k, "\n") {
		line = strings.TrimSpace(line)
		if line != "" {
			parsed = append(parsed, line)
		}
	}

	out, err := mihomoage.DecryptBytes([]byte(s), parsed...)
	if err != nil {
		// Not age-encrypted, or the key is missing/wrong: return the input
		// unchanged so callers degrade gracefully (e.g. find no tun section)
		// without ever persisting plaintext.
		return C.CString(s)
	}

	return C.CString(string(out))
}

//export verifySecretKeys
func verifySecretKeys(keys C.c_string) C.int {
	s := C.GoString(keys)
	for _, line := range strings.Split(s, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		// Accept both X25519 and Hybrid (MLKEM768-X25519) secret keys
		if _, err := age.ParseX25519Identity(line); err == nil {
			continue
		}
		if _, err := age.ParseHybridIdentity(line); err == nil {
			continue
		}
		return 0
	}
	return 1
}

//export generateAgeKeyPair
func generateAgeKeyPair() *C.char {
	return generateAgeKeyPairWithType(C.CString("MLKEM768-X25519"))
}

//export generateAgeKeyPairWithType
func generateAgeKeyPairWithType(keyType C.c_string) *C.char {
	kType := C.GoString(keyType)
	var secretKey, publicKey string

	if kType == "X25519" {
		identity, err := age.GenerateX25519Identity()
		if err != nil {
			return C.CString("{}")
		}
		secretKey = identity.String()
		publicKey = identity.Recipient().String()
	} else {
		// Default: MLKEM768-X25519 hybrid post-quantum
		identity, err := age.GenerateHybridIdentity()
		if err != nil {
			// Fallback to X25519 if hybrid not available
			x25519, err2 := age.GenerateX25519Identity()
			if err2 != nil {
				return C.CString("{}")
			}
			secretKey = x25519.String()
			publicKey = x25519.Recipient().String()
		} else {
			secretKey = identity.String()
			publicKey = identity.Recipient().String()
		}
	}

	result, err := json.Marshal(map[string]string{
		"secretKey": secretKey,
		"publicKey": publicKey,
	})
	if err != nil {
		return C.CString("{}")
	}
	return C.CString(string(result))
}
