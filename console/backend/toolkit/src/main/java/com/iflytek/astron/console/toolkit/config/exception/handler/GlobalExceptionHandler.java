package com.iflytek.astron.console.toolkit.config.exception.handler;

import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.*;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * Global exception handler
 *
 * @author junzhang27
 */
@ControllerAdvice(name = "toolkitGlobalExceptionHandler")
@Slf4j
@ResponseBody
public class GlobalExceptionHandler {
    /** Handle business exceptions */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<Void> handleBusinessException(BusinessException e) {
        log.error("Business exception: {}", e.getMessage(), e);
        return ApiResult.error(e);
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
        String messageCode = "http.url.not.found";
        log.warn("Handler not found exception: {}", messageCode, e);
        return ApiResult.error(ResponseEnum.NOT_FOUND.getCode(), messageCode);
    }

    /** Handle other exceptions */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleException(Exception e) {
        log.error("Unknown exception: {}", e.getMessage(), e);
        return ApiResult.error(ResponseEnum.SYSTEM_ERROR.getCode(), "error.system");
    }

}
