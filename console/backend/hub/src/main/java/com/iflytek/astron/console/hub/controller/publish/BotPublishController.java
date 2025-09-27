package com.iflytek.astron.console.hub.controller.publish;

import com.iflytek.astron.console.commons.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@Tag(name = "Bot Publish Management")
@RestController
@RequestMapping("/u/publish/bot")
public class BotPublishController {
    // TODO refactor bot publish
    //
    // @Autowired
    // private CensorshipUtil censorshipUtil;
    //
    // @Autowired
    // private UserLangChainInfoDao userLangChainInfoDao;
    //
    // @Autowired
    // private ReleaseManageClientService releaseManageClientService;
    //
    // @Autowired
    // private MassUtil massUtil;
    //
    // @Autowired
    // private MCPDataDao mcpDataDao;
    //
    // @Autowired
    // private BotPermissionUtil botPermissionUtil;

    /**
     * Publish MCP Original API: POST /publishMCP
     */
    @Operation(summary = "Publish MCP")
    // @SpacePreAuth(key = "BotPublishController_publishMCP_POST")
    @PostMapping("/mcp")
    public ApiResult publishMCP(
    // @RequestBody MCPDto dto
    ) throws IOException {
        return ApiResult.success();
        // String uid = CommonUtil.getUid();
        // Long botId = dto.getBotId();
        // KeyValuePair res = botPermissionUtil.checkBot(request, Math.toIntExact(botId));
        // if (!CommonResCode.SUCCESS.equals(res)) {
        // return ResponseMsg.of(res);
        // }
        // Long teamCreateUid = SpaceInfoUtil.getUidByCurrentSpaceId();
        // List<LinkedHashMap> hashMaps = userLangChainInfoDao.findListByBotId(Math.toIntExact(botId));
        // if (Objects.isNull(hashMaps) || hashMaps.isEmpty()) {
        // log.info("----- Assistant protocol not found, uid: {}, botId: {}", uid, botId);
        // throw new BusinessException(CommonResCode.BOT_MCP_CREATE_ERROR_CBM);
        // }
        //
        // String allText = dto.getServerName() + dto.getDescription() + dto.getContent();
        // if ("block".equals(censorshipUtil.checkText(allText))) {
        // throw new BusinessException(CommonResCode.CENSORSHIP_BOT_ERROR);
        // }
        // if ("block".equals(censorshipUtil.checkImage(dto.getIcon()))) {
        // throw new BusinessException(CommonResCode.CENSORSHIP_BOT_ERROR);
        // }
        //
        // String versionName = releaseManageClientService.getVersionNameByBotId(botId, request);
        // String cookie = MassUtil.getRequestCookies(request);
        // JSONObject mcp = massUtil.registerMcp(cookie, hashMaps.get(0), dto, request, versionName);
        // log.info("Publish MCP result: {}", mcp);
        //
        // mcp.set("createTime", LocalDateTime.now());
        // mcp.set("uid", uid);
        // mcpDataDao.insert(mcp);
        // releaseManageClientService.releaseMCP(botId, versionName, request);
        //
        // return new ResponseMsg<>();
    }

    /**
     * Get MCP content Original API: POST /getMcpContent
     */
    @Operation(summary = "Get MCP Content")
    @PostMapping("/mcp-content")
    public ApiResult getMcpContent(
    // @RequestBody MCPDto dto
    ) {
        return ApiResult.success();
        // Long botId = dto.getBotId();
        // KeyValuePair res = botPermissionUtil.checkBot(request, Math.toIntExact(botId));
        // if (!CommonResCode.SUCCESS.equals(res)) {
        // return ResponseMsg.of(res);
        // }
        // return new ResponseMsg<>(mcpDataDao.getMcp(botId));
    }
}
