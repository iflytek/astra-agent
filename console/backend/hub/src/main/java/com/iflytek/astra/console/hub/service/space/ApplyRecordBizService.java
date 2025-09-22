package com.iflytek.astra.console.hub.service.space;


import com.iflytek.astra.console.commons.response.ApiResult;

public interface ApplyRecordBizService {

    ApiResult<String> joinEnterpriseSpace(Long spaceId);

    ApiResult<String> agreeEnterpriseSpace(Long applyId);

    ApiResult<String> refuseEnterpriseSpace(Long applyId);
}
