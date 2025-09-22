package com.iflytek.astra.console.commons.entity.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bot chat file parameter information table entity class
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "bot_chat_file_param", autoResultMap = true)
public class BotChatFileParam {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * User ID
     */
    private String uid;

    /**
     * Chat ID
     */
    private Long chatId;

    /**
     * Parameter name
     */
    private String name;

    /**
     * File ID list
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fileIds;

    /**
     * File URL list
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fileUrls;

    /**
     * Creation time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * Update time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * Deletion flag: 0-not deleted, 1-deleted
     */
    private Integer isDelete;
}
