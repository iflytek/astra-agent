package com.iflytek.astron.console.toolkit.config.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Data
@ConfigurationProperties(prefix = "biz")
public class BizConfig {
    String adminUid;

    /**
     * List of CBG RAG compatible source types that have the same behavior as CBG-RAG
     */
    List<String> cbgRagCompatibleSources;

    /**
     * List of AIUI RAG compatible source types that have the same behavior as AIUI-RAG2
     */
    List<String> aiuiRagCompatibleSources;

    /**
     * List of Spark RAG compatible source types that have the same behavior as SparkDesk-RAG
     */
    List<String> sparkRagCompatibleSources;
}
