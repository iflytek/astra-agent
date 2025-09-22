package com.iflytek.astra.console.toolkit.entity.vo.repo;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeVO {
    private String id;
    private Long fileId;
    private String content;
    private List<String> tags;
}
