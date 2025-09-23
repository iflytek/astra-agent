package com.iflytek.astron.console.toolkit.config.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "common")
public class CommonConfig {
    String appId;
    String apiKey;
    String apiSecret;
}
