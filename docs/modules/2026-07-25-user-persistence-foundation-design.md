# Stackora T0.3 用户持久化基础需求与技术设计

## 0. 文档使用方式

本文档只设计用户数据进入 MySQL 之前必须具备的持久化基础，不设计用户注册业务。

开发顺序固定为：

```text
数据库迁移与约束
  -> Entity、枚举与时间映射
  -> Mapper 基础读写
  -> 真实 MySQL 持久化验证
```

每个需求在同一章节内说明具体设计、设计思路、涉及文件、正常与异常流程、测试和完成标准。

---

## 1. 模块概览

### 1.1 用户价值

注册、登录、文章作者和评论归属都依赖稳定的用户数据。当前模块先建立可靠的数据底座，使后续业务能够安全使用用户表，而不是提前实现注册流程。

### 1.2 完成后的能力

完成本模块后，项目应具备：

1. 使用 Flyway 在空 MySQL schema 中创建 `user_account` 表。
2. 使用自增 `BIGINT` 生成稳定用户 ID。
3. 通过数据库约束保证账号格式、账号唯一性和字段合法性。
4. 使用 Entity 和显式枚举编码正确映射用户表。
5. 由 MySQL 自动生成并维护创建时间和更新时间。
6. 使用 `UserAccountMapper` 完成插入、按 ID 查询和按账号查询。
7. 通过真实 MySQL 测试验证迁移、约束、映射、时间和唯一索引。

### 1.3 当前交付范围

- Flyway 依赖和配置。
- `V1__create_user_account.sql`。
- `user_account` 表、约束和账号唯一索引。
- `UserAccount` Entity。
- `UserRole`、`UserStatus` 枚举。
- 数据库时间默认值、自动更新时间及 Java 字段映射。
- `UserAccountMapper`。
- Flyway 和 Mapper 的真实 MySQL 集成测试。

### 1.4 当前明确不做

本模块不创建或实现：

- `RegisterRequest`。
- `UserAccountService` 和 `UserAccountServiceImpl`。
- `register`、`userRegister` 或 `createAccount`。
- `PasswordEncoder` 和密码编码配置。
- 注册 Controller、注册接口和注册响应。
- 注册相关业务错误码。
- 禁用、恢复和注销用户的业务方法。
- `CANCELLED`、`cancelled_at` 和状态迁移 SQL。
- 用户头像、简介和资料修改。
- 用户列表、搜索和分页。
- Redis、Redisson、RabbitMQ、Elasticsearch。

### 1.5 模块边界

本模块只证明：

```text
数据库结构正确
+ Java 映射正确
+ Mapper 能够真实读写
+ 数据库约束能够阻止非法数据
```

Mapper 测试可以构造一条测试 Entity 并执行 `insert`，但这不代表系统已经具备正式注册功能。

---

## 2. 需求与开发顺序

| 顺序 | 需求 | 可验证交付物 | 依赖 |
| --- | --- | --- | --- |
| 1 | 建立用户表迁移与数据库约束 | 空 schema 能创建结构正确的 `user_account` | MySQL 可连接 |
| 2 | 建立 Java 数据与数据库时间映射 | Entity、枚举、自增 ID 和数据库生成时间能够正确映射 | 需求 1 |
| 3 | 建立 Mapper 基础读写 | Mapper 能插入并按 ID、账号查询 | 需求 1、2 |
| 4 | 建立真实持久化验证 | `mvn test` 能执行迁移和 Mapper 集成测试 | 需求 1～3 |

当前模块不包含 Service，因此不存在“先写 `createAccount` 还是先写 `register`”的问题。

---

## 3. 需求一：建立用户表迁移与数据库约束

### 3.1 需求行为

应用连接空 MySQL schema 时，Flyway 必须自动创建 `user_account`。数据库必须拒绝重复账号和不符合数据契约的记录。

### 3.2 具体设计

#### 3.2.1 Flyway 依赖

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

T0.3 不需要 `spring-boot-starter-validation` 和 `spring-security-crypto`，它们属于注册模块。

#### 3.2.2 Flyway 配置

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: false
    validate-on-migrate: true
    clean-disabled: true
```

迁移规则：

1. V1 只面向空 schema。
2. 已在共享环境执行的迁移不得原地修改。
3. 后续表结构变化必须增加新的版本迁移。
4. checksum、SQL 或权限错误必须阻止应用启动。
5. 应用不得自动执行 `clean` 或 `repair`。
6. 共享环境使用前滚迁移修复，不使用破坏性回滚。

#### 3.2.3 账号规则

账号存储前必须规范化为小写，数据库保存的账号必须匹配：

```regex
^[a-z0-9][a-z0-9_]{2,30}[a-z0-9]$
```

规则含义：

- 总长度 4～32。
- 只包含小写字母、数字和下划线。
- 首尾必须是字母或数字。
- 数据库账号值始终为规范化结果。

未来注册请求可以允许大写输入，但写入数据库前必须转换为小写。

#### 3.2.4 用户表

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
        COMMENT '状态：0-ACTIVE，1-DISABLED',
    created_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_account (account),
    CONSTRAINT chk_user_account_account_format
        CHECK (
            REGEXP_LIKE(
                account,
                '^[a-z0-9][a-z0-9_]{2,30}[a-z0-9]$',
                'c'
            )
        ),
    CONSTRAINT chk_user_account_password_hash
        CHECK (
            REGEXP_LIKE(
                password_hash,
                '[^[:space:]]',
                'c'
            )
        ),
    CONSTRAINT chk_user_account_nickname
        CHECK (
            REGEXP_LIKE(
                nickname,
                '[^[:space:]]',
                'c'
            )
        ),
    CONSTRAINT chk_user_account_role
        CHECK (role IN (0, 1)),
    CONSTRAINT chk_user_account_status
        CHECK (status IN (0, 1)),
    CONSTRAINT chk_user_account_time_order
        CHECK (updated_at >= created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户账号表';
```

第一版只创建：

```text
PRIMARY KEY (id)
UNIQUE KEY uk_user_account_account (account)
```

没有真实列表查询前，不预建昵称、角色和状态索引。

### 3.3 设计思路与取舍

- 使用自增 `BIGINT`：当前是单体 MySQL 应用，不需要分布式 ID。
- 使用 Flyway：数据库结构必须可重复部署和审查。
- Java 校验不能代替数据库约束：数据库还可能接收脚本或其他程序写入。
- 唯一索引是并发防重的最终保障：“先查询再插入”不能保证唯一。
- `password_hash` 只规定存储边界，当前模块不负责产生哈希。
- 当前没有注销需求，因此不提前增加注销状态和注销时间。

### 3.4 涉及文件与契约

```text
pom.xml
src/main/resources/application.yml
src/main/resources/db/migration/V1__create_user_account.sql
```

数据库契约：

- 表名固定为 `user_account`。
- 唯一索引名固定为 `uk_user_account_account`。
- 正式 V1 一旦进入共享环境就不可修改。
- MySQL 必须实际执行 CHECK 约束。

### 3.5 正常流程

```text
应用或集成测试启动
  -> Flyway 连接空 schema
  -> 执行 V1
  -> 创建 flyway_schema_history
  -> 创建 user_account
  -> 校验 checksum
  -> 应用继续启动
```

第二次启动时，Flyway识别迁移已经执行，不重复建表。

### 3.6 异常、权限与并发边界

- 非空且没有 Flyway 历史的 schema：停止并人工确认，不能自动 baseline。
- 迁移 SQL、checksum 或数据库权限异常：启动失败。
- 数据库连接信息不得写入仓库或输出到客户端。
- 密码哈希不得进入普通日志。
- 如果旧版 V1 已经在共享环境执行，不得直接删除字段；需要新增前滚迁移。
- 仅在可丢弃的本地测试 schema 执行过时，可以重新创建测试 schema 验证最终 V1。

### 3.7 测试设计

- 空临时 schema 首次迁移成功。
- 生成 `flyway_schema_history` 和 `user_account`。
- 同一 schema 重复迁移不重复建表。
- 元数据中存在预期字段、主键、唯一索引和 CHECK 约束。
- 大写账号、首尾下划线、非法字符和错误长度被拒绝。
- 空白密码哈希和空白昵称被拒绝。
- 非法角色、状态和时间顺序被拒绝。
- 相同账号第二次插入被唯一索引拒绝。

### 3.8 完成标准

- [x] Flyway 可在空测试 schema 执行 V1。
- [x] 表字段、索引和约束与设计一致。
- [x] 重复迁移保持幂等。
- [x] 数据库真实拒绝非法记录。
- [x] 表中没有注销或逻辑删除字段。

---

## 4. 需求二：建立 Java 数据与数据库时间映射

### 4.1 需求行为

Java 必须能够正确映射用户表字段、枚举编码、自增 ID 和数据库生成时间。MySQL 是创建时间与更新时间的唯一写入方；Java 在写入后通过重新查询获得数据库中的最终时间值。

### 4.2 具体设计

#### 4.2.1 Entity 字段

| Java 字段 | Java 类型 | 数据库字段 | 关键映射 |
| --- | --- | --- | --- |
| `id` | `Long` | `id` | `@TableId(type = IdType.AUTO)` |
| `account` | `String` | `account` | `@TableField("account")` |
| `passwordHash` | `String` | `password_hash` | `@TableField("password_hash")` |
| `nickname` | `String` | `nickname` | `@TableField("nickname")` |
| `role` | `UserRole` | `role` | 枚举显式编码 |
| `status` | `UserStatus` | `status` | 枚举显式编码 |
| `createdAt` | `LocalDateTime` | `created_at` | `@TableField("created_at")` |
| `updatedAt` | `LocalDateTime` | `updated_at` | `@TableField("updated_at")` |

Entity 使用：

```java
@TableName("user_account")
```

时间字段使用：

```java
@TableField("created_at")
private LocalDateTime createdAt;

@TableField("updated_at")
private LocalDateTime updatedAt;
```

Entity 不包含：

```text
cancelledAt
@TableLogic
注册 DTO 字段
Controller 序列化职责
```

#### 4.2.2 枚举编码

角色：

```text
USER  -> 0
ADMIN -> 1
```

状态：

```text
ACTIVE   -> 0
DISABLED -> 1
```

枚举要求：

- 使用显式 `int code`。
- 持久化字段标记 `@EnumValue`。
- 提供只读 `getCode()`。
- 禁止使用 `Enum.ordinal()`。

本模块只验证编码映射，不实现角色或状态变更业务。

#### 4.2.3 数据库时间所有权

时间规则：

- MySQL 是 `created_at` 和 `updated_at` 的唯一权威写入方。
- 插入记录时，两个字段均由 `DEFAULT CURRENT_TIMESTAMP(3)` 生成。
- 真实数据更新时，`ON UPDATE CURRENT_TIMESTAMP(3)` 自动刷新 `updated_at`。
- `CHECK (updated_at >= created_at)` 保证时间顺序合法。
- 两个字段只用于接收查询结果；T0.3 不在 Entity 字段上增加时间生成或更新时间逻辑。
- 新建 Entity 时不设置这两个字段，使 INSERT 使用数据库默认值。
- 后续更新业务字段时必须使用只包含目标业务列的条件更新或专用 SQL，不得把已查询的完整 Entity 直接传给 `updateById`，否则非空旧时间可能参与 UPDATE。
- 条件更新和自定义 SQL 不得主动设置这两个时间字段，使 MySQL 的 `ON UPDATE` 规则生效。
- MyBatis-Plus 插入后只保证回填自增 ID，不保证把数据库默认生成的时间同步到原 Entity；需要通过 `selectById` 重新查询最终时间。
- 数据库连接时区应与项目统一时区一致，时间精度统一到毫秒。

### 4.3 设计思路与取舍

- 显式枚举编码比 magic number 更易理解，比 ordinal 更稳定。
- 由数据库单独管理时间可以避免应用与数据库同时写入造成时间来源不一致。
- 数据库默认值同时覆盖 Mapper 写入和合法的非 ORM 写入，不需要为当前需求增加额外组件。
- 只有未来出现“插入后不重新查询也必须立即获得时间”或“需要适配不支持当前时间语法的数据库”等真实需求时，才重新评估应用层时间管理。
- Entity 不直接作为 HTTP 响应，避免暴露 `passwordHash`。
- 避免使用会把 `passwordHash` 自动写入 `toString()` 的实现；如果使用 Lombok，应显式排除敏感字段。

### 4.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/model/entity/UserAccount.java
src/main/java/org/victor/stackora/model/enums/UserRole.java
src/main/java/org/victor/stackora/model/enums/UserStatus.java
```

稳定编码：

```text
USER=0
ADMIN=1
ACTIVE=0
DISABLED=1
```

### 4.5 正常流程

```text
Mapper INSERT
  -> 枚举转换为显式数值
  -> MySQL 生成自增 ID、created_at 和 updated_at
  -> MyBatis-Plus 回填 ID
  -> Mapper 按 ID 重新查询
  -> 获得数据库生成的 createdAt、updatedAt
```

```text
Mapper SELECT
  -> 数值转换为枚举
  -> DATETIME(3) 转为 LocalDateTime
  -> 返回 UserAccount
```

### 4.6 异常、权限与并发边界

- 未知枚举编码属于数据异常，不能静默映射为默认值。
- Entity、DDL 和 XML 字段必须一致。
- Entity 不得直接进入 Controller 返回值。
- 日志不得输出完整 Entity 或密码哈希。
- 当前不增加乐观锁、逻辑删除或生命周期方法。

### 4.7 测试设计

- 插入后自增 ID 正确回填。
- `USER/ADMIN` 能双向映射为 0/1。
- `ACTIVE/DISABLED` 能双向映射为 0/1。
- 插入后按 ID 重新查询，两个时间字段均非空且 `updatedAt` 不早于 `createdAt`。
- 等待跨过毫秒精度边界后更新真实业务字段并重新查询，`createdAt` 不变，`updatedAt` 晚于更新前的值。
- Entity 不包含逻辑删除和注销字段。
- Entity 的字符串表示不包含密码哈希。

### 4.8 完成标准

- [x] Entity 与 DDL 完全一致。
- [x] 枚举不依赖 ordinal。
- [x] Java 不生成或更新时间；写操作不显式提交两个时间字段，DDL 是其唯一写入规则。
- [x] ID、枚举和数据库生成时间的映射通过真实 Mapper 测试。
- [x] Entity 不承担公开响应职责。

---

## 5. 需求三：建立 Mapper 基础读写

### 5.1 需求行为

持久化层必须能够插入用户测试记录，并按 ID 或账号查询。当前只验证数据库访问能力，不实现注册用例。

### 5.2 具体设计

`UserAccountMapper`：

```java
public interface UserAccountMapper
        extends BaseMapper<UserAccount> {
}
```

当前能力来自 `BaseMapper`：

```java
insert(entity)
selectById(id)
selectOne(wrapper)
```

按账号查询使用 Lambda 条件：

```java
LambdaQueryWrapper<UserAccount> wrapper =
        Wrappers.lambdaQuery(UserAccount.class)
                .eq(
                        UserAccount::getAccount,
                        normalizedAccount
                );
```

T0.3 没有自定义 SQL，因此不需要空的 `UserAccountMapper.xml`。以后出现复杂查询时再创建 XML。

### 5.3 设计思路与取舍

- Mapper 只承担数据访问，不负责账号转小写、密码编码或业务异常转换。
- 测试直接使用 Mapper 是为了证明持久化映射，不代表 Controller 可以直接依赖 Mapper。
- 当前没有复杂查询，不为“以后可能使用”提前保留空 XML。
- 不建立 Repository 层，避免与 MyBatis-Plus Mapper 重复。

### 5.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/mapper/UserAccountMapper.java
```

Mapper 扫描可以保留在应用入口或 MyBatis 配置类中的一个位置，不能重复配置，也不应为移动注解产生无关代码修改。

### 5.5 正常流程

```text
测试构造 UserAccount
  -> 设置合法 account、passwordHash、nickname、role、status
  -> Mapper.insert
  -> 回填 ID
  -> Mapper.selectById
  -> 读取数据库生成的时间
  -> Mapper.selectOne(account wrapper)
  -> 断言字段一致
```

### 5.6 异常、权限与并发边界

- Mapper 不接收原始密码。
- Mapper 不负责默认昵称、角色或状态。
- Mapper 不把数据库异常转换为注册错误码。
- 唯一键冲突应保留为数据库异常证据。
- 当前没有 Service、Controller 和权限行为，认证授权不适用。

### 5.7 测试设计

- 插入合法 Entity 影响一行。
- 插入后 ID 回填。
- 按 ID 查询得到相同记录。
- 按规范化账号查询得到相同记录。
- 查询不存在的 ID 返回 `null`。
- 插入相同账号时数据库抛出唯一键异常。
- 事务型 Mapper 测试结束后回滚测试数据。

### 5.8 完成标准

- [x] Mapper 继承 `BaseMapper<UserAccount>`。
- [x] 没有无用途的自定义方法和 XML。
- [x] 插入、ID 查询和账号查询在真实 MySQL 上通过。
- [x] 数据库异常没有被 Mapper 吞掉。
- [x] Mapper 不包含注册业务。

---

## 6. 需求四：建立真实 MySQL 持久化验证

### 6.1 需求行为

默认测试命令必须真实执行 T0.3 集成测试。不能出现“构建成功，但迁移和 Mapper 测试没有运行”的假绿结果。

### 6.2 具体设计

#### 6.2.1 测试类型

迁移测试验证：

- 空 schema 首次迁移。
- 重复迁移。
- Flyway 历史。
- 表、索引和 CHECK 约束元数据。
- 非法数据被拒绝。

Mapper 集成测试验证：

- ID 回填。
- Entity 和枚举映射。
- 数据库创建、更新时间生成与重新查询映射。
- 按 ID 和账号查询。
- 唯一索引冲突。

#### 6.2.2 测试发现规则

如果使用默认 Surefire，测试类名必须匹配默认规则，例如：

```text
UserAccountMigrationTest
UserAccountMapperIntegrationTest
```

不要仅使用：

```text
UserAccountServiceIT
```

因为默认 `mvn test` 不保证发现 `*IT`。

另一种方案是显式配置 Failsafe 并使用：

```bash
./mvnw verify
```

当前阶段推荐使用 `*Test` 命名，让项目统一通过 `./mvnw test` 验证。

#### 6.2.3 数据库隔离

迁移测试使用临时 schema：

```text
stackora_it_<timestamp>_<random>
```

安全规则：

1. schema 名必须匹配严格白名单。
2. 测试记录本次运行创建的 schema。
3. 删除前验证前缀、白名单和本次运行记录。
4. 任一验证失败时拒绝执行 `DROP DATABASE`。
5. 不在长期开发 schema 上测试首次迁移和 checksum。

Mapper 测试可以连接测试 schema，并使用测试事务回滚 DML。

#### 6.2.4 测试配置

创建：

```text
src/test/resources/application-test.yml
```

数据库连接来自环境变量，不写入真实凭据。测试配置必须明确映射实际使用的变量，不能只在 `.env.example` 声明未被 Spring 读取的变量。

### 6.3 设计思路与取舍

- Mock 只能验证方法调用，不能证明 Flyway、MySQL 约束和枚举映射。
- 构建日志必须出现持久化测试类和用例数。
- 临时 schema 默认安全拒绝删除，避免误删开发数据库。
- 当前不强制 Testcontainers；开发者提供的独立测试 MySQL 也可以。
- 注册 Service 测试和密码编码测试不能替代 T0.3 证据。

### 6.4 涉及文件与契约

```text
src/test/java/org/victor/stackora/persistence/UserAccountMigrationTest.java
src/test/java/org/victor/stackora/mapper/UserAccountMapperIntegrationTest.java
src/test/resources/application-test.yml
```

测试名称和 Maven 配置必须保证默认验收命令能够发现它们。

### 6.5 正常流程

```text
./mvnw clean test
  -> 编译生产和测试代码
  -> 创建并校验临时 schema
  -> Flyway 执行 V1
  -> 执行迁移与约束测试
  -> 执行 Mapper 映射和唯一性测试
  -> 回滚 DML
  -> 安全清理临时 schema
  -> Maven 返回 0
```

### 6.6 异常、权限与并发边界

- 数据库未配置或不可用：测试明确失败，不能跳过后仍报告通过。
- 测试类未被发现：验收失败。
- 临时 schema 删除验证失败：保留 schema 并明确报告，不能盲目删除。
- 数据库凭据不得进入源码、日志和测试报告。
- 测试不得依赖已经存在的开发数据。

### 6.7 测试设计

至少覆盖：

1. Flyway 首次迁移和重复迁移。
2. 表、字段、主键、唯一索引和约束元数据。
3. 非法字段值的数据库拒绝行为。
4. Mapper 插入、ID 回填和查询。
5. 角色、状态枚举双向映射。
6. 数据库创建、更新时间生成与重新查询映射。
7. 相同账号唯一键冲突。
8. 测试数据回滚和临时 schema 安全清理。

### 6.8 完成标准

- [x] `./mvnw clean test` 日志明确出现两个持久化测试类。
- [x] 迁移和 Mapper 测试实际连接真实 MySQL。
- [x] 所有关键数据库约束具有失败测试。
- [x] 测试不污染长期开发 schema。
- [x] 构建成功不是依赖测试未被发现。

---

## 7. 模块级共同约束

### 7.1 分层

当前层次：

```text
测试
  -> UserAccountMapper
  -> MySQL
```

下一注册模块才形成：

```text
Controller
  -> UserAccountService
  -> UserAccountMapper
  -> MySQL
```

### 7.2 敏感数据

- 原始密码不进入 Entity、Mapper 或数据库。
- `passwordHash` 不进入公开响应和普通日志。
- 不直接记录完整 UserAccount。
- 数据库凭据只通过本地环境配置提供。

### 7.3 事务

- 单条 Mapper INSERT 本身具有数据库原子性。
- Mapper 集成测试使用事务回滚测试数据。
- Flyway DDL 不依赖事务回滚实现清理。
- 当前没有跨表业务事务。

### 7.4 并发

- 唯一索引是账号并发防重的基础。
- 当前只验证数据库唯一性，不实现注册并发错误转换。
- 不引入进程锁、Redis 锁或 Redisson。

### 7.5 版本兼容

- JDK 和 MySQL 环境版本由开发者配置。
- Java 编译基线与项目 `pom.xml` 保持一致。
- DDL 使用的 CHECK 约束必须在目标 MySQL 中实际生效。
- 不因为本模块引入微服务或分布式组件。

---

## 8. 完整完成清单

### 8.1 数据库迁移

- [x] Flyway 依赖完整。
- [x] V1 能在空 schema 执行。
- [x] 表、索引和约束符合设计。
- [x] 没有注销和逻辑删除字段。

### 8.2 Java 映射

- [x] Entity 与 DDL 一致。
- [x] 主键使用 `IdType.AUTO`。
- [x] 角色和状态使用显式编码。
- [x] 时间字段使用普通查询映射，由数据库负责生成和更新。
- [x] 密码哈希不会被自动输出。

### 8.3 Mapper

- [x] Mapper 继承 `BaseMapper`。
- [x] 没有无用途的 XML。
- [x] 插入、ID 查询和账号查询真实通过。
- [x] 唯一键异常能够到达调用方。

### 8.4 测试

- [x] `mvn test` 能发现持久化测试。
- [x] Flyway 和 Mapper 测试连接真实 MySQL。
- [x] 非法数据和重复账号由数据库拒绝。
- [x] 测试隔离与清理安全。

### 8.5 范围

- [x] 没有 `UserAccountService`。
- [x] 没有注册 DTO、密码编码和注册错误码。
- [x] 没有禁用、恢复和注销行为。
- [x] 没有 Redis、MQ、ES 或分布式锁。

---

## 9. 测试与验收命令

在正式仓库根目录执行：

```bash
./mvnw clean test
```

必须在输出中看到：

```text
UserAccountMigrationTest
UserAccountMapperIntegrationTest
```

提交前执行：

```bash
git diff --check
git status --short
```

检查迁移和 Mapper 测试报告：

```bash
find target/surefire-reports \
  -maxdepth 1 \
  -name 'TEST-*UserAccount*.xml' \
  -print
```

预期结果：

- Maven 退出码为 0。
- 持久化测试确实执行且没有跳过。
- 临时 schema 被安全清理或明确报告保留。
- Git diff 不包含凭据和范围外代码。

---

## 10. 模块整体验收标准

T0.3 只有同时满足以下条件才算完成：

1. Flyway 能够在空测试 schema 创建用户表。
2. DDL 不包含未设计的注销或逻辑删除能力。
3. Entity、枚举和数据库字段完全一致。
4. 自增 ID、枚举和数据库生成时间能够正确映射。
5. Mapper 插入、ID 查询和账号查询真实可用。
6. 数据库约束能够拒绝非法记录。
7. 唯一索引能够拒绝重复账号。
8. 默认 Maven 测试命令确实执行持久化测试。
9. 测试不会污染或误删长期数据库。
10. 当前提交不包含注册 Service 和其他 T1 内容。

---

## 11. 风险与后续技术债

### 11.1 测试数据库权限

创建临时 schema 需要测试数据库权限。应使用本地或专用测试实例，不应提高生产数据库账号权限。

### 11.2 已执行的旧版 V1

如果旧 V1 只在可丢弃本地 schema 执行，可以重新创建测试 schema。如果已经进入共享环境，必须新增前滚迁移，不能重写历史。

### 11.3 密码安全

当前表只保存 `password_hash`。如何产生安全哈希属于注册模块，后续不得使用 `String.hashCode()`、MD5 或可逆加密。

### 11.4 状态管理

当前只定义 `ACTIVE` 和 `DISABLED` 编码，不实现状态变化。禁用、恢复和注销必须在具有明确权限与数据保留规则后独立设计。

### 11.5 列表与分页

当前没有用户列表查询，因此不增加额外索引，也不验证分页 SQL。

---

## 12. 下一模块：用户注册

T0.3 完成后再设计注册模块。注册模块包含：

1. `RegisterRequest` 和 Jakarta Validation。
2. `PasswordEncoder`。
3. `UserAccountService.register(account, rawPassword)`。
4. 账号规范化和重复账号提前提示。
5. 服务端设置默认昵称、`USER` 和 `ACTIVE`。
6. 数据库唯一键异常转换。
7. 注册 Controller 和安全响应。
8. 注册单元测试、接口测试和并发测试。

第一版直接在 `register` 中完成账号构造和保存，不提前拆出 `createAccount`。

只有出现管理员创建、第三方登录或批量导入等第二种创建场景时，才根据真实重复代码提取公共创建能力。

---

## 13. 后续用户治理与注销

禁用、恢复和注销既不属于 T0.3，也不属于第一版注册。

后续单独设计：

```text
后台用户治理
  -> 禁用和恢复
  -> 管理员权限
  -> 状态并发控制
```

```text
用户注销
  -> 身份确认
  -> 数据保留与匿名化
  -> 账号是否允许复用
  -> 历史内容归属
  -> 注销状态和时间字段
```

在这些规则确认前，不增加对应字段、Mapper SQL 和 Service 方法。

---

## 14. Paicoding 后续对比点

只有读取 `paicoding-reference` 的真实源码后才能填写实现对比。

| 维度 | Stackora 当前方案 | 需要核对的参考证据 |
| --- | --- | --- |
| 主键 | MySQL 自增 BIGINT | 参考项目主键策略 |
| 账号约束 | 小写规范 + 唯一索引 | 参考项目账号规则 |
| 数据迁移 | Flyway | 参考项目版本管理方式 |
| Mapper | BaseMapper 基础读写 | 参考项目持久化边界 |
| 时间 | MySQL `DEFAULT` / `ON UPDATE` 为唯一写入方 | 参考项目时间策略 |
| 注册 | 下一模块直接实现 register | 参考项目注册调用链 |
| 测试 | 真实 MySQL 迁移与 Mapper 测试 | 参考项目测试证据 |
