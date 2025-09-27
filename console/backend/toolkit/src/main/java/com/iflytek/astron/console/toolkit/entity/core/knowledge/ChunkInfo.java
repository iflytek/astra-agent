package com.iflytek.astron.console.toolkit.entity.core.knowledge;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

@Data
public class ChunkInfo {
    Double score;
    String docId;
    String dataIndex;
    String title;
    String content;
    String context;
    JSONObject references;

    // vo
    Object fileInfo;
}
