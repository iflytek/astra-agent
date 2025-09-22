package com.iflytek.astra.console.toolkit.config.mybatis;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.iflytek.astra.console.toolkit.handler.MySqlJsonHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisConfig {
    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            configuration.getTypeHandlerRegistry().register(JSONObject.class, new MySqlJsonHandler());
        };
    }
}
