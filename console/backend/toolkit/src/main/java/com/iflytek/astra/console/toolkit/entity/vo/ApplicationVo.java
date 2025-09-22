package com.iflytek.astra.console.toolkit.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationVo {
    Long id;
    Integer type;
    String name;
    String appId;
}
