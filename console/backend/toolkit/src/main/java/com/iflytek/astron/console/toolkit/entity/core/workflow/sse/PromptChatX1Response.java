package com.iflytek.astron.console.toolkit.entity.core.workflow.sse;


import lombok.Data;

@Data
public class PromptChatX1Response {
    private Status data;
    private int index;
    private String sid;
    private String stage;

    @Data
    public static class Status {
        private String status;
        private String content;
    }
}
