package org.victor.stackora.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:用户身份枚举
 */
@AllArgsConstructor
public enum UserRole {

    USER(0),
    ADMIN(1);

    @EnumValue
    private final int roleCode;
}