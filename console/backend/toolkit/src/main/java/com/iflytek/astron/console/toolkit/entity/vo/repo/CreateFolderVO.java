package com.iflytek.astron.console.toolkit.entity.vo.repo;

import lombok.Data;

import java.util.List;

@Data
public class CreateFolderVO {
    private Long id;
    private Long repoId;
    private String name;
    private Long parentId;
    private List<String> tags;
}
