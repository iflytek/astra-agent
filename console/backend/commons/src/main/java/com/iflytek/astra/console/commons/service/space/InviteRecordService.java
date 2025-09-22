package com.iflytek.astra.console.commons.service.space;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.dto.space.InviteRecordParam;
import com.iflytek.astra.console.commons.dto.space.InviteRecordVO;
import com.iflytek.astra.console.commons.entity.space.InviteRecord;
import com.iflytek.astra.console.commons.enums.space.InviteRecordTypeEnum;
import com.iflytek.astra.console.commons.enums.space.SpaceTypeEnum;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Invitation records
 */
public interface InviteRecordService {

    Page<InviteRecordVO> inviteList(InviteRecordParam param, InviteRecordTypeEnum type);

    Long countBySpaceIdAndUids(Long spaceId, List<String> uids);

    Long countByEnterpriseIdAndUids(Long enterpriseId, List<String> uids);

    Long countJoiningByEnterpriseId(Long enterpriseId);

    Long countJoiningBySpaceId(Long spaceId);

    Long countJoiningByUid(String uid, SpaceTypeEnum spaceTypeEnum);

    boolean saveBatch(Collection<InviteRecord> entityList);

    InviteRecord getById(Long id);

    Set<String> getInvitingUids(InviteRecordTypeEnum type);

    boolean updateById(InviteRecord entity);

    InviteRecordVO selectVOById(Long id);

    int updateExpireRecord();


}
