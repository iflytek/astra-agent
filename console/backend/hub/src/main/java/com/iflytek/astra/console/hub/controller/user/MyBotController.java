package com.iflytek.astra.console.hub.controller.user;

import com.iflytek.astra.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astra.console.commons.entity.bot.BotDetail;
import com.iflytek.astra.console.commons.entity.bot.PromptBotDetail;
import com.iflytek.astra.console.commons.mapper.bot.ChatBotBaseMapper;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.service.bot.ChatBotDataService;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.hub.dto.user.MyBotPageDTO;
import com.iflytek.astra.console.hub.dto.user.MyBotParamDTO;
import com.iflytek.astra.console.hub.service.user.UserBotService;
import com.iflytek.astra.console.hub.util.BotPermissionUtil;
import com.iflytek.astra.console.toolkit.service.repo.MassDatasetInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * @author wowo
 * @since 2025/9/9 15:24
 **/

@Slf4j
@Controller
@RequestMapping("/my-bot")
@Tag(name = "Personal agent correlation")
@RestController
public class MyBotController {

    @Autowired
    private UserBotService userBotService;

    @Autowired
    private ChatBotDataService chatBotDataService;

    @Autowired
    private ChatBotBaseMapper chatBotBaseMapper;

    @Autowired
    private BotPermissionUtil botPermissionUtil;

    @Autowired
    private MassDatasetInfoService massDatasetInfoService;

    /**
     * Display assistants I created
     */
    @SpacePreAuth(key = "ChatBotMarketController_getCreatedList_POST")
    @PostMapping("/list")
    @Operation(summary = "User-created assistant presentation")
    public ApiResult<MyBotPageDTO> getCreatedList(@RequestBody MyBotParamDTO myBotParamDTO) {
        return ApiResult.success(userBotService.listMyBots(myBotParamDTO));
    }

    /**
     * Delete assistant
     */
    @SpacePreAuth(key = "ChatBotController_delete_POST")
    @PostMapping("/delete")
    @Operation(summary = "User-created assistant deletion")
    public ApiResult<Boolean> deleteBot(@RequestParam(value = "botId") Integer botId) {
        return ApiResult.success(userBotService.deleteBot(botId));
    }

    /**
     * Get bot detail information
     */
    // todo 此处需要打开注解,为了方便测试暂时关闭
    // @SpacePreAuth(key = "ChatBotController_botDetail_POST")
    @PostMapping("/bot-detail")
    @Operation(summary = "Get bot detail information")
    public ApiResult<BotDetail> getBotDetail(HttpServletRequest request, @RequestParam("botId") Integer botId) {
        // Permission validation
        botPermissionUtil.checkBot(botId);
        String uid = RequestContextUtil.getUID();

        // Get bot detail data
        PromptBotDetail botDetail = chatBotDataService.getPromptBotDetail(botId, uid);
        botDetail.setMaasDatasetList(massDatasetInfoService.getDatasetMaasByBot(uid, botId, request));

        // Manually parse inputExample to inputExampleList
        if (botDetail != null) {
            botDetail.parseInputExampleList();
        }

        return ApiResult.success(botDetail);
    }

}
