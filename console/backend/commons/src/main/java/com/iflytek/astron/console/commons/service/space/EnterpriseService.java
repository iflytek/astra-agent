package com.iflytek.astron.console.commons.service.space;

import com.iflytek.astron.console.commons.dto.space.EnterpriseVO;
import com.iflytek.astron.console.commons.entity.space.Enterprise;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Enterprise team
 */
public interface EnterpriseService {

    boolean setLastVisitEnterpriseId(Long enterpriseId);

    Long getLastVisitEnterpriseId();

    Integer checkNeedCreateTeam();

    void orderChangeNotify(String uid, LocalDateTime endTime);

    boolean checkCertification();

    EnterpriseVO detail();

    List<EnterpriseVO> joinList();

    boolean checkExistByName(String name, Long id);

    boolean checkExistByUid(String uid);

    Enterprise getEnterpriseById(Long id);

    Enterprise getEnterpriseByUid(String uid);

    String getUidByEnterpriseId(Long enterpriseId);

    int updateExpireTime(Enterprise enterprise);

    boolean save(Enterprise enterprise);

    boolean updateById(Enterprise enterprise);

    Enterprise getById(Long id);


}
