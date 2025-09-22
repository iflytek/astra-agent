package com.iflytek.astra.console.toolkit.entity.dto.eval;

import com.iflytek.astra.console.toolkit.entity.table.trace.NodeInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class NodeDataDto extends NodeInfo {
    String markData;
}
