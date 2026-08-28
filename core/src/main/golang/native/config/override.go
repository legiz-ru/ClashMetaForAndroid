package config

import (
	"io"
	"os"

	"github.com/metacubex/mihomo/constant"
)

type OverrideSlot int

const (
	OverrideSlotPersist OverrideSlot = iota
	OverrideSlotSession
)

const defaultPersistOverride = `{}`
const defaultSessionOverride = `{}`

var sessionOverride = defaultSessionOverride

func overridePersistPath() string {
	return constant.Path.Resolve("override.json")
}

func ReadOverride(slot OverrideSlot) string {
	switch slot {
	case OverrideSlotPersist:
		file, err := os.OpenFile(overridePersistPath(), os.O_RDONLY, 0600)
		if err != nil {
			return defaultPersistOverride
		}

		buf, err := io.ReadAll(file)
		if err != nil {
			return defaultPersistOverride
		}

		return string(buf)
	case OverrideSlotSession:
		return sessionOverride
	}

	return ""
}

func WriteOverride(slot OverrideSlot, content string) {
	switch slot {
	case OverrideSlotPersist:
		// Write to a temp file and rename over the target so a crash or write
		// failure mid-write can never leave override.json truncated: the
		// O_TRUNC+write pattern this replaced zeroed the file before writing,
		// so anything that killed the process between truncate and write
		// (including a write error) left ReadOverride reading garbage/empty
		// JSON on the next launch. Rename within the same directory is atomic.
		path := overridePersistPath()
		tmp := path + ".tmp"

		file, err := os.OpenFile(tmp, os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0600)
		if err != nil {
			return
		}

		_, err = file.Write([]byte(content))
		closeErr := file.Close()
		if err != nil || closeErr != nil {
			_ = os.Remove(tmp)
			return
		}

		if err := os.Rename(tmp, path); err != nil {
			_ = os.Remove(tmp)
		}
	case OverrideSlotSession:
		sessionOverride = content
	}
}

func ClearOverride(slot OverrideSlot) {
	switch slot {
	case OverrideSlotPersist:
		_ = os.Remove(overridePersistPath())
	case OverrideSlotSession:
		sessionOverride = defaultSessionOverride
	}
}
