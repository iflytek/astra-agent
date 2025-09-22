package com.iflytek.astra.console.hub.controller.share;

import cn.hutool.json.JSONUtil;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.entity.chat.ChatListCreateResponse;
import com.iflytek.astra.console.commons.entity.space.AgentShareRecord;
import com.iflytek.astra.console.hub.dto.share.ShareKey;
import com.iflytek.astra.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astra.console.commons.enums.ShelfStatusEnum;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.hub.dto.share.CardAddBody;
import com.iflytek.astra.console.hub.service.chat.ChatListService;
import com.iflytek.astra.console.hub.service.share.ShareService;
import com.iflytek.astra.console.hub.util.BotPermissionUtil;
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
@Tag(name = "分享相关")
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
    @Operation(summary = "获取分享标识符")
    public ApiResult<ShareKey> getShareKey(@RequestBody CardAddBody body) {
        String uid = RequestContextUtil.getUID();
        Long relatedId = body.getRelateId();
        int relatedType = body.getRelateType();
        log.info("****** uid: {} 分享智能体: {}", uid, JSONUtil.toJsonStr(body));
        int status = shareService.getBotStatus(relatedId);
        //检验是否已发布
        if (ShelfStatusEnum.isOffShelf(status)) {
            // 如果未发布 检验是否越权
            botPermissionUtil.checkBot(Math.toIntExact(relatedId));
        }
        // 生成分享标识符
        String shareKey = shareService.getShareKey(uid, relatedType, relatedId);
        ShareKey result = new ShareKey(shareKey);
        return ApiResult.success(result);
    }

    @PostMapping("/add-shared-agent")
    @Operation(summary = "添加被分享的智能体")
    public ApiResult<ChatListCreateResponse> addSharedAgent(HttpServletRequest request, @RequestBody ShareKey shareKey) {
        String uid = RequestContextUtil.getUID();
        String shareAgentKey = shareKey.getShareAgentKey();
        log.info("****** uid: {} 添加被分享的友伴: {}", uid, shareAgentKey);
        AgentShareRecord record = shareService.getShareByKey(shareAgentKey);
        if (Objects.isNull(record)) {
            return ApiResult.error(ResponseEnum.SHARE_URL_INVALID);
        }
        int relatedType = record.getShareType();
        // 后面如果relatedType 超过2，就enum 和 switch
        if (relatedType == 0) {
            return ApiResult.success(chatListService.createChatList(uid, "", Math.toIntExact(record.getBaseId())));
        }
        return ApiResult.success();
    }
}
