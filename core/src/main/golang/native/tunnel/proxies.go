package tunnel

import (
	"crypto/md5"
	"fmt"
	"os"
	P "path"
	"sort"
	"strings"

	"cfa/native/config"

	"github.com/dlclark/regexp2"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/component/profile/cachefile"
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
	Rank     string  `json:"rank"`   // smart group rank: MostUsed / OccasionalUsed / RarelyUsed
}

type ProxyGroup struct {
	Type    string   `json:"type"`
	Now     string   `json:"now"`
	Icon    string   `json:"icon"`
	Hidden  bool     `json:"hidden"`
	Proxies []*Proxy `json:"proxies"`
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

func isProxyGroupHidden(p C.Proxy) bool {
	if g, ok := p.Adapter().(outboundgroup.ProxyGroup); ok {
		switch v := g.(type) {
		case *outboundgroup.Selector:
			return v.Hidden()
		case *outboundgroup.URLTest:
			return v.Hidden()
		case *outboundgroup.Fallback:
			return v.Hidden()
		case *outboundgroup.LoadBalance:
			return v.Hidden()
		}
		return false
	}
	if p.Type() == C.Smart {
		if sg, ok := p.Adapter().(*outboundgroup.Smart); ok {
			return sg.Hidden()
		}
	}
	return false
}

func isProxyGroupVisible(p C.Proxy, excludeNotSelectable bool) bool {
	_, isGroup := p.Adapter().(outboundgroup.ProxyGroup)
	isSmart := p.Type() == C.Smart
	if !isGroup && !isSmart {
		return false
	}
	if isProxyGroupHidden(p) {
		return false
	}
	if excludeNotSelectable && p.Type() != C.Selector {
		return false
	}
	return true
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

	seen := make(map[string]bool, len(proxies))
	for _, p := range proxies {
		if !isProxyGroupVisible(p, excludeNotSelectable) {
			continue
		}
		result = append(result, p.Name())
		seen[p.Name()] = true
	}

	// When a user defines a proxy-group named "GLOBAL", it replaces the
	// auto-generated catch-all GLOBAL. The auto-generated GLOBAL contains
	// all proxy groups; the user-defined one only contains explicitly listed
	// proxies. Any proxy group not covered above belongs to this case and
	// must be included so all groups are visible in the UI.
	for name, p := range tunnel.Proxies() {
		if name == "GLOBAL" || seen[name] {
			continue
		}
		if !isProxyGroupVisible(p, excludeNotSelectable) {
			continue
		}
		result = append(result, name)
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

		// Fetch weights and ranks from the global smart store — same source the
		// external-controller /group/{name}/weights route uses.
		// NodeRankItem.Weight is a float64 0-100 (percentage); normalise to 0-1.
		type proxyRankInfo struct {
			weight float64
			rank   string
		}
		proxyRankMap := make(map[string]proxyRankInfo)
		if store := cachefile.GetSmartStore(); store != nil {
			if ranking, err := store.GetNodeWeightRankingCache(sg.Name(), sg.GetConfigFilename()); err == nil {
				for _, nr := range ranking.Result {
					proxyRankMap[nr.Name] = proxyRankInfo{
						weight: nr.Weight / 100.0,
						rank:   nr.Rank,
					}
				}
			}
		}

		// Populate Weight and Rank for each proxy.
		for _, px := range proxies {
			if info, found := proxyRankMap[px.Name]; found {
				px.Weight = info.weight
				px.Rank = info.rank
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
				if px.Weight > maxWeight {
					maxWeight = px.Weight
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

		icon := sg.Icon()
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
			Hidden:  sg.Hidden(),
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
		icon = v.Icon()
		hidden = v.Hidden()
	case *outboundgroup.URLTest:
		icon = v.Icon()
		hidden = v.Hidden()
	case *outboundgroup.Fallback:
		icon = v.Icon()
		hidden = v.Hidden()
	case *outboundgroup.LoadBalance:
		icon = v.Icon()
		hidden = v.Hidden()
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
