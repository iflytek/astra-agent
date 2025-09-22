package com.iflytek.astra.console.toolkit.handler;


import com.iflytek.astra.console.commons.config.JwtClaimsFilter;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.entity.user.UserInfo;
import com.iflytek.astra.console.commons.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 【!Attention!】Do not use in multi-threaded environments. This class uses ThreadLocal and cannot be
 * retrieved. Please use UserUtil instead
 */
public final class UserInfoManagerHandler {
    private UserInfoManagerHandler() {}

    public static UserInfo get() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            throw new BusinessException(ResponseEnum.UNAUTHORIZED);
        }
        Object userInfoObj = request.getAttribute(JwtClaimsFilter.USER_INFO_ATTRIBUTE);
        if (userInfoObj instanceof UserInfo userInfo) {
            return userInfo;
        } else {
            throw new BusinessException(ResponseEnum.UNAUTHORIZED);
        }
    }

    public static String getUserId() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            throw new BusinessException(ResponseEnum.UNAUTHORIZED);
        }
        String uid = (String) request.getAttribute(JwtClaimsFilter.USER_ID_ATTRIBUTE);
        if (StringUtils.isBlank(uid)) {
            throw new BusinessException(ResponseEnum.UNAUTHORIZED);
        }
        return uid;
    }

    public static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

}
