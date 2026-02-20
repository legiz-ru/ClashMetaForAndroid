package main

//#include "bridge.h"
import "C"

import (
	"encoding/json"
	"strings"

	"github.com/metacubex/mihomo/common/convert"
	"gopkg.in/yaml.v3"
)

type convertResult struct {
	Yaml  string `json:"yaml,omitempty"`
	Error string `json:"error,omitempty"`
}

// convertAndApplyTemplate detects the format of proxy content (V2Ray/XRay links
// such as vless://, trojan://, vmess://, ss://, hysteria2://, or their
// base64-encoded form), converts the proxies found therein, and merges them
// into the provided Clash YAML template.
//
// Returns a JSON string: {"yaml": "..."} on success, or {"error": "..."} on failure.
//
//export convertAndApplyTemplate
func convertAndApplyTemplate(contentRaw C.c_string, templateContentRaw C.c_string) *C.char {
	content := strings.TrimSpace(C.GoString(contentRaw))
	templateContent := C.GoString(templateContentRaw)

	data := []byte(content)

	// ConvertsV2Ray handles vless://, trojan://, vmess://, ss://, ssr://,
	// hysteria://, hysteria2://, hy2://, tuic://, anytls://, wireguard://,
	// as well as base64-encoded subscriptions containing any of the above.
	proxies, err := convert.ConvertsV2Ray(data)
	if err != nil || len(proxies) == 0 {
		errText := "content is not convertible: no recognised proxy links found"
		if err != nil && err.Error() != "" {
			errText = "content is not convertible: " + err.Error()
		}
		result, _ := json.Marshal(convertResult{Error: errText})
		return C.CString(string(result))
	}

	// Parse the template as a generic YAML document.
	var templateMap map[string]any
	if err := yaml.Unmarshal([]byte(templateContent), &templateMap); err != nil {
		result, _ := json.Marshal(convertResult{Error: "failed to parse template: " + err.Error()})
		return C.CString(string(result))
	}
	if templateMap == nil {
		templateMap = make(map[string]any)
	}

	// Replace the proxies list in the template with the converted proxies.
	templateMap["proxies"] = proxies

	// Marshal back to YAML.
	yamlBytes, err := yaml.Marshal(templateMap)
	if err != nil {
		result, _ := json.Marshal(convertResult{Error: "failed to serialise config: " + err.Error()})
		return C.CString(string(result))
	}

	result, _ := json.Marshal(convertResult{Yaml: string(yamlBytes)})
	return C.CString(string(result))
}
