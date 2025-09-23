package com.iflytek.astra.console.toolkit.controller.knowledge;


import com.iflytek.astra.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astra.console.toolkit.entity.mongo.Knowledge;
import com.iflytek.astra.console.toolkit.entity.vo.repo.KnowledgeVO;
import com.iflytek.astra.console.toolkit.service.repo.KnowledgeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

/**
 * Knowledge Controller
 *
 * This controller handles HTTP requests related to knowledge management operations including
 * creating, updating, enabling/disabling, and deleting knowledge entries.
 *
 * @author Astra Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/knowledge")
@Slf4j
@ResponseResultBody
public class KnowledgeController {
    @Resource
    private KnowledgeService knowledgeService;

    /**
     * Create knowledge
     *
     * @param knowledgeVO knowledge creation request object containing knowledge details
     * @return ApiResult<Knowledge> containing the created knowledge information
     * @throws ExecutionException if the computation threw an exception
     * @throws InterruptedException if the thread is interrupted
     */
    @PostMapping("/create-knowledge")
    @SpacePreAuth(key = "KnowledgeController_createKnowledge_POST",
            module = "Knowledge", point = "Create Knowledge", description = "Create Knowledge")
    public ApiResult<Knowledge> createKnowledge(@RequestBody KnowledgeVO knowledgeVO) throws ExecutionException, InterruptedException {
        return ApiResult.success(knowledgeService.createKnowledge(knowledgeVO));
    }

    /**
     * Update knowledge
     *
     * @param knowledgeVO knowledge update request object containing updated knowledge details
     * @return ApiResult<Knowledge> containing the updated knowledge information
     * @throws ExecutionException if the computation threw an exception
     * @throws InterruptedException if the thread is interrupted
     * @throws BusinessException if tag length exceeds 30 characters
     */
    @PostMapping("/update-knowledge")
    @SpacePreAuth(key = "KnowledgeController_updateKnowledge_POST",
            module = "Knowledge", point = "Update Knowledge", description = "Update Knowledge")
    public ApiResult<Knowledge> updateKnowledge(@RequestBody KnowledgeVO knowledgeVO) throws ExecutionException, InterruptedException {
        if (CollectionUtils.isNotEmpty(knowledgeVO.getTags())) {
            for (String tag : knowledgeVO.getTags()) {
                if (tag.length() > 30) {
                    throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_TAG_TOO_LONG);
                }
            }
        }
        return ApiResult.success(knowledgeService.updateKnowledge(knowledgeVO));
    }

    /**
     * Enable or disable knowledge
     *
     * @param id knowledge ID to be enabled or disabled
     * @param enabled status flag: 1 to enable, 0 to disable
     * @return ApiResult<String> containing operation result message
     * @throws ExecutionException if the computation threw an exception
     * @throws InterruptedException if the thread is interrupted
     */
    @PutMapping("/enable-knowledge")
    @SpacePreAuth(key = "KnowledgeController_enableKnowledge_PUT",
            module = "Knowledge", point = "Enable Knowledge", description = "Enable Knowledge")
    public ApiResult<String> enableKnowledge(@RequestParam("id") String id, @RequestParam("enabled") Integer enabled) throws ExecutionException, InterruptedException {
        return ApiResult.success(knowledgeService.enableKnowledge(id, enabled));
    }

    /**
     * Delete knowledge
     *
     * @param id knowledge ID to be deleted
     * @return ApiResult<Void> indicating successful deletion
     */
    @DeleteMapping("/delete-knowledge")
    @SpacePreAuth(key = "KnowledgeController_deleteKnowledge_DELETE",
            module = "Knowledge", point = "Delete Knowledge", description = "Delete Knowledge")
    public ApiResult<Void> deleteKnowledge(@RequestParam("id") String id) {
        knowledgeService.deleteKnowledge(id);
        return ApiResult.success();
    }


}
