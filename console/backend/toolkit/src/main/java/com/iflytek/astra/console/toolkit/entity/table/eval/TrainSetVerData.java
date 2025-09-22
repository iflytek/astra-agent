package com.iflytek.astra.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("train_set_ver_data")
public class TrainSetVerData {
    @TableId(type = IdType.AUTO)
    Long id;
    Long trainSetVerId;
    Integer seq;
    String question;
    String expectedAnswer;
    String sid;
    Date createTime;
    Boolean deleted;
    Integer source;
}
