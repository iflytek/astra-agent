package com.iflytek.astron.console.commons.aspect;

import com.iflytek.astron.console.commons.annotation.RateLimit;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Rate limit aspect
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Autowired(required = false)
    private RedissonClient redissonClient;

    // Read unified rate limit config from application properties
    @Value("${rate-limit.window:60}")
    private int defaultWindow;

    @Value("${rate-limit.limit:10}")
    private int defaultLimit;

    @Before("@annotation(com.iflytek.astron.console.commons.annotation.RateLimit)")
    public void checkRateLimit(JoinPoint joinPoint) {
        if (redissonClient == null) {
            log.warn("RedissonClient not available, rate limiting disabled");
            return;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        RateLimitConfig config = getRateLimitConfig(rateLimit);
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();
        String key = buildRateLimitKey(joinPoint, request, rateLimit);

        checkAndApplyRateLimit(key, config);
    }

    private RateLimitConfig getRateLimitConfig(RateLimit rateLimit) {
        // Prefer annotation config; if default value, fall back to properties
        int window = rateLimit.window() != RateLimit.DEFAULT_WINDOW ? rateLimit.window() : defaultWindow;
        int limit = rateLimit.limit() != RateLimit.DEFAULT_LIMIT ? rateLimit.limit() : defaultLimit;

        return new RateLimitConfig(window, limit);
    }

    private void checkAndApplyRateLimit(String key, RateLimitConfig config) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, config.limit, config.window, RateIntervalUnit.SECONDS);

        if (!rateLimiter.tryAcquire()) {
            log.warn("Rate limit exceeded for key: {}, limit: {}/{} seconds", key, config.limit, config.window);
            throw new BusinessException(ResponseEnum.TOO_MANY_REQUESTS);
        }

        log.debug("Rate limit check passed for key: {}", key);
    }

    private static class RateLimitConfig {
        final int window;
        final int limit;

        RateLimitConfig(int window, int limit) {
            this.window = window;
            this.limit = limit;
        }
    }

    /**
     * Build the rate limit key
     */
    private String buildRateLimitKey(JoinPoint joinPoint, HttpServletRequest request, RateLimit rateLimit) {
        String dimension = rateLimit.dimension();
        String keyPart = getKeyPart(joinPoint, rateLimit);
        String ip = getClientIpAddress(request);

        switch (dimension.toUpperCase()) {
            case "USER":
                String userId = getUserIdFromContext();
                return String.format("rate_limit:%s:user:%s", keyPart, userId);
            case "IP_USER":
                String userIdForIpUser = getUserIdFromContext();
                return String.format("rate_limit:%s:ip_user:%s:%s", keyPart, ip, userIdForIpUser);
            case "IP_USERAGENT":
                String clientIdentifier = getClientIdentifier(request);
                return String.format("rate_limit:%s:ip_useragent:%s", keyPart, clientIdentifier);
            case "IP":
            default:
                return String.format("rate_limit:%s:ip:%s", keyPart, ip);
        }
    }

    /**
     * Get the main part of the key: prefer annotation key, otherwise className.methodName
     */
    private String getKeyPart(JoinPoint joinPoint, RateLimit rateLimit) {
        String customKey = rateLimit.key();
        if (StringUtils.isNotBlank(customKey)) {
            return customKey;
        }

        // Auto-generate using className + methodName
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        return className + "." + methodName;
    }

    /**
     * Get client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Get client identifier (IP + User-Agent)
     */
    private String getClientIdentifier(HttpServletRequest request) {
        String ip = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        // Sanitize User-Agent to avoid overly long strings or special chars
        if (userAgent == null || userAgent.isEmpty()) {
            userAgent = "unknown";
        } else {
            // Truncate to 100 chars and replace special characters
            userAgent = userAgent.length() > 100 ? userAgent.substring(0, 100) : userAgent;
            userAgent = userAgent.replaceAll("[^a-zA-Z0-9.\\-_/\\s]", "");
        }

        // Use simple concatenation here
        return ip + ":" + userAgent.hashCode();
    }

    /**
     * Get user ID from context; throws if UID missing in HttpServletRequest
     */
    private String getUserIdFromContext() {
        return RequestContextUtil.getUID();
    }
}
