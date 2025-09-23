package com.iflytek.astron.console.hub.service.space.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.data.UserInfoDataService;
import com.iflytek.astron.console.commons.entity.space.EnterpriseUser;
import com.iflytek.astron.console.commons.entity.space.Enterprise;
import com.iflytek.astron.console.commons.entity.space.InviteRecord;
import com.iflytek.astron.console.commons.entity.space.Space;
import com.iflytek.astron.console.commons.entity.space.SpaceUser;
import com.iflytek.astron.console.commons.entity.user.UserInfo;
import com.iflytek.astron.console.commons.enums.space.*;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.service.space.*;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.commons.util.S3ClientUtil;
import com.iflytek.astron.console.commons.dto.space.InviteRecordAddDTO;
import com.iflytek.astron.console.hub.dto.notification.SendNotificationRequest;
import com.iflytek.astron.console.hub.dto.user.UserInfoExcelDTO;
import com.iflytek.astron.console.hub.dto.user.UserInfoResultExcelDTO;
import com.iflytek.astron.console.hub.enums.*;
import com.iflytek.astron.console.hub.properties.InviteMessageTempProperties;
import com.iflytek.astron.console.hub.properties.SpaceLimitProperties;
import com.iflytek.astron.console.hub.service.notification.NotificationService;
import com.iflytek.astron.console.hub.service.space.EnterpriseUserBizService;
import com.iflytek.astron.console.hub.service.space.InviteRecordBizService;
import com.iflytek.astron.console.hub.util.AESUtil;
import com.iflytek.astron.console.hub.util.NameUtil;
import com.iflytek.astron.console.commons.util.space.EnterpriseInfoUtil;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.commons.dto.space.BatchChatUserVO;
import com.iflytek.astron.console.commons.dto.space.ChatUserVO;
import com.iflytek.astron.console.commons.dto.space.InviteRecordVO;
import com.iflytek.astron.console.commons.dto.space.UserLimitVO;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InviteRecordBizServiceImpl implements InviteRecordBizService {
    private static final String AES_KEY = "32c8aa9a342a3e130d24e86294709b02";
    private static final int MAX_EXPIRE_TIME = 7;
    @Autowired
    private SpaceUserService spaceUserService;
    @Autowired
    private EnterpriseUserService enterpriseUserService;
    @Autowired
    private SpaceService spaceService;
    @Autowired
    private EnterpriseService enterpriseService;
    @Resource
    private InviteMessageTempProperties tempProperties;
    @Autowired
    private SpaceLimitProperties spaceLimitProperties;
    @Autowired
    private InviteRecordService inviteRecordService;
    @Autowired
    private EnterpriseUserBizService enterpriseUserBizService;
    @Autowired
    private S3ClientUtil s3ClientUtil;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private UserInfoDataService userInfoDataService;

    /**
     * Space invitation
     *
     * @param dtos
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> spaceInvite(List<InviteRecordAddDTO> dtos) {
        List<String> uids = dtos.stream().map(InviteRecordAddDTO::getUid).collect(Collectors.toList());
        Long spaceId = SpaceInfoUtil.getSpaceId();
        Space space = spaceService.getSpaceById(spaceId);
        // Check if space capacity is full, including users being invited
        if (Objects.equals(space.getType(), SpaceTypeEnum.FREE.getCode())) {
            if ((spaceUserService.countFreeSpaceUser(space.getUid())
                    + inviteRecordService.countJoiningByUid(space.getUid(), SpaceTypeEnum.FREE) + dtos.size()) > spaceLimitProperties.getFree().getUserCount()) {
                return ApiResult.error(ResponseEnum.INVITE_SPACE_USER_FULL);
            }
        } else if (Objects.equals(space.getType(), SpaceTypeEnum.PRO.getCode())) {
            if ((spaceUserService.countProSpaceUser(space.getUid())
                    + inviteRecordService.countJoiningByUid(space.getUid(), SpaceTypeEnum.PRO) + dtos.size()) > spaceLimitProperties.getPro().getUserCount()) {
                return ApiResult.error(ResponseEnum.INVITE_SPACE_USER_FULL);
            }
        } else if (Objects.equals(space.getType(), SpaceTypeEnum.TEAM.getCode())) {
            if ((enterpriseUserService.countByEnterpriseId(space.getEnterpriseId())
                    + inviteRecordService.countJoiningByEnterpriseId(space.getEnterpriseId()) + dtos.size()) > spaceLimitProperties.getTeam().getUserCount()) {
                return ApiResult.error(ResponseEnum.INVITE_TEAM_USER_FULL);
            }
        } else if (Objects.equals(space.getType(), SpaceTypeEnum.ENTERPRISE.getCode())) {
            if ((enterpriseUserService.countByEnterpriseId(space.getEnterpriseId())
                    + inviteRecordService.countJoiningByEnterpriseId(space.getEnterpriseId()) + dtos.size()) > spaceLimitProperties.getEnterprise().getUserCount()) {
                return ApiResult.error(ResponseEnum.INVITE_ENTERPRISE_USER_FULL);
            }
        }
        // Check if already a space user
        Long count = spaceUserService.countSpaceUserByUids(spaceId, uids);
        if (count > 0) {
            return ApiResult.error(ResponseEnum.INVITE_USER_ALREADY_SPACE_MEMBER);
        }
        // Check if invitation already sent
        if (inviteRecordService.countBySpaceIdAndUids(spaceId, uids) > 0) {
            return ApiResult.error(ResponseEnum.INVITE_USER_ALREADY_INVITED);
        }
        List<InviteRecord> inviteRecords = new ArrayList<>();
        String uid = RequestContextUtil.getUID();
        for (InviteRecordAddDTO dto : dtos) {
            InviteRecord inviteRecord = new InviteRecord();
            inviteRecord.setType(InviteRecordTypeEnum.SPACE.getCode());
            inviteRecord.setSpaceId(spaceId);
            inviteRecord.setEnterpriseId(space.getEnterpriseId());
            inviteRecord.setInviteeUid(dto.getUid());
            inviteRecord.setRole(dto.getRole());
            UserInfo userInfo = userInfoDataService.findByUid(dto.getUid()).orElseThrow();
            inviteRecord.setInviteeNickname(userInfo.getNickname());
            inviteRecord.setInviterUid(uid);
            inviteRecord.setStatus(InviteRecordStatusEnum.INIT.getCode());
            inviteRecord.setExpireTime(LocalDateTime.now().plusDays(MAX_EXPIRE_TIME));
            inviteRecords.add(inviteRecord);
        }
        // Batch save invitation records
        if (inviteRecordService.saveBatch(inviteRecords)) {
            // Message notification
            UserInfo userInfo = userInfoDataService.findByUid(uid).orElseThrow();
            for (InviteRecord record : inviteRecords) {
                SendNotificationRequest request = new SendNotificationRequest();
                request.setType(NotificationType.SYSTEM);
                request.setReceiverUids(List.of(record.getInviteeUid()));
                request.setTitle(tempProperties.getSpaceTitle());
                String outLink = tempProperties.getUrl() + AESUtil.encrypt(record.getId().toString(), AES_KEY);
                request.setBody(String.format(tempProperties.getSpaceContent(), userInfo.getNickname(), space.getName(), outLink));
                request.setPayload(JSONObject.of("outlink", outLink).toString());
                notificationService.sendNotification(request);
            }
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.INVITE_FAILED);
        }
    }

    /**
     * Enterprise invitation
     *
     * @param dtos
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> enterpriseInvite(List<InviteRecordAddDTO> dtos) {
        List<String> uids = dtos.stream().map(InviteRecordAddDTO::getUid).collect(Collectors.toList());
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        Enterprise enterprise = enterpriseService.getEnterpriseById(enterpriseId);
        Integer userCount = 0;
        if (Objects.equals(enterprise.getServiceType(), EnterpriseServiceTypeEnum.ENTERPRISE.getCode())) {
            userCount = spaceLimitProperties.getEnterprise().getUserCount();
        } else if (Objects.equals(enterprise.getServiceType(), EnterpriseServiceTypeEnum.TEAM.getCode())) {
            userCount = spaceLimitProperties.getTeam().getUserCount();
        }
        Long count = enterpriseUserService.countByEnterpriseIdAndUids(enterpriseId, uids);

        // Check if enterprise member count is full, including users being invited
        if ((enterpriseUserService.countByEnterpriseId(enterpriseId)
                + inviteRecordService.countJoiningByEnterpriseId(enterpriseId) + dtos.size()) > userCount) {
            return ApiResult.error(ResponseEnum.INVITE_ENTERPRISE_USER_FULL);
        }
        // Check if already an enterprise user
        if (count > 0) {
            return ApiResult.error(ResponseEnum.INVITE_USER_ALREADY_TEAM_MEMBER);
        }
        // Check if invitation already sent
        if (inviteRecordService.countByEnterpriseIdAndUids(enterpriseId, uids) > 0) {
            return ApiResult.error(ResponseEnum.INVITE_USER_ALREADY_INVITED);
        }
        List<InviteRecord> inviteRecords = new ArrayList<>();
        String uid = RequestContextUtil.getUID();
        for (InviteRecordAddDTO dto : dtos) {
            InviteRecord inviteRecord = new InviteRecord();
            inviteRecord.setType(InviteRecordTypeEnum.ENTERPRISE.getCode());
            inviteRecord.setEnterpriseId(enterpriseId);
            inviteRecord.setInviteeUid(dto.getUid());
            inviteRecord.setRole(dto.getRole());
            UserInfo userInfo = userInfoDataService.findByUid(dto.getUid()).orElseThrow();
            inviteRecord.setInviteeNickname(userInfo.getNickname());
            inviteRecord.setInviterUid(uid);
            inviteRecord.setStatus(InviteRecordStatusEnum.INIT.getCode());
            inviteRecord.setExpireTime(LocalDateTime.now().plusDays(MAX_EXPIRE_TIME));
            inviteRecords.add(inviteRecord);
        }
        // Batch save invitation records
        if (inviteRecordService.saveBatch(inviteRecords)) {
            // Message notification
            UserInfo userInfo = userInfoDataService.findByUid(uid).orElseThrow();
            for (InviteRecord record : inviteRecords) {
                SendNotificationRequest request = new SendNotificationRequest();
                request.setType(NotificationType.SYSTEM);
                request.setReceiverUids(List.of(record.getInviteeUid()));
                request.setTitle(tempProperties.getEnterpriseTitle());
                String outLink = tempProperties.getUrl() + AESUtil.encrypt(record.getId().toString(), AES_KEY);
                request.setBody(String.format(tempProperties.getEnterpriseContent(), userInfo.getNickname(), enterprise.getName(), outLink));
                request.setPayload(JSONObject.of("outlink", outLink).toString());
                notificationService.sendNotification(request);
            }
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.INVITE_FAILED);
        }
    }

    /**
     * Accept invitation
     *
     * @param inviteId
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> acceptInvite(Long inviteId) {
        InviteRecord inviteRecord = inviteRecordService.getById(inviteId);
        ApiResult<String> responseMsg = checkInviteRecord(inviteRecord);
        if (responseMsg != null) {
            return responseMsg;
        }
        // Update invitation record
        inviteRecord.setStatus(InviteRecordStatusEnum.ACCEPT.getCode());
        if (!inviteRecordService.updateById(inviteRecord)) {
            return ApiResult.error(ResponseEnum.OPERATION_FAILED);
        }
        // For enterprise invitation, add enterprise user
        if (InviteRecordTypeEnum.ENTERPRISE.getCode().equals(inviteRecord.getType())) {
            if (!enterpriseUserService.addEnterpriseUser(inviteRecord.getEnterpriseId(), inviteRecord.getInviteeUid(),
                    Objects.equals(InviteRecordRoleEnum.ADMIN.getCode(), inviteRecord.getRole()) ? EnterpriseRoleEnum.GOVERNOR : EnterpriseRoleEnum.STAFF)) {
                throw new BusinessException(ResponseEnum.INVITE_ADD_TEAM_USER_FAILED);
            }
            // Add space user
        } else if (InviteRecordTypeEnum.SPACE.getCode().equals(inviteRecord.getType())) {
            if (!spaceUserService.addSpaceUser(inviteRecord.getSpaceId(), inviteRecord.getInviteeUid(),
                    Objects.equals(InviteRecordRoleEnum.ADMIN.getCode(), inviteRecord.getRole()) ? SpaceRoleEnum.ADMIN : SpaceRoleEnum.MEMBER)) {
                throw new BusinessException(ResponseEnum.SPACE_USER_ADD_FAILED);
            }
            Space space = spaceService.getSpaceById(inviteRecord.getSpaceId());
            // For enterprise space invitation, if not joined team, add user
            if (space.getEnterpriseId() != null) {
                if (!enterpriseUserService.addEnterpriseUser(space.getEnterpriseId(), inviteRecord.getInviteeUid(),
                        Objects.equals(InviteRecordRoleEnum.ADMIN.getCode(), inviteRecord.getRole()) ? EnterpriseRoleEnum.GOVERNOR : EnterpriseRoleEnum.STAFF)) {
                    throw new BusinessException(ResponseEnum.INVITE_ADD_TEAM_USER_FAILED);
                }
            }
        } else {
            throw new BusinessException(ResponseEnum.INVITE_UNSUPPORTED_TYPE);
        }
        return ApiResult.success();
    }

    /**
     * Decline invitation
     *
     * @param inviteId
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> refuseInvite(Long inviteId) {
        InviteRecord inviteRecord = inviteRecordService.getById(inviteId);
        ApiResult<String> responseMsg = checkInviteRecord(inviteRecord);
        if (responseMsg != null) {
            return responseMsg;
        }
        inviteRecord.setStatus(InviteRecordStatusEnum.REFUSE.getCode());
        if (inviteRecordService.updateById(inviteRecord)) {
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.OPERATION_FAILED);
        }
    }

    private ApiResult<String> checkInviteRecord(InviteRecord inviteRecord) {
        if (inviteRecord == null) {
            return ApiResult.error(ResponseEnum.INVITE_RECORD_NOT_FOUND);
        }
        if (!Objects.equals(inviteRecord.getInviteeUid(), RequestContextUtil.getUID())) {
            return ApiResult.error(ResponseEnum.INVITE_CURRENT_USER_NOT_INVITEE);
        }
        if (Objects.equals(inviteRecord.getStatus(), InviteRecordStatusEnum.REFUSE.getCode())) {
            return ApiResult.error(ResponseEnum.INVITE_ALREADY_REFUSED);
        }
        if (Objects.equals(inviteRecord.getStatus(), InviteRecordStatusEnum.ACCEPT.getCode())) {
            return ApiResult.error(ResponseEnum.INVITE_ALREADY_ACCEPTED);
        }
        if (Objects.equals(inviteRecord.getStatus(), InviteRecordStatusEnum.WITHDRAW.getCode())) {
            return ApiResult.error(ResponseEnum.INVITE_ALREADY_WITHDRAWN);
        }
        if (Objects.equals(inviteRecord.getStatus(), InviteRecordStatusEnum.EXPIRED.getCode())) {
            return ApiResult.error(ResponseEnum.INVITE_ALREADY_EXPIRED);
        }
        if (inviteRecord.getExpireTime().isBefore(LocalDateTime.now())) {
            return ApiResult.error(ResponseEnum.INVITE_ALREADY_EXPIRED);
        }
        return null;
    }

    /**
     * Revoke enterprise invitation
     *
     * @param inviteId
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> revokeEnterpriseInvite(Long inviteId) {
        InviteRecord inviteRecord = inviteRecordService.getById(inviteId);
        if (inviteRecord == null) {
            return ApiResult.error(ResponseEnum.INVITE_RECORD_NOT_FOUND);
        }
        if (!Objects.equals(inviteRecord.getEnterpriseId(), EnterpriseInfoUtil.getEnterpriseId())) {
            return ApiResult.error(ResponseEnum.INVITE_ENTERPRISE_INCONSISTENT);
        }
        if (!Objects.equals(inviteRecord.getStatus(), InviteRecordStatusEnum.INIT.getCode())) {
            return ApiResult.error(ResponseEnum.INVITE_STATUS_NOT_SUPPORTED);
        }
        inviteRecord.setStatus(InviteRecordStatusEnum.WITHDRAW.getCode());
        if (inviteRecordService.updateById(inviteRecord)) {
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.OPERATION_FAILED);
        }
    }

    /**
     * Revoke space invitation
     *
     * @param inviteId
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> revokeSpaceInvite(Long inviteId) {
        InviteRecord inviteRecord = inviteRecordService.getById(inviteId);
        if (inviteRecord == null) {
            return ApiResult.error(ResponseEnum.INVITE_RECORD_NOT_FOUND);
        }
        if (!Objects.equals(inviteRecord.getSpaceId(), SpaceInfoUtil.getSpaceId())) {
            return ApiResult.error(ResponseEnum.SPACE_APPLICATION_CURRENT_SPACE_INCONSISTENT);
        }
        if (!Objects.equals(inviteRecord.getStatus(), InviteRecordStatusEnum.INIT.getCode())) {
            return ApiResult.error(ResponseEnum.INVITE_STATUS_NOT_SUPPORTED);
        }
        inviteRecord.setStatus(InviteRecordStatusEnum.WITHDRAW.getCode());
        if (inviteRecordService.updateById(inviteRecord)) {
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.OPERATION_FAILED);
        }
    }


    /**
     * Get invitation record by parameter
     *
     * @param param Invitation record ID AES encrypted
     * @return
     */
    @Override
    public InviteRecordVO getRecordByParam(String param) {
        long id = 0;
        try {
            String decrypt = AESUtil.decrypt(param, AES_KEY);
            assert decrypt != null;
            id = Long.parseLong(decrypt);
        } catch (Exception e) {
            log.error("Failed to parse invitation parameters", e);
            throw new BusinessException(ResponseEnum.INVITE_PARAMETER_EXCEPTION);
        }
        InviteRecordVO vo = inviteRecordService.selectVOById(id);
        if (vo == null) {
            throw new BusinessException(ResponseEnum.INVITE_RECORD_NOT_FOUND);
        }
        UserInfo inviterUser = userInfoDataService.findByUid(vo.getInviterUid()).orElseThrow();
        vo.setInviterName(inviterUser.getNickname());
        vo.setInviterAvatar(inviterUser.getAvatar());
        if (Objects.equals(InviteRecordTypeEnum.SPACE.getCode(), vo.getType())) {
            SpaceUser spaceOwner = spaceUserService.getSpaceOwner(vo.getSpaceId());
            if (spaceOwner != null) {
                UserInfo ownerUser = userInfoDataService.findByUid(spaceOwner.getUid()).orElseThrow();
                vo.setOwnerName(ownerUser.getNickname());
                vo.setOwnerAvatar(ownerUser.getAvatar());
            }
            Space space = spaceService.getSpaceById(vo.getSpaceId());
            if (space != null) {
                vo.setSpaceName(space.getName());
                vo.setSpaceAvatar(space.getAvatarUrl());
                vo.setSpaceDescription(space.getDescription());
                vo.setIsBelong(spaceUserService.getSpaceUserByUid(vo.getSpaceId(), vo.getInviteeUid()) != null);
            } else {
                throw new BusinessException(ResponseEnum.INVITE_SPACE_ALREADY_DELETED);
            }

        } else if (Objects.equals(InviteRecordTypeEnum.ENTERPRISE.getCode(), vo.getType())) {
            Enterprise enterprise = enterpriseService.getEnterpriseById(vo.getEnterpriseId());
            vo.setEnterpriseName(enterprise.getName());
            vo.setEnterpriseAvatar(enterprise.getAvatarUrl());
            UserInfo ownerUser = userInfoDataService.findByUid(enterprise.getUid()).orElseThrow();
            vo.setOwnerName(ownerUser.getNickname());
            vo.setOwnerAvatar(ownerUser.getAvatar());
            vo.setIsBelong(enterpriseUserService.getEnterpriseUserByUid(vo.getEnterpriseId(), vo.getInviteeUid()) != null);
        }
        return vo;
    }

    /**
     * Get UIDs that have joined space/team
     *
     * @param type
     * @return
     */
    @NotNull
    private Set<String> getJoinedUids(InviteRecordTypeEnum type) {
        if (type == InviteRecordTypeEnum.SPACE) {
            Long spaceId = SpaceInfoUtil.getSpaceId();
            List<SpaceUser> allSpaceUsers = spaceUserService.getAllSpaceUsers(spaceId);
            return allSpaceUsers.stream().map(SpaceUser::getUid).collect(Collectors.toSet());
        } else {
            Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
            List<EnterpriseUser> enterpriseUsers = enterpriseUserService.listByEnterpriseId(enterpriseId);
            return enterpriseUsers.stream().map(EnterpriseUser::getUid).collect(Collectors.toSet());
        }
    }

    /**
     * Search user, return user information (including whether user is in team/space)
     *
     * @param mobile
     * @param type
     * @return
     */
    @Override
    public List<ChatUserVO> searchUser(String mobile, InviteRecordTypeEnum type) {
        List<UserInfo> userInfos = userInfoDataService.findUsersByMobile(mobile);
        return getChatUserVOS(type, userInfos);
    }

    @Override
    public List<ChatUserVO> searchUsername(String username, InviteRecordTypeEnum type) {
        List<UserInfo> userInfos = userInfoDataService.findUsersByUsername(username);
        return getChatUserVOS(type, userInfos);
    }

    private @NotNull List<ChatUserVO> getChatUserVOS(InviteRecordTypeEnum type, List<UserInfo> userInfos) {
        if (CollectionUtil.isNotEmpty(userInfos)) {
            Set<String> joinedUids = getJoinedUids(type);
            Set<String> invitingUids = inviteRecordService.getInvitingUids(type);
            Map<String, String> mobileMap = userInfos.stream()
                    .filter(i -> i.getUid() != null)
                    .collect(Collectors.toMap(UserInfo::getUid, i -> i.getMobile() != null ? i.getMobile() : ""));
            return userInfos.stream().map(i -> {
                ChatUserVO chatUserVO = new ChatUserVO();
                chatUserVO.setMobile(mobileMap.get(i.getUid()));
                chatUserVO.setNickname(i.getNickname());
                chatUserVO.setUid(i.getUsername());
                chatUserVO.setUid(i.getUid());
                chatUserVO.setAvatar(i.getAvatar());
                if (joinedUids.contains(i.getUid())) {
                    chatUserVO.setStatus(1);
                } else if (invitingUids.contains(i.getUid())) {
                    chatUserVO.setStatus(2);
                } else {
                    chatUserVO.setStatus(0);
                }
                return chatUserVO;

            }).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public ApiResult<BatchChatUserVO> searchUserBatch(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            BatchChatUserVO batchChatUserVO = new BatchChatUserVO();
            // Read file
            List<String> mobiles = readMobilesFromExcel(inputStream);
            if (mobiles.isEmpty()) {
                return ApiResult.error(ResponseEnum.INVITE_PLEASE_UPLOAD_PHONE_NUMBERS);
            }
            UserLimitVO userLimit = enterpriseUserBizService.getUserLimit(EnterpriseInfoUtil.getEnterpriseId());
            if (mobiles.size() > userLimit.getRemain()) {
                return ApiResult.error(ResponseEnum.INVITE_EXCEED_BATCH_IMPORT_LIMIT);
            }
            // Query users
            List<UserInfo> userInfos = userInfoDataService.findUsersByMobiles(
                    mobiles.stream()
                            .filter(i -> StringUtils.isNumeric(i) && i.length() == 11)
                            .collect(Collectors.toSet()));
            List<ChatUserVO> chatUserVOS = getChatUserVOS(InviteRecordTypeEnum.ENTERPRISE, userInfos);
            if (CollectionUtil.isEmpty(chatUserVOS)) {
                return ApiResult.error(ResponseEnum.INVITE_NO_CORRESPONDING_USERS_FOUND);
            }
            // Upload result file
            String resultUrl = uploadResultExcelFile(chatUserVOS, mobiles);
            batchChatUserVO.setResultUrl(resultUrl);
            batchChatUserVO.setChatUserVOS(chatUserVOS);
            return ApiResult.success(batchChatUserVO);
        } catch (IOException e) {
            log.error("Failed to read uploaded file", e);
            return ApiResult.error(ResponseEnum.INVITE_READ_UPLOAD_FILE_FAILED);
        }
    }

    private @NotNull String uploadResultExcelFile(List<ChatUserVO> chatUserVOS, List<String> mobiles) {
        List<UserInfoResultExcelDTO> userInfoResultExcelDTOS = getUserInfoResultDTOS(chatUserVOS, mobiles);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, UserInfoResultExcelDTO.class)
                .registerWriteHandler(new CellWriteHandler() {
                    @Override
                    public void afterCellDispose(CellWriteHandlerContext context) {
                        if (BooleanUtils.isTrue(context.getHead()) && context.getRowIndex() == 0) {
                            WriteCellData<?> cellData = context.getFirstCellData();
                            WriteCellStyle writeCellStyle = cellData.getOrCreateStyle();
                            writeCellStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
                            writeCellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                        }
                        if (BooleanUtils.isFalse(context.getHead())
                                && Objects.equals(context.getHeadData().getField().getName(), "result")) {
                            String value = context.getFirstCellData().getStringValue();
                            WriteCellData<?> cellData = context.getFirstCellData();
                            WriteCellStyle writeCellStyle = cellData.getOrCreateStyle();
                            writeCellStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
                            if (Objects.equals(value, UserInfoResultEnum.NORMAL.getDesc())) {
                                writeCellStyle.setFillForegroundColor(IndexedColors.BRIGHT_GREEN.getIndex());
                            } else if (Objects.equals(value, UserInfoResultEnum.NOT_EXIST.getDesc())) {
                                writeCellStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
                            } else if (Objects.equals(value, UserInfoResultEnum.JOINED.getDesc())) {
                                writeCellStyle.setFillForegroundColor(IndexedColors.TURQUOISE.getIndex());
                            } else if (Objects.equals(value, UserInfoResultEnum.INVITING.getDesc())) {
                                writeCellStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
                            } else if (Objects.equals(value, UserInfoResultEnum.INVALID_MOBILE.getDesc())) {
                                writeCellStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
                            }
                        }
                    }
                })
                .useDefaultStyle(false)
                .sheet("Sheet1")
                .doWrite(userInfoResultExcelDTOS);
        String fileName = NameUtil.generateUniqueFileName("result.xlsx");
        return s3ClientUtil.uploadObject("space/" + fileName,
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new ByteArrayInputStream(outputStream.toByteArray()));
    }

    private @NotNull List<UserInfoResultExcelDTO> getUserInfoResultDTOS(List<ChatUserVO> chatUserVOS, List<String> mobiles) {
        List<UserInfoResultExcelDTO> userInfoResultExcelDTOS = new ArrayList<>();
        Map<String, ChatUserVO> collect = chatUserVOS.stream()
                .collect(Collectors.toMap(ChatUserVO::getMobile, i -> i));
        for (String mobile : mobiles) {
            UserInfoResultExcelDTO userInfoResultExcelDTO = new UserInfoResultExcelDTO();
            userInfoResultExcelDTO.setMobile(mobile);
            if (!StringUtils.isNumeric(mobile) || mobile.length() != 11) {
                userInfoResultExcelDTO.setResult(UserInfoResultEnum.INVALID_MOBILE.getDesc());
            } else if (!collect.containsKey(mobile)) {
                userInfoResultExcelDTO.setResult(UserInfoResultEnum.NOT_EXIST.getDesc());
            } else if (collect.get(mobile).getStatus() == 1) {
                userInfoResultExcelDTO.setResult(UserInfoResultEnum.JOINED.getDesc());
            } else if (collect.get(mobile).getStatus() == 2) {
                userInfoResultExcelDTO.setResult(UserInfoResultEnum.INVITING.getDesc());
            } else {
                userInfoResultExcelDTO.setResult(UserInfoResultEnum.NORMAL.getDesc());
            }
            userInfoResultExcelDTOS.add(userInfoResultExcelDTO);
        }
        return userInfoResultExcelDTOS;
    }

    private @NotNull List<String> readMobilesFromExcel(InputStream inputStream) {
        List<String> mobiles = new ArrayList<>();
        EasyExcel.read(inputStream, UserInfoExcelDTO.class, new ReadListener<UserInfoExcelDTO>() {
            @Override
            public void invoke(UserInfoExcelDTO o, AnalysisContext analysisContext) {
                mobiles.add(o.getMobile());
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext analysisContext) {

            }
        })
                .sheet()
                .headRowNumber(2)
                .doRead();
        return mobiles;
    }
}
