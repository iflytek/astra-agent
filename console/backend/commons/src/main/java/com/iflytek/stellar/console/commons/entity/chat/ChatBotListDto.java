package com.iflytek.astra.console.commons.entity.chat;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author yingpeng
 */
@Data
public class ChatBotListDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Non-business primary key
     */
    private Long id;

    private String uid;

    /**
     * Chat list title
     */
    private String title;

    /**
     * Whether deleted, 0 not deleted, 1 deleted
     */
    private Integer isDelete;

    /**
     * Whether available, 0 not available, 1 available
     */
    private Integer enable;

    private Long chatId;
    private String enabledPluginIds;

    // bot related parameters
    private String botDesc;
    private String botDescEn;
    private Integer hotNum;
    private String botType;
    private String botTitle;
    private String botTitleEn;
    private Integer botId;
    private Integer botStatus;
    private Integer marketBotId;
    private String botAvatar;
    private Long marketBotUid;
    private Long botUid;
    private String clientHide;
    private String creatorName;
    /**
     * Creation time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * Modification time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    private Integer albumVisible;

    private Integer supportContext;

    private Integer sticky;

    private int isFavorite;

    private String action;

    private Object extra;

    private String blockReason;

    private Integer version;

    @TableField(exist = false, select = false)
    private List<String> tags;

    @TableField(exist = false, select = false)
    private Boolean recommend;

    @TableField(exist = false)
    private Long virtualAgentId;


    public String getClientHide() {
        if (StrUtil.isBlank(clientHide)) {
            return "";
        }
        return clientHide;
    }
}
