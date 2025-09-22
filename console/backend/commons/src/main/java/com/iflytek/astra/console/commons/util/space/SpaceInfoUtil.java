package com.iflytek.astra.console.commons.util.space;


import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.commons.service.space.EnterpriseSpaceService;
import org.apache.commons.lang3.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

public class SpaceInfoUtil {

    private static String spaceIdKey = "space-id";

    private static EnterpriseSpaceService enterpriseSpaceService;


    public static void init(EnterpriseSpaceService service, String key) {
        if (service == null) {
            throw new IllegalArgumentException("EnterpriseSpaceService cannot be null");
        }
        SpaceInfoUtil.enterpriseSpaceService = service;
        if (key != null && !key.trim().isEmpty()) {
            SpaceInfoUtil.spaceIdKey = key;
        } else {
            throw new IllegalArgumentException("spaceIdKey cannot be null or empty");
        }
    }

    /**
     * Get the owner UID by the current request's spaceId; if not found, return the current user's uid.
     *
     * @return UID string
     */
    public static String getUidByCurrentSpaceId() {
        String currentUid = RequestContextUtil.getUID();
        Long spaceId = getSpaceId();
        if (spaceId == null) {
            return currentUid;
        }
        String uid = enterpriseSpaceService.getUidByCurrentSpaceId(spaceId);
        return StringUtils.isBlank(uid) ? currentUid : uid;
    }

    /**
     * Get the owner UID by the given spaceId; return null if not found.
     *
     * @return UID string or null
     */
    public static String getUidBySpaceId(Long spaceId) {
        if (spaceId == null) {
            return null;
        }
        String uid = enterpriseSpaceService.getUidByCurrentSpaceId(spaceId);
        return StringUtils.isBlank(uid) ? null : uid;
    }

    /**
     * Get the spaceId of the current request. Cross-thread retrieval is not supported for now.
     *
     * @return spaceId or null
     */
    public static Long getSpaceId() {
        HttpServletRequest request = RequestContextUtil.getCurrentRequest();
        String spaceId = request.getHeader(spaceIdKey);
        if (StringUtils.isNotBlank(spaceId)) {
            return Long.parseLong(spaceId);
        }
        return null;
    }

    /**
     * Check whether the current user belongs to the space.
     *
     * @return true if belongs; false otherwise
     */
    public static boolean checkUserBelongSpace() {
        Long spaceId = getSpaceId();
        String uid = RequestContextUtil.getUID();
        if (spaceId == null) {
            return false;
        }
        return enterpriseSpaceService.checkUserBelongSpace(spaceId, uid) != null;
    }
}
