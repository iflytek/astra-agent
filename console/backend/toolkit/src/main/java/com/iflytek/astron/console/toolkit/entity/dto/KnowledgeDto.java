package com.iflytek.astron.console.toolkit.entity.dto;

import com.iflytek.astron.console.toolkit.entity.mongo.Knowledge;
import com.iflytek.astron.console.toolkit.entity.table.repo.FileInfoV2;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class KnowledgeDto extends Knowledge {
    private List<TagDto> tagDtoList;
    private FileInfoV2 fileInfoV2;
}
