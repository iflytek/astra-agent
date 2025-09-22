package com.iflytek.astra.console.toolkit.entity.core.knowledge;

import lombok.Data;

@Data
public class CbgKnowledgeData {
    String id;
    String datasetId;
    String fileId;
    String createTime;
    String updateTime;
    String chunkType;
    String content;
    String question;
    String answer;
    Double dataIndex;
    String imgReference;
    String copiedFrom;
}
