package com.iflytek.astra.console.toolkit.entity.pojo;

import lombok.Data;

import java.util.List;

@Data
public class DeleteKnowledgeFileResult {
    private List<DeleteKnowledgeFileFailedResult> fail_file_ids;
    private List<String> success_file_ids;
}
