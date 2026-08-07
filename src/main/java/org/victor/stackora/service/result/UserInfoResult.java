package org.victor.stackora.service.result;

import org.victor.stackora.model.enums.UserRole;

public record UserInfoResult(
        Long userId,
        String account,
        String nickname,
        UserRole role
) {
    
}