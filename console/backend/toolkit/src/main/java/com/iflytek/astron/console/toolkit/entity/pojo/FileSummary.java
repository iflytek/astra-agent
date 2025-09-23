package com.iflytek.astron.console.toolkit.entity.pojo;

import com.iflytek.astron.console.toolkit.entity.table.repo.FileInfoV2;
import lombok.Data;

import java.util.List;

@Data
public class FileSummary {
    private Integer sliceType;// Slice type
    private List<String> seperator;// Separator
    private List<Integer> lengthRange;// Split length
    private Long knowledgeCount;// Knowledge point count
    private Long knowledgeTotalLength;// Total knowledge point length
    private Long knowledgeAvgLength;// Average knowledge point length
    private Long hitCount;// Hit count
    private FileInfoV2 fileInfoV2;// File information
    private Long fileDirectoryTreeId;
}
