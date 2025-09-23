package com.iflytek.astra.console.commons.entity.workflow;

import com.google.common.collect.Maps;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * @author zhangbj
 * @version 1.0
 */
@Data
@Builder
public class WorkflowEventData {

    public static final String MARKDOWN_WORKFLOW_OPERATION = "<workflow_operation>";

    /**
     * Event ID, used as a flag for the resume interface to restore events
     */
    private String eventId;

    /**
     * Event type, "interrupt" when interrupted
     */
    private String eventType;

    /**
     * Whether a response is required
     */
    private boolean needReply;

    /**
     * Event value, contains response type and content
     */
    private EventValue value;

    /**
     * Event value, contains response type and content
     */
    @Data
    @Builder
    public static class EventValue {
        /**
         * 'direct' for direct answer; 'option' for option answer {@link WorkflowValueType}
         */
        private String type;

        /**
         * Body message, has value only when body exists
         */
        private String message;

        /**
         * Option content for option answers
         */
        private List<ValueOption> option;

        /**
         * Question content for Q&A nodes
         */
        private String content;

        public EventValue withType(String type) {
            if (this.type != null && this.type.equals(type)) {
                return this;
            }
            return EventValue.builder()
                    .type(type)
                    .message(this.message)
                    .option(this.option)
                    .content(this.content)
                    .build();
        }

        public EventValue withMessage(String message) {
            if (this.message != null && this.message.equals(message)) {
                return this;
            }
            return EventValue.builder()
                    .type(this.type)
                    .message(message)
                    .option(this.option)
                    .content(this.content)
                    .build();
        }

        public EventValue withContent(String content) {
            if (this.content != null && this.content.equals(content)) {
                return this;
            }
            return EventValue.builder()
                    .type(this.type)
                    .message(this.message)
                    .option(this.option)
                    .content(content)
                    .build();
        }

        @Data
        public static class ValueOption {
            private String id;
            private String text;
            private Boolean selected;
            private String contentType;
        }
    }

    /**
     * Intelligent answer type
     */
    @Getter
    @AllArgsConstructor
    public enum WorkflowValueType {
        DIRECT("direct", "<workflow_direct>", "Direct answer"),
        OPTION("option", "<workflow_option>", "Option answer"),
        ;

        /**
         * Response type
         */
        private final String type;

        /**
         * Tag for frontend Markdown markup
         */
        private final String tag;

        /**
         * Description
         */
        private final String desc;

        public static String getTag(String type) {
            for (WorkflowValueType valueType : WorkflowValueType.values()) {
                if (valueType.getType().equals(type)) {
                    return valueType.getTag();
                }
            }
            return null;
        }
    }

    /**
     * Operation tag type
     */
    @Getter
    @AllArgsConstructor
    public enum WorkflowOperation {
        RESUME("resume", "request", "Resume this question"),
        IGNORE("ignore", "request", "Ignore this question"),
        ABORT("abort", "request", "End this conversation"),

        INTERRUPT("interrupt", "response", "Interrupt this conversation"),
        STOP("stop", "response", "End this conversation"),
        ;

        /**
         * Operation tag
         */
        private final String operation;

        /**
         * Operation stage
         */
        private final String stage;

        /**
         * Description
         */
        private final String desc;

        /**
         * Get operation tags that need to be displayed
         *
         * @param needReply Whether a response is required
         * @return Map<String, String>
         */
        public static Map<String, String> getDisplayOperation(boolean needReply) {
            Map<String, String> resMap = Maps.newHashMap();
            resMap.put(ABORT.operation, ABORT.desc);
            if (!needReply) {
                resMap.put(IGNORE.getOperation(), IGNORE.getDesc());
            }
            return resMap;
        }


        /**
         * Determine if the request is for resuming after interruption. There are three strategies for
         * resuming conversation: {@link WorkflowOperation#RESUME} {@link WorkflowOperation#IGNORE}
         * {@link WorkflowOperation#ABORT}
         *
         * @param workflowOperation Strategy
         * @return Whether the resume interface can be called
         */
        public static boolean resumeDial(String workflowOperation) {
            for (WorkflowOperation value : values()) {
                if (!"request".equals(value.getStage())) {
                    continue;
                }
                if (value.getOperation().equals(workflowOperation)) {
                    return true;
                }
            }
            return false;
        }

    }

}
