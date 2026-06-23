package config

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	U "net/url"
	"os"
	P "path"
	"runtime"
	"sync"
	"time"

	"cfa/native/app"

	clashHttp "github.com/metacubex/mihomo/component/http"
	RB "github.com/metacubex/mihomo/rules/bundle"
)

type Status struct {
	Action      string   `json:"action"`
	Args        []string `json:"args"`
	Progress    int      `json:"progress"`
	MaxProgress int      `json:"max"`
}

func openUrl(ctx context.Context, url string) (io.ReadCloser, error) {
	response, err := clashHttp.HttpRequest(ctx, url, http.MethodGet, http.Header{"User-Agent": {"Clash-Meta/Prizrak-Box (Android Build " + app.VersionName() + ")"}}, nil)

	if err != nil {
		return nil, err
	}

	return response.Body, nil
}

func openContent(url string) (io.ReadCloser, error) {
	return app.OpenContent(url)
}

func fetch(url *U.URL, file string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	var reader io.ReadCloser
	var err error

	switch url.Scheme {
	case "http", "https":
		reader, err = openUrl(ctx, url.String())
	case "content":
		reader, err = openContent(url.String())
	default:
		err = fmt.Errorf("unsupported scheme %s of %s", url.Scheme, url)
	}

	if err != nil {
		return err
	}

	defer reader.Close()

	return writeFile(file, reader)
}

func writeFile(file string, reader io.Reader) error {
	_ = os.MkdirAll(P.Dir(file), 0700)

	f, err := os.OpenFile(file, os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0600)
	if err != nil {
		return err
	}

	defer f.Close()

	_, err = io.Copy(f, reader)
	if err != nil {
		_ = os.Remove(file)
	}

	return err
}

func FetchAndValid(
	path string,
	url string,
	force bool,
	reportStatus func(string),
) error {
	configPath := P.Join(path, "config.yaml")

	if _, err := os.Stat(configPath); os.IsNotExist(err) || force {
		url, err := U.Parse(url)
		if err != nil {
			return err
		}

		bytes, _ := json.Marshal(&Status{
			Action:      "FetchConfiguration",
			Args:        []string{url.Host},
			Progress:    -1,
			MaxProgress: -1,
		})

		reportStatus(string(bytes))

		if err := fetch(url, configPath); err != nil {
			return err
		}
	}

	defer runtime.GC()

	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		return err
	}

	// Collect the providers that actually need downloading (skip cached / invalid),
	// then fetch them concurrently with a bounded worker pool. Provider downloads are
	// independent network calls, so parallelising them cuts the wall-clock time roughly
	// by the pool size and turns stacked per-host timeouts into parallel ones.
	type providerTask struct {
		name         string
		url          *U.URL
		path         string
		pathInBundle string
		isRule       bool
	}

	var tasks []providerTask

	forEachProviders(rawCfg, func(index int, total int, name string, provider map[string]any, prefix string) {
		u, uok := provider["url"]
		p, pok := provider["path"]

		if !uok || !pok {
			return
		}

		us, uok := u.(string)
		ps, pok := p.(string)

		if !uok || !pok {
			return
		}

		if _, err := os.Stat(ps); err == nil {
			return
		}

		url, err := U.Parse(us)
		if err != nil {
			return
		}

		pib := ""
		if prefix == RULES {
			if v, ok := provider["path-in-bundle"]; ok {
				if s, ok := v.(string); ok {
					pib = s
				}
			}
		}

		tasks = append(tasks, providerTask{
			name:         name,
			url:          url,
			path:         ps,
			pathInBundle: pib,
			isRule:       prefix == RULES,
		})
	})

	if total := len(tasks); total > 0 {
		const maxConcurrent = 5

		fetchOne := func(t providerTask) {
			if t.isRule && t.pathInBundle != "" {
				// The core will handle extraction; we maintain fetch consistency
				// with historical CMFA behavior by pre-fetching from the bundle.
				if file, err := RB.Open(t.pathInBundle); err == nil {
					wrErr := writeFile(t.path, file)
					file.Close()
					if wrErr == nil {
						return
					}
				}
			}

			_ = fetch(t.url, t.path)
		}

		sem := make(chan struct{}, maxConcurrent)
		doneCh := make(chan string, total)
		var wg sync.WaitGroup

		for i := range tasks {
			t := tasks[i]
			wg.Add(1)
			sem <- struct{}{}

			go func() {
				defer wg.Done()
				defer func() { <-sem }()

				fetchOne(t)

				doneCh <- t.name
			}()
		}

		go func() {
			wg.Wait()
			close(doneCh)
		}()

		// Report progress from this goroutine only, preserving the original
		// single-threaded reportStatus call site (the JNI callback is not safe
		// to invoke concurrently from worker goroutines).
		completed := 0
		for name := range doneCh {
			completed++

			bytes, _ := json.Marshal(&Status{
				Action:      "FetchProviders",
				Args:        []string{name},
				Progress:    completed,
				MaxProgress: total,
			})

			reportStatus(string(bytes))
		}
	}

	// Fetch proxy group icons
	fetchProxyGroupIcons(rawCfg, path, reportStatus)

	bytes, _ := json.Marshal(&Status{
		Action:      "Verifying",
		Args:        []string{},
		Progress:    0xffff,
		MaxProgress: 0xffff,
	})

	reportStatus(string(bytes))

	cfg, err := Parse(rawCfg)
	if err != nil {
		return err
	}

	destroyProviders(cfg)

	return nil
}
