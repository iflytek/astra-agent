package com.iflytek.astron.console.commons.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rate limit annotation
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    // Default values for annotation
    int DEFAULT_WINDOW = 60;
    int DEFAULT_LIMIT = 10;

    /**
     * Custom limit key; when empty, auto-use className.methodName
     */
    String key() default "";

    /**
     * Time window in seconds; default 60
     */
    int window() default DEFAULT_WINDOW;

    /**
     * Max allowed requests per window
     */
    int limit() default DEFAULT_LIMIT;

    /**
     * Limit dimension: IP, USER, IP_USER, IP_USERAGENT
     */
    String dimension() default "USER";
}
