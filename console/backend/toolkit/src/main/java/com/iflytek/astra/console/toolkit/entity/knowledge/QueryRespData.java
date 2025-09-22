package com.iflytek.astra.console.toolkit.entity.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class QueryRespData {
    String query;
    Integer count;
    List<ChunkInfo> results;
}
