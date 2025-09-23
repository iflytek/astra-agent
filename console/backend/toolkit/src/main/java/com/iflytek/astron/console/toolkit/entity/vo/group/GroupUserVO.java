package com.iflytek.astron.console.toolkit.entity.vo.group;

import lombok.Data;

import java.util.List;

@Data
public class GroupUserVO {
    private String name;
    private List<Long> userIds;
    private List<String> tagNames;
}
