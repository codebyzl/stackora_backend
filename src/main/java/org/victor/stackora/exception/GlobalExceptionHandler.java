package org.victor.stackora.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.victor.stackora.common.ApiResponse;
import org.victor.stackora.common.ApiResponseFactory;
import org.victor.stackora.common.ErrorCode;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:全局异常处理
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException e) {

        log.warn("业务异常：{}", e.getMessage());

        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ApiResponseFactory.error(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(
            RuntimeException e) {

        log.error("系统异常", e);

        return ResponseEntity
                .status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(ApiResponseFactory.error(ErrorCode.SYSTEM_ERROR));
    }
}