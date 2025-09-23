package com.iflytek.astron.console.toolkit.entity.pojo;

import lombok.Data;

@Data
public class DeleteKnowledgeFileFailedResult {
    private String file_id;
    private String failed_reason;
}
