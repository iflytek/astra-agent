package com.iflytek.astron.console.toolkit.entity.core.knowledge;

import com.iflytek.astron.console.toolkit.common.constant.ProjectContent;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeRequest {
    /**
     * Required: Yes. Document ID
     */
    String docId;

    /**
     * Required: No. Group ID to which the document belongs, users can specify themselves
     */
    String group;

    /**
     * Required: No. User ID to which the document belongs, users can specify themselves
     */
    String uid;

    /**
     * Required: Yes. Data parameter returned by document chunk interface
     */
    Object[] chunks;

    /**
     * Required: Yes. List of chunk IDs to be deleted, if not specified, all chunks under the document
     * will be deleted
     */
    List<String> chunkIds;

    /**
     * Required: Yes. Enum: AIUI-RAG2
     */
    String ragType;

    public KnowledgeRequest() {
        this.ragType = ProjectContent.FILE_SOURCE_AIUI_RAG2_STR;
    }
}
