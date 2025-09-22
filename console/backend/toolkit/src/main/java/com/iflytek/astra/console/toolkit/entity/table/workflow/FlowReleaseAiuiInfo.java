package com.iflytek.astra.console.toolkit.entity.table.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class FlowReleaseAiuiInfo {
    @TableId(type = IdType.AUTO)
    Long id;
    String data;
}
