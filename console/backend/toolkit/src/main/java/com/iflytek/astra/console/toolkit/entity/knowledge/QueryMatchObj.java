package com.iflytek.astra.console.toolkit.entity.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class QueryMatchObj {
    /**
     * Required: No. Document ID list
     */
    List<String> docIds;

    /**
     * Required: Yes. Knowledge base name
     */
    List<String> repoId;

    /**
     * Required: No. Knowledge base score threshold, default 0
     */
    Integer threshold = 0;
}
