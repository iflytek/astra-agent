package com.iflytek.astron.console.toolkit.entity.table.bot;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.Date;

@Data
public class CreateBotContext {
    @TableId
    String chatId;
    Integer step;
    String bizData;
    String chatHistory;
    Date createTime;
    Date updateTime;
}
