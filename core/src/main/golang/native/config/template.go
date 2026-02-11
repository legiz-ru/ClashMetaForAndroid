package config

import (
	"encoding/base64"
	"fmt"
	"os"
	P "path"
	"strings"

	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/log"
)

// TemplateID represents available template types
type TemplateID string

const (
	TemplateDefault  TemplateID = "default"
	TemplateRubundle TemplateID = "rubundle"
	TemplateDavoyan  TemplateID = "davoyan"
	TemplateCustom   TemplateID = "custom"
)

// Template represents a profile template
type Template struct {
	ID          TemplateID `json:"id"`
	Name        string     `json:"name"`
	Description string     `json:"description"`
	Builtin     bool       `json:"builtin"` // true for default/rubundle/davoyan, false for custom
}

// GetAvailableTemplates returns list of available templates
func GetAvailableTemplates() []Template {
	return []Template{
		{
			ID:          TemplateDefault,
			Name:        "Default",
			Description: "Базовый шаблон с фокусом на российские сервисы",
			Builtin:     true,
		},
		{
			ID:          TemplateRubundle,
			Name:        "RU Bundle",
			Description: "Шаблон с Re:filter и международными сервисами",
			Builtin:     true,
		},
		{
			ID:          TemplateDavoyan,
			Name:        "Davoyan",
			Description: "Детальная настройка роутинга для российских пользователей",
			Builtin:     true,
		},
		{
			ID:          TemplateCustom,
			Name:        "Custom",
			Description: "Пользовательский редактируемый шаблон",
			Builtin:     false,
		},
	}
}

// GetTemplateContent loads template content from embedded constants or custom file
func GetTemplateContent(templateID TemplateID, profilePath string) (string, error) {
	if templateID == TemplateCustom {
		// Load custom template from profile directory
		customPath := P.Join(profilePath, "custom-template.yaml")
		data, err := os.ReadFile(customPath)
		if err != nil {
			// If custom template doesn't exist, use default as fallback
			log.Warnln("[Template] Custom template not found, using default")
			return GetBuiltinTemplateContent(TemplateDefault)
		}
		return string(data), nil
	}

	// Load builtin template from embedded constants
	return GetBuiltinTemplateContent(templateID)
}

// GetCurrentTemplate returns the template ID used by a profile
func GetCurrentTemplate(profilePath string) (TemplateID, error) {
	templateFile := P.Join(profilePath, "template.txt")
	data, err := os.ReadFile(templateFile)
	if err != nil {
		// Default to "default" template if file doesn't exist
		return TemplateDefault, nil
	}

	templateID := TemplateID(strings.TrimSpace(string(data)))

	// Validate template ID
	validTemplates := map[TemplateID]bool{
		TemplateDefault:  true,
		TemplateRubundle: true,
		TemplateDavoyan:  true,
		TemplateCustom:   true,
	}

	if !validTemplates[templateID] {
		log.Warnln("Invalid template ID '%s' in profile, using default", templateID)
		return TemplateDefault, nil
	}

	return templateID, nil
}

// SetCurrentTemplate saves the template ID for a profile
func SetCurrentTemplate(profilePath string, templateID TemplateID) error {
	templateFile := P.Join(profilePath, "template.txt")
	return os.WriteFile(templateFile, []byte(string(templateID)), 0644)
}

// ApplyTemplateToProfile merges proxies from subscription with template
func ApplyTemplateToProfile(profilePath string, templateContent string, proxies []map[string]interface{}) error {
	// Parse template YAML
	var templateConfig map[string]interface{}
	if err := yaml.Unmarshal([]byte(templateContent), &templateConfig); err != nil {
		return fmt.Errorf("failed to parse template: %w", err)
	}

	// Merge proxies from subscription into template
	templateConfig["proxies"] = proxies

	// Update proxy groups with include-all
	if proxyGroups, ok := templateConfig["proxy-groups"].([]interface{}); ok {
		for _, group := range proxyGroups {
			if groupMap, ok := group.(map[string]interface{}); ok {
				// If group has include-all: true, add all proxy names
				if includeAll, ok := groupMap["include-all"].(bool); ok && includeAll {
					proxyNames := make([]string, 0, len(proxies))
					for _, proxy := range proxies {
						if name, ok := proxy["name"].(string); ok {
							proxyNames = append(proxyNames, name)
						}
					}

					// Append to existing proxies list
					if existingProxies, ok := groupMap["proxies"].([]interface{}); ok {
						for _, proxyName := range proxyNames {
							existingProxies = append(existingProxies, proxyName)
						}
						groupMap["proxies"] = existingProxies
					} else {
						// Create new proxies list
						proxiesInterface := make([]interface{}, len(proxyNames))
						for i, name := range proxyNames {
							proxiesInterface[i] = name
						}
						groupMap["proxies"] = proxiesInterface
					}
				}
			}
		}
	}

	// Marshal back to YAML
	yamlData, err := yaml.Marshal(templateConfig)
	if err != nil {
		return fmt.Errorf("failed to marshal config: %w", err)
	}

	// Write to config.yaml
	configPath := P.Join(profilePath, "config.yaml")
	if err := os.WriteFile(configPath, yamlData, 0644); err != nil {
		return fmt.Errorf("failed to write config: %w", err)
	}

	log.Infoln("Applied template to profile at %s", profilePath)
	return nil
}

// ValidateTemplateYAML validates that template YAML is parseable
func ValidateTemplateYAML(content string) error {
	var config map[string]interface{}
	if err := yaml.Unmarshal([]byte(content), &config); err != nil {
		return fmt.Errorf("invalid YAML: %w", err)
	}

	// Basic validation - check required fields
	requiredFields := []string{"mode", "proxies", "proxy-groups", "rules"}
	for _, field := range requiredFields {
		if _, ok := config[field]; !ok {
			return fmt.Errorf("missing required field: %s", field)
		}
	}

	return nil
}

// ApplyTemplateIfNeeded checks if config is non-YAML (URI/subscription) and applies template
// Returns true if template was applied, false if it's already a valid YAML config
func ApplyTemplateIfNeeded(profilePath string, templateContent string) (bool, error) {
	configPath := P.Join(profilePath, "config.yaml")

	// Read current config.yaml content
	configData, err := os.ReadFile(configPath)
	if err != nil {
		return false, fmt.Errorf("failed to read config: %w", err)
	}

	contentStr := string(configData)

	// Try to detect format
	format := DetectFormat(contentStr)

	// If it's already Clash/ClashMeta YAML, don't apply template
	if format == FormatClash || format == FormatClashMeta {
		log.Infoln("[Template] Config is already YAML, skipping template application")
		return false, nil
	}

	// For URI or Base64 subscriptions, we need to parse proxies first
	var proxies []map[string]interface{}

	// Check if it's a single URI line (starts with protocol)
	if strings.HasPrefix(contentStr, "ss://") ||
		strings.HasPrefix(contentStr, "vmess://") ||
		strings.HasPrefix(contentStr, "trojan://") ||
		strings.HasPrefix(contentStr, "vless://") {
		// Single URI - parse it using existing function from convert.go
		proxy, err := parseProxyURI(contentStr)
		if err != nil {
			return false, fmt.Errorf("failed to parse proxy URI: %w", err)
		}
		proxies = []map[string]interface{}{proxy}
	} else if format == FormatBase64 {
		// Base64 subscription - decode and parse
		decoded, err := base64.StdEncoding.DecodeString(contentStr)
		if err != nil {
			return false, fmt.Errorf("failed to decode base64: %w", err)
		}

		// Parse each line as proxy URI
		lines := strings.Split(string(decoded), "\n")
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if line == "" || strings.HasPrefix(line, "#") {
				continue
			}

			proxy, err := parseProxyURI(line)
			if err != nil {
				log.Warnln("[Template] Failed to parse proxy line: %s, error: %s", line, err)
				continue // Skip invalid proxies
			}
			proxies = append(proxies, proxy)
		}

		if len(proxies) == 0 {
			return false, fmt.Errorf("no valid proxies found in subscription")
		}
	} else {
		// Unknown format - don't apply template
		log.Warnln("[Template] Unknown format %s, skipping template application", format)
		return false, nil
	}

	// Apply template with parsed proxies
	if err := ApplyTemplateToProfile(profilePath, templateContent, proxies); err != nil {
		return false, fmt.Errorf("failed to apply template: %w", err)
	}

	log.Infoln("[Template] Applied template to profile with %d proxies", len(proxies))
	return true, nil
}
