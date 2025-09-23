package com.iflytek.astron.console.hub.service.space;

import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.dto.space.UserLimitVO;

public interface EnterpriseUserBizService {

    ApiResult<String> remove(String uid);

    ApiResult<String> updateRole(String uid, Integer role);

    ApiResult<String> quitEnterprise();

    UserLimitVO getUserLimit(Long enterpriseId);
}
