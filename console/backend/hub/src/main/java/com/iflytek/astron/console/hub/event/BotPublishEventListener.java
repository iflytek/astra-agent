package com.iflytek.astron.console.hub.event;

import com.iflytek.astron.console.hub.service.publish.BotPublishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Bot publishing related event listener
 *
 * Handles various events in the publishing management module
 *
 * @author Omuigix
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotPublishEventListener {

    private final BotPublishService botPublishService;

    /**
     * Handle bot publish status changed event Used for sending notifications, updating cache, logging,
     * etc.
     */
    @Async
    @EventListener
    public void handleBotPublishStatusChanged(BotPublishStatusChangedEvent event) {
        log.info("Handle bot publish status change: botId={}, action={}, {} -> {}",
                event.getBotId(), event.getAction(), event.getOldStatus(), event.getNewStatus());

        try {
            // 1. Record publish status change log
            if (event.isFirstPublish()) {
                log.info("Bot first publish: botId={}, uid={}, channels={}",
                        event.getBotId(), event.getUid(), event.getPublishChannels());
            } else if (event.isOnline()) {
                log.info("Bot republished: botId={}, uid={}", event.getBotId(), event.getUid());
            } else if (event.isOffline()) {
                log.info("Bot taken offline: botId={}, uid={}", event.getBotId(), event.getUid());
            }

            // 2. Additional follow-up processing can be added here
            // - Send notifications to users
            // - Update search index
            // - Clear cache
            // - Statistical analysis

            log.info("Bot publish status change processing completed: botId={}", event.getBotId());
        } catch (Exception e) {
            log.error("Bot publish status change processing failed: botId={}", event.getBotId(), e);
        }
    }


}
