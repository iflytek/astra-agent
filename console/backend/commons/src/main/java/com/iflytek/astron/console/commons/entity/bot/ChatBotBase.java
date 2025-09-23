package com.iflytek.astron.console.commons.entity.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_bot_base")
@Schema(name = "ChatBotBase", description = "User-created assistant table")
public class ChatBotBase {

    @TableId(type = IdType.AUTO)
    @Schema(description = "bot_id")
    private Integer id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Bot name")
    private String botName;

    @Schema(description = "Bot type: 1 Custom Assistant, 2 Life Assistant, 3 Workplace Assistant, 4 Marketing Assistant, 5 Writing Expert, 6 Knowledge Expert")
    private Integer botType;

    @Schema(description = "Bot avatar")
    private String avatar;

    @Schema(description = "PC chat background image")
    private String pcBackground;

    @Schema(description = "Mobile chat background image")
    private String appBackground;

    @Schema(description = "Background image color scheme: 0 Light, 1 Dark")
    private Integer backgroundColor;

    @Schema(description = "bot_prompt")
    private String prompt;

    @Schema(description = "Opening statement")
    private String prologue;

    @Schema(description = "Bot description")
    private String botDesc;

    @Schema(description = "Deletion status: 0 Not deleted, 1 Deleted")
    private Integer isDelete;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "Multi-turn conversation support: 1 Support, 0 Not supported")
    private Integer supportContext;

    @Schema(description = "Input template")
    private String botTemplate;

    @Schema(description = "Instruction type: 0 Regular (custom instruction), 1 Structured instruction")
    private Integer promptType;

    @Schema(description = "Input example")
    private String inputExample;

    @Schema(description = "Independent assistant app status: 0 Disabled, 1 Enabled")
    private Integer botwebStatus;

    @Schema(description = "Assistant version")
    private Integer version;

    @Schema(description = "File support: 0 Not supported, 1 Strictly based on document, 2 Can provide extended answers")
    private Integer supportDocument;

    @Schema(description = "System instruction support: 0 Not supported, 1 Supported")
    private Integer supportSystem;

    @Schema(description = "System instruction status")
    private Integer promptSystem;

    @Schema(description = "Document upload support: 0 Not supported, 1 Supported")
    private Integer supportUpload;

    @Schema(description = "Assistant name in English")
    private String botNameEn;

    @Schema(description = "Assistant description in English")
    private String botDescEn;

    @Schema(description = "Client type")
    private Integer clientType;

    @Schema(description = "Chinese voice")
    private String vcnCn;

    @Schema(description = "English voice")
    private String vcnEn;

    @Schema(description = "Voice speed")
    private Integer vcnSpeed;

    @Schema(description = "One-sentence generation: 0 No, 1 Yes")
    private Integer isSentence;

    @Schema(description = "Enabled tools, separated by commas")
    private String openedTool;

    @Schema(description = "Hidden on certain clients")
    private String clientHide;

    @Schema(description = "Virtual personality type")
    private Integer virtualBotType;

    @Schema(description = "virtual_agent_list primary key")
    private Long virtualAgentId;

    @Schema(description = "Style type: 0 Original image, 1 Business elite, 2 Casual moment")
    private Integer style;

    @Schema(description = "Background setting")
    private String background;

    @Schema(description = "Character setting")
    private String virtualCharacter;

    @Schema(description = "Selected model for assistant")
    private String model;

    @Schema(description = "mass_bot_id")
    private String massBotId;

    @Schema(description = "Opening statement - English")
    private String prologueEn;

    @Schema(description = "Recommended questions - English")
    private String inputExampleEn;

    @Schema(description = "Space ID")
    private Long spaceId;
}
