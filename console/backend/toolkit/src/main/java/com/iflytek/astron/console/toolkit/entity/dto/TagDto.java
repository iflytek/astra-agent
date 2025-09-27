package com.iflytek.astron.console.toolkit.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class TagDto {
    private String Id;
    private String parentId;
    private String repoId;
    private Integer type;
    private List<String> tags;
}
