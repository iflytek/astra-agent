package com.iflytek.astron.console.toolkit.entity.spark.chat;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class Message {
    String role;
    String type;
    Object content;
    @JSONField(name = "content_type")
    String contentType;
    // @JSONField(name = "extra_info")
    // ExtraInfo extraInfo;
}
