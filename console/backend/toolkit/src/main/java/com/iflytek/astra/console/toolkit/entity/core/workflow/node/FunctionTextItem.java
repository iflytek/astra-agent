package com.iflytek.astra.console.toolkit.entity.core.workflow.node;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.Collections;

@Data
public class FunctionTextItem {
    JSONObject parameters = new JSONObject()
            .fluentPut("type", "object")
            .fluentPut("required", Collections.singletonList("next_inputs"))
            .fluentPut("properties",
                    new JSONObject()
                            .fluentPut("next_inputs",
                                    new JSONObject()
                                            .fluentPut("description", "User input content")
                                            .fluentPut("type", "string")));

    String name;
    String description;

}
