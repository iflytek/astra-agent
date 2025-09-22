package com.iflytek.astra.console.toolkit.entity.vo.knowledge;

import lombok.Data;

@Data
public class SparkUploadVo {

    private String fileId;

    private String letterNum;

    private String parseType;

    private String quantity;

    private String fileName;

    private Long spliceCount;
}
