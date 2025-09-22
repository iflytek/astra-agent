package com.iflytek.astra.console.commons.entity.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatContentMeta {

    private String ocr;
    private String desc;
    private boolean url = false;
    private Integer only_desc;
    private String data_id;
    private String img_type;

    public ChatContentMeta(String ocr, String desc, boolean url) {
        this.ocr = ocr;
        this.desc = desc;
        this.url = url;
    }

    public ChatContentMeta(String ocr, String desc, boolean url, String dataId) {
        this.ocr = ocr;
        this.desc = desc;
        this.url = url;
        this.data_id = dataId;
    }

    public ChatContentMeta(String ocr, String desc, boolean url, String data_id, String img_type) {
        this.ocr = ocr;
        this.desc = desc;
        this.url = url;
        this.data_id = data_id;
        this.img_type = img_type;
    }

}
