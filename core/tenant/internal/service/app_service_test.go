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

var appService *AppService

func AppPre() {
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
	appService, err = NewAppService(appDAO, auDAO)
	if err != nil {
		panic(err)
	}
}
func TestSaveApp(*testing.T) {
	AppPre()
	app := &models.App{
		AppId:      generator.GenAppId(8),
		AppName:    "test0001",
		DevId:      11,
		ChannelId:  "0",
		IsDisable:  false,
		Source:     "test",
		Desc:       "test app id",
		IsDelete:   false,
		CreateTime: generator.GenCurrTime(""),
		UpdateTime: generator.GenCurrTime(""),
		Extend:     "",
	}
	result, err := appService.SaveApp(app, nil)
	if err != nil {
		panic(err)
	}
	log.Printf("save app result is %s, %s, %s", result.AppId, result.ApiKey, result.ApiSecret)
}

func TestModifyApp(*testing.T) {
	AppPre()
	app := &models.App{
		AppId:     "625adda8",
		AppName:   "test0002",
		ChannelId: "2",
		Desc:      "test app id1",
		Source:    "test1",
	}
	err := appService.ModifyApp(app)
	if err != nil {
		panic(err)
	}
	log.Printf("modify app success:%s", "625adda8")
}

func TestDisableOrEnable(*testing.T) {
	AppPre()
	err := appService.DisableOrEnable("625adda8", false)
	if err != nil {
		panic(err)
	}
	log.Printf("disable or enable app success:%s", "7aba3779")
}

func TestDelete(*testing.T) {
	AppPre()
	err := appService.Delete("7aba3779")
	if err != nil {
		panic(err)
	}
	log.Printf("delete app success:%s", "7aba3779")

}

func TestQueryApp(*testing.T) {
	AppPre()
	appList, err := appService.Query(&AppQuery{
		//AppIds: []string{"51f9797f", "20bd6593", "9f7beb29"},
		//DevId: 1819,
		Name: "测试",
	})
	if err != nil {
		panic(err)
	}
	for _, app := range appList {
		fmt.Println(app.AppId, app.AppName, app.ChannelId)
	}
}

func TestQueryDetails(*testing.T) {
	AppPre()
	details, err := appService.QueryDetails(&AppQuery{
		AppIds: []string{"51f9797f", "20bd6593", "9f7beb29"},
	})
	if err != nil {
		panic(err)
	}
	for _, detail := range details {
		fmt.Println(detail.Appid, detail.Name, detail.IsDisable, detail.Desc)
		for _, auth := range detail.AuthList {
			fmt.Println(auth.ApiKey, auth.ApiSecret)
		}
	}
}
