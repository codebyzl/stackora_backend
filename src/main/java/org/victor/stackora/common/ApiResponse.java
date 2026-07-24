package org.victor.stackora.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:全局响应数据
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 2122871793694403136L;

    private final int code;
    private final String message;
    private final T data;

    public ApiResponse(int code, String message) {
       this(code, message, null);
    }
}