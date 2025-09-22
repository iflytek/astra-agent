package com.iflytek.astra.console.toolkit.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


/**
 * @program: AICloud-Customer-Service-Robot
 * @description: Remote API address configuration class
 * @create: 2020-10-23 15:16
 */
@Component
@Data
@ConfigurationProperties(prefix = "api.url")
public class ApiUrl {
    String defaultAddRepo;
    String knowledgeUrl;
    String streamChatUrl;
    String toolUrl;
    String appUrl;
    String apiKey;
    String apiSecret;
    String workflow;
    String openPlatform;
    String tenantId;
    String tenantKey;
    String tenantSecret;
    /**
     * Teacher Zhang's MCP server address
     */
    String mcpToolServer;

    String mcpAuthServer;
    String mcpUrlServer;
    String sparkDB;

    // Get fine-tuning model authentication parameters
    String modelAk;
    String modelSk;
    String localModel;
    String datasetUrl;
    String datasetFileUrl;
    String xinghuoDatasetFileUrl;
    String deleteXinghuoDatasetFileUrl;
    String deleteXinghuoDatasetUrl;
}
