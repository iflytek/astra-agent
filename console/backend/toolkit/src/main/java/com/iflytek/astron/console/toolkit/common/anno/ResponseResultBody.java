package com.iflytek.astron.console.toolkit.common.anno;

import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ResponseBody
public @interface ResponseResultBody {
}
