package org.victor.stackora.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:用户状态枚举
 */
@AllArgsConstructor
public enum UserStatus {

    /**
     * 活跃正常用户
     */
    ACTIVE(0),

    /**
     * 禁用用户
     */
    DISABLED(1),


    /**
     * 注销用户
     */
    CANCELLED(2);


    @EnumValue
    private final int statusCode;
}