package com.iflytek.astron.console.hub.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "space.limit")
@Data
@Component
public class SpaceLimitProperties {

    private SpaceLimit free;

    private SpaceLimit pro;

    private SpaceLimit team;

    private SpaceLimit enterprise;


    @Data
    static public class SpaceLimit {
        private Integer spaceCount;
        private Integer userCount;
    }
}
