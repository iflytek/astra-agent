package com.iflytek.astron.console.toolkit.entity.dto;

import com.iflytek.astron.console.toolkit.entity.mongo.PreviewKnowledge;
import com.iflytek.astron.console.toolkit.entity.table.repo.FileInfoV2;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PreviewKnowledgeDto extends PreviewKnowledge {
    private FileInfoV2 fileInfoV2;
}
