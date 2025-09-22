package com.iflytek.astra.console.commons.entity.chat;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ChatReqModelDto extends ChatReqRecords {

    private String url;
    private int type;
    private String content;
    private String imgDesc;
    private String ocrResult;
    private String dataId;
    private int needHis = 1;
    private String intention;

}
