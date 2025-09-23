package com.iflytek.astron.console.hub.service.space.impl;

import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.data.UserInfoDataService;
import com.iflytek.astron.console.commons.entity.user.UserInfo;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.service.user.MessageCodeService;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.commons.dto.space.SpaceAddDTO;
import com.iflytek.astron.console.commons.dto.space.SpaceUpdateDTO;
import com.iflytek.astron.console.commons.entity.space.Enterprise;
import com.iflytek.astron.console.commons.entity.space.EnterpriseUser;
import com.iflytek.astron.console.commons.entity.space.Space;
import com.iflytek.astron.console.commons.entity.space.SpaceUser;
import com.iflytek.astron.console.commons.enums.space.EnterpriseRoleEnum;
import com.iflytek.astron.console.commons.enums.space.EnterpriseServiceTypeEnum;
import com.iflytek.astron.console.commons.enums.space.SpaceRoleEnum;
import com.iflytek.astron.console.commons.enums.space.SpaceTypeEnum;
import com.iflytek.astron.console.commons.service.space.EnterpriseService;
import com.iflytek.astron.console.commons.service.space.EnterpriseUserService;
import com.iflytek.astron.console.commons.service.space.SpaceService;
import com.iflytek.astron.console.commons.service.space.SpaceUserService;
import com.iflytek.astron.console.commons.service.bot.ChatBotDataService;
import com.iflytek.astron.console.hub.properties.SpaceLimitProperties;
import com.iflytek.astron.console.hub.service.space.SpaceBizService;
import com.iflytek.astron.console.commons.util.space.OrderInfoUtil;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Slf4j
public class SpaceBizServiceImpl implements SpaceBizService {
    @Autowired
    private SpaceUserService spaceUserService;
    @Autowired(required = false)
    private MessageCodeService messageCodeService;
    @Autowired
    private EnterpriseUserService enterpriseUserService;
    @Autowired
    private EnterpriseService enterpriseService;
    @Autowired
    private SpaceLimitProperties spaceLimitProperties;
    @Autowired
    private SpaceService spaceService;
    @Autowired
    private ChatBotDataService chatBotDataService;
    @Autowired
    private UserInfoDataService userInfoDataService;


    /**
     * Create space
     *
     * @param spaceAddDTO
     * @param enterpriseId
     * @return
     */
    @Override
    @Transactional
    public ApiResult<Long> create(SpaceAddDTO spaceAddDTO, Long enterpriseId) {
        if (spaceService.checkExistByName(spaceAddDTO.getName(), null)) {
            return ApiResult.error(ResponseEnum.SPACE_NAME_EXISTS);
        }
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddDTO, space);
        // Set creator UID
        String uid = RequestContextUtil.getUID();
        space.setUid(uid);
        // Set enterprise ID
        if (enterpriseId != null) {
            // Enterprise space limit check
            Enterprise enterprise = enterpriseService.getEnterpriseById(enterpriseId);
            space.setEnterpriseId(enterpriseId);
            space.setType(Objects.equals(enterprise.getServiceType(), EnterpriseServiceTypeEnum.ENTERPRISE.getCode())
                    ? SpaceTypeEnum.ENTERPRISE.getCode()
                    : SpaceTypeEnum.TEAM.getCode());
            Long count = spaceService.countByEnterpriseId(enterpriseId);
            Integer spaceCount = 0;
            if (Objects.equals(enterprise.getServiceType(), EnterpriseServiceTypeEnum.ENTERPRISE.getCode())) {
                spaceCount = spaceLimitProperties.getEnterprise().getSpaceCount();
            } else if (Objects.equals(enterprise.getServiceType(), EnterpriseServiceTypeEnum.TEAM.getCode())) {
                spaceCount = spaceLimitProperties.getTeam().getSpaceCount();
            }
            if (count >= spaceCount) {
                return ApiResult.error(ResponseEnum.SPACE_ENTERPRISE_TEAM_MAX_EXCEEDED);
            }
        } else {
            // Personal space limit check
            Long count = spaceService.countByUid(uid);
            if (OrderInfoUtil.existValidProOrder(uid)) {
                space.setType(SpaceTypeEnum.PRO.getCode());
                if (count >= spaceLimitProperties.getPro().getSpaceCount()) {
                    return ApiResult.error(ResponseEnum.SPACE_PERSONAL_PRO_MAX_EXCEEDED);
                }
            } else {
                space.setType(SpaceTypeEnum.FREE.getCode());
                if (count >= spaceLimitProperties.getFree().getSpaceCount()) {
                    return ApiResult.error(ResponseEnum.SPACE_FREE_USER_MAX_EXCEEDED);
                }

            }
        }
        // Save space data
        if (spaceService.save(space)) {
            // Creator becomes space owner by default
            if (!spaceUserService.addSpaceUser(space.getId(), space.getUid(), SpaceRoleEnum.OWNER)) {
                throw new BusinessException(ResponseEnum.INVITE_ADD_SPACE_USER_FAILED);
            }
            return ApiResult.success(space.getId());
        } else {
            return ApiResult.error(ResponseEnum.ENTERPRISE_CREATE_FAILED);
        }
    }

    /**
     * Delete space
     *
     * @param spaceId
     * @param mobile
     * @param verifyCode
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> deleteSpace(Long spaceId, String mobile, String verifyCode) {
        // Send verification code
        if (messageCodeService != null && StringUtils.isNotBlank(mobile)) {
            messageCodeService.checkVerifyCodeCommon(mobile, verifyCode,
                    MessageCodeService.DEL_SPACE_VERIFY_CODE_PREFIX);
        } else {
            log.warn("messageCodeService not configured, or mobile number not provided, skipping verification code check");
        }
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            return ApiResult.error(ResponseEnum.SPACE_NOT_EXISTS);
        }
        if (spaceService.removeById(spaceId)) {
            try {
                String uid = RequestContextUtil.getUID();
                HttpServletRequest request = RequestContextUtil.getCurrentRequest();
                log.debug("Deleting space related assistants, space ID: {}, uid: {}", spaceId, uid);
                chatBotDataService.deleteBotForDeleteSpace(uid, spaceId, request);
            } catch (Exception e) {
                log.error("Failed to delete space related assistants, space ID: {}", spaceId, e);
            }
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.SPACE_DELETE_FAILED);
        }
    }

    /**
     * Update space
     *
     * @param spaceUpdateDTO Name, description, avatar
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> updateSpace(SpaceUpdateDTO spaceUpdateDTO) {
        if (!Objects.equals(SpaceInfoUtil.getSpaceId(), spaceUpdateDTO.getId())) {
            return ApiResult.error(ResponseEnum.SPACE_APPLICATION_CURRENT_SPACE_INCONSISTENT);
        }
        Space space = spaceService.getById(spaceUpdateDTO.getId());
        if (spaceService.checkExistByName(spaceUpdateDTO.getName(), spaceUpdateDTO.getId())) {
            return ApiResult.error(ResponseEnum.SPACE_NAME_DUPLICATE);
        }
        space.setName(spaceUpdateDTO.getName());
        space.setDescription(spaceUpdateDTO.getDescription());
        space.setAvatarUrl(spaceUpdateDTO.getAvatarUrl());
        if (spaceService.updateById(space)) {
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.ENTERPRISE_UPDATE_FAILED);
        }
    }

    /**
     * Visit space, called when user switches space
     *
     * @param spaceId
     * @return
     */
    @Override
    @Transactional
    public ApiResult<Space> visitSpace(Long spaceId) {
        if (spaceId == null || spaceId <= 0L) {
            enterpriseService.setLastVisitEnterpriseId(null);
            spaceService.setLastVisitPersonalSpaceTime();
            return ApiResult.success();
        }
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            return ApiResult.error(ResponseEnum.SPACE_NOT_EXISTS);
        }
        String uid = RequestContextUtil.getUID();
        SpaceUser spaceUser = spaceUserService.getSpaceUserByUid(spaceId, uid);
        if (spaceUser == null) {
            return ApiResult.error(ResponseEnum.SPACE_USER_NOT_IN_SPACE);
        }
        if (spaceUserService.updateVisitTime(spaceId, spaceUser.getUid())) {
            if (space.getEnterpriseId() != null) {
                enterpriseService.setLastVisitEnterpriseId(space.getEnterpriseId());
            }
            return ApiResult.success(space);
        } else {
            return ApiResult.error(ResponseEnum.ENTERPRISE_UPDATE_FAILED);
        }
    }


    /**
     * Send space deletion verification code
     *
     * @param spaceId
     * @return
     */
    @Override
    public ApiResult<String> sendMessageCode(Long spaceId) {
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            return ApiResult.error(ResponseEnum.SPACE_NOT_EXISTS);
        }
        String uid = RequestContextUtil.getUID();
        SpaceUser spaceUser = spaceUserService.getSpaceUserByUid(spaceId, uid);
        if (spaceUser == null) {
            return ApiResult.error(ResponseEnum.SPACE_USER_NOT_IN_SPACE);
        }
        if (space.getEnterpriseId() == null && !Objects.equals(spaceUser.getRole(), SpaceRoleEnum.OWNER.getCode())) {
            return ApiResult.error(ResponseEnum.SPACE_USER_NOT_OWNER);
        }
        if (space.getEnterpriseId() != null) {
            EnterpriseUser enterpriseUser = enterpriseUserService.getEnterpriseUserByUid(space.getEnterpriseId(), uid);
            if (enterpriseUser == null) {
                return ApiResult.error(ResponseEnum.SPACE_USER_NOT_ENTERPRISE_USER);
            }
            if (!(Objects.equals(enterpriseUser.getRole(), EnterpriseRoleEnum.OFFICER.getCode()) ||
                    Objects.equals(enterpriseUser.getRole(), EnterpriseRoleEnum.GOVERNOR.getCode()))) {
                return ApiResult.error(ResponseEnum.SPACE_USER_NOT_ENTERPRISE_ADMIN);
            }
        }
        // Get user mobile number and send SMS
        if (messageCodeService != null) {
            UserInfo userInfo = userInfoDataService.findByUid(uid).orElseThrow();
            if (StringUtils.isNotBlank(userInfo.getMobile())) {
                messageCodeService.sendVerifyCodeCommon(userInfo.getMobile(),
                        MessageCodeService.DEL_SPACE_VERIFY_CODE_PREFIX);
            } else {
                log.warn("User has not bound mobile number, cannot send verification code, uid: {}", uid);
            }
        } else {
            log.warn("messageCodeService not configured, skipping verification code check");
        }

        return ApiResult.success();
    }
}
