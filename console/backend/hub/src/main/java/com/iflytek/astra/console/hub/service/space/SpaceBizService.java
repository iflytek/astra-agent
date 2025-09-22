package com.iflytek.astra.console.hub.service.space;

import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.dto.space.SpaceAddDTO;
import com.iflytek.astra.console.commons.dto.space.SpaceUpdateDTO;
import com.iflytek.astra.console.commons.entity.space.Space;

public interface SpaceBizService {

    ApiResult<Long> create(SpaceAddDTO spaceAddDTO, Long enterpriseId);

    ApiResult<String> deleteSpace(Long spaceId, String mobile, String verifyCode);

    ApiResult<String> updateSpace(SpaceUpdateDTO spaceUpdateDTO);

    ApiResult<Space> visitSpace(Long spaceId);

    ApiResult<String> sendMessageCode(Long spaceId);
}
