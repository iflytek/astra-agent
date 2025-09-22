package com.iflytek.astra.console.toolkit.task.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.toolkit.entity.enumVo.ModelStatusEnum;
import com.iflytek.astra.console.toolkit.entity.table.model.Model;
import com.iflytek.astra.console.toolkit.service.model.ModelService;
import com.iflytek.astra.console.toolkit.util.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ModelStatusScheduler {

    private static final String LOCK_KEY = "cron:model:flushStatus:lock";
    // Task runs every 3 minutes by default, TTL set to 240s + heartbeat renewal to avoid expiration
    // concurrency
    private static final long LOCK_TTL_SEC = 240;
    private static final int HEARTBEAT_SEC = 60;

    @Resource
    private ModelService modelService;
    @Resource
    private RedisUtil redisUtil;

    // Unified scheduling thread pool provided by "Plan B" AppSchedulingConfig
    @Resource
    private TaskScheduler taskScheduler;

    /** Execute every 3 minutes */
    @Scheduled(cron = "0 */3 * * * ?")
    public void flushNonRunningLocalModelsCron() {
        // 1) Distributed lock (with token)
        final String token = UUID.randomUUID().toString();
        if (!redisUtil.tryLock(LOCK_KEY, LOCK_TTL_SEC, token)) {
            log.debug("[flushNonRunningLocalModelsCron] another instance is running, skip.");
            return;
        }

        // 2) Heartbeat renewal (using unified TaskScheduler; cancel before this task ends)
        Runnable renewTask = () -> {
            try {
                // Only renew if the lock still belongs to "me"
                redisUtil.renew(LOCK_KEY, LOCK_TTL_SEC, token);
            } catch (Throwable t) {
                log.warn("[flushStatusCron] heartbeat renew failed: {}", t.getMessage());
            }
        };
        ScheduledFuture<?> heartbeatFuture =
                        taskScheduler.scheduleAtFixedRate(renewTask, Duration.ofSeconds(HEARTBEAT_SEC));

        long startTs = System.currentTimeMillis();
        int pageNo = 1, pageSize = 500;
        int totalHandled = 0, totalUpdated = 0;

        try {
            while (true) {
                Page<Model> page = new Page<>(pageNo, pageSize);
                LambdaQueryWrapper<Model> lqw = new LambdaQueryWrapper<Model>()
                                .select(Model::getId, Model::getUid, Model::getType, Model::getStatus,
                                                Model::getRemark, Model::getUrl)
                                .eq(Model::getType, 2)
                                .eq(Model::getIsDeleted, 0)
                                // (status IS NULL) OR (status <> RUNNING)
                                .and(w -> w.isNull(Model::getStatus)
                                                .or()
                                                .ne(Model::getStatus, ModelStatusEnum.RUNNING.getCode()))
                                .orderByAsc(Model::getId);

                Page<Model> ret = modelService.page(page, lqw);
                List<Model> records = ret.getRecords();
                if (records == null || records.isEmpty()) {
                    break;
                }

                Map<String, List<Model>> byUid = records.stream()
                                .filter(m -> m.getUid() != null)
                                .collect(Collectors.groupingBy(Model::getUid));

                for (Map.Entry<String, List<Model>> e : byUid.entrySet()) {
                    String uid = e.getKey();
                    List<Model> list = e.getValue();
                    try {
                        int updated = modelService.flushStatusBatch(uid, list);
                        totalUpdated += updated;
                    } catch (Exception ex) {
                        log.warn("[flushStatusCron] uid={} flush failed: {}", uid, ex.getMessage(), ex);
                    }
                    totalHandled += list.size();
                }

                if (records.size() < pageSize) {
                    break;
                }
                pageNo++;
            }
        } catch (Throwable ex) {
            log.error("[flushStatusCron] unexpected error: {}", ex.getMessage(), ex);
        } finally {
            // 3) Cancel heartbeat and release lock (only if holder is me)
            try {
                if (heartbeatFuture != null) {
                    heartbeatFuture.cancel(true);
                }
            } catch (Throwable ignore) {
            }
            try {
                redisUtil.unlock(LOCK_KEY, token);
            } catch (Throwable unlockEx) {
                log.warn("[flushStatusCron] unlock failed: {}", unlockEx.getMessage());
            }
            log.info("[flushStatusCron] done, handled={}, updated={}, cost={}ms",
                            totalHandled, totalUpdated, (System.currentTimeMillis() - startTs));
        }
    }
}
