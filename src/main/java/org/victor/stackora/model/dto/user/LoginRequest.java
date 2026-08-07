package org.victor.stackora.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:
 */
public record LoginRequest(
        /**
         * 登录账号
         */
        @NotBlank
        @Pattern(
                regexp = "^[a-z0-9][a-z0-9_]{2,30}[a-z0-9]$",
                message = "账号格式不正确"
        )
        String account,

        /**
         * 登录密码
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
        return "LoginRequest[account=%s, password=***]"
                .formatted(account);
    }
}