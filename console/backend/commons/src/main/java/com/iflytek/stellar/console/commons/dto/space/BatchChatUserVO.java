package com.iflytek.astra.console.commons.dto.space;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(name = "Batch user info")
public class BatchChatUserVO {

    private List<ChatUserVO> chatUserVOS;
    @Schema(description = "Result file URL")
    private String resultUrl;
}
