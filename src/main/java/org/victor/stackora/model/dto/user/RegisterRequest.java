package org.victor.stackora.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:用户注册请求类
 */

public record RegisterRequest(
        /**
         * 用户名
         */
        @NotBlank
        @Pattern(
                regexp = "^[A-Za-z0-9][A-Za-z0-9_]{2,30}[A-Za-z0-9]$",
                message = "账号格式不正确"
        )
        String account,

        /**
         * 原始密码
         */
        @NotBlank(message = "密码不能为空")
        @Size(
                min = 8,
                max = 64,
                message = "密码长度必须为8到64位"
        )
        String rawPassword
) {

    @Override
    public String toString() {
        return "RegisterRequest[account=%s, password=***]"
                .formatted(account);
    }
}