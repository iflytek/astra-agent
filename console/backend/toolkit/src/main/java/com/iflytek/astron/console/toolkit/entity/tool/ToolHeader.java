package com.iflytek.astron.console.toolkit.entity.tool;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class ToolHeader {

    String uid;
    @JSONField(name = "app_id")
    String appId;

    // Tool run resp
    Integer code;
    String message;
    String sid;
}
