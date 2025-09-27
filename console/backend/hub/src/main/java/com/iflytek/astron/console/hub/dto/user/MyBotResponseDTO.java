package com.iflytek.astron.console.hub.dto.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author wowo_zZ
 * @since 2025/9/9 15:43
 **/
@Data
public class MyBotResponseDTO {

    // Basic identifier fields
    private Long botId;

    private String uid;

    private Long marketBotId;

    // Bot basic information
    private String botName;

    private String botDesc;

    private String avatar;

    private String prompt;

    // Configuration and properties
    private Integer botType;

    private Integer version;

    private Boolean supportContext;

    private Object multiInput;

    // Status and control
    private Integer botStatus;

    private String blockReason;

    private List<Object> releaseType;

    // Statistics and user-related
    private String hotNum;

    private Integer isFavorite;

    private String af;

    private Long maasId;

    // Time fields
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

}
