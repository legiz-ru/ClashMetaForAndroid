package tunnel

import (
	"sync"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

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

func HealthCheckAll() {
	for _, g := range QueryProxyGroupNames(false) {
		go func(group string) {
			HealthCheck(group)
		}(g)
	}
}
