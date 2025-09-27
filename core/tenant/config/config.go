package config

import "fmt"

type Config struct {
	Server struct {
		Port     int    `toml:"port"`
		Location string `toml:"location"`
	} `toml:"service"`

	DataBase struct {
		DBType       string `toml:"dbType"`
		UserName     string `toml:"username"`
		Password     string `toml:"password"`
		Url          string `toml:"url"`
		MaxOpenConns int    `toml:"maxOpenConns"`
		MaxIdleConns int    `toml:"maxIdleConns"`
	} `toml:"database"`

	Log struct {
		LogFile string `toml:"path"`
	} `toml:"log"`
}

func (c *Config) String() string {
	return fmt.Sprintf("Config{Server: %v, DataBase: %v, Log: %v}", c.Server, c.DataBase, c.Log)
}
func (c *Config) Validate() error {
	if c.Server.Port == 0 {
		return fmt.Errorf("server port is required")
	}
	if c.DataBase.DBType == "" {
		return fmt.Errorf("database type is required")
	}
	if c.DataBase.UserName == "" {
		return fmt.Errorf("database username is required")
	}
	if c.DataBase.Password == "" {
		return fmt.Errorf("database password is required")
	}
	if c.DataBase.Url == "" {
		return fmt.Errorf("database url is required")
	}
	if c.Log.LogFile == "" {
		return fmt.Errorf("log file is required")
	}
	return nil
}
func LoadConfig(path string) (*Config, error) {
	cfg := &Config{}
	// load config from local file
	localLoader := NewLocalLoader(path)
	if err := localLoader.Load(cfg); err != nil {
		fmt.Printf("failed to load config from file: %v\n", err)
	}
	// load config from environment variables
	envLoader := NewEnvLoader()
	if err := envLoader.Load(cfg); err != nil {
		return nil, err
	}
	return cfg, cfg.Validate()
}
