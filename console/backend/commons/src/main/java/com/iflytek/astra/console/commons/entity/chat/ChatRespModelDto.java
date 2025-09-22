package com.iflytek.astra.console.commons.entity.chat;

import com.iflytek.astra.console.commons.entity.workflow.WorkflowEventData;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;


@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class ChatRespModelDto extends ChatRespRecords {
    private String url;
    private String content;
    private String type;
    private int needHis = 1;
    /**
     * Needs to be underlined
     */
    private boolean needDraw;
    private String intention;
    private String dataId;
    /**
     * Virtual field
     */
    // Thumbnail
    private String thumbUrl;
    /**
     * Feedback type, 1 good, 2 bad
     */
    private Integer status;
    // Trace source record
    private String traceSource;
    // Trace source type
    private String sourceType;
    // allTools record
    private String allTools;

    // v2 long text trace source
    private String v2TraceSourceId;

    /**
     * Group chat assistant ID
     */
    private Long botId;
    /**
     * Assistant name
     */
    private String botName;
    /**
     * Assistant avatar
     */
    private String botAvatar;
    /**
     * Reasoning content
     */
    private String reasoning;
    /**
     * Reasoning elapsed time in seconds
     */
    private Long reasoningElapsedSecs;

    /**
     * Q&A node special data format
     */
    private WorkflowEventData.EventValue workflowEventData;
}
