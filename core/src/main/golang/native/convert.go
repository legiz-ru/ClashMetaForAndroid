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

// convertAndApplyTemplate detects the format of proxy content (V2Ray/XRay links,
// SingBox JSON, or their base64-encoded form), converts the proxies found therein,
// and merges them into the provided Clash YAML template.
//
// Detection order:
//  1. V2Ray/XRay proxy links (vless://, trojan://, vmess://, ss://, hy2://, etc.)
//     including base64-encoded subscriptions.
//  2. SingBox JSON (outbounds array) via convert.ConvertsSingBox.
//
// Returns a JSON string: {"yaml": "..."} on success, or {"error": "..."} on failure.
//
//export convertAndApplyTemplate
func convertAndApplyTemplate(contentRaw C.c_string, templateContentRaw C.c_string) *C.char {
	content := strings.TrimSpace(C.GoString(contentRaw))
	templateContent := C.GoString(templateContentRaw)

	data := []byte(content)

	// Try V2Ray/XRay format first.
	// ConvertsV2Ray also handles base64-encoded subscriptions automatically.
	proxies, err := convert.ConvertsV2Ray(data)
	if err != nil || len(proxies) == 0 {
		// Fall back to SingBox JSON format (snakem982/mihomo moshen provides this).
		var err2 error
		proxies, err2 = convert.ConvertsSingBox(data)
		if err2 != nil || len(proxies) == 0 {
			errText := "content is not convertible: no recognised proxy links or SingBox JSON found"
			if err != nil && err.Error() != "" {
				errText = "content is not convertible: " + err.Error()
			} else if err2 != nil && err2.Error() != "" {
				errText = "content is not convertible: " + err2.Error()
			}
			result, _ := json.Marshal(convertResult{Error: errText})
			return C.CString(string(result))
		}
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

	// Merge converted proxies with any static proxies already defined in the template.
	// Some templates define static entries (e.g. {name: 直连, type: direct}) that
	// proxy groups reference by name; those must be preserved alongside the
	// user-supplied proxies.
	merged := make([]any, 0, len(proxies))
	if existing, ok := templateMap["proxies"].([]any); ok {
		merged = append(merged, existing...)
	}
	for _, p := range proxies {
		merged = append(merged, p)
	}
	templateMap["proxies"] = merged

	// Marshal back to YAML.
	yamlBytes, err := yaml.Marshal(templateMap)
	if err != nil {
		result, _ := json.Marshal(convertResult{Error: "failed to serialise config: " + err.Error()})
		return C.CString(string(result))
	}

	result, _ := json.Marshal(convertResult{Yaml: string(yamlBytes)})
	return C.CString(string(result))
}
