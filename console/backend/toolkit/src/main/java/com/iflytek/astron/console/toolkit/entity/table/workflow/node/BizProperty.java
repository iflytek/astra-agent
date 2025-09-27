package com.iflytek.astron.console.toolkit.entity.table.workflow.node;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class BizProperty {
    String id;
    String name;
    @JSONField(name = "default")
    String dft;
    Boolean required;
    String type;
    List<BizProperty> properties;
}
