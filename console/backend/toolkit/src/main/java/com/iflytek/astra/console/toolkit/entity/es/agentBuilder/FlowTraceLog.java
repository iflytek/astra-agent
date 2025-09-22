package com.iflytek.astra.console.toolkit.entity.es.agentBuilder;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowTraceLog {
    /**
     * Session ID
     */
    private String sid;
    /**
     * User question
     */
    private String question;
    /**
     * Chain output
     */
    private String answer;
    /**
     * Execution start time
     */
    @JSONField(name = "start_time")
    private Long startTime;
    /**
     * Execution end time
     */
    @JSONField(name = "end_time")
    private Long endTime;
    /**
     * Runtime status
     */
    private String status;
    /**
     * Execution duration
     */
    private Integer duration;
    private Usage usage;


    /**
     * Redundant field
     */
    @JSONField(name = "flow_id")
    private String flowId;
    /**
     * Application ID
     */
    @JSONField(name = "app_id")
    private String appId;
    /**
     * Window ID
     */
    @JSONField(name = "chat_id")
    private String chatId;
    private String uid;
    private JSONArray trace;
    /**
     * Business service category sub = workflow, service_id refers to flow_id sub = SparkAgent,
     * service_id refers to bot_id sub = mcp, service_id refers to mcp_id
     */
    private String sub;

    @Data
    public static class Usage {
        /**
         * Input tokens
         */
        @JSONField(name = "question_tokens")
        private Long questionTokens;
        /**
         * Output tokens
         */
        @JSONField(name = "prompt_tokens")
        private Long promptTokens;
        /**
         * Total tokens
         */
        @JSONField(name = "total_tokens")
        private Long totalTokens;
    }

    private JSONObject srv;
}
