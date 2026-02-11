package config

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

// GetBuiltinTemplateContent returns embedded template content by ID
func GetBuiltinTemplateContent(templateID TemplateID) (string, error) {
	switch templateID {
	case TemplateDefault:
		return TemplateDefaultContent, nil
	case TemplateRubundle:
		// TODO: Add rubundle template content
		return TemplateDefaultContent, nil
	case TemplateDavoyan:
		// TODO: Add davoyan template content
		return TemplateDefaultContent, nil
	default:
		return "", fmt.Errorf("unknown builtin template: %s", templateID)
	}
}
