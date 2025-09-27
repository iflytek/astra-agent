package com.iflytek.astron.console.toolkit.entity.es.agentBuilder;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

@Data
public class FlowDataLog {
    /**
     * Session ID
     */
    private String sid;
    /**
     * User question
     */
    private String question;

    private JSONObject questionJson;
    /**
     * Chain output
     */
    private String answer;
    /**
     * Execution status 0: success, -1: failure
     */
    private Integer statusCode;

    private String expectedAnswer;
}
