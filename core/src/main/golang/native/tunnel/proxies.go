package tunnel

import (
	"crypto/md5"
	"encoding/json"
	"fmt"
	"os"
	P "path"
	"reflect"
	"sort"
	"strings"

	"cfa/native/config"

	"github.com/dlclark/regexp2"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

type SortMode int

const (
	Default SortMode = iota
	Title
	Delay
)

type Proxy struct {
	Name     string  `json:"name"`
	Title    string  `json:"title"`
	Subtitle string  `json:"subtitle"`
	Type     string  `json:"type"`
	Delay    int     `json:"delay"`
	Weight   float64 `json:"weight"` // smart group weight; 0 if not applicable
}

type ProxyGroup struct {
	Type    string   `json:"type"`
	Now     string   `json:"now"`
	Icon    string   `json:"icon"`
	Hidden  bool     `json:"hidden"`
	Proxies []*Proxy `json:"proxies"`
}

// fetchSmartWeights tries to obtain proxy weights from a Smart group
// adapter using reflection so it is compatible with any return type
// the fork may use (map[string]float64, map[string]int,
// []struct{Name,Rank,Weight}, etc.).
//
// The function looks for a method named "GetWeights" on the adapter,
// calls it, JSON-marshals the result, then decodes into either a flat
// map or a named-entry slice.  The returned map values are normalised
// to the range [0, 1] (dividing integer percentages by 100) so that
// the caller can compare them uniformly as float64.
func fetchSmartWeights(adapter interface{}) map[string]float64 {
	v := reflect.ValueOf(adapter)
	m := v.MethodByName("GetWeights")
	if !m.IsValid() {
		return nil
	}

	results := m.Call(nil)
	if len(results) == 0 {
		return nil
	}

	raw := results[0].Interface()
	if raw == nil {
		return nil
	}

	// Fast paths for the two most common concrete types —
	// avoids the JSON round-trip overhead.
	if mf, ok := raw.(map[string]float64); ok && len(mf) > 0 {
		return mf
	}
	if mi, ok := raw.(map[string]int); ok && len(mi) > 0 {
		out := make(map[string]float64, len(mi))
		for k, vv := range mi {
			out[k] = float64(vv) / 100.0
		}
		return out
	}

	// Generic path: marshal to JSON then decode into known shapes.
	data, err := json.Marshal(raw)
	if err != nil {
		return nil
	}

	// Shape A: map[string]float64 or map[string]json.Number
	var mapFloat map[string]json.Number
	if json.Unmarshal(data, &mapFloat) == nil && len(mapFloat) > 0 {
		out := make(map[string]float64, len(mapFloat))
		for k, n := range mapFloat {
			if f, e := n.Float64(); e == nil {
				// normalise: if the value is > 1, assume it is a
				// 0-100 percentage
				if f > 1 {
					f /= 100.0
				}
				out[k] = f
			}
		}
		return out
	}

	// Shape B: []{"Name":"...", "Rank":"...", "Weight": <number>}
	var slice []struct {
		Name   string      `json:"Name"`
		Rank   string      `json:"Rank"`
		Weight json.Number `json:"Weight"`
	}
	if json.Unmarshal(data, &slice) == nil && len(slice) > 0 {
		out := make(map[string]float64, len(slice))
		for _, item := range slice {
			f, e := item.Weight.Float64()
			if e != nil {
				continue
			}
			if f > 1 {
				f /= 100.0
			}
			out[item.Name] = f
		}
		return out
	}

	return nil
}

type sortableProxyList struct {
	list []*Proxy
	less func(a, b *Proxy) bool
}

func (s *sortableProxyList) Len() int {
	return len(s.list)
}

func (s *sortableProxyList) Less(i, j int) bool {
	return s.less(s.list[i], s.list[j])
}

func (s *sortableProxyList) Swap(i, j int) {
	s.list[i], s.list[j] = s.list[j], s.list[i]
}

func QueryProxyGroupNames(excludeNotSelectable bool) []string {
	mode := tunnel.Mode()

	if mode == tunnel.Direct {
		return []string{}
	}

	global := tunnel.Proxies()["GLOBAL"].Adapter().(outboundgroup.ProxyGroup)
	proxies := global.Providers()[0].Proxies()
	result := make([]string, 0, len(proxies)+1)

	if mode == tunnel.Global {
		result = append(result, "GLOBAL")
	}

	for _, p := range proxies {
		if g, ok := p.Adapter().(outboundgroup.ProxyGroup); ok {
			// Skip hidden groups using concrete type assertions
			hidden := false
			switch v := g.(type) {
			case *outboundgroup.Selector:
				hidden = v.Hidden
			case *outboundgroup.URLTest:
				hidden = v.Hidden
			case *outboundgroup.Fallback:
				hidden = v.Hidden
			case *outboundgroup.LoadBalance:
				hidden = v.Hidden
			}
			if hidden {
				continue
			}
			if !excludeNotSelectable || p.Type() == C.Selector {
				result = append(result, p.Name())
			}
		} else if p.Type() == C.Smart {
			// Smart does not implement outboundgroup.ProxyGroup (no Providers()),
			// so handle it separately.
			if sg, ok := p.Adapter().(*outboundgroup.Smart); ok {
				if sg.Hidden {
					continue
				}
				result = append(result, p.Name())
			}
		}
	}

	return result
}

func QueryProxyGroup(name string, sortMode SortMode, uiSubtitlePattern *regexp2.Regexp) *ProxyGroup {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Query group `%s`: not found", name)

		return nil
	}

	// Smart groups do not implement outboundgroup.ProxyGroup — handle separately.
	if p.Type() == C.Smart {
		sg, ok := p.Adapter().(*outboundgroup.Smart)
		if !ok {
			log.Warnln("Query group `%s`: Smart cast failed", name)
			return nil
		}

		proxies := convertProxies(sg.GetProxies(false), uiSubtitlePattern)

		// Fetch weights via reflection so we work with any return type the
		// fork's Smart group exposes (map[string]float64, map[string]int,
		// []*WeightInfo, etc.).  We JSON-round-trip the result and then
		// try to decode it into two well-known shapes.
		proxyWeights := fetchSmartWeights(sg)

		// Populate Weight for each proxy.
		for _, px := range proxies {
			if w, found := proxyWeights[px.Name]; found {
				px.Weight = w
			}
		}

		// Determine the best proxy name to display as "Now".
		// sg.Now() may return a mode string like "Smart" or "Select" rather than
		// a real proxy name, so verify it against the actual proxy list.
		bestNow := sg.Now()
		isRealProxy := false
		for _, px := range proxies {
			if px.Name == bestNow {
				isRealProxy = true
				break
			}
		}
		if !isRealProxy {
			bestNow = ""
			// Priority 1: highest-weight proxy from weights API.
			var maxWeight float64 = -1
			for _, px := range proxies {
				if w, found := proxyWeights[px.Name]; found && w > maxWeight {
					maxWeight = w
					bestNow = px.Name
				}
			}
			// Priority 2: lowest-delay proxy as fallback.
			if bestNow == "" {
				minDelay := -1
				for _, px := range proxies {
					if px.Delay > 0 && (minDelay < 0 || px.Delay < minDelay) {
						minDelay = px.Delay
						bestNow = px.Name
					}
				}
			}
		}

		switch sortMode {
		case Title:
			sort.Sort(&sortableProxyList{
				list: proxies,
				less: func(a, b *Proxy) bool { return strings.Compare(a.Title, b.Title) < 0 },
			})
		case Delay:
			sort.Sort(&sortableProxyList{
				list: proxies,
				less: func(a, b *Proxy) bool { return a.Delay < b.Delay },
			})
		}

		icon := sg.Icon
		if icon != "" && config.CurrentProfileDir != "" {
			hash := fmt.Sprintf("%x", md5.Sum([]byte(icon)))
			cachedPath := P.Join(config.CurrentProfileDir, "icons", hash)
			if _, err := os.Stat(cachedPath); err == nil {
				icon = "file://" + cachedPath
			}
		}

		return &ProxyGroup{
			Type:    p.Type().String(),
			Now:     bestNow,
			Icon:    icon,
			Hidden:  sg.Hidden,
			Proxies: proxies,
		}
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Query group `%s`: invalid type %s", name, p.Type().String())

		return nil
	}

	proxies := convertProxies(g.Proxies(), uiSubtitlePattern)
	// 	proxies := collectProviders(g.Providers(), uiSubtitlePattern)

	switch sortMode {
	case Title:
		wrapper := &sortableProxyList{
			list: proxies,
			less: func(a, b *Proxy) bool {
				return strings.Compare(a.Title, b.Title) < 0
			},
		}

		sort.Sort(wrapper)
	case Delay:
		wrapper := &sortableProxyList{
			list: proxies,
			less: func(a, b *Proxy) bool {
				return a.Delay < b.Delay
			},
		}

		sort.Sort(wrapper)
	case Default:
	default:
	}

	icon := ""
	hidden := false
	switch v := g.(type) {
	case *outboundgroup.Selector:
		icon = v.Icon
		hidden = v.Hidden
	case *outboundgroup.URLTest:
		icon = v.Icon
		hidden = v.Hidden
	case *outboundgroup.Fallback:
		icon = v.Icon
		hidden = v.Hidden
	case *outboundgroup.LoadBalance:
		icon = v.Icon
		hidden = v.Hidden
	}

	// Check for cached icon file
	if icon != "" && config.CurrentProfileDir != "" {
		hash := fmt.Sprintf("%x", md5.Sum([]byte(icon)))
		cachedPath := P.Join(config.CurrentProfileDir, "icons", hash)
		if _, err := os.Stat(cachedPath); err == nil {
			icon = "file://" + cachedPath
		}
	}

	return &ProxyGroup{
		Type:    g.Type().String(),
		Now:     g.Now(),
		Icon:    icon,
		Hidden:  hidden,
		Proxies: proxies,
	}
}

func PatchSelector(selector, name string) bool {
	p := tunnel.Proxies()[selector]

	if p == nil {
		log.Warnln("Patch selector `%s`: not found", selector)

		return false
	}

	// Smart implements SelectAble but not ProxyGroup — handle separately.
	if p.Type() == C.Smart {
		sg, ok := p.Adapter().(*outboundgroup.Smart)
		if !ok {
			log.Warnln("Patch selector `%s`: Smart cast failed", selector)
			return false
		}
		if err := sg.Set(name); err != nil {
			log.Warnln("Patch selector `%s`: %s", selector, err.Error())
		}
		log.Infoln("Patch selector %s -> %s", selector, name)
		closeConnByGroup(selector)
		return true
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Patch selector `%s`: invalid type %s", selector, p.Type().String())

		return false
	}

	s, ok := g.(outboundgroup.SelectAble)
	if !ok {
		log.Warnln("Patch selector `%s`: invalid type %s", selector, p.Type().String())

		return false
	}

	if err := s.Set(name); err != nil {
		log.Warnln("Patch selector `%s`: %s", selector, err.Error())
	}

	log.Infoln("Patch selector %s -> %s", selector, name)

	closeConnByGroup(selector)

	return true
}

func convertProxies(proxies []C.Proxy, uiSubtitlePattern *regexp2.Regexp) []*Proxy {
	result := make([]*Proxy, 0, 128)

	for _, p := range proxies {
		name := p.Name()
		title := name
		subtitle := p.Type().String()

		if uiSubtitlePattern != nil {
			if _, ok := p.Adapter().(outboundgroup.ProxyGroup); !ok {
				runes := []rune(name)
				match, err := uiSubtitlePattern.FindRunesMatch(runes)
				if err == nil && match != nil {
					title = string(runes[:match.Index]) + string(runes[match.Index+match.Length:])
					subtitle = string(runes[match.Index : match.Index+match.Length])
				}
			}
		}
		testURL := "https://www.gstatic.com/generate_204"
		for k := range p.ExtraDelayHistories() {
			if len(k) > 0 {
				testURL = k
				break
			}
		}

		result = append(result, &Proxy{
			Name:     name,
			Title:    strings.TrimSpace(title),
			Subtitle: strings.TrimSpace(subtitle),
			Type:     p.Type().String(),
			Delay:    int(p.LastDelayForTestUrl(testURL)),
		})
	}
	return result
}

func collectProviders(providers []provider.ProxyProvider, uiSubtitlePattern *regexp2.Regexp) []*Proxy {
	result := make([]*Proxy, 0, 128)

	for _, p := range providers {
		for _, px := range p.Proxies() {
			name := px.Name()
			title := name
			subtitle := px.Type().String()

			if uiSubtitlePattern != nil {
				if _, ok := px.Adapter().(outboundgroup.ProxyGroup); !ok {
					runes := []rune(name)
					match, err := uiSubtitlePattern.FindRunesMatch(runes)
					if err == nil && match != nil {
						title = string(runes[:match.Index]) + string(runes[match.Index+match.Length:])
						subtitle = string(runes[match.Index : match.Index+match.Length])
					}
				}
			}

			testURL := "https://www.gstatic.com/generate_204"
			for k := range px.ExtraDelayHistories() {
				if len(k) > 0 {
					testURL = k
					break
				}
			}

			result = append(result, &Proxy{
				Name:     name,
				Title:    strings.TrimSpace(title),
				Subtitle: strings.TrimSpace(subtitle),
				Type:     px.Type().String(),
				Delay:    int(px.LastDelayForTestUrl(testURL)),
			})
		}
	}

	return result
}
