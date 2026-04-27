package tunnel

import (
	"github.com/metacubex/mihomo/tunnel/statistic"
)

func QueryConnections() *statistic.Snapshot {
	return statistic.DefaultManager.Snapshot()
}

func CloseConnection(id string) bool {
	found := false

	statistic.DefaultManager.Range(func(t statistic.Tracker) bool {
		if t.ID() == id {
			_ = t.Close()
			found = true
			return false
		}
		return true
	})

	return found
}
