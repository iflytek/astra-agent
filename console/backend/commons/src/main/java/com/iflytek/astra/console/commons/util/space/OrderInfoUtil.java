package com.iflytek.astra.console.commons.util.space;

import com.iflytek.astra.console.commons.enums.space.EnterpriseServiceTypeEnum;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Order information utilities.
 *
 * @implNote This class will be implemented in the commercial edition.
 */
public class OrderInfoUtil {
    public static boolean existValidEnterpriseOrder(String uid) {
        // The order system has been removed; return true
        return true;
    }

    public static EnterpriseResult getEnterpriseResult(String uid) {
        // The order system has been removed; temporarily return an enterprise edition
        return new EnterpriseResult(EnterpriseServiceTypeEnum.ENTERPRISE, LocalDateTime.now().plusDays(365));
    }

    public static boolean existValidProOrder(String uid) {
        // The order system has been removed; return true
        return true;
    }

    @Data
    @Builder
    public static class EnterpriseResult {

        private EnterpriseServiceTypeEnum serviceType;

        private LocalDateTime endTime;
    }
}
