package com.iflytek.astra.console.commons.aspect.space;


import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astra.console.commons.entity.space.SpacePermission;
import com.iflytek.astra.console.commons.entity.space.SpaceUser;
import com.iflytek.astra.console.commons.enums.space.SpaceRoleEnum;
import com.iflytek.astra.console.commons.service.space.EnterpriseSpaceService;
import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
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
public class SpaceAuthAspect {

    @Autowired
    private EnterpriseSpaceService enterpriseSpaceService;

    @Pointcut("@annotation(com.iflytek.astra.console.commons.annotation.space.SpacePreAuth)")
    public void annotatedMethod() {}

    @Around("annotatedMethod()")
    public Object interceptAnnotatedMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        SpacePreAuth annotation = signature.getMethod().getAnnotation(SpacePreAuth.class);
        // If space ID is not null, start validation
        if (spaceId != null) {
            // 1) Check whether the user is in the current space
            String uid = RequestContextUtil.getUID();
            SpaceUser spaceUser = enterpriseSpaceService.checkUserBelongSpace(spaceId, uid);
            if (spaceUser == null) {
                return response(ResponseEnum.PERMISSION_NOT_BELONG_SPACE, signature);
            }
            // 2) Check user's role permissions
            String key = annotation.key();
            SpaceRoleEnum roleEnum = SpaceRoleEnum.getByCode(spaceUser.getRole());
            if (roleEnum == null) {
                return response(ResponseEnum.PERMISSION_NOT_SUPPORT_SPACE_ROLE, signature);
            }
            // If configured in DB, DB takes precedence; otherwise use annotation settings
            SpacePermission permission = enterpriseSpaceService.getSpacePermissionByKey(key);
            if (permission == null) {
                return response(ResponseEnum.PERMISSION_NO_SPACE_CONFIG, signature);
            }
            if (!checkAuth(roleEnum, permission)) {
                return response(ResponseEnum.PERMISSION_DENIED, signature);
            }
            if (!permission.getAvailableExpired() && enterpriseSpaceService.checkSpaceExpired(spaceId)) {
                return response(ResponseEnum.PERMISSION_PACKAGE_EXPIRED, signature);
            }
        } else if (annotation.requireSpaceId()) {
            return response(ResponseEnum.PERMISSION_NO_SPACE_ID, signature);
        }
        // Proceed with the original method
        return joinPoint.proceed();
    }

    private Object response(ResponseEnum responseEnum, MethodSignature signature) {
        Class<?> returnType = signature.getMethod().getReturnType();
        if (!returnType.equals(ApiResult.class)) {
            log.warn("Method {} return type is not the unified ApiResult, please check!", signature.getMethod().getName());
        }
        return ApiResult.error(responseEnum);
    }

    private boolean checkAuth(SpaceRoleEnum roleEnum, SpacePermission permission) {
        switch (roleEnum) {
            case SpaceRoleEnum.OWNER:
                return permission.getOwner();
            case SpaceRoleEnum.ADMIN:
                return permission.getAdmin();
            case SpaceRoleEnum.MEMBER:
                return permission.getMember();
            default:
                return false;
        }

    }

}
