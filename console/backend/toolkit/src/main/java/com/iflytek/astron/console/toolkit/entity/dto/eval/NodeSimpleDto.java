package com.iflytek.astron.console.toolkit.entity.dto.eval;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeSimpleDto {
    String nodeId;
    String nodeName;

    @Deprecated
    String domain;
}
