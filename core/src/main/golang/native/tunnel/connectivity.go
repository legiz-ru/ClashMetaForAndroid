package tunnel

import (
	"context"
	"encoding/json"
	"strings"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

// Timeout of a SINGLE probe. Same as mihomo uses: there every proxy gets its
// own context with the default 5s.
const probeTimeout = 5 * time.Second

func HealthCheck(name string) {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Request health check for `%s`: not found", name)

		return
	}

	// Smart groups do not implement outboundgroup.ProxyGroup — handle separately.
	if p.Type() == C.Smart {
		if sg, ok := p.Adapter().(*outboundgroup.Smart); ok {
			type providersGetter interface {
				Providers() []provider.ProxyProvider
			}
			if pg, ok2 := any(sg).(providersGetter); ok2 {
				wg := &sync.WaitGroup{}
				for _, pr := range pg.Providers() {
					wg.Add(1)
					go func(prov provider.ProxyProvider) {
						defer wg.Done()
						prov.HealthCheck()
					}(pr)
				}
				wg.Wait()
			} else {
				log.Warnln("Request health check for Smart `%s`: Providers() not available", name)
			}
		}
		return
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Request health check for `%s`: invalid type %s", name, p.Type().String())

		return
	}

	wg := &sync.WaitGroup{}

	for _, pr := range g.Providers() {
		wg.Add(1)

		go func(provider provider.ProxyProvider) {
			provider.HealthCheck()

			wg.Done()
		}(pr)
	}

	wg.Wait()
}

// checkableGroup is the little bit of a proxy group ProbeCurrentNodes needs.
// Declared here instead of using outboundgroup.ProxyGroup because Smart groups
// do not implement that interface, and they carry a current node just the same.
type checkableGroup interface {
	Now() string
	Providers() []provider.ProxyProvider
}

// groupCheckOptions returns the test URL and the expected status a group has to
// be checked with, i.e. exactly the ones the core itself checks it with.
//
// They are read from the JSON representation of the group: there are no
// testUrl/expectedStatus fields on the interfaces, but MarshalJSON has them for
// every group type (adapter/outboundgroup/urltest.go, selector.go, smart.go and
// friends). Getting the URL wrong is not an option: url-test picks the fastest
// node by LastDelayForTestUrl(its url), and the proxy list shows the delay for
// one specific URL — the check would run and nothing would change on screen.
//
// The fallback is the provider test URL: for groups with an inline proxies:
// list mihomo creates a "compatible" provider carrying the group URL, and when
// the group has none it substitutes C.DefaultTestURL
// (adapter/outboundgroup/parser.go).
func groupCheckOptions(g checkableGroup) (string, utils.IntRanges[uint16]) {
	url := ""
	status := ""

	if data, err := json.Marshal(g); err == nil {
		var meta map[string]any

		if json.Unmarshal(data, &meta) == nil {
			if v, ok := meta["testUrl"].(string); ok {
				url = strings.TrimSpace(v)
			}

			if v, ok := meta["expectedStatus"].(string); ok {
				status = strings.TrimSpace(v)
			}
		}
	}

	if url == "" {
		for _, pr := range g.Providers() {
			if u := strings.TrimSpace(pr.HealthCheckURL()); u != "" {
				url = u

				break
			}
		}
	}

	if url == "" {
		url = C.DefaultTestURL
	}

	// An empty string and "*" both mean "any response will do"; an empty
	// IntRanges means exactly the same in mihomo (IntRanges.Check).
	if status == "" || status == "*" {
		return url, nil
	}

	expected, err := utils.NewUnsignedRanges[uint16](status)
	if err != nil {
		log.Warnln("Health check: bad expected status `%s`: %s", status, err.Error())

		return url, nil
	}

	return url, expected
}

// groupTestOptions returns the check options of the named group, falling back to
// the mihomo defaults when the group is unknown or carries no options.
func groupTestOptions(group string) (string, utils.IntRanges[uint16]) {
	p := tunnel.Proxies()[group]
	if p == nil {
		return C.DefaultTestURL, nil
	}

	g, ok := p.Adapter().(checkableGroup)
	if !ok {
		return C.DefaultTestURL, nil
	}

	return groupCheckOptions(g)
}

// HealthCheckProxy measures the delay of ONE proxy, the way the group it is
// shown in would measure it.
//
// Backs the tap on a single row's delay badge. Runs a single probe instead of
// going through the provider: the provider path checks every proxy it holds and
// is bound to its own interval/lazy settings, while here the user asked about
// this proxy and asked now.
//
// group only supplies the test URL and the expected status — the delay is stored
// per URL, so probing with anything else would leave the badge unchanged.
func HealthCheckProxy(group, name string) {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Request health check for proxy `%s`: not found", name)

		return
	}

	url, expectedStatus := groupTestOptions(group)

	ctx, cancel := context.WithTimeout(context.Background(), probeTimeout)
	defer cancel()

	delay, err := p.URLTest(ctx, url, expectedStatus)
	if err != nil {
		log.Infoln("Health check `%s`: %s failed: %s", group, name, err.Error())

		return
	}

	log.Infoln("Health check `%s`: %s is alive, %d ms", group, name, delay)
}

// ProbeCurrentNodes probes the CURRENT node of every group, one probe per group.
//
// Called after a network change. A full group check (HealthCheck) would be
// unjustified here: a subscription holds dozens of nodes, while the question is
// a single one — is the node the user works through still alive. If it is not,
// the groups that pick automatically will move on their own: they run the same
// probe inside, and the result lands in the same delay history.
//
// A node shared by several groups is probed once per test URL: the core keeps
// one proxy object per name, but the test URL is per group and the delay is
// stored per URL.
//
// Smart groups are included: their Now() names a node only while the user has
// one fixed, and otherwise returns a placeholder that resolves to no proxy and
// is skipped — which is correct, a Smart group picks per connection and has no
// single current node to probe.
func ProbeCurrentNodes() {
	proxies := tunnel.Proxies()
	seen := make(map[string]bool, len(proxies))

	for _, p := range proxies {
		g, ok := p.Adapter().(checkableGroup)
		if !ok {
			continue
		}

		now := g.Now()
		if now == "" {
			continue
		}

		target := proxies[now]
		if target == nil {
			continue
		}

		url, expectedStatus := groupCheckOptions(g)
		if url == "" {
			continue
		}

		key := now + "|" + url
		if seen[key] {
			continue
		}

		seen[key] = true

		go func(px C.Proxy, url string, expected utils.IntRanges[uint16]) {
			ctx, cancel := context.WithTimeout(context.Background(), probeTimeout)
			defer cancel()

			delay, err := px.URLTest(ctx, url, expected)
			if err != nil {
				log.Infoln("Probe after network change: %s failed: %s", px.Name(), err.Error())

				return
			}

			log.Infoln("Probe after network change: %s is alive, %d ms", px.Name(), delay)
		}(target, url, expectedStatus)
	}
}

func HealthCheckAll() {
	for _, g := range QueryProxyGroupNames(false) {
		go func(group string) {
			HealthCheck(group)
		}(g)
	}
}
