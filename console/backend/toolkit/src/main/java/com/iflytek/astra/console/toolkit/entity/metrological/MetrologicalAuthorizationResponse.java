package com.iflytek.astra.console.toolkit.entity.metrological;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * @author: tctan
 * @date: 2023/9/14 11:06
 * @description:
 */
@Data
public class MetrologicalAuthorizationResponse {
    private String ret;
    private String desc;
    @JSONField(name = "response_type")
    private String responseType;
    private Object result;
}
