package com.iflytek.astron.console.toolkit.entity.core.openapi;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class Info {
    String title;
    String version;
    @JSONField(name = "x-is-official")
    Boolean xIsOfficial;
}
