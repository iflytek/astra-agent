package com.iflytek.astron.console.toolkit.entity.pojo;

import lombok.Data;

import java.util.List;

@Data
public class SliceConfig {
    // 0: default, 1: custom slice
    private Integer type;
    // Separator (spelling error, don't change unless coordinating with frontend)
    private List<String> seperator;
    // Force split
    private List<String> cutOff;
    // Length range for data slicing knowledge points
    private List<Integer> lengthRange;
}
