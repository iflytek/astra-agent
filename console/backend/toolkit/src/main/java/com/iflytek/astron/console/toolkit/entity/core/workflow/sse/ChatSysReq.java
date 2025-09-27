package com.iflytek.astron.console.toolkit.entity.core.workflow.sse;


import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ChatSysReq {
    // necessary

    @JsonProperty(value = "flow_id")
    String flowId;


    /**
     * Whether to enable streaming return. - Streaming: true - Non-streaming: false
     */
    Boolean stream = true;

    /**
     * Whether to return the output of each node. Default value false - Return: true - Don't return:
     * false
     */
    Boolean debug = true;

    /**
     * Input parameters and values for the start node of the workflow. You can view the parameter list
     * on the orchestration page of the specified workflow { "input1": "xxxxx", "input2": "xxxxx" }
     */
    Object parameters;

    // unnecessary
    String uid;

    /**
     * Used to specify some additional fields, such as some plugin hidden fields. Not used for now,
     * currently includes: bot_id and caller
     */
    Object ext;
    @JsonProperty(value = "chat_id")
    String chatId;
    /**
     * Multi-turn conversation history
     */
    List<JSONObject> history;

    // Workflow version name
    String version;

}
