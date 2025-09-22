package com.iflytek.astra.console.toolkit.util.ssrf;

import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Dns;

import java.net.URL;
import java.util.List;

/**
 * Unified SSRF (Server-Side Request Forgery) parameter guard.
 *
 * <p>
 * This class provides validation methods to ensure URLs comply with configured rules to prevent
 * SSRF attacks.
 * </p>
 *
 * @author clliu19
 */
@Slf4j
public class SsrfParamGuard {

    private final SsrfProperties props;

    /**
     * Construct a guard instance with SSRF-related configuration.
     *
     * @param props SSRF configuration properties, including allowed schemes and blacklists
     */
    public SsrfParamGuard(SsrfProperties props) {
        this.props = props;
    }

    /**
     * Validate whether the given URL string is compliant with SSRF protection rules.
     *
     * <p>
     * Validation steps:
     * </p>
     * <ul>
     * <li>Check if the URL scheme (protocol) is allowed.</li>
     * <li>Check if the host is blocked by the configured IP blacklist (supporting both hostnames and
     * IPs).</li>
     * </ul>
     *
     * @param url the URL string to validate
     * @throws BusinessException if the URL does not pass validation
     */
    public void validateUrlParam(String url) {
        try {
            SsrfValidators.Normalized n = SsrfValidators.normalizeFlex(url);
            URL u = n.effectiveUrl;

            // 1) Protocol and port
            if (!SsrfValidators.isAllowedScheme(u.getProtocol(), props.getAllowedSchemes())) {
                throw new BusinessException(ResponseEnum.MODEL_URL_ILLEGAL_FAILED);
            }
            if (!SsrfValidators.isAllowedScheme(u.getProtocol(), props.getAllowedSchemes())) {
                throw new BusinessException(
                                ResponseEnum.RESPONSE_FAILED,
                                "Only allowed schemes: " + props.getAllowedSchemes());
            }

            // 2) IP blacklist (compatible with hostnames and IPs)
            List<String> ipBlacklist = props.getIpBlaklist();
            if (!ipBlacklist.isEmpty()) {
                if (SsrfValidators.isHostBlockedByIpBlacklist(u.getHost(), ipBlacklist, Dns.SYSTEM)) {
                    throw new BusinessException(ResponseEnum.MODEL_URL_CHECK_FAILED);
                }
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SSRF] URL validation failed", e);
            throw new BusinessException(ResponseEnum.MODEL_URL_ILLEGAL_FAILED);
        }
    }
}
