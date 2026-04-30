package tunnel

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
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

// vehicleGetter matches ruleSetProvider (HTTP/File) which embeds resource.Fetcher.
type vehicleGetter interface {
	Vehicle() provider.Vehicle
}

// mrsStrategy matches domain and ipcidr strategies that can enumerate their entries.
type mrsStrategy interface {
	DumpMrs(f func(key string) bool)
}

// QueryRuleProviderFilePath returns the path to the cached rule file for the named provider.
// Returns empty string for inline providers or if the provider is not found.
func QueryRuleProviderFilePath(name string) string {
	p := tunnel.RuleProviders()[name]
	if p == nil {
		return ""
	}
	vg, ok := p.(vehicleGetter)
	if !ok {
		return ""
	}
	return vg.Vehicle().Path()
}

// DumpRuleProviderToText writes all rules of the named provider to outputPath (one per line).
// Works for domain/ipcidr (including MRS format) and inline classical providers.
// Returns an error description, or empty string on success.
func DumpRuleProviderToText(name string, outputPath string) string {
	p := tunnel.RuleProviders()[name]
	if p == nil {
		return "provider not found: " + name
	}

	var lines []string

	if dumper, ok := p.Strategy().(mrsStrategy); ok {
		// Domain and IPCIDR strategies — works for both inline and MRS/YAML formats.
		dumper.DumpMrs(func(key string) bool {
			lines = append(lines, key)
			return true
		})
	} else {
		// Classical strategy: retrieve raw payload via MarshalJSON (present on inline providers).
		if jm, ok := p.(json.Marshaler); ok {
			data, err := jm.MarshalJSON()
			if err == nil {
				var wrapper struct {
					Payload []string `json:"payload"`
				}
				if json.Unmarshal(data, &wrapper) == nil {
					lines = wrapper.Payload
				}
			}
		}
	}

	content := strings.Join(lines, "\n")
	if err := os.WriteFile(outputPath, []byte(content), 0644); err != nil {
		return "write error: " + err.Error()
	}
	return ""
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
