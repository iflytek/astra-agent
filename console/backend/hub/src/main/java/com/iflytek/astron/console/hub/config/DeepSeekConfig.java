package com.iflytek.astron.console.hub.config;

import lombok.Data;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "deepseek")
@Data
public class DeepSeekConfig {

    private String apiKey;
    private String baseUrl = "https://api.deepseek.com";
    private String chatCompletionPath = "/chat/completions";
    private Duration connectTimeout = Duration.ofSeconds(30);
    private Duration readTimeout = Duration.ofSeconds(60);
    private Duration writeTimeout = Duration.ofSeconds(60);

    @Bean("deepSeekHttpClient")
    public OkHttpClient deepSeekHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .writeTimeout(writeTimeout)
                .build();
    }

    public String getChatCompletionUrl() {
        return baseUrl + chatCompletionPath;
    }
}
