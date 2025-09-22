package com.iflytek.astra.console.toolkit.entity.spark.chat;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class Header {
    Integer code;
    String message;
    String sid;
    Integer status;
    Integer seq;
    @JSONField(name = "is_finish")
    Boolean isFinish;
}
