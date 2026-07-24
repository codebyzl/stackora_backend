package org.victor.stackora.common;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:接口工厂
 */
public final class ApiResponseFactory  {

    private ApiResponseFactory() {
    }

    /**
     * 响应成功
     * @param data
     * @return ApiResponse
     * @param <T>
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "ok", data);
    }


    /**
     * 响应成功无数据
     * @param
     * @return ApiResponse
     */
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(0, "ok");
    }



    /**
     * 响应失败
     * @param errorCode
     * @return ApiResponse
     */
    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage());
    }


    /**
     * 自定义失败
     * @param
     * @param errorCode
     * @param message
     * @return
     */
    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(),message);
    }
}