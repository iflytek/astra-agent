package com.iflytek.astra.console.commons.service.space;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.dto.space.EnterpriseUserParam;
import com.iflytek.astra.console.commons.dto.space.EnterpriseUserVO;
import com.iflytek.astra.console.commons.entity.space.EnterpriseUser;
import com.iflytek.astra.console.commons.enums.space.EnterpriseRoleEnum;

import java.util.List;

/**
 * Enterprise team users
 */
public interface EnterpriseUserService {


    EnterpriseUser getEnterpriseUserByUid(Long enterpriseId, String uid);

    Long countByEnterpriseIdAndUids(Long enterpriseId, List<String> uids);

    List<EnterpriseUser> listByEnterpriseId(Long enterpriseId);

    boolean addEnterpriseUser(Long enterpriseId, String uid, EnterpriseRoleEnum roleEnum);

    List<EnterpriseUser> listByRole(Long enterpriseId, EnterpriseRoleEnum roleEnum);

    Long countByEnterpriseId(Long enterpriseId);

    Page<EnterpriseUserVO> page(EnterpriseUserParam param);

    boolean removeById(EnterpriseUser enterpriseUser);

    boolean updateById(EnterpriseUser enterpriseUser);

}
