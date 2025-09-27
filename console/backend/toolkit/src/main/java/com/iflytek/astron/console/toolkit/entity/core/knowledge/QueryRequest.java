package com.iflytek.astron.console.toolkit.entity.core.knowledge;

import com.iflytek.astron.console.toolkit.common.constant.ProjectContent;
import lombok.Data;

@Data
public class QueryRequest {
    /**
     * User input content
     */
    String query;

    /**
     * Expected number of retrieved chunks
     */
    Integer topN;

    /**
     * Match conditions
     */
    QueryMatchObj match;

    /**
     * Default AIUI-RAG2
     */
    String ragType;

    public QueryRequest() {
        this.ragType = ProjectContent.FILE_SOURCE_AIUI_RAG2_STR;
    }
}
