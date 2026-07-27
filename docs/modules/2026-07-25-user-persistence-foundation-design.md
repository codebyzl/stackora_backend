# Stackora 用户持久化基础需求与技术设计

## 文档说明

本文档同时包含用户持久化基础模块的业务需求、技术设计、开发步骤和验收标准。

推荐阅读和开发顺序：

推荐阅读和开发顺序：

1. 先阅读第一部分，理解本模块解决什么问题、包含哪些能力以及明确不做什么。
2. 再按照第二部分的七个步骤依次开发，每一步完成后执行对应测试。
3. 最后使用第三部分的完整清单进行整体验收。

---

# 第一部分：需求说明

## 1. 背景与用户价值

用户账号是 Stackora 注册、登录、文章作者、评论归属、互动关系和后台治理的共同数据基础。

本模块需要解决以下基础问题：

- 每个用户必须拥有稳定且不可复用的内部 ID。
- 相同账号不能因为大小写或并发请求被重复创建。
- 数据库只能保存密码哈希，不能保存明文密码。
- 用户角色和状态必须具有稳定、可解释的编码。
- 用户被禁用或注销后，历史文章、评论和审计数据仍能关联原用户。
- 数据库结构必须通过版本化迁移维护，并能在不同环境重复验证。
- 业务代码不能绕过 Service 任意修改角色、状态和时间字段。

本模块只建立用户账号的数据与持久化基础，不提前实现注册、登录和公开用户接口。

## 2. 模块最终目标

完成本模块后，系统应具备以下工程能力：

1. 通过 Flyway 在空 MySQL schema 中创建 `user_account` 表。
2. 使用 `BIGINT AUTO_INCREMENT` 生成稳定用户 ID。
3. 将账号统一规范化为小写，并通过唯一索引防止重复账号。
4. 只保存密码哈希，不保存或记录明文密码。
5. 创建账号时由服务端设置默认昵称、普通用户角色和正常状态。
6. 使用稳定数值编码保存用户角色和状态。
7. 通过受控 Service 完成用户创建、查询、禁用、恢复和注销。
8. 通过条件更新保护 `CANCELLED` 注销终态。
9. 由应用维护创建时间和更新时间，数据库默认值作为兜底。
10. 通过迁移、约束、Service、并发和架构边界测试验证上述能力。

## 3. 功能范围

### 3.1 本次实现

- Flyway 依赖与迁移配置。
- `V1__create_user_account.sql`。
- `user_account` 表、唯一索引和检查约束。
- `UserAccount` Entity。
- `UserRole`、`UserStatus` 枚举。
- `UserAccountMapper` 及状态条件更新。
- `UserAccountService` 与 `UserAccountServiceImpl`。
- 账号规范化和持久化字段校验。
- 默认昵称、角色和状态。
- 用户查询、禁用、恢复和注销。
- MyBatis-Plus 时间字段自动填充。
- 迁移、数据库约束、Service、并发和架构边界测试。
- 测试临时 schema 的创建与安全清理。

### 3.2 本次不实现

- 用户注册、登录、退出或当前用户 HTTP 接口。
- BCrypt、Argon2 等密码算法的具体实现。
- Session、Cookie、拦截器或认证框架。
- 邮箱、手机号、验证码或多身份登录。
- 用户资料修改、头像上传或用户主页。
- 管理员封禁、解封或角色管理 HTTP 接口。
- 用户列表、搜索或分页接口。
- Redis、Redisson、RabbitMQ、Elasticsearch。
- 对外公开的 User VO。
- Testcontainers。
- 乐观锁版本列。
- 额外 Repository 分层。

## 4. 核心业务规则

### 4.1 账号规则

1. 第一版只使用 `account + password` 作为注册和登录身份。
2. `account` 写入和查询前统一使用 `Locale.ROOT` 转为小写。
3. 规范化后的账号必须匹配 `^[a-z0-9_]{4,32}$`。
4. `Victor`、`VICTOR` 和 `victor` 表示同一账号。
5. 账号创建后永久保留；禁用或注销都不能释放账号。
6. Java 层校验用于提供可读错误，数据库唯一索引是并发防重的最终保障。

### 4.2 昵称规则

1. 第一版不要求用户额外输入昵称。
2. 创建账号时由 Service 设置 `nickname = normalizedAccount`。
3. 昵称不承担登录身份，不要求唯一。
4. 后续个人资料模块可以修改昵称。
5. 昵称不能为空、纯空白或超过 32 个字符。

### 4.3 密码规则

1. 持久化 Service 只接收 `passwordHash`，不接收明文密码。
2. `passwordHash` 不能为空、纯空白或超过 255 个字符。
3. 数据库和持久化层只能验证字段边界，不能判断字符串是否真的由安全算法生成。
4. 明文密码到密码哈希的转换由后续注册模块负责。
5. 密码哈希不得进入 VO、普通日志、异常消息或监控标签。

### 4.4 角色规则

```text
USER  -> 0
ADMIN -> 1
```

1. 新用户默认角色为 `USER`。
2. 外部调用方不能指定 `ADMIN`。
3. 枚举使用显式 `code` 和 MyBatis-Plus `@EnumValue`。
4. 禁止使用 `Enum.ordinal()` 或业务魔法数字。

### 4.5 状态规则

```text
ACTIVE    -> 0
DISABLED  -> 1
CANCELLED -> 2
```

允许的状态迁移：

```text
ACTIVE   -> DISABLED
DISABLED -> ACTIVE
ACTIVE   -> CANCELLED
DISABLED -> CANCELLED
CANCELLED -> terminal
```

具体规则：

1. 新用户默认状态为 `ACTIVE`。
2. `DISABLED` 表示平台治理禁用，可以通过受控流程恢复。
3. `CANCELLED` 表示永久注销，是不可恢复的终态。
4. 用户表不使用 MyBatis-Plus `@TableLogic`。
5. 系统不提供物理删除用户的方法。
6. 注销后保留用户记录、用户 ID 和账号唯一占用。

## 5. 正常业务流程

### 5.1 用户记录创建

```text
上层密码组件生成 passwordHash
  -> 调用 UserAccountService.createAccount(account, passwordHash)
  -> Service 规范化和校验 account
  -> Service 校验 passwordHash
  -> Service 设置 nickname、role、status
  -> Mapper 执行 INSERT
  -> MySQL 唯一索引执行最终防重
  -> MySQL 生成自增 ID
  -> 应用写入 createdAt 和 updatedAt
  -> 返回持久化结果
```

### 5.2 用户查询

按 ID 查询：

```text
findById(userId)
  -> 校验 userId
  -> Mapper 按主键查询
  -> 返回 Optional<UserAccount>
```

按账号查询：

```text
findByAccount(account)
  -> 使用与创建相同的规范化和格式校验
  -> Mapper 按规范化账号查询
  -> 返回 Optional<UserAccount>
```

### 5.3 用户禁用

```text
disableUser(userId)
  -> UPDATE user_account
     SET status=DISABLED, updated_at=?
     WHERE id=? AND status=ACTIVE AND cancelled_at IS NULL
  -> 影响一行表示成功
  -> 影响零行表示用户不存在或状态不允许
```

### 5.4 用户恢复

```text
restoreUser(userId)
  -> UPDATE user_account
     SET status=ACTIVE, updated_at=?
     WHERE id=? AND status=DISABLED AND cancelled_at IS NULL
  -> 影响一行表示成功
  -> 影响零行表示用户不存在或状态不允许
```

### 5.5 用户注销

```text
cancelUser(userId)
  -> UPDATE user_account
     SET status=CANCELLED,
         cancelled_at=?,
         updated_at=?
     WHERE id=?
       AND status IN (ACTIVE, DISABLED)
       AND cancelled_at IS NULL
  -> 影响一行表示成功
  -> 影响零行表示用户不存在、已注销或发生并发状态变化
```

注销后：

- 用户记录继续存在。
- 原账号继续受唯一索引保护。
- 历史文章、评论和审计数据继续引用原用户 ID。
- 后续公开接口应显示“已注销用户”等脱敏信息。

## 6. 异常流程

| 场景 | 处理规则 |
| --- | --- |
| 账号为空或格式非法 | Service 在进入 Mapper 前拒绝 |
| 账号包含大写字母 | Service 先规范化为小写 |
| 并发创建相同账号 | 一个成功，另一个被唯一索引拒绝 |
| 密码哈希为空、纯空白或过长 | Service 拒绝，数据库约束兜底 |
| 角色或状态编码非法 | 数据库检查约束拒绝 |
| 非法状态迁移 | 条件更新影响零行，不执行无条件覆盖 |
| 恢复已注销用户 | 条件更新影响零行 |
| Flyway SQL 或 checksum 异常 | 应用启动失败 |
| 数据库不可用 | 操作失败，客户端不得收到连接信息或 SQL |
| 临时 schema 名称不安全 | 测试在发出 DDL 前失败 |

## 7. 权限与数据规则

1. 用户 ID 是文章、评论、互动和审计记录的稳定归属标识。
2. 账号只作为登录身份，跨表业务关联使用用户 ID。
3. 注册调用方不能指定昵称、角色、状态或时间字段。
4. 普通用户不能把自己提升为管理员。
5. 普通用户不能直接禁用或恢复自己。
6. 管理员角色只能通过后续受控初始化或后台权限流程产生。
7. Entity 不得直接作为 Controller 响应。
8. 后续公开查询已注销用户时不得返回账号或密码哈希。
9. 用户注销不自动删除其文章；文章生命周期由文章模块独立管理。

## 8. 需求级验收结果

从业务视角，本模块完成后应满足：

- 相同语义的账号只能创建一个用户。
- 用户注销后账号不能被重新注册。
- 用户注销后历史业务数据仍保留归属。
- 外部调用方不能指定管理员角色或任意用户状态。
- 数据库中不存在明文密码字段。
- 公开接口不存在直接返回用户 Entity 的路径。
- 不依赖 Redis、分布式锁或微服务完成账号防重。

---

# 第二部分：技术设计与开发步骤

## 步骤一：数据库迁移与用户表

### 9.1 目标

通过 Flyway 在空 MySQL schema 中创建第一张正式业务表，并由数据库保证账号唯一、字段有效和状态时间一致。

### 9.2 涉及文件

```text
pom.xml
src/main/resources/application.yml
src/main/resources/db/migration/V1__create_user_account.sql
.env.example
```

### 9.3 需要完成的内容

1. 添加 Spring Boot 4 Flyway starter。
2. 添加 Flyway MySQL 数据库支持。
3. 配置迁移校验和安全选项。
4. 创建 `V1__create_user_account.sql`。
5. 移除固定 `TEST_DB_*` 测试库示例。
6. 明确 V1 只面向空 schema。

### 9.4 Flyway 依赖

版本由 Spring Boot BOM 管理：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

### 9.5 Flyway 配置

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: false
    validate-on-migrate: true
    clean-disabled: true
```

迁移规则：

1. 生产和开发环境使用相同迁移脚本。
2. 已经在共享环境执行的迁移不得直接修改。
3. 后续结构变化使用新的版本脚本。
4. 禁止应用启动时自动执行 `repair`。
5. 迁移失败必须阻止应用启动。
6. 已执行迁移出现问题时使用新迁移前滚修复，不自动执行逆向 DDL。

### 9.6 表结构

MySQL 最低兼容版本为 8.0.16。

```sql
CREATE TABLE user_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    account VARCHAR(32)
        CHARACTER SET ascii
        COLLATE ascii_general_ci
        NOT NULL COMMENT '登录账号，统一小写',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    nickname VARCHAR(32) NOT NULL COMMENT '展示昵称',
    role TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '角色：0-USER，1-ADMIN',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '状态：0-ACTIVE，1-DISABLED，2-CANCELLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',
    cancelled_at DATETIME(3) NULL COMMENT '注销时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_account (account),
    CONSTRAINT chk_user_account_account_format
        CHECK (
            REGEXP_LIKE(account, '^[a-z0-9_]{4,32}$', 'c')
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
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户账号表';
```

第一版只建立：

- 主键索引 `PRIMARY KEY (id)`。
- 账号唯一索引 `uk_user_account_account (account)`。

当前没有真实后台列表 SQL，因此不提前为昵称、角色和状态增加索引。

### 9.7 异常与边界

- V1 只能用于空 schema。
- 非空且没有 Flyway 历史的 schema 必须停止并人工确认。
- 数据库只能验证密码哈希的字段结构，不能验证密码算法。
- 不使用数据库级联删除用户或未来文章数据。
- 不允许为了测试 checksum 修改正式 V1 文件。

### 9.8 本步骤测试

- 空临时 schema 执行 V1 后生成 `flyway_schema_history` 和 `user_account`。
- 对同一 schema 再次执行迁移不重复建表。
- `SHOW CREATE TABLE user_account` 与设计一致。
- `information_schema` 中存在预期索引和检查约束。
- 测试专用迁移副本发生 checksum 变化时校验失败。
- 非法迁移阻止测试应用上下文启动。

### 9.9 本步骤完成标准

- V1 在目标 MySQL 上执行成功。
- 重复迁移保持幂等。
- 全部字段、索引和约束与设计一致。
- Flyway 异常不会被忽略。
- 长期开发 schema 和迁移历史未被测试污染。

## 步骤二：Entity 与枚举

### 10.1 目标

建立与 `user_account` 表一一对应的 Java 数据模型，并使用稳定枚举编码表达角色和状态。

### 10.2 涉及文件

```text
src/main/java/org/victor/stackora/model/entity/UserAccount.java
src/main/java/org/victor/stackora/model/enums/UserRole.java
src/main/java/org/victor/stackora/model/enums/UserStatus.java
```

### 10.3 UserAccount 字段设计

| Java 字段 | Java 类型 | 数据库字段 | 关键注解 |
| --- | --- | --- | --- |
| `id` | `Long` | `id` | `@TableId(type = IdType.AUTO)` |
| `account` | `String` | `account` | `@TableField("account")` |
| `passwordHash` | `String` | `password_hash` | `@TableField("password_hash")` |
| `nickname` | `String` | `nickname` | `@TableField("nickname")` |
| `role` | `UserRole` | `role` | `@TableField("role")` |
| `status` | `UserStatus` | `status` | `@TableField("status")` |
| `createdAt` | `LocalDateTime` | `created_at` | `FieldFill.INSERT` |
| `updatedAt` | `LocalDateTime` | `updated_at` | `FieldFill.INSERT_UPDATE` |
| `cancelledAt` | `LocalDateTime` | `cancelled_at` | 不使用通用自动填充 |

Entity 规则：

1. 使用 `@TableName("user_account")`。
2. 主键使用 `IdType.AUTO`。
3. 不使用 `@TableLogic`。
4. `cancelledAt` 只能由注销状态迁移显式写入。
5. Entity 只承担数据库映射，不进入公开 API。

### 10.4 枚举设计

```java
public enum UserRole {
    USER(0),
    ADMIN(1);
}
```

```java
public enum UserStatus {
    ACTIVE(0),
    DISABLED(1),
    CANCELLED(2);
}
```

每个枚举都必须：

- 使用显式 `int code`。
- 在持久化字段上标记 `@EnumValue`。
- 提供只读 `getCode()`。
- 禁止依赖声明顺序。

### 10.5 异常与边界

- Java 字段名、数据库字段名和 XML 映射必须一致。
- 不向 Entity 增加注册 Request 或公开 VO 的职责。
- 不向用户表增加逻辑删除字段。
- 不在本步骤增加头像等个人资料字段。

### 10.6 本步骤测试

- 枚举 `USER`、`ADMIN` 分别映射为 0、1。
- 枚举 `ACTIVE`、`DISABLED`、`CANCELLED` 分别映射为 0、1、2。
- 插入后自增 ID 能正确回填。
- 查询后角色和状态能恢复为正确枚举。
- Entity 不包含逻辑删除注解。

### 10.7 本步骤完成标准

- Entity 与 V1 字段完全一致。
- 枚举不使用 ordinal。
- 时间字段具有正确填充注解。
- Entity 不承担公开接口模型职责。

## 步骤三：Mapper 数据访问

### 11.1 目标

提供用户基础查询和带期望旧状态的条件更新，确保并发请求不能覆盖注销终态。

### 11.2 涉及文件

```text
src/main/java/org/victor/stackora/mapper/UserAccountMapper.java
src/main/resources/mapper/UserAccountMapper.xml
```

### 11.3 Mapper 方法

`UserAccountMapper` 可以继承 `BaseMapper<UserAccount>` 复用基础持久化能力，但业务调用方不得直接依赖 Mapper。

需要增加：

```java
int disableIfActive(Long userId, LocalDateTime updatedAt);

int restoreIfDisabled(Long userId, LocalDateTime updatedAt);

int cancelIfActiveOrDisabled(
        Long userId,
        LocalDateTime cancelledAt,
        LocalDateTime updatedAt);
```

### 11.4 条件更新 SQL

禁用：

```sql
UPDATE user_account
SET status = 1,
    updated_at = #{updatedAt}
WHERE id = #{userId}
  AND status = 0
  AND cancelled_at IS NULL;
```

恢复：

```sql
UPDATE user_account
SET status = 0,
    updated_at = #{updatedAt}
WHERE id = #{userId}
  AND status = 1
  AND cancelled_at IS NULL;
```

注销：

```sql
UPDATE user_account
SET status = 2,
    cancelled_at = #{cancelledAt},
    updated_at = #{updatedAt}
WHERE id = #{userId}
  AND status IN (0, 1)
  AND cancelled_at IS NULL;
```

### 11.5 异常与边界

- 每个方法返回受影响行数。
- 影响零行不等于数据库故障，表示用户不存在、状态不允许或发生并发变化。
- 禁止先查询 Entity，再使用 `updateById` 覆盖旧快照。
- Mapper 不实现账号规范化、默认角色等业务规则。
- 不提供物理删除用户的专用方法。

### 11.6 本步骤测试

- `ACTIVE` 可以禁用。
- `DISABLED` 可以恢复。
- `ACTIVE` 和 `DISABLED` 可以注销。
- `ACTIVE` 不能直接恢复。
- `DISABLED` 不能重复禁用。
- `CANCELLED` 不能恢复或再次注销。
- 状态不满足时影响行数为 0。

### 11.7 本步骤完成标准

- 三个条件更新方法均使用旧状态作为 `WHERE` 条件。
- 注销状态与注销时间在同一 SQL 中写入。
- 不存在旧 Entity 全字段覆盖路径。
- 状态迁移测试全部通过。

## 步骤四：受控 Service

### 12.1 目标

把账号规范化、字段校验、服务端默认值和状态规则收敛到唯一业务入口。

### 12.2 涉及文件

```text
src/main/java/org/victor/stackora/service/UserAccountService.java
src/main/java/org/victor/stackora/service/impl/UserAccountServiceImpl.java
```

### 12.3 Service 接口

`UserAccountService` 不继承 `IService<UserAccount>`，只暴露：

```java
UserAccount createAccount(String account, String passwordHash);

Optional<UserAccount> findById(Long userId);

Optional<UserAccount> findByAccount(String account);

boolean disableUser(Long userId);

boolean restoreUser(Long userId);

boolean cancelUser(Long userId);
```

接口不得提供：

- 通用 `save`。
- 通用 `updateById`。
- 通用 `removeById`。
- 任意角色设置。
- 任意状态设置。
- 全 Entity 更新。

### 12.4 Service 实现结构

`UserAccountServiceImpl`：

1. 实现 `UserAccountService`。
2. 继承 MyBatis-Plus `ServiceImpl<UserAccountMapper, UserAccount>`，复用基础持久化能力。
3. 继承获得的通用 CRUD 只允许在实现类内部使用，不属于用户账号业务的公开契约。
4. 对外只通过 `UserAccountService` 暴露具有明确业务语义的方法。
5. Controller 和其他业务 Service 禁止依赖 `UserAccountServiceImpl`、`IService<UserAccount>` 或 `UserAccountMapper`。
6. 用户状态迁移必须调用 Mapper 的条件更新方法，禁止使用通用 `updateById` 或 `removeById` 绕过状态机。

推荐结构：

```java
@Service
public class UserAccountServiceImpl
        extends ServiceImpl<UserAccountMapper, UserAccount>
        implements UserAccountService {
}
```

该结构属于工程折中：业务接口保持受控，避免上层直接获得通用 CRUD；实现类保留 MyBatis-Plus 的复用能力，减少当前单体项目中的重复持久化代码。由于 `ServiceImpl` 本身实现了 `IService<UserAccount>`，本方案依赖架构边界测试防止其他组件绕过业务接口。

### 12.5 createAccount 设计

输入：

```text
account
passwordHash
```

处理顺序：

1. 校验 `account` 不为 `null`。
2. 使用 `Locale.ROOT` 将账号转为小写。
3. 校验规范化账号匹配 `^[a-z0-9_]{4,32}$`。
4. 校验 `passwordHash` 非空白且不超过 255 个字符。
5. 创建内部 `UserAccount`。
6. 设置 `account = normalizedAccount`。
7. 设置 `nickname = normalizedAccount`。
8. 设置 `role = UserRole.USER`。
9. 设置 `status = UserStatus.ACTIVE`。
10. Mapper 执行插入。
11. 返回持久化后的用户对象。

调用方没有机会提交昵称、角色、状态和时间字段。

### 12.6 查询设计

`findById`：

- 拒绝 `null` 和非正数 ID。
- 使用 Mapper 主键查询。
- 返回 `Optional.empty()` 表示不存在。

`findByAccount`：

- 使用与创建完全相同的账号规范化规则。
- 禁止创建与查询采用不同格式规则。
- 返回 `Optional.empty()` 表示不存在。

### 12.7 状态迁移设计

- `disableUser` 调用 `disableIfActive`。
- `restoreUser` 调用 `restoreIfDisabled`。
- `cancelUser` 生成同一个毫秒精度时间，同时作为 `cancelledAt` 和 `updatedAt`。
- Mapper 返回 1 时 Service 返回 `true`。
- Mapper 返回 0 时 Service 返回 `false`。
- 返回 0 后不执行无条件覆盖或自动重试。

### 12.8 重复账号处理

Service 可以提前查询账号是否存在，用于更早反馈，但不能以此保证唯一性。

最终行为：

```text
两个并发请求查询都不存在
  -> 一个 INSERT 成功
  -> 另一个被 uk_user_account_account 拒绝
```

本模块保留数据库重复键异常作为服务端证据。后续注册模块再将其转换为稳定的账号冲突业务错误。

### 12.9 本步骤测试

- 输入 `Victor` 后实际保存账号和昵称均为 `victor`。
- 默认角色为 `USER`。
- 默认状态为 `ACTIVE`。
- 调用方不能指定管理员角色。
- `null`、空串、纯空白和非法账号被拒绝。
- `null`、空串、空格、Tab、换行、回车和超长密码哈希被拒绝。
- 按 ID 和规范化账号能够查询用户。
- 三个状态方法正确转换 Mapper 影响行数。

### 12.10 本步骤完成标准

- Service 接口只包含受控方法。
- 实现类允许继承 `ServiceImpl`，但通用 CRUD 不进入业务接口。
- 其他组件只依赖 `UserAccountService`，不能依赖实现类、`IService<UserAccount>` 或 Mapper。
- 账号规范化和默认字段真实生效。
- 状态迁移不使用通用更新。
- Service 测试覆盖正常和异常路径。

## 步骤五：时间字段自动填充

### 13.1 目标

统一应用写入的时间来源、时区和精度，避免应用与数据库对同一时间字段产生不确定竞争。

### 13.2 涉及文件

```text
src/main/java/org/victor/stackora/config/MybatisMetaObjectHandler.java
src/main/java/org/victor/stackora/model/entity/UserAccount.java
```

### 13.3 时间职责

应用侧 `MetaObjectHandler` 是正常 ORM 写入的主要时间来源：

```text
INSERT -> createdAt, updatedAt
UPDATE -> updatedAt
```

数据库侧：

- `DEFAULT CURRENT_TIMESTAMP(3)` 是非应用写入的兜底。
- `ON UPDATE CURRENT_TIMESTAMP(3)` 是非应用更新的兜底。
- 正常 Mapper 写操作应显式携带应用生成的时间。

### 13.4 时间规则

1. 应用和数据库会话时区统一为 `Asia/Shanghai`。
2. 时间统一为毫秒精度。
3. `createdAt` 使用 `FieldFill.INSERT`。
4. `updatedAt` 使用 `FieldFill.INSERT_UPDATE`。
5. `cancelledAt` 不由通用填充器处理。
6. 注销时 `cancelledAt` 和 `updatedAt` 在同一业务动作中生成和写入。

### 13.5 异常与边界

- 更新不能修改 `createdAt`。
- `updatedAt` 不能早于 `createdAt`。
- `cancelledAt` 不能早于 `createdAt`。
- 非注销状态不能携带 `cancelledAt`。
- 注销状态必须携带 `cancelledAt`。

### 13.6 本步骤测试

- 插入时 `createdAt` 和 `updatedAt` 非空。
- 创建时间和更新时间保存为毫秒精度。
- 更新后 `createdAt` 不变。
- 更新后 `updatedAt` 不早于旧值。
- 注销时同时写入 `cancelledAt` 和 `updatedAt`。
- 数据库读取后的时间与应用写入时间一致。

### 13.7 本步骤完成标准

- 应用和数据库的时间职责明确且真实生效。
- Entity 填充注解完整。
- 时间字段测试全部通过。

## 步骤六：迁移、约束与并发测试

### 14.1 目标

在真实 MySQL 行为上验证迁移、数据库约束、账号唯一性和状态并发正确性。

### 14.2 涉及文件

```text
src/test/java/org/victor/stackora/persistence/UserAccountMigrationTest.java
src/test/java/org/victor/stackora/service/UserAccountServiceTest.java
src/test/resources/
```

### 14.3 临时 schema 设计

1. 测试复用基础数据库连接的主机、端口和凭据。
2. schema 名称由测试代码内部随机生成。
3. 名称必须匹配 `^stackora_it_[a-z0-9]{12,32}$`。
4. 禁止接受外部传入的待删除 schema 名称。
5. 测试工具记录本次进程成功创建的 schema 集合。
6. 只有同时满足以下条件才能执行删除：
   - 名称匹配白名单。
   - 名称不是 `stackora`。
   - 名称存在于本次创建集合。
7. 名称通过校验后才能作为带反引号的 SQL 标识符。
8. 权限不足时测试必须在执行 DDL 前失败。
9. 清理失败必须使测试失败并报告 schema 名称。

### 14.4 迁移测试

- 首次执行 V1 成功。
- 生成 `flyway_schema_history`。
- 生成 `user_account`。
- 重复执行迁移不重复建表。
- 测试专用迁移副本 checksum 变化时校验失败。
- 正式 V1 文件不因测试被修改。
- 测试结束后长期开发 schema 没有变化。

### 14.5 数据库约束测试

直接 SQL 验证：

- 相同账号不能重复写入。
- 大写账号和非法字符账号被拒绝。
- 账号长度非法时被拒绝。
- 密码哈希为 `null`、空串或纯空白时被拒绝。
- 昵称为 `null`、空串或纯空白时被拒绝。
- 普通空格、Tab、换行、回车和混合空白均被覆盖。
- 非法角色和状态编码被拒绝。
- `CANCELLED` 缺少 `cancelled_at` 时被拒绝。
- 非注销状态携带 `cancelled_at` 时被拒绝。
- 时间顺序非法时被拒绝。

### 14.6 重复账号并发测试

使用两个独立连接或事务，同时写入同一规范化账号。

预期：

- 一个事务提交成功并获得用户 ID。
- 一个事务因 `uk_user_account_account` 失败。
- 最终按规范化账号查询只有一行。
- 重复执行测试结果稳定。

### 14.7 状态并发测试

场景：

```text
Request A 准备执行 DISABLED -> ACTIVE
Request B 先执行 DISABLED -> CANCELLED
Request A 的 WHERE status=DISABLED 更新影响零行
```

必须证明：

- `CANCELLED` 不会被旧恢复请求覆盖。
- 注销与禁用并发后只会得到合法状态。
- 不需要 JVM 锁、分布式锁或乐观锁版本列。

### 14.8 本步骤完成标准

- 迁移、约束和并发测试真实连接 MySQL。
- 测试结果可重复。
- 测试不依赖执行顺序。
- 测试不会污染或删除长期开发 schema。
- 并发测试具有明确成功数、失败类型和最终行数断言。

## 步骤七：架构边界测试

### 15.1 目标

通过自动化测试保护 Service 和 Mapper 分层，防止后续业务绕过受控入口。

### 15.2 涉及文件

```text
src/test/java/org/victor/stackora/architecture/UserAccountArchitectureTest.java
```

### 15.3 需要验证的边界

1. `UserAccountService` 不继承 `IService`。
2. `UserAccountService` 不暴露通用 CRUD。
3. `UserAccountServiceImpl` 允许继承 `ServiceImpl<UserAccountMapper, UserAccount>`。
4. 继承的通用 CRUD 只能作为实现类内部能力，不能形成上层业务依赖。
5. 除 `UserAccountServiceImpl` 外，Spring 业务组件不得依赖：
   - `UserAccountMapper`
   - `UserAccountServiceImpl`
   - `IService<UserAccount>`
6. Controller 和其他业务 Service 必须通过 `UserAccountService` 调用用户账号能力。
7. 扫描范围覆盖：
   - 构造器参数。
   - 字段注入。
   - Setter 注入。
   - 其他注入方法参数。
8. Entity 不得作为 Controller 方法返回类型。

### 15.4 异常与边界

- Mapper 继承 `BaseMapper` 只用于持久化适配器内部复用。
- Controller 和后续业务 Service 只能依赖 `UserAccountService`。
- 实现类继承 `ServiceImpl` 不代表允许上层调用 `save`、`updateById`、`removeById`、`list`、`page` 等通用方法。
- 架构测试用于防止直接依赖，不代替业务行为测试。

### 15.5 本步骤完成标准

- 合法分层代码通过测试。
- 增加非法 Mapper 或具体实现依赖时测试能够失败。
- 通用 CRUD 不能从用户业务 Service 接口访问。

---

# 第三部分：整体总结与验收

## 16. 完整完成清单

以下项目用于模块最终验收；必须依据真实代码和测试结果逐项确认。

### 16.1 数据库与迁移

- [x] Flyway starter 和 MySQL 支持依赖已配置。
- [x] V1 能在临时空 schema 中成功执行。
- [x] `flyway_schema_history` 正常生成。
- [x] `user_account` 字段与设计一致。
- [x] 主键和账号唯一索引存在。
- [x] 全部检查约束存在并真实生效。
- [x] 重复执行迁移不会重复建表。
- [ ] checksum 异常能够阻止迁移。
- [ ] `.env.example` 不再使用固定 `TEST_DB_*`。

### 16.2 Entity 与枚举

- [x] `UserAccount` 字段映射完整。
- [x] 主键使用 `IdType.AUTO`。
- [x] 用户表没有逻辑删除注解。
- [x] 时间字段填充注解完整。
- [x] `UserRole` 使用稳定数值编码。
- [x] `UserStatus` 使用稳定数值编码。
- [ ] 枚举映射不依赖 ordinal。

### 16.3 Mapper

- [ ] Mapper 基础查询可用。
- [ ] 禁用使用条件更新。
- [ ] 恢复使用条件更新。
- [ ] 注销使用条件更新。
- [ ] 注销状态和时间在同一 SQL 中写入。
- [ ] 状态冲突时影响行数为 0。
- [ ] 不存在旧 Entity 全字段覆盖路径。

### 16.4 Service

- [ ] Service 接口不继承 `IService`。
- [ ] Service 实现继承 `ServiceImpl<UserAccountMapper, UserAccount>` 复用基础持久化能力。
- [ ] Controller 和其他业务 Service 不依赖实现类、`IService<UserAccount>` 或 Mapper。
- [ ] 通用 CRUD 未被作为用户账号业务接口暴露。
- [ ] `createAccount` 完成账号规范化。
- [ ] `createAccount` 完成字段校验。
- [ ] 默认昵称等于规范化账号。
- [ ] 默认角色为 `USER`。
- [ ] 默认状态为 `ACTIVE`。
- [ ] 按 ID 查询可用。
- [ ] 按账号查询使用相同规范化规则。
- [ ] 禁用、恢复和注销方法可用。
- [ ] Service 不暴露物理删除或任意更新。

### 16.5 时间字段

- [ ] `MetaObjectHandler` 已实现。
- [ ] 插入时填充创建和更新时间。
- [ ] 更新时只更新 `updatedAt`。
- [ ] 注销时间由注销动作显式写入。
- [ ] 应用与数据库使用统一时区。
- [ ] 时间统一为毫秒精度。

### 16.6 测试与安全

- [ ] 迁移测试通过。
- [ ] 数据库约束测试通过。
- [ ] Service 正常和异常测试通过。
- [ ] 重复账号并发测试通过。
- [ ] 状态迁移并发测试通过。
- [ ] 架构边界测试通过。
- [ ] 临时 schema 清理安全测试通过。
- [ ] 测试没有污染长期开发数据。
- [ ] 密码哈希未进入日志、VO 或异常消息。

## 17. 交付文件清单

```text
pom.xml
.env.example
src/main/resources/application.yml
src/main/resources/db/migration/V1__create_user_account.sql
src/main/resources/mapper/UserAccountMapper.xml
src/main/java/org/victor/stackora/config/MybatisMetaObjectHandler.java
src/main/java/org/victor/stackora/model/entity/UserAccount.java
src/main/java/org/victor/stackora/model/enums/UserRole.java
src/main/java/org/victor/stackora/model/enums/UserStatus.java
src/main/java/org/victor/stackora/mapper/UserAccountMapper.java
src/main/java/org/victor/stackora/service/UserAccountService.java
src/main/java/org/victor/stackora/service/impl/UserAccountServiceImpl.java
src/test/java/org/victor/stackora/persistence/UserAccountMigrationTest.java
src/test/java/org/victor/stackora/service/UserAccountServiceTest.java
src/test/java/org/victor/stackora/architecture/UserAccountArchitectureTest.java
```

## 18. 整体调用链

当前模块验证调用链：

```text
Integration Test
  -> UserAccountService
  -> UserAccountServiceImpl
  -> UserAccountMapper
  -> MyBatis-Plus / Mapper XML
  -> MySQL
```

后续注册模块调用链：

```text
RegisterController
  -> RegisterService
  -> PasswordHasher
  -> UserAccountService
  -> UserAccountMapper
  -> MySQL
```

## 19. 测试命令与预期结果

至少执行：

```bash
./mvnw clean test
./mvnw -Dtest=UserAccountMigrationTest test
./mvnw -Dtest=UserAccountServiceTest test
./mvnw -Dtest=UserAccountArchitectureTest test
```

预期结果：

- Maven 输出 `BUILD SUCCESS`。
- 所有目标测试被实际执行。
- failures、errors、skipped 均为 0。
- 临时 schema 在测试后被安全删除。
- 长期 `stackora` schema 和迁移历史没有变化。

## 20. 模块整体验收标准

本模块只有同时满足以下条件才视为完成：

1. Flyway 能稳定创建并校验用户表。
2. SQL、Entity、枚举、Mapper 和 Service 字段契约一致。
3. 账号规范化和唯一性真实生效。
4. 默认昵称、角色和状态真实生效。
5. Service 写入入口受到控制。
6. 所有状态迁移均为条件更新。
7. `CANCELLED` 不会被并发旧请求覆盖。
8. 用户注销后记录和账号唯一占用继续保留。
9. 时间字段按统一规则维护。
10. 所有关键测试通过。
11. 没有明显密码哈希泄露路径。
12. 文档、SQL、代码和测试保持一致。

## 21. 已知风险与技术债

### 21.1 本地数据库测试权限

迁移测试依赖本地 MySQL 测试账号具备创建和删除临时 schema 的权限。权限不足时测试必须在执行 DDL 前失败。后续可引入 Testcontainers 消除本机权限和环境差异。

### 21.2 账号体系扩展

第一版只支持 ASCII 小写账号。邮箱、手机号、第三方登录和账号改名需要独立设计。

### 21.3 注销隐私处理

本模块保留账号记录以维护业务归属。正式注销模块还需要设计个人资料匿名化、登录凭据失效、Session 清理和数据保留期限。

### 21.4 后台查询索引

当前没有真实后台用户列表 SQL，因此不提前为角色和状态建立索引。后续根据查询条件、排序方式和 `EXPLAIN` 决定。

### 21.5 头像与个人资料

本模块不保存头像。后续个人资料模块可以通过新迁移向 `user_account` 增加 `avatar_key`，数据库不保存图片二进制或临时签名 URL。

### 21.6 分页行为

分页插件已经配置，但真实 `COUNT + LIMIT` 行为在第一个列表业务中验证。

## 22. 后续模块边界

完成用户持久化基础后，后续模块按独立设计推进：

1. 用户注册与密码哈希。
2. 用户登录与 HttpSession。
3. 当前用户和退出登录。
4. 用户个人资料、昵称与头像。
5. 管理员用户治理。
6. 用户注销和隐私处理。

本模块不提前实现这些 HTTP 接口和认证逻辑。

## 23. 进入用户注册模块的前置条件

进入用户注册模块前必须确认：

- 第 16 节所有必要完成项均有代码或测试证据。
- 第 19 节全部测试命令成功执行。
- 当前 Git 变更经过代码审查。
- V1 已与最终设计一致。
- 数据库结构、状态并发和敏感数据验收均已满足。

## 24. Paicoding 后续对比点

能够读取 Paicoding 实际源码后，再对比：

- 用户主键和账号唯一性。
- 用户表字段边界。
- 密码字段设计。
- 角色与状态编码。
- 用户逻辑删除或状态生命周期。
- Mapper 与 Service 分层。
- 数据库迁移方式。

当前参考目录没有可读源码，不对其实现作推断。
