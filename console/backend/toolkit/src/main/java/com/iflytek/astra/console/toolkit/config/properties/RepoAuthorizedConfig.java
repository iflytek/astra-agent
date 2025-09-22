package com.iflytek.astra.console.toolkit.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "repo.authorized")
public class RepoAuthorizedConfig {
    private String appId;
    private String apiKey;
    private String apiSecret;
    private String businessId;
}
