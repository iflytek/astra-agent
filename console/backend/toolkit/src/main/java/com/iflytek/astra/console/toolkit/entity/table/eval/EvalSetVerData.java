package com.iflytek.astra.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;

@Data
@TableName("effect_eval_set_ver_data")
public class EvalSetVerData {
    @TableId(type = IdType.AUTO)
    Long id;
    Long evalSetVerId;
    Integer seq;
    String question = StringUtils.EMPTY;
    String expectedAnswer;
    String sid;
    Date createTime;
    Boolean deleted;
    Boolean autoAdd;
    Integer source;
}
