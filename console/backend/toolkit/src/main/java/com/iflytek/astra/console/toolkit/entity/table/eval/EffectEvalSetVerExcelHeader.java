package com.iflytek.astra.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("effect_eval_set_ver_excel_headers")
public class EffectEvalSetVerExcelHeader {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long setVerId;

    private String name;

    private Integer sort;

    private Date createTime;

    private Boolean deleted;

    @TableField(exist = false)
    private Boolean hasAvailable;

    // New column: 0 - copy column, 1 - new column
    @TableField(exist = false)
    private Integer isCreated;
}
