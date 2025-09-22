package com.iflytek.astra.console.toolkit.entity.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeRequest {
    /**
     * Required: Yes Document ID
     */
    String docId;

    /**
     * Required: No Document group ID, user can specify
     */
    String group;

    /**
     * Required: No Document user ID, user can specify
     */
    String uid;

    /**
     * Required: Yes Document chunk interface returned data parameter
     */
    Object[] chunks;

    /**
     * Required: Yes List of chunk IDs to be deleted, if not specified, all chunks under the document
     * will be deleted
     */
    List<String> chunkIds;

    /**
     * Required: Yes Enum: AIUI-RAG2
     */
    String ragType = "AIUI-RAG2";
}
