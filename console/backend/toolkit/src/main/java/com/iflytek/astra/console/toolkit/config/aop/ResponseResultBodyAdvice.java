package com.iflytek.astra.console.toolkit.config.aop;

import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.toolkit.common.anno.ResponseResultBody;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.annotation.Annotation;

/**
 * @description: Response result interceptor wrapper
 */
@RestControllerAdvice
public class ResponseResultBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final Class<? extends Annotation> ANNOTATION_TYPE = ResponseResultBody.class;

    /**
     * Determine whether the class or method uses @ResponseResultBody
     */
    @Override
    public boolean supports(MethodParameter returnType, @NotNull Class<? extends HttpMessageConverter<?>> converterType) {
        return AnnotatedElementUtils.hasAnnotation(returnType.getContainingClass(), ANNOTATION_TYPE) || returnType.hasMethodAnnotation(ANNOTATION_TYPE);
    }

    /**
     * This method will be called when the class or method uses @ResponseResultBody
     */
    @Override
    public Object beforeBodyWrite(Object body, @NotNull MethodParameter returnType, @NotNull MediaType selectedContentType, @NotNull Class<? extends HttpMessageConverter<?>> selectedConverterType, @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response) {
        // Prevent duplicate wrapping issues
        if (null == body) {
            return ApiResult.success();
        } else {
            if (body instanceof ApiResult) {
                return body;
            }
            return ApiResult.success(body);
        }
    }
}
