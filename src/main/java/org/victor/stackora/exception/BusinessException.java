package org.victor.stackora.exception;

import org.victor.stackora.common.ErrorCode;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:业务异常类
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 4586289365299618458L;

    private final ErrorCode errorCode;


    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message == null || message.isBlank()
                ? errorCode.getMessage()
                : message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
