package com.iflytek.astron.console.toolkit.entity.core.workflow.sse;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class V3Response {
    private String id;
    private String model;
    private String object;
    private long created;
    private List<Choice> choices;
    private Usage usage;

    @Data
    public static class Choice {
        private int index;
        @JSONField(name = "delta")
        private Message delta;
        private String finish_reason;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
        private String reasoning_content;
        private Object plugins_content;
    }
}
