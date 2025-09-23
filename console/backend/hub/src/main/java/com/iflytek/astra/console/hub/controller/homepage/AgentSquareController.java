package com.iflytek.astra.console.hub.controller.homepage;

import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.hub.dto.homepage.BotListPageDto;
import com.iflytek.astra.console.hub.dto.homepage.BotTypeDto;
import com.iflytek.astra.console.hub.service.homepage.AgentSquareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author yun-zhi-ztl
 */
@Slf4j
@Tag(name = "Homepage Agent Square")
@RestController
@RequestMapping("/home-page/agent-square")
public class AgentSquareController {

    @Autowired
    private AgentSquareService agentSquareService;

    @GetMapping("/get-bot-type-list")
    @Operation(summary = "Get agent category list")
    public ApiResult<List<BotTypeDto>> getBotTypeList() {
        return ApiResult.success(agentSquareService.getBotTypeList());
    }

    @GetMapping("/get-bot-page-by-type")
    @Operation(summary = "Get agent paginated list by category")
    public ApiResult<BotListPageDto> getBotPageByType(@RequestParam(value = "type") Integer type,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(defaultValue = "1") Integer page) {
        return ApiResult.success(agentSquareService.getBotPageByType(type, search, pageSize, page));
    }
}
