package com.iflytek.astra.console.commons.util.space;

import com.iflytek.astra.console.commons.util.RequestContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;


public class EnterpriseInfoUtil {

    private static String enterpriseIdKey = "enterprise-id";

    public static void init(String key) {
        EnterpriseInfoUtil.enterpriseIdKey = key;
    }

    /**
     * Get enterprise id; cross-thread retrieval is not supported for now.
     *
     * @return enterprise id or null
     */
    public static Long getEnterpriseId() {
        HttpServletRequest request = RequestContextUtil.getCurrentRequest();
        String enterpriseId = request.getHeader(enterpriseIdKey);
        if (StringUtils.isNotBlank(enterpriseId)) {
            return Long.parseLong(enterpriseId);
        }
        return null;
    }
}
