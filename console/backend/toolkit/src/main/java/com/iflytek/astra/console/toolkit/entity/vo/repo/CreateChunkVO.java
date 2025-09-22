package com.iflytek.astra.console.toolkit.entity.vo.repo;

import lombok.Data;

import java.util.List;

@Data
public class CreateChunkVO {
    private String app_id;
    private String bot_id;
    private String repo_id;
    private String content;

    /**
     * Optional, if not provided, it defaults to writing to the default document under the current
     * knowledge base
     */
    private String file_id;
    private List<String> tags;
}
