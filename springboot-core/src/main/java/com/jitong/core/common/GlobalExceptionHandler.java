package com.jitong.core.common;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return body(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(MessageSourceResolvable::getDefaultMessage)
                .orElse("请求参数不正确");
        return body(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
                .findFirst()
                .map(MessageSourceResolvable::getDefaultMessage)
                .orElse("请求参数不正确");
        return body(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("请求参数不正确");
        return body(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "bad_request", "请求参数格式不正确");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "请求方法不支持");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "not_found", "资源不存在");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return body(HttpStatus.CONFLICT, "concurrent_update", "数据已被其他请求修改，请刷新后重试");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("data integrity violation", ex);
        return body(HttpStatus.CONFLICT, "data_conflict", "数据冲突，请检查订单号、工号或报工记录");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("unhandled server error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", "服务器内部错误");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "detail", Map.of("code", code, "message", message)
        ));
    }
}
