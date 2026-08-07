package org.victor.stackora.model.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import org.victor.stackora.model.enums.UserRole;
import org.victor.stackora.service.result.UserInfoResult;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:
 */
public record UserInfoResponse(
        @Schema(
                type = "string",
                description = "用户ID",
                example = "4"
        )
        @JsonSerialize(using = ToStringSerializer.class)
        Long userId,
        String account,
        String nickname,
        UserRole role
) {

    public static UserInfoResponse from(UserInfoResult result) {
        return new UserInfoResponse(
                result.userId(),
                result.account(),
                result.nickname(),
                result.role()
        );
    }

}