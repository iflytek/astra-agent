package com.iflytek.astron.console.commons.entity.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_bot_list")
@Schema(name = "ChatBotList", description = "User added assistant table")
public class ChatBotList {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Market bot ID, if 0 then original, if other value then referencing other users' bot")
    private Integer marketBotId;

    @Schema(description = "Self-created assistant is 0, only when adding others' assistants from market, the original bot_id will be added")
    private Integer realBotId;

    @Schema(description = "Bot name")
    private String name;

    @Schema(description = "Bot type: 1 Custom Assistant, 2 Life Assistant, 3 Workplace Assistant, 4 Marketing Assistant, 5 Writing Expert, 6 Knowledge Expert")
    private Integer botType;

    @Schema(description = "Bot avatar")
    private String avatar;

    @Schema(description = "bot_prompt")
    private String prompt;

    @Schema(description = "Bot description")
    private String botDesc;

    @Schema(description = "Enable status: 0 Disabled, 1 Enabled")
    private Integer isAct;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "Multi-turn conversation support: 1 Support, 0 Not supported")
    private Integer supportContext;
}
