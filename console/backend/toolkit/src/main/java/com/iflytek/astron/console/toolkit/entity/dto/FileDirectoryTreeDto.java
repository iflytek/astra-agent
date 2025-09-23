package com.iflytek.astron.console.toolkit.entity.dto;

import com.iflytek.astron.console.toolkit.entity.table.repo.FileDirectoryTree;
import lombok.Data;

import java.util.List;

@Data
public class FileDirectoryTreeDto extends FileDirectoryTree {
    private List<TagDto> tagDtoList;
    // private Long hitCount;
}
