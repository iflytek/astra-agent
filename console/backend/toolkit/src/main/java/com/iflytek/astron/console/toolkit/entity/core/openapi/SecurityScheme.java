package com.iflytek.astron.console.toolkit.entity.core.openapi;

import lombok.Data;

@Data
public class SecurityScheme {
    String type;
    String name;
    String in;
}
