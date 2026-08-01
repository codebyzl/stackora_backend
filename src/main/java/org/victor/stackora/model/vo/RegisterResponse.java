package org.victor.stackora.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:
 */
public record RegisterResponse(

        @Schema(
                type = "string",
                description = "用户ID",
                example = "4"
        )
        @JsonSerialize(using = ToStringSerializer.class)
        Long userId
) {
}