package com.iflytek.astron.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * <p>
 *
 * </p>
 *
 * @author xdsun6
 * @since 2023-09-05
 */
@Getter
@Setter
@TableName("application_form")
public class ApplicationForm implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * User nickname
     */
    private String nickname;

    /**
     * Mobile phone number
     */
    private String mobile;

    /**
     * Bot name
     */
    private String botName;

    /**
     * Bot ID
     */
    private Long botId;


}
