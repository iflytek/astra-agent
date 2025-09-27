package com.iflytek.astron.console.hub.controller.notification;

import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.hub.dto.notification.MarkReadRequest;
import com.iflytek.astron.console.hub.dto.notification.NotificationPageResponse;
import com.iflytek.astron.console.hub.dto.notification.NotificationQueryRequest;
import com.iflytek.astron.console.hub.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notification Management", description = "消息通知管理接口")
@Slf4j
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/list")
    @Operation(summary = "查询当前用户的通知列表", description = "分页查询当前用户的通知消息列表")
    public ApiResult<NotificationPageResponse> getUserNotifications(
            @Parameter(description = "查询参数") @Valid NotificationQueryRequest queryRequest) {

        String currentUserUid = RequestContextUtil.getUID();
        log.debug("查询用户通知列表: uid={}, pageIndex={}, pageSize={}",
                currentUserUid, queryRequest.getPageIndex(), queryRequest.getPageSize());

        NotificationPageResponse response = notificationService.getUserNotifications(currentUserUid, queryRequest);
        log.debug("查询成功，返回 {} 条通知，未读数量: {}",
                response.getNotifications().size(), response.getUnreadCount());

        return ApiResult.success(response);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "获取当前用户未读通知数量", description = "获取当前用户的未读通知消息数量")
    public ApiResult<Long> getUnreadNotificationCount() {
        String currentUserUid = RequestContextUtil.getUID();
        log.debug("查询用户未读通知数量: uid={}", currentUserUid);

        long unreadCount = notificationService.getUnreadNotificationCount(currentUserUid);
        log.debug("用户未读通知数量: {}", unreadCount);

        return ApiResult.success(unreadCount);
    }

    @PostMapping("/mark-read")
    @Operation(summary = "标记通知为已读", description = "将指定的通知消息标记为已读状态")
    public ApiResult<Boolean> markNotificationsAsRead(@Valid @RequestBody MarkReadRequest request) {
        String currentUserUid = RequestContextUtil.getUID();
        log.info("标记通知为已读: uid={}, markAll={}, notificationIds={}",
                currentUserUid, request.getMarkAll(), request.getNotificationIds());

        boolean success = notificationService.markNotificationsAsRead(currentUserUid, request);
        log.info("标记通知为已读操作完成: success={}", success);

        return ApiResult.success(success);
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "删除通知", description = "删除指定的通知消息")
    public ApiResult<Boolean> deleteNotification(
            @Parameter(description = "通知ID") @PathVariable Long notificationId) {

        String currentUserUid = RequestContextUtil.getUID();
        log.info("删除通知: uid={}, notificationId={}", currentUserUid, notificationId);

        boolean success = notificationService.deleteNotification(currentUserUid, notificationId);
        log.info("删除通知操作完成: success={}", success);

        return ApiResult.success(success);
    }
}
