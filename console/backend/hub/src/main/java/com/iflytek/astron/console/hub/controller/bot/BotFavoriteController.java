package com.iflytek.astron.console.hub.controller.bot;

import com.iflytek.astron.console.commons.dto.bot.BotFavoritePageDto;
import com.iflytek.astron.console.commons.entity.bot.BotMarketForm;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.service.bot.BotFavoriteService;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Assistant Favorites")
@RestController
@RequestMapping(value = "/bot/favorite")
public class BotFavoriteController {

    @Autowired
    private BotFavoriteService botFavoriteService;

    @PostMapping(value = "/list")
    public ApiResult<BotFavoritePageDto> list(HttpServletRequest request, @RequestBody BotMarketForm botMarketForm) {
        String uid = RequestContextUtil.getUID();
        String langCode = request.getHeader("Lang-Code") == null ? "" : request.getHeader("Lang-Code");
        BotFavoritePageDto pageDto = botFavoriteService.selectPage(botMarketForm, uid, langCode);
        return ApiResult.success(pageDto);
    }

    @PostMapping(value = "/create")
    public ApiResult<Void> create(@RequestParam Integer botId) {
        String uid = RequestContextUtil.getUID();
        botFavoriteService.create(uid, botId);

        return ApiResult.success();
    }

    @PostMapping(value = "/delete")
    public ApiResult<Void> delete(@RequestParam Integer botId) {
        String uid = RequestContextUtil.getUID();
        botFavoriteService.delete(uid, botId);

        return ApiResult.success();
    }
}
