package org.victor.stackora.exception;

import org.victor.stackora.common.ErrorCode;

import java.util.Objects;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:业务异常类
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 4586289365299618458L;

    private final ErrorCode errorCode;


    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(resolveMessage(errorCode, message));
        this.errorCode = errorCode;
    }

    private static String resolveMessage(ErrorCode errorCode, String message) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");

        return message == null || message.isBlank()
                ? errorCode.getMessage()
                : message;
    }
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}



