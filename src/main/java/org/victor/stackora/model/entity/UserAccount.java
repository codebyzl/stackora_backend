package org.victor.stackora.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.victor.stackora.model.enums.UserRole;
import org.victor.stackora.model.enums.UserStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户账号表
 *
 * @TableName user_account
 */
@TableName(value = "user_account")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount implements Serializable {
    /**
     * 用户主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录账号，统一小写
     */
    @TableField(value = "account")
    private String account;

    /**
     * 密码哈希
     */
    @TableField(value = "password_hash")
    private String passwordHash;

    /**
     * 展示昵称
     */
    @TableField(value = "nickname")
    private String nickname;

    /**
     * 角色：0-USER，1-ADMIN
     */
    @TableField(value = "role")
    private UserRole role;

    /**
     * 状态：0-ACTIVE，1-DISABLED，2-CANCELLED
     */
    @TableField(value = "status")
    private UserStatus status;

    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 注销时间
     */
    @TableField(value = "cancelled_at")
    private LocalDateTime cancelledAt;

    private static final long serialVersionUID = 8750236807859121573L;
    
}