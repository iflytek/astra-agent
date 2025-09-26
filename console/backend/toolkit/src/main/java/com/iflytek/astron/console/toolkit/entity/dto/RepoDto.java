package com.iflytek.astron.console.toolkit.entity.dto;

import com.iflytek.astron.console.toolkit.entity.table.repo.Repo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class RepoDto extends Repo {
    private static final long serialVersionUID = 1L;
    private String address;
    private List<TagDto> tagDtoList;
    private List<SparkBotVO> bots;
    private Long fileCount;
    private Long charCount;
    private Long knowledgeCount;
    private String corner;
}
