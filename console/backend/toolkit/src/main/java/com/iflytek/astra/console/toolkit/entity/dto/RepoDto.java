package com.iflytek.astra.console.toolkit.entity.dto;

import com.iflytek.astra.console.toolkit.entity.table.repo.Repo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class RepoDto extends Repo {
    private String address;
    private List<TagDto> tagDtoList;
    private List<SparkBotVO> bots;
    private Long fileCount;
    private Long charCount;
    private Long knowledgeCount;
    private String corner;
}
