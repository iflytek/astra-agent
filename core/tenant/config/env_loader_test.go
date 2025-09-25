package config

import (
	"fmt"
	"os"
	"testing"
)

func TestEnvLoader_Load(t *testing.T) {
	// Set environment variables for testing
	if err := os.Setenv("SERVICE_PORT", "8080"); err != nil {
		t.Errorf("Setenv() error = %v", err)
	}
	if err := os.Setenv("SERVICE_LOCATION", "localhost"); err != nil {
		t.Errorf("Setenv() error = %v", err)
	}
	if err := os.Setenv("DATABASE_DB_TYPE", "mysql"); err != nil {
		t.Errorf("Setenv() error = %v", err)
	}
	if err := os.Setenv("DATABASE_USERNAME", "root"); err != nil {
		t.Errorf("Setenv() error = %v", err)
	}
	if err := os.Setenv("DATABASE_PASSWORD", "password"); err != nil {
		t.Errorf("Setenv() error = %v", err)
	}
	if err := os.Setenv("DATABASE_URL", "(localhost:3306)/tenant"); err != nil {
		t.Errorf("Setenv() error = %v", err)
	}
	if err := os.Setenv("DATABASE_MAX_OPEN_CONNS", "5"); err != nil {
		t.Errorf("Setenv() error = %v", err)
	}
	if err := os.Setenv("DATABASE_MAX_IDLE_CONNS", "5"); err != nil {
		t.Errorf("Setenv() error = %v", err)
	}
	if err := os.Setenv("LOG_PATH", "log.txt"); err != nil {
		t.Errorf("Setenv() error = %v", err)
	}

	cfg := &Config{}
	l := NewEnvLoader()
	if err := l.Load(cfg); err != nil {
		t.Errorf("Load() error = %v", err)
	}
	fmt.Println(cfg.String())
}
