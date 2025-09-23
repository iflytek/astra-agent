package com.iflytek.astron.console.hub.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "space.invite-message-template")
@Data
@Component
public class InviteMessageTempProperties {
    private String url;

    private String spaceTitle;

    private String spaceContent;

    private String enterpriseTitle;

    private String enterpriseContent;
}
