package com.iflytek.astra.console.toolkit.common.anno;

import java.lang.annotation.*;

/**
 * @Author clliu19
 * @Date: 2025/3/15 09:15
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelHeader {
    // Header name
    String value();

    /**
     * Sort value
     *
     * @return
     */
    int order() default Integer.MAX_VALUE;
}
