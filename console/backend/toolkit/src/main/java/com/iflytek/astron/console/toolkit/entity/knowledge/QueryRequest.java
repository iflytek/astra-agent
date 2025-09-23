package com.iflytek.astron.console.toolkit.entity.knowledge;

import lombok.Data;

@Data
public class QueryRequest {
    /**
     * User input content
     */
    String query;

    /**
     * Expected number of recalled chunks
     */
    Integer topN;

    /**
     * Matching conditions
     */
    QueryMatchObj match;

    /**
     * Default AIUI-RAG2
     */
    String ragType = "AIUI-RAG2";
}
