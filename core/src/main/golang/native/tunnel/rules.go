package tunnel

import (
	"github.com/metacubex/mihomo/tunnel"
)

type Rule struct {
	Type    string `json:"type"`
	Payload string `json:"payload"`
	Proxy   string `json:"proxy"`
}

func QueryRules() []*Rule {
	rawRules := tunnel.Rules()
	result := make([]*Rule, 0, len(rawRules))
	for _, r := range rawRules {
		result = append(result, &Rule{
			Type:    r.RuleType().String(),
			Payload: r.Payload(),
			Proxy:   r.Adapter(),
		})
	}
	return result
}
