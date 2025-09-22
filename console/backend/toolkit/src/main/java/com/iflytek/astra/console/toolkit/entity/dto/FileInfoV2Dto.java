package com.iflytek.astra.console.toolkit.entity.dto;

import com.iflytek.astra.console.toolkit.entity.table.repo.FileInfoV2;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FileInfoV2Dto extends FileInfoV2 {
    private Long paragraphCount;
}
