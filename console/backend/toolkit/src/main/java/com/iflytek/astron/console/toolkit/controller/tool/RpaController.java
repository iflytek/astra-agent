package com.iflytek.astron.console.toolkit.controller.tool;

import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astron.console.toolkit.entity.table.tool.RpaInfo;
import com.iflytek.astron.console.toolkit.entity.table.tool.RpaUserAssistant;
import com.iflytek.astron.console.toolkit.entity.tool.CreateRpaAssistantReq;
import com.iflytek.astron.console.toolkit.entity.tool.RpaAssistantResp;
import com.iflytek.astron.console.toolkit.entity.tool.UpdateRpaAssistantReq;
import com.iflytek.astron.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astron.console.toolkit.service.tool.RpaAssistantService;
import com.iflytek.astron.console.toolkit.service.tool.RpaInfoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for RPA resources.
 * <p>
 * Provides endpoints to:
 * <ul>
 * <li>List available RPA platforms/sources</li>
 * <li>Manage user RPA assistants (create, query, update, delete)</li>
 * </ul>
 * No business logic is implemented here; all operations delegate to service layer.
 */
@RestController
@RequestMapping("/api/rpa")
@Slf4j
@ResponseResultBody
@Tag(name = "RPA management interface")
public class RpaController {

    @Autowired
    private RpaInfoService rpaInfoService;

    @Autowired
    private RpaAssistantService rpaAssistantService;

    /**
     * Get all available RPA platforms/sources.
     *
     * @return an {@link ApiResult} containing a list of {@link RpaInfo}
     * @throws org.springframework.dao.DataAccessException if a data access error occurs while querying
     */
    @GetMapping("/source/list")
    public ApiResult<List<RpaInfo>> list() {
        return ApiResult.success(rpaInfoService.list());
    }

    /**
     * Get current user's RPA assistant list by assistant name (fuzzy match or exact, depending on
     * service implementation).
     *
     * @param name assistant name filter (required)
     * @return an {@link ApiResult} containing a list of {@link RpaUserAssistant}
     * @throws org.springframework.dao.DataAccessException if a data access error occurs while querying
     */
    @GetMapping("/list")
    public ApiResult<List<RpaUserAssistant>> getList(@RequestParam String name) {
        return ApiResult.success(rpaAssistantService.getList(name));
    }

    /**
     * Create an RPA assistant with plaintext credentials.
     *
     * @param req creation request body; must pass bean validation
     * @return created assistant basic info
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if validation fails
     * @throws com.iflytek.astron.console.commons.exception.BusinessException for business-rule
     *         violations
     */
    @PostMapping
    public RpaAssistantResp create(@RequestBody @Validated CreateRpaAssistantReq req) {
        String userId = UserInfoManagerHandler.getUserId();
        return rpaAssistantService.create(userId, req);
    }

    /**
     * Get assistant details by id for the current user.
     *
     * @param id assistant primary key
     * @param name optional assistant name filter used by downstream service (may be null)
     * @return assistant detail info
     * @throws com.iflytek.astron.console.commons.exception.BusinessException if the assistant does not
     *         exist or no permission
     */
    @GetMapping("/{id}")
    public RpaAssistantResp detail(@PathVariable("id") Long id, @RequestParam(required = false) String name) {
        String userId = UserInfoManagerHandler.getUserId();
        return rpaAssistantService.detail(userId, id, name);
    }

    /**
     * Update assistant info for the given id.
     *
     * @param id assistant primary key
     * @param req update request body; must pass bean validation
     * @return updated {@link RpaUserAssistant}
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if validation fails
     * @throws com.iflytek.astron.console.commons.exception.BusinessException if the assistant does not
     *         exist or no permission
     */
    @PutMapping("/{id}")
    public RpaUserAssistant update(@PathVariable("id") Long id,
            @RequestBody @Validated UpdateRpaAssistantReq req) {
        String userId = UserInfoManagerHandler.getUserId();
        return rpaAssistantService.update(userId, id, req);
    }

    /**
     * Delete assistant by id for the current user.
     *
     * @param id assistant primary key
     * @throws com.iflytek.astron.console.commons.exception.BusinessException if the assistant does not
     *         exist or no permission
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        String userId = UserInfoManagerHandler.getUserId();
        rpaAssistantService.delete(userId, id);
    }
}
