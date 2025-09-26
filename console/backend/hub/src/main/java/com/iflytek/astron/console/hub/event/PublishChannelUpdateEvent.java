package com.iflytek.astron.console.hub.event;

import com.iflytek.astron.console.commons.enums.PublishChannelEnum;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;

/**
 * Publish Channel Update Event
 *
 * Used to decouple circular dependencies between services, handling publish channel updates
 * asynchronously through event mechanism
 *
 * @author Omuigix
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class PublishChannelUpdateEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Bot ID
     */
    private final Integer botId;

    /**
     * User ID
     */
    private final String uid;

    /**
     * Space ID
     */
    private final Long spaceId;

    /**
     * Publish channel
     */
    private final PublishChannelEnum channel;

    /**
     * Whether to add channel (true=add, false=remove)
     */
    private final boolean isAdd;

    /**
     * Construct publish channel update event
     *
     * @param source Event source
     * @param botId Bot ID
     * @param uid User ID
     * @param spaceId Space ID
     * @param channel Publish channel
     * @param isAdd Whether to add channel
     */
    public PublishChannelUpdateEvent(Object source, Integer botId, String uid, Long spaceId,
            PublishChannelEnum channel, boolean isAdd) {
        super(source);
        this.botId = botId;
        this.uid = uid;
        this.spaceId = spaceId;
        this.channel = channel;
        this.isAdd = isAdd;
    }
}
