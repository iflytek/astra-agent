package com.iflytek.astron.console.hub.dto.chat;

import lombok.Data;

/**
 * @author yun-zhi-ztl
 */
@Data
public class ChatEnhanceSaveFileVo {
    private String fileUrl;
    private String fileName;
    private Long chatId;
    private Integer businessType;
    private String extraLink;
    private Long fileSize;
    private String fileBusinessKey;

    /**
     * File category, default is 1 for Spark
     */
    private Integer documentType = 1;

    /**
     * Special window file upload, see SpecialChatEnum for details
     */
    private Integer specialType;
    /**
     * File parameter name for agent start node
     */
    private String paramName;
}
