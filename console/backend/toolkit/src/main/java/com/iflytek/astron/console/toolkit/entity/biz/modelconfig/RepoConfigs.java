package com.iflytek.astron.console.toolkit.entity.biz.modelconfig;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.List;

@Data
public class RepoConfigs {
    List<JSONObject> reposet;
    Double scoreThreshold;
    Boolean scoreThresholdEnabled;
    Integer topK;
}
