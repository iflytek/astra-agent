package com.iflytek.astron.console.hub.controller.bot;

import com.iflytek.astron.console.commons.annotation.RateLimit;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.bot.BotCreateForm;
import com.iflytek.astron.console.commons.entity.bot.BotInfoDto;
import com.iflytek.astron.console.commons.entity.bot.BotTypeList;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.service.bot.BotService;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.hub.dto.bot.BotGenerationDTO;
import com.iflytek.astron.console.commons.dto.bot.BotModelDto;
import com.iflytek.astron.console.commons.enums.bot.DefaultBotModelEnum;
import com.iflytek.astron.console.hub.service.bot.BotAIService;
import com.iflytek.astron.console.hub.util.BotPermissionUtil;
import com.iflytek.astron.console.toolkit.service.model.LLMService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;

@Slf4j
@RestController
@RequestMapping("/bot")
public class BotCreateController {

    @Autowired
    private BotAIService botAIService;

    @Autowired
    private BotService botService;

    @Autowired
    private BotPermissionUtil botPermissionUtil;

    @Autowired
    private LLMService llmService;

    /**
     * Create workflow assistant
     *
     * @param request HTTP request containing space context
     * @param bot Assistant creation form
     * @return Created assistant ID
     */
    @PostMapping("/create")
    public ApiResult<Integer> createBot(HttpServletRequest request, @RequestBody BotCreateForm bot) {
        try {
            String uid = RequestContextUtil.getUID();
            Long spaceId = SpaceInfoUtil.getSpaceId();
            BotInfoDto botInfo = botService.insertBotBasicInfo(uid, bot, spaceId);
            return ApiResult.success(botInfo.getBotId());
        } catch (Exception e) {
            log.error("Failed to create basic assistant: {}", e.getMessage(), e);
            return ApiResult.error(ResponseEnum.SYSTEM_ERROR);
        }
    }

    /**
     * Get assistant type list
     *
     * @return Assistant type list
     */
    @PostMapping("/type-list")
    public ApiResult<List<BotTypeList>> getBotTypeList() {
        try {
            List<BotTypeList> typeList = botService.getBotTypeList();
            return ApiResult.success(typeList);
        } catch (Exception e) {
            log.error("Failed to get assistant type list: {}", e.getMessage(), e);
            return ApiResult.error(ResponseEnum.SYSTEM_ERROR);
        }
    }

    /**
     * AI generate assistant avatar
     *
     * @param requestBody Robot creation form
     * @return Generated avatar URL
     */
    @PostMapping("/ai-avatar-gen")
    @RateLimit(dimension = "USER", window = 86400, limit = 50)
    public ApiResult<String> generateAvatar(@Valid @RequestBody BotCreateForm requestBody) {
        try {
            String uid = RequestContextUtil.getUID();
            String botName = requestBody.getName();
            String botDesc = requestBody.getBotDesc();

            if (botName == null || botName.trim().isEmpty()) {
                return ApiResult.error(ResponseEnum.PARAMS_ERROR);
            }

            String avatar = botAIService.generateAvatar(uid, botName, botDesc);

            if (avatar == null || avatar.trim().isEmpty()) {
                return ApiResult.error(ResponseEnum.SYSTEM_ERROR);
            }

            return ApiResult.success(avatar);
        } catch (Exception e) {
            log.error("User [{}] AI assistant avatar generation failed", RequestContextUtil.getUID(), e);
            return ApiResult.error(ResponseEnum.SYSTEM_ERROR);
        }
    }

    /**
     * Generate assistant with one sentence
     *
     * @param sentence Input sentence
     * @return Generated assistant details
     */
    @PostMapping("/ai-sentence-gen")
    public ApiResult<BotGenerationDTO> sentence(@RequestParam String sentence) {
        try {
            if (sentence == null || sentence.trim().isEmpty()) {
                return ApiResult.error(ResponseEnum.PARAMS_ERROR);
            }

            String uid = RequestContextUtil.getUID();
            BotGenerationDTO botDetail = botAIService.sentenceBot(sentence, uid);
            return ApiResult.success(botDetail);
        } catch (Exception e) {
            log.error("One sentence assistant generation failed", e);
            return ApiResult.error(ResponseEnum.SYSTEM_ERROR);
        }
    }

    /**
     * Large model generates assistant prologue
     *
     * @param form Robot creation form
     * @return Generated prologue
     */
    @PostMapping("/ai-prologue-gen")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> aiGenPrologue(@Valid @RequestBody BotCreateForm form) {
        try {
            String botName = form.getName();
            if (botName == null || botName.trim().isEmpty()) {
                return ApiResult.error(ResponseEnum.PARAMS_ERROR);
            }
            String aiPrologue = botAIService.generatePrologue(botName);
            return ApiResult.success(aiPrologue);
        } catch (Exception e) {
            log.error("AI assistant prologue generation failed", e);
            return ApiResult.error(ResponseEnum.SYSTEM_ERROR);
        }
    }

    /**
     * Update workflow assistant
     *
     * @param request HTTP request containing space context
     * @param bot Assistant update form (must contain botId)
     * @return Update result
     */
    @PostMapping("/update")
    public ApiResult<Boolean> updateBot(HttpServletRequest request, @RequestBody BotCreateForm bot) {
        try {
            // Validate botId is provided
            if (bot.getBotId() == null) {
                return ApiResult.error(ResponseEnum.PARAMS_ERROR);
            }

            String uid = RequestContextUtil.getUID();
            Long spaceId = SpaceInfoUtil.getSpaceId();

            // Permission validation
            botPermissionUtil.checkBot(bot.getBotId());

            // Update bot basic information only (simplified without MaaS sync)
            Boolean result = botService.updateBotBasicInfo(uid, bot, spaceId);

            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("Failed to update assistant: {}", e.getMessage(), e);
            return ApiResult.error(ResponseEnum.SYSTEM_ERROR);
        }
    }

    /**
     * Handle request to get bot models
     *
     * @param request HTTP request object
     * @return API result containing all model lists
     */
    @Operation(summary = "Get bot model list", description = "Fetches both default and custom bot models")
    @GetMapping("/bot-model")
    public ApiResult<List<BotModelDto>> botModel(HttpServletRequest request) {
        List<BotModelDto> allModels = new ArrayList<>();

        // 1. Add default models: Spark 4.0 and x1
        BotModelDto x1Model = new BotModelDto();
        x1Model.setModelDomain(DefaultBotModelEnum.X1.getDomain());
        x1Model.setModelName(DefaultBotModelEnum.X1.getName());
        x1Model.setModelIcon(DefaultBotModelEnum.X1.getIcon());
        x1Model.setIsCustom(false);
        allModels.add(x1Model);

        BotModelDto sparkModel = new BotModelDto();
        sparkModel.setModelDomain(DefaultBotModelEnum.SPARK_4_0.getDomain());
        sparkModel.setModelName(DefaultBotModelEnum.SPARK_4_0.getName());
        sparkModel.setModelIcon(DefaultBotModelEnum.SPARK_4_0.getIcon());
        sparkModel.setIsCustom(false);
        allModels.add(sparkModel);

        // 2. Get custom models
        JSONObject result = JSONObject.from(llmService.getLlmAuthList(request, null, "workflow", "spark-llm"));

        try {
            if (result != null && result.containsKey("workflow")) {
                JSONArray workflowArray = result.getJSONArray("workflow");

                // Get the second array element (index 1, i.e., "Custom Models")
                if (workflowArray != null && workflowArray.size() > 1) {
                    JSONObject secondCategory = workflowArray.getJSONObject(1);

                    if (secondCategory != null && secondCategory.containsKey("modelList")) {
                        JSONArray modelList = secondCategory.getJSONArray("modelList");

                        if (modelList != null) {
                            for (int i = 0; i < modelList.size(); i++) {
                                JSONObject modelObj = modelList.getJSONObject(i);
                                if (modelObj != null) {
                                    BotModelDto customModel = convertToModelDto(modelObj);
                                    allModels.add(customModel);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract custom models from LLM auth list", e);
        }

        return ApiResult.success(allModels);
    }

    /**
     * Convert JSONObject to BotModelDto object
     */
    private BotModelDto convertToModelDto(JSONObject modelJson) {
        BotModelDto model = new BotModelDto();

        // Set basic properties
        if (modelJson.containsKey("id") && modelJson.get("id") != null) {
            model.setModelId(modelJson.getLong("id"));
        }
        model.setModelName(modelJson.getString("name"));
        model.setModelDomain(modelJson.getString("domain"));
        model.setModelIcon(modelJson.getString("icon"));

        return model;
    }
}
