package com.iflytek.astron.console.hub.event;

import com.iflytek.astron.console.commons.enums.ShelfStatusEnum;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;

/**
 * Bot Publish Status Changed Event
 *
 * Triggered when bot is published/taken offline, used for handling related follow-up operations
 *
 * @author Omuigix
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class BotPublishStatusChangedEvent extends ApplicationEvent {

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
     * Action type (ONLINE=publish, OFFLINE=take offline)
     */
    private final String action;

    /**
     * Status before change
     */
    private final Integer oldStatus;

    /**
     * Status after change
     */
    private final Integer newStatus;

    /**
     * Publish channels
     */
    private final String publishChannels;

    /**
     * Construct bot publish status changed event
     *
     * @param source Event source
     * @param botId Bot ID
     * @param uid User ID
     * @param spaceId Space ID
     * @param action Action type
     * @param oldStatus Status before change
     * @param newStatus Status after change
     * @param publishChannels Publish channels
     */
    public BotPublishStatusChangedEvent(Object source, Integer botId, String uid, Long spaceId,
            String action, Integer oldStatus, Integer newStatus, String publishChannels) {
        super(source);
        this.botId = botId;
        this.uid = uid;
        this.spaceId = spaceId;
        this.action = action;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.publishChannels = publishChannels;
    }

    /**
     * Whether it's a publish to market operation
     */
    public boolean isOnline() {
        return "ONLINE".equals(action);
    }

    /**
     * Whether it's a take offline operation
     */
    public boolean isOffline() {
        return "OFFLINE".equals(action);
    }

    /**
     * Whether it's the first publish
     */
    public boolean isFirstPublish() {
        return oldStatus == null && ShelfStatusEnum.ON_SHELF.getCode().equals(newStatus);
    }
}
