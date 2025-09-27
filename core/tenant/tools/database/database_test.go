package database

import (
	"fmt"
	"tenant/config"
	"testing"
)

func TestNewDatabase(*testing.T) {
	conf := &config.Config{
		DataBase: struct {
			DBType       string `toml:"dbType"`
			UserName     string `toml:"username"`
			Password     string `toml:"password"`
			Url          string `toml:"url"`
			MaxOpenConns int    `toml:"maxOpenConns"`
			MaxIdleConns int    `toml:"maxIdleConns"`
		}(struct {
			DBType       string
			UserName     string
			Password     string
			Url          string
			MaxOpenConns int
			MaxIdleConns int
		}{
			DBType:       "mysql",
			UserName:     "admin",
			Password:     "123456",
			Url:          "(localhost:3306)/tenant",
			MaxOpenConns: 10,
			MaxIdleConns: 5,
		}),
	}
	_, err := NewDatabase(conf)
	fmt.Println(err)
}
