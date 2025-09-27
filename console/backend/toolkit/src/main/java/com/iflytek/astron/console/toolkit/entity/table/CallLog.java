package com.iflytek.astron.console.toolkit.entity.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.Date;

@Data
public class CallLog {
    @TableId(type = IdType.AUTO)
    Long id;
    String sid;
    String url;
    String method;
    String type;
    String req;
    String resp;
    Date createTime;
}
