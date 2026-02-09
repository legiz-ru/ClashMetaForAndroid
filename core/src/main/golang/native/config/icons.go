package config

import (
	"crypto/md5"
	"encoding/json"
	"fmt"
	U "net/url"
	"os"
	P "path"

	"github.com/metacubex/mihomo/config"
)

// fetchProxyGroupIcons iterates proxy-groups and downloads icon URLs
func fetchProxyGroupIcons(rawCfg *config.RawConfig, profilePath string, reportStatus func(string)) {
	iconsDir := P.Join(profilePath, "icons")
	_ = os.MkdirAll(iconsDir, 0700)

	// Collect icon URLs from proxy groups
	type iconEntry struct {
		name string
		url  string
	}

	var entries []iconEntry
	for _, group := range rawCfg.ProxyGroup {
		icon, ok := group["icon"]
		if !ok {
			continue
		}
		iconStr, ok := icon.(string)
		if !ok || iconStr == "" {
			continue
		}
		name, _ := group["name"].(string)
		entries = append(entries, iconEntry{name: name, url: iconStr})
	}

	if len(entries) == 0 {
		return
	}

	for i, entry := range entries {
		bytes, _ := json.Marshal(&Status{
			Action:      "FetchIcons",
			Args:        []string{entry.name},
			Progress:    i,
			MaxProgress: len(entries),
		})
		reportStatus(string(bytes))

		url, err := U.Parse(entry.url)
		if err != nil {
			continue
		}

		// Use MD5 hash of URL as filename
		hash := fmt.Sprintf("%x", md5.Sum([]byte(entry.url)))
		iconPath := P.Join(iconsDir, hash)

		// Skip if already downloaded
		if _, err := os.Stat(iconPath); err == nil {
			continue
		}

		_ = fetch(url, iconPath)
	}
}
