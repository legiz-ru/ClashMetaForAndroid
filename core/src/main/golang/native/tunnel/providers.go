package tunnel

import (
	"errors"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
	"gopkg.in/yaml.v3"
)

var ErrInvalidType = errors.New("invalid type")

type Provider struct {
	Name        string `json:"name"`
	VehicleType string `json:"vehicleType"`
	Type        string `json:"type"`
	UpdatedAt   int64  `json:"updatedAt"`
}

type UpdatableProvider interface {
	UpdatedAt() time.Time
}

func QueryProviders() []*Provider {
	r := tunnel.RuleProviders()
	p := tunnel.Providers()

	providers := make([]provider.Provider, 0, len(r)+len(p))

	for _, rule := range r {
		if rule.VehicleType() == provider.Compatible {
			continue
		}

		providers = append(providers, rule)
	}

	for _, proxy := range p {
		if proxy.VehicleType() == provider.Compatible {
			continue
		}

		providers = append(providers, proxy)
	}

	result := make([]*Provider, 0, len(providers))

	for _, p := range providers {
		updatedAt := time.Time{}

		if s, ok := p.(UpdatableProvider); ok {
			updatedAt = s.UpdatedAt()
		}

		result = append(result, &Provider{
			Name:        p.Name(),
			VehicleType: p.VehicleType().String(),
			Type:        p.Type().String(),
			UpdatedAt:   updatedAt.UnixNano() / 1000 / 1000,
		})
	}

	return result
}

// vehicleGetter matches ruleSetProvider (HTTP/File providers) which embeds resource.Fetcher.
type vehicleGetter interface {
	Vehicle() provider.Vehicle
}

// QueryRuleProviderContent reads the cached rule file for the named provider and returns
// each rule entry as a string. Returns nil if the provider is not found or is inline/binary.
func QueryRuleProviderContent(name string) []string {
	p := tunnel.RuleProviders()[name]
	if p == nil {
		return nil
	}

	vg, ok := p.(vehicleGetter)
	if !ok {
		return nil // inline providers have no backing file
	}

	buf, err := os.ReadFile(vg.Vehicle().Path())
	if err != nil {
		return nil
	}

	return parseRuleFileContent(buf)
}

func parseRuleFileContent(buf []byte) []string {
	// YAML format: payload: [...] or rules: [...]
	var schema struct {
		Payload []string `yaml:"payload"`
		Rules   []string `yaml:"rules"`
	}
	if err := yaml.Unmarshal(buf, &schema); err == nil {
		if len(schema.Payload) > 0 {
			return schema.Payload
		}
		if len(schema.Rules) > 0 {
			return schema.Rules
		}
	}

	// Text format: one rule per line
	var result []string
	for _, line := range strings.Split(string(buf), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "//") {
			continue
		}
		result = append(result, line)
	}
	return result
}

func UpdateProvider(t string, name string) error {
	err := ErrInvalidType

	switch t {
	case "Rule":
		p := tunnel.RuleProviders()[name]
		if p == nil {
			return fmt.Errorf("%s not found", name)
		}

		err = p.Update()
	case "Proxy":
		p := tunnel.Providers()[name]
		if p == nil {
			return fmt.Errorf("%s not found", name)
		}

		err = p.Update()
	}

	if err != nil {
		log.Warnln("Updating provider %s: %s", name, err.Error())
	}

	return err
}
