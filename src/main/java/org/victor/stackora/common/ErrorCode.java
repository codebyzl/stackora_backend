package org.victor.stackora.common;

import org.springframework.http.HttpStatus;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:错误码
 */


public enum ErrorCode {


    /**
     * 通用错误码
     */
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

    /**
     * 用户账号错误码
     */
    ACCOUNT_ALREADY_EXISTS(
            20000,
            "账号已存在",
            HttpStatus.CONFLICT
    ),

    ACCOUNT_OR_PASSWORD_ERROR(
            20001,
            "账号或密码错误",
            HttpStatus.UNAUTHORIZED
    ),

    ACCOUNT_DISABLED(
            20002,
            "账号已被禁用",
            HttpStatus.FORBIDDEN
    ),

    ACCOUNT_CANCELLED(
            20003,
            "账号已注销",
            HttpStatus.FORBIDDEN
    ),

    ACCOUNT_NOT_FOUND(
            20004,
            "账号不存在",
            HttpStatus.NOT_FOUND
    ),


    /**
     * 系统错误码
     */

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
