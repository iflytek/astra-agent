package com.iflytek.astra.console.hub.dto.chat;

import com.alibaba.fastjson2.JSONArray;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(name = "ChatHistoryResponseDto", description = "Chat history response DTO")
public class ChatHistoryResponseDto {

    @Schema(description = "Chat ID")
    private Long chatId;

    @Schema(description = "Unbound request chat file list")
    private List<ChatEnhanceChatHistoryListFileVo> chatFileListNoReq;

    @Schema(description = "History message list")
    private JSONArray historyList;

    @Schema(description = "Business type")
    private String businessType;

    @Schema(description = "Number of existing chat files")
    private Integer existChatFileSize;

    @Schema(description = "Whether chat images exist")
    private Boolean existChatImage;

    @Schema(description = "Enabled plugin ID list")
    private String enabledPluginIds;
}
