package com.iflytek.astron.console.toolkit.entity.core.openapi;


import lombok.Data;

import java.util.Map;

@Data
public class Components {
    Map<String, SecurityScheme> securitySchemes;
}
