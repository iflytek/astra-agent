package com.iflytek.astra.console.toolkit.entity.knowledge;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

@Data
public class ChunkInfo {
    Double score;
    String docId;
    String title;
    String content;
    String context;
    JSONObject references;

    // vo
    Object fileInfo;
}
