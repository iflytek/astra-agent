package com.iflytek.astra.console.toolkit.entity.vo.repo;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeQueryVO {
    private List<String> fileIds;
    private Integer source;// 0:knowledge extraction 1:knowledge embedding
    private Integer pageNo;
    private Integer pageSize;
    private String query;
    private Integer auditType;// Audit type: pass 1 for violations
    private String tag;
}
