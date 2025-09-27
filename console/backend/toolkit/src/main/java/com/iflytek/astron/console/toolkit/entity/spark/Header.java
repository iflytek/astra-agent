package com.iflytek.astron.console.toolkit.entity.spark;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class Header {
    // request
    @JSONField(name = "app_id")
    String appId;

    String uid;

    // response
    Integer code;
    String message;
    String sid;
    Integer status;
}
