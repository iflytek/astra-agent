package com.iflytek.astron.console.toolkit.controller.common;

import com.iflytek.astron.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astron.console.toolkit.service.model.LLMService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/llm")
@Slf4j
@ResponseResultBody
@Tag(name = "Model acquisition interface")
public class LLMController {

    @Resource
    LLMService llmService;

    @GetMapping("/auth-list")
    public Object getLlmAuthList(
            HttpServletRequest request,
            @RequestParam String appId,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String nodeType) throws InterruptedException {
        return llmService.getLlmAuthList(request, appId, scene, nodeType);
    }

    /**
     * 前端非要加一个多余的接口取数，不知道起啥名，故叫inter1
     *
     * @param request
     * @param id
     * @param llmSource
     * @return
     */
    @GetMapping("/inter1")
    public Object inter1(HttpServletRequest request, @RequestParam Long id, @RequestParam Integer llmSource) {
        return llmService.getModelServerInfo(request, id, llmSource);
    }

    /**
     * 自定义模型参数
     *
     * @param id
     * @param llmSource
     * @return
     */
    @GetMapping("/self-model-config")
    public Object selfModelConfig(@RequestParam Long id, @RequestParam Integer llmSource) {
        return llmService.selfModelConfig(id, llmSource);
    }

    @GetMapping("/flow-use-list")
    public Object flowUseList(String flowId) {
        return llmService.getFlowUseList(flowId);
    }
}
