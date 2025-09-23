package com.iflytek.astra.console.toolkit.controller.node;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.astra.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astra.console.toolkit.entity.table.node.TextNodeConfig;
import com.iflytek.astra.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astra.console.toolkit.service.node.TextNodeConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Date;

/**
 * @Author clliu19
 * @Date: 2025/3/10 09:17
 */
@RestController
@RequestMapping("/textNode/config")
@ResponseResultBody
@Tag(name = "Text node management interface")
public class TextNodeConfigController {
    @Resource
    private TextNodeConfigService textNodeConfigService;

    @PostMapping("/save")
    public Object save(@RequestBody TextNodeConfig textNodeConfig, HttpServletRequest httpServletRequest) {
        String userId = UserInfoManagerHandler.getUserId();
        textNodeConfig.setUid(userId);
        return textNodeConfigService.saveInfo(textNodeConfig);
    }

    @GetMapping("/list")
    public Object list() {
        String uid = UserInfoManagerHandler.getUserId();
        return textNodeConfigService.list(new LambdaQueryWrapper<TextNodeConfig>()
                .in(TextNodeConfig::getUid, Arrays.asList(uid, -1))
                .orderByDesc(TextNodeConfig::getCreateTime));
    }

    @GetMapping("/delete")
    public Object delete(Long id) {
        return textNodeConfigService.getBaseMapper().delete(new LambdaQueryWrapper<TextNodeConfig>().eq(TextNodeConfig::getId, id));
    }

    @PostMapping("/update")
    public Object update(@RequestBody TextNodeConfig textNodeConfig) {
        textNodeConfig.setUpdateTime(new Date());
        return textNodeConfigService.updateById(textNodeConfig);
    }
}
