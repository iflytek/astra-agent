package com.iflytek.astron.console.toolkit.config.spring;

import com.iflytek.astron.console.toolkit.service.workflow.WorkflowService;
import com.iflytek.astron.console.toolkit.util.RedisUtil;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Application shutdown cleanup logic: 1) Uses Redis distributed lock (with token) to ensure
 * idempotency across multiple instances/repeated callbacks; 2) Failures do not block process exit,
 * but complete logs are recorded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteShutdown {

    /** Lock Key: Ensures shutdown logic is executed only once across instances */
    private static final String LOCK_KEY = "spark_bot:application:destroy";
    /** Lock TTL: Shutdown process is usually short, allowing 5 minutes redundancy here */
    private static final Duration LOCK_TTL = Duration.ofSeconds(300);

    /** If execution is needed only in specific environments, maintain profiles to skip in this list. */
    private static final String[] SKIP_PROFILES = {"test"};

    private final WorkflowService workflowService;
    private final RedisUtil redisUtil;
    private final Environment environment;

    @PreDestroy
    public void onShutdown() {
        // Optional: Skip in local/unit test environments to avoid running actual cleanup process
        if (shouldSkipByProfile()) {
            log.info("ExecuteShutdown skipped by active profiles: {}", Arrays.toString(environment.getActiveProfiles()));
            return;
        }

        final String token = UUID.randomUUID().toString();
        log.info(">>> ExecuteShutdown start, try acquire lock. key={}, ttl={}s", LOCK_KEY, LOCK_TTL.getSeconds());

        if (!redisUtil.tryLock(LOCK_KEY, LOCK_TTL, token)) {
            log.info("ExecuteShutdown skipped: lock already held by another instance. key={}", LOCK_KEY);
            return;
        }

        try {
            // Actual shutdown action: Clear canvas hold count
            workflowService.removeAllCanvasHold();
            log.info("ExecuteShutdown done: removeAllCanvasHold finished.");
        } catch (Exception e) {
            // Do not block shutdown, but need complete logging
            log.error("ExecuteShutdown failed while removing canvas hold.", e);
        } finally {
            boolean released = false;
            try {
                released = redisUtil.unlock(LOCK_KEY, token);
            } catch (Exception e) {
                log.warn("ExecuteShutdown unlock threw exception. key={}, tokenTail=***{}", LOCK_KEY, tail4(token), e);
            }
            if (!released) {
                log.warn("ExecuteShutdown unlock not released (maybe expired or token mismatch). key={}, tokenTail=***{}",
                        LOCK_KEY, tail4(token));
            } else {
                log.debug("ExecuteShutdown lock released. key={}", LOCK_KEY);
            }
        }
    }

    private boolean shouldSkipByProfile() {
        final String[] actives = environment.getActiveProfiles();
        if (actives == null || actives.length == 0) {
            return false;
        }
        for (String p : actives) {
            for (String skip : SKIP_PROFILES) {
                if (skip.equalsIgnoreCase(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String tail4(String s) {
        if (s == null || s.length() < 4) {
            return "***";
        }
        return s.substring(s.length() - 4);
    }
}
