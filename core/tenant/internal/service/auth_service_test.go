package service

import (
	"fmt"
	"log"
	"tenant/config"
	"tenant/internal/dao"
	"tenant/internal/models"
	"tenant/tools/database"
	"tenant/tools/generator"
	"testing"
)

var authService *AuthService

func AUthPre() {
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
	db, err := database.NewDatabase(conf)
	if err != nil {
		panic(err)
	}
	appDAO, err := dao.NewAppDao(db)
	if err != nil {
		panic(err)
	}
	auDAO, err := dao.NewAuthDao(db)
	if err != nil {
		panic(err)
	}
	authService, err = NewAuthService(appDAO, auDAO)
	if err != nil {
		panic(err)
	}
}

func TestAddAuth(*testing.T) {
	AUthPre()
	auth := &models.Auth{
		AppId:      "e4776a38",
		ApiKey:     generator.GenKey("e4776a38"),
		ApiSecret:  generator.GenSecret(),
		IsDelete:   false,
		CreateTime: generator.GenCurrTime(""),
		UpdateTime: generator.GenCurrTime(""),
	}
	result, err := authService.AddAuth(auth)
	if err != nil {
		panic(err)
	}
	log.Printf("add auth success,apiKey: %s, apiSecret: %s", result.ApiKey, result.ApiSecret)
}

func TestDeleteApiKey(*testing.T) {
	AUthPre()
	err := authService.DeleteApiKey("e4776a38", "2720e1e2516882b055bac286fedb70db")
	if err != nil {
		panic(err)
	}
}

func TestQuery(*testing.T) {
	AUthPre()
	authList, err := authService.Query("625adda8")
	if err != nil {
		panic(err)
	}
	for _, auth := range authList {
		fmt.Println(auth.AppId, auth.ApiKey, auth.ApiSecret)
	}
}
