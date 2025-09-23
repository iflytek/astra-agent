package com.iflytek.astra.console.toolkit.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.entity.bot.UserLangChainInfo;
import com.iflytek.astra.console.commons.entity.workflow.Workflow;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.mapper.UserLangChainInfoMapper;
import com.iflytek.astra.console.commons.service.bot.BotMarketDataService;
import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astra.console.toolkit.config.properties.BizConfig;
import com.iflytek.astra.console.toolkit.entity.table.bot.SparkBot;
import com.iflytek.astra.console.toolkit.entity.table.database.DbInfo;
import com.iflytek.astra.console.toolkit.entity.table.database.DbTable;
import com.iflytek.astra.console.toolkit.entity.table.eval.EvalDimension;
import com.iflytek.astra.console.toolkit.entity.table.eval.EvalScene;
import com.iflytek.astra.console.toolkit.entity.table.group.GroupVisibility;
import com.iflytek.astra.console.toolkit.entity.table.repo.FileInfoV2;
import com.iflytek.astra.console.toolkit.entity.table.repo.Repo;
import com.iflytek.astra.console.toolkit.entity.table.tool.ToolBox;
import com.iflytek.astra.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astra.console.toolkit.mapper.bot.SparkBotMapper;
import com.iflytek.astra.console.toolkit.mapper.database.DbInfoMapper;
import com.iflytek.astra.console.toolkit.mapper.database.DbTableMapper;
import com.iflytek.astra.console.toolkit.mapper.group.GroupVisibilityMapper;
import com.iflytek.astra.console.toolkit.mapper.repo.RepoMapper;
import com.iflytek.astra.console.toolkit.mapper.workflow.WorkflowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Data permission control tool.
 *
 * <p>
 * Conventions (consistent with original logic):
 * </p>
 * <ul>
 * <li>If current Space context exists ({@link SpaceInfoUtil#getSpaceId()} is not null), prioritize
 * Space-based validation;</li>
 * <li>Otherwise, validate by user dimension (resource owner uid must equal current uid);</li>
 * <li>Public resources (isPublic=true) are allowed; administrators
 * ({@link BizConfig#getAdminUid()}) have fallback permissions (preserved per original logic).</li>
 * </ul>
 *
 * <p>
 * Note: This class <strong>does not change</strong> any external method signatures or return
 * strategies, only enhances null checking, logging, and maintainability.
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataPermissionCheckTool {
    private final GroupVisibilityMapper groupVisibilityMapper;
    private final BizConfig bizConfig;
    private final RepoMapper repoMapper;
    private final SparkBotMapper sparkBotMapper;
    private final WorkflowMapper workflowMapper;
    private final DbInfoMapper dbInfoMapper;
    private final DbTableMapper dbTableMapper;
    private final UserLangChainInfoMapper userLangChainInfoDao;
    private final BotMarketDataService botMarketDataService;

    /**
     * Get the current thread's uid, throw business exception if empty.
     *
     * @return the current user ID
     * @throws BusinessException if no user ID found in thread local
     */
    public String getThreadLocalUidNoNull() {
        String uid = UserInfoManagerHandler.getUserId();
        if (uid == null) {
            throw new BusinessException(ResponseEnum.INVITE_NO_CORRESPONDING_USERS_FOUND);
        }
        return uid;
    }

    /**
     * Check if currently in space context.
     *
     * @return true if in space context, false otherwise
     */
    private boolean inSpace() {
        return SpaceInfoUtil.getSpaceId() != null;
    }

    /**
     * Get the current SpaceId (may be null).
     *
     * @return current space ID or null
     */
    private Long currentSpaceId() {
        return SpaceInfoUtil.getSpaceId();
    }

    /**
     * Check if the given uid is an admin.
     *
     * @param ownerUid the user ID to check
     * @return true if the user is an admin, false otherwise
     */
    private boolean isAdmin(String ownerUid) {
        return ownerUid != null && ownerUid.equals(bizConfig.getAdminUid());
    }

    /**
     * Throw access denied exception when resource is not visible (and print necessary context).
     *
     * @param action the action being performed
     * @param resource the resource being accessed
     * @throws BusinessException with EXCEED_AUTHORITY error
     */
    private void deny(String action, Object resource) {
        String uid = UserInfoManagerHandler.getUserId();
        log.warn("Permission check failed: action={}, uid={}, resource={}", action, uid, resource);
        throw new BusinessException(ResponseEnum.EXCEED_AUTHORITY);
    }

    // ===================== Repo / Tool / File =====================

    /**
     * Check repository ownership.
     *
     * @param repo the repository to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkRepoBelong(Repo repo) {
        if (repo == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        String uid = getThreadLocalUidNoNull();
        Long spaceId = currentSpaceId();

        boolean noPermission = spaceId != null
                ? !Objects.equals(repo.getSpaceId(), spaceId)
                : !Objects.equals(repo.getUserId(), uid.toString());

        if (noPermission)
            deny("checkRepoBelong", repo);
    }

    /**
     * Check repository visibility (supports space visibility/user visibility).
     *
     * @param repo the repository to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkRepoVisible(Repo repo) {
        if (repo == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        String uid = getThreadLocalUidNoNull();
        Long spaceId = currentSpaceId();

        boolean hasGroupVisibility;
        if (spaceId != null) {
            hasGroupVisibility = groupVisibilityMapper.selectOne(
                    Wrappers.lambdaQuery(GroupVisibility.class)
                            .eq(GroupVisibility::getType, 1)
                            .eq(GroupVisibility::getSpaceId, spaceId)
                            .eq(GroupVisibility::getRelationId, String.valueOf(repo.getId()))) != null;
            if (!hasGroupVisibility && !Objects.equals(repo.getSpaceId(), spaceId)) {
                deny("checkRepoVisible(space)", repo);
            }
        } else {
            hasGroupVisibility = groupVisibilityMapper.selectOne(
                    Wrappers.lambdaQuery(GroupVisibility.class)
                            .eq(GroupVisibility::getType, 1)
                            .eq(GroupVisibility::getUserId, uid)
                            .eq(GroupVisibility::getRelationId, String.valueOf(repo.getId()))) != null;
            if (!hasGroupVisibility && !Objects.equals(repo.getUserId(), uid.toString())) {
                deny("checkRepoVisible(user)", repo);
            }
        }
    }

    /**
     * Check tool ownership.
     *
     * @param toolBox the tool to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkToolBelong(ToolBox toolBox) {
        if (toolBox == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        String uid = getThreadLocalUidNoNull();
        Long spaceId = currentSpaceId();

        boolean noPermission = spaceId != null
                ? !(Objects.equals(toolBox.getSpaceId(), spaceId) && SpaceInfoUtil.checkUserBelongSpace())
                : !Objects.equals(toolBox.getUserId(), uid.toString());

        if (noPermission)
            deny("checkToolBelong", toolBox);
    }

    /**
     * Check file ownership.
     *
     * @param fileInfoV2 the file to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkFileBelong(FileInfoV2 fileInfoV2) {
        if (fileInfoV2 == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        String uid = getThreadLocalUidNoNull();
        Long spaceId = currentSpaceId();

        boolean noPermission = spaceId != null
                ? !Objects.equals(fileInfoV2.getSpaceId(), spaceId)
                : !Objects.equals(fileInfoV2.getUid(), uid.toString());

        if (noPermission)
            deny("checkFileBelong", fileInfoV2);
    }

    /**
     * Check tool visibility (public → allow; otherwise space/personal match or admin).
     *
     * @param toolBox the tool to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkToolVisible(ToolBox toolBox) {
        if (toolBox == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        String uid = getThreadLocalUidNoNull();
        Long spaceId = currentSpaceId();

        boolean noToolPermission = spaceId != null
                ? !(Objects.equals(toolBox.getSpaceId(), spaceId) && SpaceInfoUtil.checkUserBelongSpace())
                : !Objects.equals(toolBox.getUserId(), uid.toString());

        boolean noPermission = !Boolean.TRUE.equals(toolBox.getIsPublic())
                && noToolPermission
                && !Objects.equals(toolBox.getUserId(), String.valueOf(bizConfig.getAdminUid()));

        if (noPermission)
            deny("checkToolVisible", toolBox);
    }

    /**
     * Batch check file info list visibility (check each Repo's visibility individually).
     *
     * @param list the list of files to check
     * @throws BusinessException if any file access is denied
     */
    public void checkFileInfoListVisible(List<FileInfoV2> list) {
        if (CollectionUtils.isEmpty(list))
            return;
        for (FileInfoV2 f : list) {
            if (f == null)
                throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
            Repo repo = repoMapper.selectById(f.getRepoId());
            if (repo == null)
                throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
            checkRepoVisible(repo);
        }
    }

    // ===================== Bot / Workflow =====================

    /**
     * Check bot ownership.
     *
     * @param bot the bot to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkBotBelong(SparkBot bot) {
        if (bot == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        if (!Objects.equals(bot.getUserId(), getThreadLocalUidNoNull())) {
            deny("checkBotBelong", bot);
        }
    }

    /**
     * Check bot visibility (public / owner / admin).
     *
     * @param bot the bot to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkBotVisible(SparkBot bot) {
        if (bot == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        String uid = getThreadLocalUidNoNull();
        boolean noPermission = (bot.getIsPublic() == 0)
                && !Objects.equals(bot.getUserId(), uid)
                && !isAdmin(bot.getUserId());
        if (noPermission)
            deny("checkBotVisible", bot);
    }

    /**
     * Check workflow ownership (space first, then user; public allowed).
     *
     * @param workflow the workflow to check
     * @param spaceId the space ID context
     * @throws BusinessException if access denied or data not exists
     */
    public void checkWorkflowBelong(Workflow workflow, Long spaceId) {
        if (workflow == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        String uid = getThreadLocalUidNoNull();

        boolean noPermission = (spaceId == null)
                ? (!Boolean.TRUE.equals(workflow.getIsPublic())
                        && !Objects.equals(workflow.getUid(), uid)
                        && !isAdmin(workflow.getUid()))
                : (!Boolean.TRUE.equals(workflow.getIsPublic())
                        && !Objects.equals(workflow.getSpaceId(), spaceId));

        if (noPermission)
            deny("checkWorkflowBelong", workflow);
    }

    /**
     * Check workflow visibility (same strategy as ownership).
     *
     * @param workflow the workflow to check
     * @param spaceId the space ID context
     * @throws BusinessException if access denied or data not exists
     */
    public void checkWorkflowVisible(Workflow workflow, Long spaceId) {
        if (workflow == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        String uid = getThreadLocalUidNoNull();

        boolean noPermission = (spaceId == null)
                ? (!Boolean.TRUE.equals(workflow.getIsPublic())
                        && !Objects.equals(workflow.getUid(), uid)
                        && !isAdmin(workflow.getUid()))
                : (!Boolean.TRUE.equals(workflow.getIsPublic())
                        && !Objects.equals(workflow.getSpaceId(), spaceId));

        if (noPermission)
            deny("checkWorkflowVisible", workflow);
    }

    /**
     * Workflow detail visibility:
     * <ul>
     * <li>Allow public/owner/admin;</li>
     * <li>If bound to AIUI agent and listed on market (market flag=true), also allow;</li>
     * </ul>
     * <p>
     * Preserves your original "parse botId from ext and check if on market" logic skeleton, external
     * dependencies commented.
     * </p>
     *
     * @param workflow the workflow to check
     * @param spaceId the space ID context
     * @throws BusinessException if access denied or data not exists
     */
    public void checkWorkflowVisibleForDetail(Workflow workflow, Long spaceId) {
        if (workflow == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        String uid = getThreadLocalUidNoNull();

        // Public/owner/admin evaluation first
        boolean baseDenied = (spaceId == null)
                ? (!Boolean.TRUE.equals(workflow.getIsPublic())
                        && !Objects.equals(workflow.getUid(), uid)
                        && !isAdmin(workflow.getUid()))
                : (!Boolean.TRUE.equals(workflow.getIsPublic())
                        && !Objects.equals(workflow.getSpaceId(), spaceId));

        if (!baseDenied)
            return;

        // Try to parse botId from ext and check "whether on market"
        Integer botId = 0;
        String ext = workflow.getExt();
        if (StringUtils.isNotBlank(ext)) {
            try {
                JSONObject obj = JSON.parseObject(ext);
                botId = obj.getInteger("botId");
            } catch (Exception ignore) {
                // Ignore parsing exceptions, treat as unbound
            }
        } else {
            UserLangChainInfo userLangChainInfo = userLangChainInfoDao.selectOne(new LambdaQueryWrapper<UserLangChainInfo>().eq(UserLangChainInfo::getFlowId, workflow.getFlowId()));
            if (userLangChainInfo != null) {
                botId = userLangChainInfo.getBotId();
            }
        }

        boolean onMarket = false;
        if (botId != null && botId > 0) {
            List<Long> botIds = new ArrayList<>(Collections.singletonList(Integer.toUnsignedLong(botId)));
            onMarket = botMarketDataService.botsOnMarket(botIds);
        }

        if (!onMarket) {
            deny("checkWorkflowVisibleForDetail", workflow);
        }
    }

    // ===================== Optimization Task / Evaluation Dimension/Scenario / DB
    // =====================

    /**
     * Check evaluation scenario ownership.
     *
     * @param evalScene the evaluation scenario to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkEvalSceneBelong(EvalScene evalScene) {
        if (evalScene == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        Long spaceId = currentSpaceId();
        if (spaceId != null) {
            if (!Objects.equals(evalScene.getSpaceId(), spaceId) || !SpaceInfoUtil.checkUserBelongSpace()) {
                deny("checkEvalSceneBelong(space)", evalScene);
            }
        } else {
            if (!Objects.equals(evalScene.getUid(), getThreadLocalUidNoNull().toString())) {
                deny("checkEvalSceneBelong(user)", evalScene);
            }
        }
    }

    /**
     * Check evaluation dimension ownership.
     *
     * @param evalDimension the evaluation dimension to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkEvalDimensionBelong(EvalDimension evalDimension) {
        if (evalDimension == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        Long spaceId = currentSpaceId();
        boolean isPublic = Boolean.TRUE.equals(evalDimension.getIsPublic());
        if (spaceId != null) {
            if ((!Objects.equals(evalDimension.getSpaceId(), spaceId) || !SpaceInfoUtil.checkUserBelongSpace()) && !isPublic) {
                deny("checkEvalDimensionBelong(space)", evalDimension);
            }
        } else {
            if ((!Objects.equals(evalDimension.getUid(), getThreadLocalUidNoNull().toString())) && !isPublic) {
                deny("checkEvalDimensionBelong(user)", evalDimension);
            }
        }
    }

    /**
     * Check database ownership.
     *
     * @param dbId the database ID to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkDbBelong(Long dbId) {
        DbInfo dbInfo = dbInfoMapper.selectById(dbId);
        if (dbInfo == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);

        Long spaceId = currentSpaceId();
        boolean noPermission = spaceId == null
                ? !Objects.equals(dbInfo.getUid(), getThreadLocalUidNoNull().toString())
                : (!Objects.equals(dbInfo.getSpaceId(), spaceId) || !SpaceInfoUtil.checkUserBelongSpace());

        if (noPermission)
            throw new BusinessException(ResponseEnum.EXCEED_AUTHORITY);
    }

    /**
     * Check database update ownership.
     *
     * @param dbId the database ID to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkDbUpdateBelong(Long dbId) {
        DbInfo dbInfo = dbInfoMapper.selectById(dbId);
        if (dbInfo == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);
        if (!Objects.equals(dbInfo.getUid(), getThreadLocalUidNoNull().toString())) {
            throw new BusinessException(ResponseEnum.EXCEED_AUTHORITY);
        }
    }

    /**
     * Check table ownership.
     *
     * @param tbId the table ID to check
     * @throws BusinessException if access denied or data not exists
     */
    public void checkTbBelong(Long tbId) {
        DbTable dbTable = dbTableMapper.selectById(tbId);
        if (dbTable == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);

        DbInfo dbInfo = dbInfoMapper.selectById(dbTable.getDbId());
        if (dbInfo == null)
            throw new BusinessException(ResponseEnum.DATA_NOT_EXIST);

        Long spaceId = currentSpaceId();
        boolean noPermission = spaceId == null
                ? !Objects.equals(dbInfo.getUid(), getThreadLocalUidNoNull().toString())
                : (!Objects.equals(dbInfo.getSpaceId(), spaceId) || !SpaceInfoUtil.checkUserBelongSpace());

        if (noPermission)
            throw new BusinessException(ResponseEnum.EXCEED_AUTHORITY);
    }
}
