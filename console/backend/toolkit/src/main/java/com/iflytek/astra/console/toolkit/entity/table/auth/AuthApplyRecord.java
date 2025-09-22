package com.iflytek.astra.console.toolkit.entity.table.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@TableName("auth_apply_record")
@Accessors(chain = true)
public class AuthApplyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String uid;
    private String appId;
    private String channel;
    private String domain;
    private String patchId;
    private String content;
    private Date createTime;
    private Boolean autoAuth;
    private String authOrderId;
}
