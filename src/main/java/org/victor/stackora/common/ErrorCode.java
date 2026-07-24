package org.victor.stackora.common;

import org.springframework.http.HttpStatus;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:错误码
 */


public enum ErrorCode {

    ERROR(
            -1,
            "请求失败",
            HttpStatus.BAD_REQUEST
    ),

    PARAMS_ERROR(
            10000,
            "请求参数错误",
            HttpStatus.BAD_REQUEST
    ),

    NOT_LOGIN(
            10001,
            "用户未登录",
            HttpStatus.UNAUTHORIZED
    ),

    NO_AUTH(
            10002,
            "用户无权限",
            HttpStatus.FORBIDDEN
    ),

    SYSTEM_ERROR(
            99999,
            "系统异常",
            HttpStatus.INTERNAL_SERVER_ERROR
    );

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }


    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
