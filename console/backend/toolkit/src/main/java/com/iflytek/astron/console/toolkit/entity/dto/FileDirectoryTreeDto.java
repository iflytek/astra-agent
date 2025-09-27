package com.iflytek.astron.console.toolkit.entity.dto;

import com.iflytek.astron.console.toolkit.entity.table.repo.FileDirectoryTree;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class FileDirectoryTreeDto extends FileDirectoryTree {
    private List<TagDto> tagDtoList;
    // private Long hitCount;
}
