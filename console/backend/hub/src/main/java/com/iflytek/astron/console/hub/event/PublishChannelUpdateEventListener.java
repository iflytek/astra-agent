package com.iflytek.astron.console.hub.event;

import com.iflytek.astron.console.hub.service.publish.BotPublishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Publish Channel Update Event Listener
 *
 * Handles publish channel update events, decoupling circular dependencies between services
 *
 * @author Omuigix
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishChannelUpdateEventListener {

    private final BotPublishService botPublishService;

    /**
     * Handle publish channel update event
     *
     * @param event Publish channel update event
     */
    @Async
    @EventListener
    public void handlePublishChannelUpdate(PublishChannelUpdateEvent event) {
        log.info("Handle publish channel update event: botId={}, uid={}, spaceId={}, channel={}, isAdd={}",
                event.getBotId(), event.getUid(), event.getSpaceId(),
                event.getChannel().getCode(), event.isAdd());

        try {
            botPublishService.updatePublishChannel(
                    event.getBotId(),
                    event.getUid(),
                    event.getSpaceId(),
                    event.getChannel(),
                    event.isAdd());

            log.info("Publish channel update event processed successfully: botId={}, channel={}",
                    event.getBotId(), event.getChannel().getCode());
        } catch (Exception e) {
            log.error("Publish channel update event processing failed: botId={}, channel={}",
                    event.getBotId(), event.getChannel().getCode(), e);
            // Retry mechanism or error handling logic can be added here
        }
    }
}
