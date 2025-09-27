package com.iflytek.astron.console.commons.aspect.space;

import com.iflytek.astron.console.commons.annotation.space.EnterprisePreAuth;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.space.EnterprisePermission;
import com.iflytek.astron.console.commons.entity.space.EnterpriseUser;
import com.iflytek.astron.console.commons.enums.space.EnterpriseRoleEnum;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.service.space.EnterpriseSpaceService;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.commons.util.space.EnterpriseInfoUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class EnterpriseAuthAspect {


    @Autowired
    private EnterpriseSpaceService enterpriseSpaceService;

    @Pointcut("@annotation(com.iflytek.astron.console.commons.annotation.space.EnterprisePreAuth)")
    public void annotatedMethod() {}

    @Around("annotatedMethod()")
    public Object interceptAnnotatedMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        // If enterprise team ID is null, access is not allowed
        if (enterpriseId == null) {
            return ApiResult.error(ResponseEnum.PERMISSION_NO_ENTERPRISE_ID);
        }
        // 1) Check whether the user is in the current enterprise team
        String uid = RequestContextUtil.getUID();
        EnterpriseUser enterpriseUser = enterpriseSpaceService.checkUserBelongEnterprise(enterpriseId, uid);
        if (enterpriseUser == null) {
            return ApiResult.error(ResponseEnum.PERMISSION_NOT_BELONG_ENTERPRISE);
        }
        // 2) Check user's role permissions
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        EnterprisePreAuth annotation = signature.getMethod().getAnnotation(EnterprisePreAuth.class);
        String key = annotation.key();
        EnterpriseRoleEnum roleEnum = EnterpriseRoleEnum.getByCode(enterpriseUser.getRole());
        if (roleEnum == null) {
            return ApiResult.error(ResponseEnum.PERMISSION_NOT_SUPPORT_ENTERPRISE_ROLE);
        }
        // If configured in DB, DB takes precedence; otherwise use annotation settings
        EnterprisePermission permission = enterpriseSpaceService.getEnterprisePermissionByKey(key);
        if (permission == null) {
            return ApiResult.error(ResponseEnum.PERMISSION_NO_ENTERPRISE_CONFIG);
        }
        if (!checkAuth(roleEnum, permission)) {
            return ApiResult.error(ResponseEnum.PERMISSION_DENIED);
        }
        if (!permission.getAvailableExpired() && enterpriseSpaceService.checkEnterpriseExpired(enterpriseId)) {
            return ApiResult.error(ResponseEnum.PERMISSION_PACKAGE_EXPIRED);
        }
        // Proceed with the original method
        return joinPoint.proceed();
    }

    private boolean checkAuth(EnterpriseRoleEnum roleEnum, EnterprisePermission permission) {
        return switch (roleEnum) {
            case OFFICER -> permission.getOfficer();
            case GOVERNOR -> permission.getGovernor();
            case STAFF -> permission.getStaff();
            default -> false;
        };
    }


}
