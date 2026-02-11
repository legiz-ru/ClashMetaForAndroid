package config

import (
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

// GetTemplateContent loads template content from assets or custom file
func GetTemplateContent(templateID TemplateID, profilePath string) (string, error) {
	if templateID == TemplateCustom {
		// Load custom template from profile directory
		customPath := P.Join(profilePath, "custom-template.yaml")
		data, err := os.ReadFile(customPath)
		if err != nil {
			return "", fmt.Errorf("failed to read custom template: %w", err)
		}
		return string(data), nil
	}

	// Load builtin template from assets
	// Note: In actual implementation, this will load from Android assets
	// For now, return empty string - will be implemented in Android layer
	return "", fmt.Errorf("builtin templates are loaded from Android assets")
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
