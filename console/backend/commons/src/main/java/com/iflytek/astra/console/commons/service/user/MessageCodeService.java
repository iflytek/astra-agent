package com.iflytek.astra.console.commons.service.user;

import com.iflytek.astra.console.commons.response.ApiResult;

/**
 * Generic SMS verification code service interface
 *
 * @implNote This will be implemented in the commercial edition.
 */
public interface MessageCodeService {
    public static final String LOGIN_VERIFY_CODE_PREFIX = "steallar_vrifycode";

    public static final String DEL_SPACE_VERIFY_CODE_PREFIX = "astra_del_space_verifycode";

    ApiResult<Void> sendLoginMessageCode(String mobile);

    void checkLoginMessageCode(String mobile, String verifyCode);

    void sendVerifyCodeCommon(String mobile, String prefix);

    void checkVerifyCodeCommon(String mobile, String verifyCode, String prefix);

}
