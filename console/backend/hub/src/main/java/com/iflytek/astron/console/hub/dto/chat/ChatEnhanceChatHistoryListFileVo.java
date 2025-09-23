package com.iflytek.astron.console.hub.dto.chat;

import lombok.Data;

/**
 * @author yingpeng
 */
@Data
public class ChatEnhanceChatHistoryListFileVo {
    private String uid;
    private Long chatId;
    private Long reqId;
    private String fileId;
    private String fileUrl;
    private String fileName;
    private String filePdfUrl;
    private String fileSize;
    private String createTime;
    private Integer businessType;
    private Integer fileStatus;
    private String extraLink;
    private String icon;
    private String collectOriginFrom;
}
