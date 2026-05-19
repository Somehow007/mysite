package io.github.somehow.mysite.commons.framework.web;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.AbstractException;
import io.github.somehow.mysite.commons.framework.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Optional;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @SneakyThrows
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());
        String exceptionStr = Optional.ofNullable(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);
        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);
        return Results.failure(ErrorCode.PARAM_VALIDATION_ERROR.code(), exceptionStr);
    }

    @ExceptionHandler(value = {AbstractException.class})
    public Result abstractException(HttpServletRequest request, AbstractException ex) {
        if (ex.getCause() != null) {
            log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURL().toString(), ex, ex.getCause());
            return Results.failure(ex);
        }
        StringBuilder stackTraceBuilder = new StringBuilder();
        stackTraceBuilder.append(ex.getClass().getName()).append(": ").append(ex.getErrorMessage()).append("\n");
        StackTraceElement[] stackTrace = ex.getStackTrace();
        for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
            stackTraceBuilder.append("\tat ").append(stackTrace[i]).append("\n");
        }
        log.error("[{}] {} [ex] {} \n\n{}", request.getMethod(), request.getRequestURL().toString(), ex, stackTraceBuilder);
        return Results.failure(ex);
    }

    @ExceptionHandler(value = {BadCredentialsException.class, UsernameNotFoundException.class})
    public Result loginExceptionHandler(HttpServletRequest request, Exception ex) {
        log.warn("[{}] {} 登录失败: {}", request.getMethod(), getUrl(request), ex.getMessage());
        return Results.failure(ErrorCode.USER_LOGIN_BAD_CREDENTIALS.code(), ErrorCode.USER_LOGIN_BAD_CREDENTIALS.message());
    }

    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public Result maxUploadSizeExceptionHandler(HttpServletRequest request, MaxUploadSizeExceededException ex) {
        log.warn("[{}] {} 文件大小超限: {}", request.getMethod(), getUrl(request), ex.getMessage());
        return Results.failure(ErrorCode.IMAGE_FILE_TOO_LARGE.code(), ErrorCode.IMAGE_FILE_TOO_LARGE.message());
    }

    @ExceptionHandler(value = MultipartException.class)
    public Result multipartExceptionHandler(HttpServletRequest request, MultipartException ex) {
        log.warn("[{}] {} multipart请求异常: {}", request.getMethod(), getUrl(request), ex.getMessage());
        return Results.failure(ErrorCode.IMAGE_UPLOAD_FAILED.code(), "请使用multipart/form-data格式上传文件");
    }

    @ExceptionHandler(value = Throwable.class)
    public Result defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("[{}] {} ", request.getMethod(), getUrl(request), throwable);
        return Results.failure();
    }

    private String getUrl(HttpServletRequest request) {
        if (StringUtils.isEmpty(request.getQueryString())) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + request.getQueryString();
    }
}
