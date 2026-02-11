package config

import "fmt"

// Builtin templates embedded in Go code
// These are synced with app/src/main/assets/templates/*.yaml

const TemplateDefaultContent = `# Default Template for ClashMetaForAndroid
mixed-port: 7890
allow-lan: true
tcp-concurrent: true
enable-process: true
find-process-mode: always
mode: rule
log-level: info
ipv6: false
keep-alive-interval: 30
unified-delay: true

profile:
  store-selected: true
  store-fake-ip: true

sniffer:
  enable: true
  force-dns-mapping: true
  parse-pure-ip: true
  sniff:
    HTTP:
      ports:
        - 80
        - 8080-8880
      override-destination: true
    TLS:
      ports:
        - 443
        - 8443

dns:
  enable: true
  prefer-h3: true
  use-hosts: true
  use-system-hosts: true
  ipv6: false
  enhanced-mode: redir-host
  default-nameserver:
    - tls://77.88.8.8#DIRECT
    - 195.208.4.1#DIRECT
  nameserver:
    - https://cloudflare-dns.com/dns-query#PROXY
    - https://1.1.1.1/dns-query#PROXY

proxies:
  # Proxies will be injected here from subscription

proxy-groups:
  - name: PROXY
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Hijacking.png
    type: select
    include-all: true
    proxies:
      - AUTO

  - name: AUTO
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Auto.png
    type: url-test
    url: https://www.gstatic.com/generate_204
    interval: 300
    include-all: true

  - name: YouTube
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/YouTube.png
    type: select
    include-all: true
    proxies:
      - PROXY
      - AUTO

  - name: DIRECT
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Blackhole.png
    type: select
    hidden: true
    proxies:
      - DIRECT

rule-providers:
  ru-inline:
    type: http
    url: https://github.com/legiz-ru/mihomo-rule-sets/raw/main/other/inline/ru-inline.yaml
    interval: 86400
    behavior: classical
    format: yaml
    path: ./rule-sets/ru-inline.yaml

  youtube:
    type: http
    behavior: domain
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geosite/youtube.mrs
    path: ./rule-sets/youtube.mrs
    interval: 86400

  geosite-ru:
    type: http
    behavior: domain
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geosite/category-ru.mrs
    path: ./rule-sets/geosite-ru.mrs
    interval: 86400

  geoip-ru:
    type: http
    behavior: ipcidr
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geoip/ru.mrs
    path: ./rule-sets/geoip-ru.mrs
    interval: 86400

  geosite-private:
    type: http
    behavior: domain
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geosite/private.mrs
    path: ./rule-sets/geosite-private.mrs
    interval: 86400

  geoip-private:
    type: http
    behavior: ipcidr
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geoip/private.mrs
    path: ./rule-sets/geoip-private.mrs
    interval: 86400

rules:
  - RULE-SET,youtube,YouTube
  - RULE-SET,geoip-private,DIRECT,no-resolve
  - RULE-SET,geosite-private,DIRECT
  - RULE-SET,ru-inline,DIRECT
  - RULE-SET,geosite-ru,DIRECT
  - RULE-SET,geoip-ru,DIRECT
  - MATCH,PROXY
`

const TemplateRubundleContent = `# RU Bundle Template for ClashMetaForAndroid
mode: rule
log-level: info
mixed-port: 7890
unified-delay: true
allow-lan: true
tcp-concurrent: true
enable-process: true
find-process-mode: always

profile:
  store-selected: true
  store-fake-ip: true

sniffer:
  enable: true
  force-dns-mapping: true
  parse-pure-ip: true
  sniff:
    HTTP:
      ports:
        - 80
        - 8080-8880
      override-destination: true
    TLS:
      ports:
        - 443
        - 8443

dns:
  enable: true
  prefer-h3: true
  use-hosts: true
  use-system-hosts: true
  ipv6: false
  enhanced-mode: redir-host
  default-nameserver:
    - tls://77.88.8.8#DIRECT
    - 195.208.4.1#DIRECT
  nameserver:
    - https://cloudflare-dns.com/dns-query#PROXY

proxies:
  # Proxies will be injected here from subscription

proxy-groups:
  - name: PROXY
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Hijacking.png
    type: select
    proxies:
      - AUTO
    include-all: true

  - name: YouTube
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/YouTube.png
    type: select
    proxies:
      - PROXY
    include-all: true

  - name: Discord
    icon: https://raw.githubusercontent.com/remnawave/templates/refs/heads/main/icons/Discord.png
    type: select
    proxies:
      - PROXY
    include-all: true

  - name: Telegram
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Telegram.png
    type: select
    include-all: true
    proxies:
      - PROXY

  - name: AUTO
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Auto.png
    type: url-test
    tolerance: 150
    url: https://www.gstatic.com/generate_204
    interval: 300
    proxies: []
    include-all: true

rule-providers:
  telegram-ips:
    type: http
    behavior: ipcidr
    format: mrs
    interval: 86400
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geoip/telegram.mrs
    path: ./rule-sets/telegram-ips.mrs

  telegram-domains:
    type: http
    behavior: domain
    format: mrs
    interval: 86400
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geosite/telegram.mrs
    path: ./rule-sets/telegram-domains.mrs

  discord-domains:
    type: http
    behavior: domain
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geosite/discord.mrs
    path: ./rule-sets/discord-domains.mrs
    interval: 86400

  refilter-domains:
    type: http
    behavior: domain
    format: mrs
    url: https://github.com/legiz-ru/mihomo-rule-sets/raw/main/re-filter/domain-rule.mrs
    path: ./rule-sets/refilter-domains.mrs
    interval: 86400

  refilter-ips:
    type: http
    behavior: ipcidr
    format: mrs
    url: https://github.com/legiz-ru/mihomo-rule-sets/raw/main/re-filter/ip-rule.mrs
    path: ./rule-sets/refilter-ips.mrs
    interval: 86400

  youtube:
    type: http
    behavior: domain
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geosite/youtube.mrs
    path: ./rule-sets/youtube.mrs
    interval: 86400

  ru-bundle:
    type: http
    behavior: domain
    format: mrs
    url: https://github.com/legiz-ru/mihomo-rule-sets/raw/main/ru-bundle/rule.mrs
    path: ./rule-sets/ru-bundle.mrs
    interval: 86400

rules:
  - OR,((RULE-SET,telegram-ips),(RULE-SET,telegram-domains)),Telegram
  - RULE-SET,youtube,YouTube
  - RULE-SET,discord-domains,Discord
  - RULE-SET,ru-bundle,PROXY
  - RULE-SET,refilter-domains,PROXY
  - RULE-SET,refilter-ips,PROXY
  - MATCH,DIRECT
`

const TemplateDavoyanContent = `# Davoyan Template for ClashMetaForAndroid
mixed-port: 7890
allow-lan: true
tcp-concurrent: true
enable-process: true
find-process-mode: always
mode: rule
log-level: info
ipv6: false
bind-address: "*"
keep-alive-interval: 30
unified-delay: false

profile:
  store-selected: true
  store-fake-ip: true

sniffer:
  enable: true
  force-dns-mapping: true
  parse-pure-ip: true
  override-destination: false
  sniff:
    HTTP:
      ports:
        - 80
        - 8080-8880
      override-destination: true
    TLS:
      ports:
        - 443
        - 8443

dns:
  enable: true
  prefer-h3: false
  use-hosts: true
  use-system-hosts: true
  ipv6: false
  enhanced-mode: redir-host
  default-nameserver:
    - https://94.140.14.14/dns-query#DIRECT
    - https://8.8.8.8/dns-query#DIRECT
  proxy-server-nameserver:
    - https://94.140.14.14/dns-query#DIRECT
    - https://8.8.8.8/dns-query#DIRECT
  direct-nameserver:
    - 77.88.8.8#DIRECT
    - 77.88.8.1#DIRECT
  nameserver:
    - tls://94.140.14.14#PROXY
    - tls://94.140.15.15#PROXY

proxies:
  # Proxies will be injected here from subscription

proxy-groups:
  - name: Blocked Sites
    icon: https://raw.githubusercontent.com/remnawave/templates/refs/heads/main/icons/Blocked.png
    type: select
    include-all: true
    proxies:
      - AUTO
      - DIRECT

  - name: YouTube
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/YouTube.png
    type: select
    include-all: true
    proxies:
      - Blocked Sites

  - name: Discord
    icon: https://raw.githubusercontent.com/remnawave/templates/refs/heads/main/icons/Discord.png
    type: select
    include-all: true
    proxies:
      - Blocked Sites

  - name: Telegram
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Telegram.png
    type: select
    include-all: true
    proxies:
      - Blocked Sites
      - DIRECT

  - name: RU Sites
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Russia.png
    type: select
    proxies:
      - DIRECT

  - name: Other Sites
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Global.png
    type: select
    include-all: true
    proxies:
      - DIRECT
      - Blocked Sites

  - name: PROXY
    type: select
    hidden: true
    proxies:
      - Blocked Sites
    include-all: true

  - name: AUTO
    type: url-test
    hidden: true
    url: https://www.gstatic.com/generate_204
    interval: 300
    include-all: true

  - name: DIRECT
    icon: https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Blackhole.png
    type: select
    hidden: true
    proxies:
      - DIRECT

rule-providers:
  geoip-private:
    type: http
    behavior: ipcidr
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geoip/private.mrs
    path: ./rule-sets/geoip-private.mrs
    interval: 86400

  geosite-private:
    type: http
    behavior: domain
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geosite/private.mrs
    path: ./rule-sets/geosite-private.mrs
    interval: 86400

  youtube:
    type: http
    behavior: domain
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geosite/youtube.mrs
    path: ./rule-sets/youtube.mrs
    interval: 86400

  telegram-ips:
    type: http
    behavior: ipcidr
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geoip/telegram.mrs
    path: ./rule-sets/telegram-ips.mrs
    interval: 86400

  telegram-domains:
    type: http
    behavior: domain
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geosite/telegram.mrs
    path: ./rule-sets/telegram-domains.mrs
    interval: 86400

  discord-domains:
    type: http
    behavior: domain
    format: mrs
    url: https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geosite/discord.mrs
    path: ./rule-sets/discord-domains.mrs
    interval: 86400

  ru-inline:
    type: http
    url: https://github.com/legiz-ru/mihomo-rule-sets/raw/main/other/inline/ru-inline.yaml
    interval: 86400
    behavior: classical
    format: yaml
    path: ./rule-sets/ru-inline.yaml

rules:
  - RULE-SET,geoip-private,DIRECT,no-resolve
  - RULE-SET,geosite-private,DIRECT
  - RULE-SET,youtube,YouTube
  - OR,((RULE-SET,telegram-ips),(RULE-SET,telegram-domains)),Telegram
  - RULE-SET,discord-domains,Discord
  - RULE-SET,ru-inline,RU Sites
  - MATCH,Other Sites
`

// GetBuiltinTemplateContent returns embedded template content by ID
func GetBuiltinTemplateContent(templateID TemplateID) (string, error) {
	switch templateID {
	case TemplateDefault:
		return TemplateDefaultContent, nil
	case TemplateRubundle:
		return TemplateRubundleContent, nil
	case TemplateDavoyan:
		return TemplateDavoyanContent, nil
	default:
		return "", fmt.Errorf("unknown builtin template: %s", templateID)
	}
}
