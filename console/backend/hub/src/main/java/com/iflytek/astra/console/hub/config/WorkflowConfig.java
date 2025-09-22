package com.iflytek.astra.console.hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Workflow configuration
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "workflow")
public class WorkflowConfig {

    /**
     * Whether to enable workflow functionality
     */
    private boolean enabled = true;

    /**
     * Workflow timeout (milliseconds)
     */
    private long timeoutMs = 300000; // 5 minutes

    /**
     * Maximum concurrent workflow count
     */
    private int maxConcurrentWorkflows = 100;

    /**
     * Workflow event cache expiration time (seconds)
     */
    private int eventCacheExpireSeconds = 1800; // 30 minutes

    /**
     * Whether to enable workflow debug logging
     */
    private boolean debugEnabled = false;

    /**
     * Workflow file upload configuration
     */
    private FileUpload fileUpload = new FileUpload();

    @Data
    public static class FileUpload {
        /**
         * Whether to enable file upload
         */
        private boolean enabled = true;

        /**
         * Maximum file size (bytes)
         */
        private long maxFileSize = 10 * 1024 * 1024; // 10MB

        /**
         * Supported file types
         */
        private String[] allowedTypes = {"txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg", "jpeg", "png", "gif"};

        /**
         * File storage path
         */
        private String storagePath = "/tmp/workflow/uploads";
    }
}
