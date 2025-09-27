package config

import (
	"fmt"
	"testing"
)

func TestLoad(*testing.T) {
	cfg := &Config{}
	loader := NewLocalLoader("../config.toml")
	if err := loader.Load(cfg); err != nil {
		panic(err)
	}
	fmt.Println(cfg)
}
