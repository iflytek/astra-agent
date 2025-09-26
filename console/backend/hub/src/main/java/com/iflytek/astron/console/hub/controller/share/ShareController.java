package com.iflytek.astron.console.hub.controller.share;

import com.alibaba.fastjson2.JSON;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.chat.ChatListCreateResponse;
import com.iflytek.astron.console.commons.entity.space.AgentShareRecord;
import com.iflytek.astron.console.hub.dto.share.ShareKey;
import com.iflytek.astron.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astron.console.commons.enums.ShelfStatusEnum;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.hub.dto.share.CardAddBody;
import com.iflytek.astron.console.hub.service.chat.ChatListService;
import com.iflytek.astron.console.hub.service.share.ShareService;
import com.iflytek.astron.console.hub.util.BotPermissionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Objects;

/**
 * @author yingpeng
 */
@Slf4j
@Tag(name = "Sharing related")
@RestController
@RequestMapping(value = "/share")
public class ShareController {

    @Autowired
    private ShareService shareService;

    @Autowired
    private BotPermissionUtil botPermissionUtil;

    @Autowired
    private ChatListService chatListService;

    @SpacePreAuth(key = "AgentController_getShareKey_POST")
    @PostMapping("/get-share-key")
    @Operation(summary = "Get sharing identifier")
    public ApiResult<ShareKey> getShareKey(@RequestBody CardAddBody body) {
        String uid = RequestContextUtil.getUID();
        Long relatedId = body.getRelateId();
        int relatedType = body.getRelateType();
        log.info("****** uid: {} sharing agent: {}", uid, JSON.toJSONString(body));
        int status = shareService.getBotStatus(relatedId);
        // Check if already published
        if (ShelfStatusEnum.isOffShelf(status)) {
            // If not published, check for privilege escalation
            botPermissionUtil.checkBot(Math.toIntExact(relatedId));
        }
        // Generate sharing identifier
        String shareKey = shareService.getShareKey(uid, relatedType, relatedId);
        ShareKey result = new ShareKey(shareKey);
        return ApiResult.success(result);
    }

    @PostMapping("/add-shared-agent")
    @Operation(summary = "Add shared agent")
    public ApiResult<ChatListCreateResponse> addSharedAgent(HttpServletRequest request, @RequestBody ShareKey shareKey) {
        String uid = RequestContextUtil.getUID();
        String shareAgentKey = shareKey.getShareAgentKey();
        log.info("****** uid: {} adding shared partner: {}", uid, shareAgentKey);
        AgentShareRecord record = shareService.getShareByKey(shareAgentKey);
        if (Objects.isNull(record)) {
            return ApiResult.error(ResponseEnum.SHARE_URL_INVALID);
        }
        int relatedType = record.getShareType();
        // In the future, if relatedType exceeds 2, use enum and switch
        if (relatedType == 0) {
            return ApiResult.success(chatListService.createChatList(uid, "", Math.toIntExact(record.getBaseId())));
        }
        return ApiResult.success();
    }
}
