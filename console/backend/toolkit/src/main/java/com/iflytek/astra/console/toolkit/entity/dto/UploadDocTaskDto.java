package com.iflytek.astra.console.toolkit.entity.dto;

import com.iflytek.astra.console.toolkit.entity.table.repo.UploadDocTask;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UploadDocTaskDto extends UploadDocTask {
    private String sourceId;
}
