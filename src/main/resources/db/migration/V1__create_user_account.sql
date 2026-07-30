CREATE TABLE user_account
(
    id            BIGINT           NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    account       VARCHAR(32)
                      CHARACTER SET ascii
                      COLLATE ascii_general_ci
                                   NOT NULL COMMENT '登录账号，统一小写',
    password_hash VARCHAR(255)     NOT NULL COMMENT '密码哈希',
    nickname      VARCHAR(32)      NOT NULL COMMENT '展示昵称',
    role          TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '角色：0-USER，1-ADMIN',
    status        TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '状态：0-ACTIVE，1-DISABLED，2-CANCELLED',
    created_at    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',
    updated_at    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',
    cancelled_at  DATETIME(3)      NULL COMMENT '注销时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_account (account),
    CONSTRAINT chk_user_account_account_format
        CHECK (
            REGEXP_LIKE(account, '^[a-z0-9][a-z0-9_]{2,30}[a-z0-9]$', 'c')
            ),
    CONSTRAINT chk_user_account_password_hash
        CHECK (
            REGEXP_LIKE(password_hash, '[^[:space:]]', 'c')
            ),
    CONSTRAINT chk_user_account_nickname
        CHECK (
            REGEXP_LIKE(nickname, '[^[:space:]]', 'c')
            ),
    CONSTRAINT chk_user_account_role
        CHECK (role IN (0, 1)),
    CONSTRAINT chk_user_account_status
        CHECK (status IN (0, 1, 2)),
    CONSTRAINT chk_user_account_cancelled_at
        CHECK (
            (status = 2 AND cancelled_at IS NOT NULL)
                OR
            (status IN (0, 1) AND cancelled_at IS NULL)
            ),
    CONSTRAINT chk_user_account_time_order
        CHECK (
            updated_at >= created_at
                AND (cancelled_at IS NULL OR cancelled_at >= created_at)
            )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='用户账号表';