package com.iflytek.astra.console.hub.exception;

import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.response.ApiResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.Locale;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/** Global exception handler */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Handle business exceptions */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<Void> handleBusinessException(BusinessException e) {
        Locale locale = LocaleContextHolder.getLocale();
        String msg;
        try {
            msg = messageSource.getMessage(
                    e.getResponseEnum().getMessageKey(),
                    e.getArgs(),
                    e.getResponseEnum().getMessageKey(),
                    locale);
        } catch (Exception ex) {
            msg = fallbackFormat(e.getResponseEnum().getMessageKey(), e.getArgs());
        }
        log.error("Business exception: {}", msg, e);
        return ApiResult.error(e.getCode(), msg);
    }

    private String fallbackFormat(String template, Object[] args) {
        if (args == null || args.length == 0)
            return template;
        try {
            return String.format(template, args);
        } catch (Exception ignore) {
            return template + " " + java.util.Arrays.toString(args);
        }
    }

    /** Handle parameter validation exceptions */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        FieldError fieldError = bindingResult.getFieldError();
        String messageCode = fieldError != null ? fieldError.getDefaultMessage() : "param.invalid";
        log.warn("Parameter validation exception: {}", messageCode, e);
        return ApiResult.error(ResponseEnum.PARAMETER_ERROR.getCode(), messageCode);
    }

    /** Handle binding exceptions */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleBindException(BindException e) {
        BindingResult bindingResult = e.getBindingResult();
        FieldError fieldError = bindingResult.getFieldError();
        String messageCode = fieldError != null ? fieldError.getDefaultMessage() : "param.invalid";
        log.warn("Binding exception: {}", messageCode, e);
        return ApiResult.error(ResponseEnum.PARAMETER_ERROR.getCode(), messageCode);
    }

    /** Handle constraint violation exceptions */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String messageCode = e.getConstraintViolations().stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("; "));
        log.warn("Constraint violation exception: {}", messageCode, e);
        return ApiResult.error(ResponseEnum.VALIDATION_ERROR.getCode(), messageCode);
    }

    /** Handle parameter type mismatch exceptions */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String messageCode = "parameter.error";
        log.warn("Parameter type mismatch exception: {}", messageCode, e);
        return ApiResult.error(ResponseEnum.PARAMETER_ERROR.getCode(), messageCode);
    }

    /** Handle missing request parameter exceptions */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String messageCode = "parameter.missing";
        log.warn("Missing request parameter exception: {}", messageCode);
        return ApiResult.error(ResponseEnum.PARAMETER_ERROR.getCode(), messageCode);
    }

    /** Handle HTTP message not readable exceptions */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("HTTP message not readable exception: {}", e.getMessage(), e);
        return ApiResult.error(ResponseEnum.BAD_REQUEST.getCode(), "parameter.illegal");
    }

    /** Handle HTTP request method not supported exceptions */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResult<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        String messageCode = "http.method.not.supported";
        log.warn("HTTP request method not supported exception: {}", messageCode, e);
        return ApiResult.error(ResponseEnum.METHOD_NOT_ALLOWED.getCode(), messageCode);
    }

    /** Handle handler not found exceptions */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<Void> handleNoHandlerFoundException(NoHandlerFoundException e) {
        String messageCode = "url.not.found";
        log.warn("Handler not found exception: {}", messageCode, e);
        return ApiResult.error(ResponseEnum.NOT_FOUND.getCode(), messageCode);
    }

    /** Handle distributed lock exceptions */
    @ExceptionHandler(DistributedLockException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleDistributedLockException(DistributedLockException e) {
        log.error("Distributed lock exception: lockKey={}, errorType={}, message={}", e.getLockKey(), e.getErrorType(), e.getMessage(), e);
        return ApiResult.error(ResponseEnum.SYSTEM_ERROR.getCode(), "lock.error." + e.getErrorType().name().toLowerCase());
    }

    /** Handle other exceptions */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleException(Exception e) {
        log.error("Unknown exception: {}", e.getMessage(), e);
        return ApiResult.error(ResponseEnum.SYSTEM_ERROR.getCode(), "error.system");
    }
}
