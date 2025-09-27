package com.iflytek.astron.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("effect_eval_set_ver_excel_data_values")
public class EffectEvalSetVerExcelDataValue {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long setVerId;

    private Long recordId;

    private Long headerId;

    private String cellValue;

    private String sid;

    private Long seq;

    private Date createTime;

    private Boolean deleted;

    private Integer source;
}
