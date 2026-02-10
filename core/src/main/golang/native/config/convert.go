package config

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/url"
	"strings"

	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/log"
)

// ConvertResult represents the result of a profile conversion
type ConvertResult struct {
	Success bool   `json:"success"`
	Content string `json:"content,omitempty"`
	Format  string `json:"format,omitempty"`
	Error   string `json:"error,omitempty"`
}

// ProfileFormat represents supported profile formats
type ProfileFormat string

const (
	FormatClash        ProfileFormat = "clash"
	FormatClashMeta    ProfileFormat = "clash_meta"
	FormatBase64       ProfileFormat = "base64"
	FormatShadowrocket ProfileFormat = "shadowrocket"
	FormatV2Ray        ProfileFormat = "v2ray"
	FormatSIP008       ProfileFormat = "sip008"
	FormatUnknown      ProfileFormat = "unknown"
)

// DetectFormat attempts to detect the format of a profile content
func DetectFormat(content string) ProfileFormat {
	content = strings.TrimSpace(content)

	// Try to detect YAML (Clash/ClashMeta)
	if strings.HasPrefix(content, "port:") ||
		strings.HasPrefix(content, "socks-port:") ||
		strings.HasPrefix(content, "mixed-port:") ||
		strings.Contains(content, "\nproxies:") ||
		strings.Contains(content, "\nproxy-groups:") {
		return FormatClash
	}

	// Try to detect Base64 encoded content
	if isBase64(content) {
		decoded, err := base64.StdEncoding.DecodeString(content)
		if err == nil {
			decodedStr := string(decoded)
			// Check if decoded content is a proxy URI list
			if strings.Contains(decodedStr, "://") {
				return FormatBase64
			}
		}
	}

	// Try to detect SIP008 (Shadowsocks JSON format)
	if strings.HasPrefix(content, "{") && strings.Contains(content, "servers") {
		var data map[string]interface{}
		if err := json.Unmarshal([]byte(content), &data); err == nil {
			if _, ok := data["servers"]; ok {
				return FormatSIP008
			}
		}
	}

	// Try to detect proxy URI format (ss://, vmess://, trojan://, etc.)
	if strings.Contains(content, "ss://") ||
		strings.Contains(content, "vmess://") ||
		strings.Contains(content, "trojan://") ||
		strings.Contains(content, "vless://") {
		return FormatBase64
	}

	return FormatUnknown
}

// isBase64 checks if a string is valid base64
func isBase64(s string) bool {
	// Remove common whitespace and newlines
	s = strings.ReplaceAll(s, "\n", "")
	s = strings.ReplaceAll(s, "\r", "")
	s = strings.ReplaceAll(s, " ", "")

	if len(s) == 0 {
		return false
	}

	_, err := base64.StdEncoding.DecodeString(s)
	if err != nil {
		// Try URL encoding
		_, err = base64.URLEncoding.DecodeString(s)
	}
	return err == nil
}

// ConvertToClash converts various profile formats to Clash format
func ConvertToClash(content string, sourceFormat ProfileFormat) (*ConvertResult, error) {
	result := &ConvertResult{Success: false}

	// Auto-detect if format is unknown
	if sourceFormat == FormatUnknown {
		sourceFormat = DetectFormat(content)
		if sourceFormat == FormatUnknown {
			result.Error = "unable to detect profile format"
			return result, fmt.Errorf(result.Error)
		}
	}

	result.Format = string(sourceFormat)

	switch sourceFormat {
	case FormatClash, FormatClashMeta:
		// Already in Clash format, validate it
		return validateClashConfig(content)

	case FormatBase64:
		return convertBase64ToClash(content)

	case FormatSIP008:
		return convertSIP008ToClash(content)

	default:
		result.Error = fmt.Sprintf("unsupported source format: %s", sourceFormat)
		return result, fmt.Errorf(result.Error)
	}
}

// validateClashConfig validates a Clash configuration
func validateClashConfig(content string) (*ConvertResult, error) {
	result := &ConvertResult{Success: false, Format: string(FormatClash)}

	// Try to parse as YAML
	_, err := config.UnmarshalRawConfig([]byte(content))
	if err != nil {
		result.Error = fmt.Sprintf("invalid clash config: %s", err.Error())
		return result, err
	}

	result.Success = true
	result.Content = content
	return result, nil
}

// convertBase64ToClash converts base64 encoded proxy list to Clash format
func convertBase64ToClash(content string) (*ConvertResult, error) {
	result := &ConvertResult{Success: false, Format: string(FormatBase64)}

	// Decode base64
	decoded, err := base64.StdEncoding.DecodeString(strings.TrimSpace(content))
	if err != nil {
		// Try URL encoding
		decoded, err = base64.URLEncoding.DecodeString(strings.TrimSpace(content))
		if err != nil {
			result.Error = fmt.Sprintf("failed to decode base64: %s", err.Error())
			return result, err
		}
	}

	lines := strings.Split(string(decoded), "\n")
	proxies := make([]map[string]interface{}, 0)

	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}

		proxy, err := parseProxyURI(line)
		if err != nil {
			log.Warnln("Failed to parse proxy URI: %s, error: %s", line, err.Error())
			continue
		}

		if proxy != nil {
			proxies = append(proxies, proxy)
		}
	}

	if len(proxies) == 0 {
		result.Error = "no valid proxies found in base64 content"
		return result, fmt.Errorf(result.Error)
	}

	// Build Clash config
	clashConfig := map[string]interface{}{
		"port":       7890,
		"socks-port": 7891,
		"allow-lan":  false,
		"mode":       "rule",
		"log-level":  "info",
		"proxies":    proxies,
		"proxy-groups": []map[string]interface{}{
			{
				"name":    "Proxy",
				"type":    "select",
				"proxies": getProxyNames(proxies),
			},
		},
		"rules": []string{
			"MATCH,Proxy",
		},
	}

	yamlBytes, err := yaml.Marshal(clashConfig)
	if err != nil {
		result.Error = fmt.Sprintf("failed to marshal config: %s", err.Error())
		return result, err
	}

	result.Success = true
	result.Content = string(yamlBytes)
	return result, nil
}

// parseProxyURI parses various proxy URI formats (ss://, vmess://, trojan://, etc.)
func parseProxyURI(uri string) (map[string]interface{}, error) {
	if strings.HasPrefix(uri, "ss://") {
		return parseShadowsocksURI(uri)
	} else if strings.HasPrefix(uri, "vmess://") {
		return parseVMessURI(uri)
	} else if strings.HasPrefix(uri, "trojan://") {
		return parseTrojanURI(uri)
	}

	return nil, fmt.Errorf("unsupported proxy protocol")
}

// parseShadowsocksURI parses Shadowsocks URI
func parseShadowsocksURI(uri string) (map[string]interface{}, error) {
	// Remove ss:// prefix
	uri = strings.TrimPrefix(uri, "ss://")

	// Try SIP002 format first: ss://base64(method:password)@server:port#name
	parts := strings.SplitN(uri, "@", 2)
	if len(parts) != 2 {
		return nil, fmt.Errorf("invalid shadowsocks URI format")
	}

	// Decode method:password
	decoded, err := base64.URLEncoding.DecodeString(parts[0])
	if err != nil {
		decoded, err = base64.StdEncoding.DecodeString(parts[0])
		if err != nil {
			return nil, fmt.Errorf("failed to decode credentials")
		}
	}

	methodPassword := strings.SplitN(string(decoded), ":", 2)
	if len(methodPassword) != 2 {
		return nil, fmt.Errorf("invalid method:password format")
	}

	// Parse server:port and name
	serverPart := parts[1]
	name := ""
	if idx := strings.Index(serverPart, "#"); idx != -1 {
		name, _ = url.QueryUnescape(serverPart[idx+1:])
		serverPart = serverPart[:idx]
	}

	serverPort := strings.SplitN(serverPart, ":", 2)
	if len(serverPort) != 2 {
		return nil, fmt.Errorf("invalid server:port format")
	}

	if name == "" {
		name = serverPort[0]
	}

	return map[string]interface{}{
		"name":     name,
		"type":     "ss",
		"server":   serverPort[0],
		"port":     serverPort[1],
		"cipher":   methodPassword[0],
		"password": methodPassword[1],
		"udp":      true,
	}, nil
}

// parseVMessURI parses VMess URI
func parseVMessURI(uri string) (map[string]interface{}, error) {
	// VMess URI format: vmess://base64(json)
	encoded := strings.TrimPrefix(uri, "vmess://")

	decoded, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil {
		decoded, err = base64.URLEncoding.DecodeString(encoded)
		if err != nil {
			return nil, fmt.Errorf("failed to decode vmess URI")
		}
	}

	var vmessData map[string]interface{}
	if err := json.Unmarshal(decoded, &vmessData); err != nil {
		return nil, fmt.Errorf("failed to parse vmess JSON: %s", err.Error())
	}

	// Convert to Clash format
	proxy := map[string]interface{}{
		"name":   getStringField(vmessData, "ps", "VMess"),
		"type":   "vmess",
		"server": getStringField(vmessData, "add", ""),
		"port":   getIntField(vmessData, "port", 0),
		"uuid":   getStringField(vmessData, "id", ""),
		"alterId": getIntField(vmessData, "aid", 0),
		"cipher": getStringField(vmessData, "scy", "auto"),
		"udp":    true,
	}

	// Optional fields
	if net := getStringField(vmessData, "net", ""); net != "" {
		proxy["network"] = net
	}

	if tls := getStringField(vmessData, "tls", ""); tls == "tls" {
		proxy["tls"] = true
		if sni := getStringField(vmessData, "sni", ""); sni != "" {
			proxy["servername"] = sni
		}
	}

	return proxy, nil
}

// parseTrojanURI parses Trojan URI
func parseTrojanURI(uri string) (map[string]interface{}, error) {
	// Trojan URI format: trojan://password@server:port#name
	uri = strings.TrimPrefix(uri, "trojan://")

	parts := strings.SplitN(uri, "@", 2)
	if len(parts) != 2 {
		return nil, fmt.Errorf("invalid trojan URI format")
	}

	password := parts[0]
	serverPart := parts[1]

	name := ""
	if idx := strings.Index(serverPart, "#"); idx != -1 {
		name, _ = url.QueryUnescape(serverPart[idx+1:])
		serverPart = serverPart[:idx]
	}

	serverPort := strings.SplitN(serverPart, ":", 2)
	if len(serverPort) != 2 {
		return nil, fmt.Errorf("invalid server:port format")
	}

	if name == "" {
		name = serverPort[0]
	}

	return map[string]interface{}{
		"name":     name,
		"type":     "trojan",
		"server":   serverPort[0],
		"port":     serverPort[1],
		"password": password,
		"udp":      true,
		"sni":      serverPort[0],
	}, nil
}

// convertSIP008ToClash converts SIP008 (Shadowsocks JSON) to Clash format
func convertSIP008ToClash(content string) (*ConvertResult, error) {
	result := &ConvertResult{Success: false, Format: string(FormatSIP008)}

	var sip008 struct {
		Version int `json:"version"`
		Servers []struct {
			Server     string `json:"server"`
			ServerPort int    `json:"server_port"`
			Password   string `json:"password"`
			Method     string `json:"method"`
			Remarks    string `json:"remarks"`
		} `json:"servers"`
	}

	if err := json.Unmarshal([]byte(content), &sip008); err != nil {
		result.Error = fmt.Sprintf("failed to parse SIP008 JSON: %s", err.Error())
		return result, err
	}

	proxies := make([]map[string]interface{}, 0)
	for _, server := range sip008.Servers {
		name := server.Remarks
		if name == "" {
			name = server.Server
		}

		proxy := map[string]interface{}{
			"name":     name,
			"type":     "ss",
			"server":   server.Server,
			"port":     server.ServerPort,
			"cipher":   server.Method,
			"password": server.Password,
			"udp":      true,
		}
		proxies = append(proxies, proxy)
	}

	if len(proxies) == 0 {
		result.Error = "no valid servers found in SIP008 config"
		return result, fmt.Errorf(result.Error)
	}

	// Build Clash config
	clashConfig := map[string]interface{}{
		"port":       7890,
		"socks-port": 7891,
		"allow-lan":  false,
		"mode":       "rule",
		"log-level":  "info",
		"proxies":    proxies,
		"proxy-groups": []map[string]interface{}{
			{
				"name":    "Proxy",
				"type":    "select",
				"proxies": getProxyNames(proxies),
			},
		},
		"rules": []string{
			"MATCH,Proxy",
		},
	}

	yamlBytes, err := yaml.Marshal(clashConfig)
	if err != nil {
		result.Error = fmt.Sprintf("failed to marshal config: %s", err.Error())
		return result, err
	}

	result.Success = true
	result.Content = string(yamlBytes)
	return result, nil
}

// Helper functions

func getProxyNames(proxies []map[string]interface{}) []string {
	names := make([]string, 0, len(proxies))
	for _, proxy := range proxies {
		if name, ok := proxy["name"].(string); ok {
			names = append(names, name)
		}
	}
	return names
}

func getStringField(data map[string]interface{}, key, defaultValue string) string {
	if val, ok := data[key]; ok {
		if str, ok := val.(string); ok {
			return str
		}
	}
	return defaultValue
}

func getIntField(data map[string]interface{}, key string, defaultValue int) int {
	if val, ok := data[key]; ok {
		switch v := val.(type) {
		case int:
			return v
		case float64:
			return int(v)
		case string:
			// Try to parse string as int
			var i int
			if _, err := fmt.Sscanf(v, "%d", &i); err == nil {
				return i
			}
		}
	}
	return defaultValue
}
