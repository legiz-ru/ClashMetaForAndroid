package config

import (
	"net"
	"os"
	P "path"
	"runtime"
	"strconv"
	"strings"
	"sync"

	"cfa/native/app"

	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/hub/route"
	"github.com/metacubex/mihomo/log"
)

// CurrentProfileDir holds the path of the currently loaded profile directory,
// used by the tunnel package to resolve cached icons.
var CurrentProfileDir string

// apiServerOnce ensures the REST API server is started at most once per process
// lifetime. moshen stubs out route.ReCreateServer (build tag cmfa), so we must
// call route.StartByPandoraBox explicitly on first Load().
var apiServerOnce sync.Once

func startAPIServer(cfg *config.Config) {
	addr := cfg.Controller.ExternalController
	if addr == "" {
		return
	}
	host, portStr, err := net.SplitHostPort(addr)
	if err != nil {
		log.Warnln("external-controller address invalid: %s", err)
		return
	}
	port, err := strconv.Atoi(portStr)
	if err != nil {
		log.Warnln("external-controller port invalid: %s", err)
		return
	}
	listenAddr := route.StartByPandoraBox(host, port, cfg.Controller.Secret, route.Cors{
		AllowOrigins:        cfg.Controller.Cors.AllowOrigins,
		AllowPrivateNetwork: cfg.Controller.Cors.AllowPrivateNetwork,
	})
	log.Infoln("REST API listening at %s", listenAddr)
}

func logDns(cfg *config.RawConfig) {
	bytes, err := yaml.Marshal(&cfg.DNS)
	if err != nil {
		log.Warnln("Marshal dns: %s", err.Error())

		return
	}

	log.Infoln("dns:")

	for _, line := range strings.Split(string(bytes), "\n") {
		log.Infoln("  %s", line)
	}
}

func UnmarshalAndPatch(profilePath string) (*config.RawConfig, error) {
	configPath := P.Join(profilePath, "config.yaml")

	configData, err := os.ReadFile(configPath)
	if err != nil {
		return nil, err
	}

	rawConfig, err := config.UnmarshalRawConfig(configData)
	if err != nil {
		return nil, err
	}

	if err := process(rawConfig, profilePath); err != nil {
		return nil, err
	}

	return rawConfig, nil
}

func Parse(rawConfig *config.RawConfig) (*config.Config, error) {
	cfg, err := config.ParseRawConfig(rawConfig)
	if err != nil {
		return nil, err
	}

	return cfg, nil
}

func Load(path string) error {
	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	logDns(rawCfg)

	cfg, err := Parse(rawCfg)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	CurrentProfileDir = path

	// like hub.Parse()
	hub.ApplyConfig(cfg)

	// moshen stubs route.ReCreateServer when built with the cmfa tag, so the
	// standard external-controller TCP listener is never started by ApplyConfig.
	// Call StartByPandoraBox explicitly. apiServerOnce ensures a single server
	// per process (StartByPandoraBox has no built-in stop/restart support).
	if cfg.Controller.ExternalController != "" {
		apiServerOnce.Do(func() { startAPIServer(cfg) })
	}

	app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)

	runtime.GC()

	return nil
}

func LoadDefault() {
	cfg, err := config.Parse([]byte{})
	if err != nil {
		panic(err.Error())
	}

	hub.ApplyConfig(cfg)
}
