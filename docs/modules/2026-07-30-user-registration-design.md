# Stackora 用户注册需求与技术设计

## 0. 文档使用方式

本文档描述 Stackora 第一版用户注册能力，按照实际开发顺序组织需求。每个需求在同一章节内说明行为、具体设计、设计思路、涉及文件、正常流程、异常与并发边界、测试和完成标准。

开发顺序固定为：

```text
完成 OpenAPI 接口文档基础能力
  -> 定义注册输入输出契约
  -> 配置密码编码能力
  -> 实现注册 Service
  -> 暴露注册 HTTP 接口
  -> 完成单元、接口和并发验证
```

本模块只完成注册闭环，不同时实现登录、Session、退出登录或个人资料。

---

## 1. 模块概览

### 1.1 用户价值

未注册用户需要通过唯一账号和密码创建 Stackora 身份。系统必须保证账号规则稳定、密码不会以明文保存、并发注册不会生成重复用户，并向调用方返回明确且安全的注册结果。

### 1.2 最终能力

完成本模块后，系统具备以下能力：

1. 匿名调用方可以提交账号和密码完成注册。
2. 请求参数不符合账号或密码规则时返回明确的 400 响应。
3. 账号写入前统一转换为小写。
4. 密码通过 Spring Security `PasswordEncoder` 编码后保存，数据库中不出现明文密码。
5. 新用户默认昵称等于规范化账号，角色为 `USER`，状态为 `ACTIVE`。
6. 相同账号及不同大小写形式不能重复注册。
7. 并发注册相同账号时最多产生一条用户记录。
8. 成功响应只暴露用户 ID，不返回 Entity、密码哈希或内部数据库信息。

### 1.3 当前范围

- `RegisterRequest` 注册请求模型。
- `RegisterResponse` 注册响应模型。
- `PasswordEncoder` Bean。
- `UserAccountService.register`。
- `UserAccountServiceImpl`。
- `AuthController` 注册接口。
- 注册相关业务错误码。
- 参数校验和请求体解析异常的统一响应。
- Service 单元测试、Controller 接口测试和真实 MySQL 注册集成测试。

### 1.4 明确不做

本模块不实现：

- 用户登录、退出和 HttpSession。
- 当前登录用户查询。
- 邮箱、手机号、验证码和第三方登录。
- 重复密码确认字段。
- 找回密码和修改密码。
- 用户头像、简介和昵称编辑。
- 管理员创建用户、分配角色或封禁用户。
- 用户注销、恢复和状态流转。
- JWT、Spring Security 认证过滤器或 Sa-Token。
- Redis、分布式锁、消息队列和 Elasticsearch。
- 注册限流、图形验证码和风控系统。
- 前端页面。

### 1.5 前置依赖

- `user_account` 表、账号唯一索引和数据库约束已经存在。
- `UserAccount`、`UserRole`、`UserStatus` 和 `UserAccountMapper` 已可使用。
- `ApiResponse`、`ApiResponseFactory`、`ErrorCode`、`BusinessException` 和 `GlobalExceptionHandler` 已存在。
- 项目已引入 Jakarta Validation 和 Spring Security Crypto。
- 必须先按 `2026-08-01-api-documentation-design.md` 完成接口文档基础能力，使 Springdoc、Swagger UI、Knife4j UI 和 `stackora` OpenAPI 分组可用。
- MySQL 是用户账号数据的事实来源。

---

## 2. 方案选择与开发顺序

### 2.1 可选方案

#### 方案 A：Controller 调用单一用户账号 Service

```text
AuthController
  -> UserAccountService.register
  -> UserAccountServiceImpl
  -> UserAccountMapper
  -> MySQL
```

特点：

- `UserAccountService` 是面向业务的纯接口，不继承 `IService<UserAccount>`。
- `UserAccountServiceImpl` 继承 `ServiceImpl<UserAccountMapper, UserAccount>`，但通用 CRUD 不向 Controller 暴露。
- 注册所需的规范化、密码编码、默认字段和异常转换集中在 `register`。
- 当前只有一种创建用户的场景，不提前拆分 `createAccount`。

#### 方案 B：Controller 直接使用 MyBatis-Plus Service 通用 CRUD

优点是代码少，缺点是 Controller 容易获得 `save`、`updateById`、`removeById` 等无业务语义的方法，无法保证角色、状态和密码字段只通过受控流程写入。

#### 方案 C：增加独立 `RegistrationService` 和持久化 Service

该方案适用于管理员创建、第三方登录、批量导入等多种用户创建场景。当前只有普通注册，立即拆成两个 Service 会增加调用层级和重复模型。

### 2.2 采用方案

第一版采用方案 A。

选择原因：

- 保持 Controller、Service、Mapper 分层清晰。
- 不向上层暴露 MyBatis-Plus 通用 CRUD。
- 注册规则集中，便于测试事务、异常和并发行为。
- 当前不需要额外的领域服务或 `createAccount` 抽象。
- 将来出现第二种用户创建场景后，再根据真实重复代码提取公共创建能力。

### 2.3 需求与开发顺序

| 顺序 | 需求 | 可验证交付物 | 依赖 |
| --- | --- | --- | --- |
| 0 | 完成 OpenAPI 接口文档基础能力 | OpenAPI JSON、Swagger UI 和 Knife4j UI 可用 | `2026-08-01-api-documentation-design.md` |
| 1 | 定义安全、稳定的注册契约 | 请求与响应模型可以编译并完成校验 | 统一响应基础、需求 0 |
| 2 | 建立密码编码能力 | 原始密码可以安全编码并验证 | Spring Security Crypto |
| 3 | 完成唯一账号注册用例 | Service 可以创建用户并处理重复账号 | 用户持久化基础、需求 1～2 |
| 4 | 暴露注册 HTTP 接口 | `POST /api/auth/register` 返回统一响应 | 需求 1～3 |
| 5 | 建立自动化验收 | 正常、异常和并发场景具有测试证据 | 需求 1～4 |

---

## 3. 需求一：定义注册输入输出契约

### 3.1 需求行为

调用方提交账号和原始密码。系统在进入注册业务前校验请求结构，不符合规则的请求返回 HTTP 400，不调用 Service，也不访问数据库。

### 3.2 具体设计

#### 3.2.1 注册请求

使用不可变 `record`：

```java
public record RegisterRequest(
        @NotBlank(message = "账号不能为空")
        @Pattern(
                regexp = "^[A-Za-z0-9][A-Za-z0-9_]{2,30}[A-Za-z0-9]$",
                message = "账号格式不正确"
        )
        String account,

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
        return "RegisterRequest[account=%s, rawPassword=<redacted>]"
                .formatted(account);
    }
}
```

账号输入规则：

- 长度 4～32。
- 只允许字母、数字和下划线。
- 首尾必须是字母或数字。
- HTTP 请求允许大写，Service 写入前统一转换为小写。
- 不自动 `trim`；带前后空格属于非法输入，而不是静默修改账号。

密码输入规则：

- 长度 8～64。
- 不能为 `null`、空串或纯空白。
- 第一版不限制必须同时包含大小写、数字和特殊字符，避免形成可预测的密码组合规则。
- 不对密码执行 `trim`、大小写转换或其他规范化。
- 原始密码只存在于请求处理和密码编码过程，不进入 Entity、日志或响应。
- `record` 默认会把全部组件加入 `toString()`，因此必须显式覆盖并脱敏 `rawPassword`；业务日志仍不得记录完整请求对象。

#### 3.2.2 注册响应

使用专用响应模型：

```java
public record RegisterResponse(
        String userId
) {
}
```

用户 ID 以字符串返回，避免 JavaScript 对大整数的精度风险。Controller 通过 `String.valueOf(userId)` 构造响应，不直接返回 `UserAccount`。

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": "123"
  }
}
```

### 3.3 设计思路与取舍

- Request、Entity 和 Response 分离，防止调用方提交角色、状态、密码哈希和时间字段。
- `record` 适合只承载不可变请求和响应数据。
- DTO 校验负责 HTTP 输入格式；数据库约束仍负责存储边界和最终防线。
- 第一版不增加确认密码字段，因为确认密码是客户端交互校验，服务端安全性最终取决于收到的密码本身。
- 用户 ID 使用字符串是公开 API 契约，不依赖 Entity 上的 Jackson 注解。

### 3.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/model/request/user/RegisterRequest.java
src/main/java/org/victor/stackora/model/vo/user/RegisterResponse.java
```

HTTP JSON 字段固定为：

```text
account
rawPassword
```

响应 `data` 中只包含：

```text
userId
```

### 3.5 正常流程

```text
HTTP JSON
  -> Jackson 反序列化 RegisterRequest
  -> @Valid 执行字段校验
  -> Controller 读取 account 和 rawPassword
  -> 调用注册 Service
```

### 3.6 异常、权限与并发边界

- 请求体缺失、字段类型错误或 JSON 语法错误：返回 HTTP 400。
- 账号或密码校验失败：返回 HTTP 400 和第一条安全校验消息。
- 参数错误响应不得回显原始密码或完整请求体。
- 本接口允许匿名访问；登录与权限体系尚未建立，因此当前没有角色鉴权。
- 参数校验不解决重复账号并发，唯一性由注册 Service 和数据库唯一索引处理。

### 3.7 测试设计

- 合法账号和密码通过 Bean Validation。
- 大写账号通过请求校验，后续由 Service 转小写。
- 账号为空、过短、过长、包含非法字符、首尾下划线时校验失败。
- 密码为空、纯空白、少于 8 位或超过 64 位时校验失败。
- 密码中的合法字符不会被修改。
- `RegisterRequest.toString()` 不包含原始密码，只显示固定脱敏标记。
- 响应模型只包含字符串类型 `userId`。

### 3.8 完成标准

- Request 不能接收角色、状态、昵称、密码哈希和时间字段。
- 输入规则与数据库账号规则在大小写规范化后保持一致。
- 原始密码没有自动生成的 `toString()` 输出路径。
- 响应不返回 Entity 和密码哈希。

---

## 4. 需求二：建立密码编码能力

### 4.1 需求行为

注册 Service 接收原始密码后必须使用安全的单向密码编码器生成密码哈希。数据库只能保存编码结果，不能保存原始密码。

### 4.2 具体设计

配置：

```java
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }
}
```

第一版使用 Spring Security 提供的委托编码器。生成值包含算法标识，例如：

```text
{bcrypt}$2a$...
```

验证密码时必须使用：

```java
passwordEncoder.matches(rawPassword, passwordHash)
```

不得通过“重新编码后比较字符串”验证密码，因为带随机盐的安全哈希每次编码结果不同。

### 4.3 设计思路与取舍

- 使用框架维护的 `PasswordEncoder`，不自行实现密码算法。
- 委托编码格式保留算法标识，后续可以在不立即重置全部密码的情况下演进编码策略。
- 不使用 MD5、SHA-1、普通 SHA-256、`String.hashCode()` 或可逆加密保存密码。
- 本模块只编码注册密码；登录模块再使用 `matches` 验证。
- 当前不引入独立盐字段，盐由密码编码算法管理。

### 4.4 涉及文件与契约

```text
pom.xml
src/main/java/org/victor/stackora/config/PasswordConfig.java
```

Service 只依赖 `PasswordEncoder` 接口，不依赖具体 BCrypt 实现。

### 4.5 正常流程

```text
rawPassword
  -> PasswordEncoder.encode
  -> 产生带算法标识和随机盐的 passwordHash
  -> 写入 UserAccount.passwordHash
```

### 4.6 异常、权限与并发边界

- 不记录原始密码、密码哈希或完整请求对象。
- 编码失败属于系统错误，注册事务不得写入用户记录。
- 密码编码属于 CPU 密集操作，但当前注册量不需要异步化或专用线程池。
- 密码编码不提供重复注册防护。

### 4.7 测试设计

- 编码结果与原始密码不同。
- `matches` 对正确密码返回 `true`。
- `matches` 对错误密码返回 `false`。
- 相同密码连续编码两次结果不同，但均能通过 `matches`。
- 编码结果长度不超过数据库 `password_hash VARCHAR(255)`。

### 4.8 完成标准

- 项目只有一个明确的 `PasswordEncoder` Bean。
- 生产代码不包含弱密码摘要或可逆密码存储。
- 原始密码不会进入数据库、响应和普通日志。

---

## 5. 需求三：完成唯一账号注册用例

### 5.1 需求行为

Service 接收已经通过 HTTP 格式校验的账号和原始密码，创建一条唯一用户记录并返回数据库生成的用户 ID。重复账号返回稳定的业务异常。

### 5.2 具体设计

#### 5.2.1 Service 接口

```java
public interface UserAccountService {

    Long register(String account, String rawPassword);
}
```

接口不继承：

```java
IService<UserAccount>
```

因此 Controller 无法通过接口调用 `save`、`removeById`、`updateById`、`list` 或 `page`。

#### 5.2.2 Service 实现

```java
@Service
public class UserAccountServiceImpl
        extends ServiceImpl<UserAccountMapper, UserAccount>
        implements UserAccountService {
}
```

实现类可以复用 MyBatis-Plus 能力，但业务调用方只依赖 `UserAccountService`。

第一版直接在 `register` 中完成账号创建，不增加 `createAccount`。

#### 5.2.3 注册规则

执行顺序：

1. 对 `account` 和 `rawPassword` 做非空防御，避免非 HTTP 调用产生空指针异常。
2. 使用 `account.toLowerCase(Locale.ROOT)` 生成 `normalizedAccount`。
3. 不对账号或密码执行 `trim`。
4. 按规范化账号执行重复账号预查询。
5. 已存在时抛出 `ACCOUNT_ALREADY_EXISTS`。
6. 使用 `PasswordEncoder.encode(rawPassword)` 生成密码哈希。
7. 构造新的 `UserAccount`。
8. 设置服务端控制字段。
9. 执行 INSERT。
10. 捕获数据库唯一键冲突并转换为 `ACCOUNT_ALREADY_EXISTS`。
11. 插入影响行数不为 1 或 ID 未回填时抛出 `SYSTEM_ERROR`。
12. 返回用户 ID。

服务端控制字段：

```text
account      = normalizedAccount
passwordHash = passwordEncoder.encode(rawPassword)
nickname     = normalizedAccount
role         = USER
status       = ACTIVE
```

调用方不能指定昵称、角色、状态、密码哈希、创建时间、更新时间或注销时间。

#### 5.2.4 错误码

```text
ACCOUNT_ALREADY_EXISTS
code = 20000
message = 账号已存在
HTTP = 409 Conflict
```

插入返回异常状态但不属于唯一键冲突时使用：

```text
SYSTEM_ERROR
HTTP = 500 Internal Server Error
```

只捕获可以转换为稳定业务语义的 `DuplicateKeyException`。其他数据库异常继续交给全局未知异常处理，不向客户端暴露 SQL、表名、连接信息或堆栈。

### 5.3 设计思路与取舍

- 重复账号预查询用于提供正常情况下的快速业务提示，但不能保证并发唯一。
- 数据库唯一索引 `uk_user_account_account` 是并发防重的最终保障。
- 不使用 JVM 锁或分布式锁，因为唯一索引已经能够解决当前单行唯一性问题。
- 不删除预查询并完全依赖异常，是为了让普通重复注册路径更清晰；并发路径仍必须捕获唯一键异常。
- Service 只做必要的非空防御和业务规则，不重复实现整套 Jakarta Validation 注解。
- `@Transactional` 标记完整注册用例边界。虽然当前只有单次 INSERT，但后续增加同事务初始化动作时仍保持一致；数据库唯一性不依赖事务注解。
- 当前只有普通注册一种创建场景，因此不提前抽取 `createAccount`。

### 5.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/service/UserAccountService.java
src/main/java/org/victor/stackora/service/impl/UserAccountServiceImpl.java
src/main/java/org/victor/stackora/common/ErrorCode.java
src/main/java/org/victor/stackora/mapper/UserAccountMapper.java
```

Mapper 不增加注册业务方法；基础查询和插入继续使用 MyBatis-Plus。

事务契约：

- `register` 是公开事务入口。
- 方法必须由 Spring 管理的 Service Bean 从外部调用。
- 不通过同类自调用绕过事务代理。
- `BusinessException`、`DuplicateKeyException` 和其他运行时异常触发回滚。

### 5.5 正常流程

```text
UserAccountService.register
  -> 非空防御
  -> Locale.ROOT 转小写
  -> 按规范化账号预查询
  -> PasswordEncoder.encode
  -> 构造 UserAccount
  -> 设置 nickname、USER、ACTIVE
  -> Mapper INSERT
  -> MySQL 唯一索引校验
  -> 回填自增 ID
  -> 返回用户 ID
```

### 5.6 异常、权限与并发边界

- 账号或密码为 `null`、空串或纯空白：抛出 `PARAMS_ERROR`，不得产生数据库写入。
- 不同大小写账号经过规范化后视为同一账号。
- 预查询发现重复：不编码密码、不执行 INSERT。
- 两个并发事务注册同一账号：一个成功，另一个由唯一索引拒绝并转换为 409。
- 唯一键冲突不能通过查询后加锁完全替代，也不需要分布式锁。
- INSERT 失败时不返回伪造用户 ID。
- 日志不得包含原始密码、密码哈希、完整 Entity 或完整注册请求。
- 注册调用方永远只能获得 `USER` 和 `ACTIVE`，不能提升自己为管理员。

### 5.7 测试设计

Service 单元测试：

- 大写账号被转换为小写。
- 默认昵称等于规范化账号。
- 默认角色为 `USER`，状态为 `ACTIVE`。
- 密码编码器接收原始密码，Entity 保存编码结果。
- 预查询重复时抛出 `ACCOUNT_ALREADY_EXISTS`，且不编码、不插入。
- INSERT 唯一键冲突转换为 `ACCOUNT_ALREADY_EXISTS`。
- INSERT 影响行数异常或 ID 未回填时抛出 `SYSTEM_ERROR`。
- 不合法的空输入不会访问 Mapper。

真实 MySQL 集成测试：

- 注册成功并回填 ID。
- 重新查询后字段与注册规则一致。
- 数据库中的密码哈希不等于原始密码，并能通过 `matches`。
- 不同大小写重复注册返回相同重复账号错误。
- 失败事务不遗留半成品用户。

并发测试：

- 使用两个独立线程或事务同时注册相同规范化账号。
- 最终恰好一个成功、一个得到重复账号错误。
- 按账号查询最终只有一条用户记录。

### 5.8 完成标准

- 只能通过 `register` 的业务契约完成公开注册。
- 相同规范化账号最多保存一条记录。
- 任何成功用户都具有非空 ID、安全密码哈希、默认昵称、`USER` 和 `ACTIVE`。
- 重复账号不会转换为 500 或泄露数据库异常。
- Service 接口没有暴露通用 CRUD。

---

## 6. 需求四：暴露注册 HTTP 接口

### 6.1 需求行为

匿名调用方通过 HTTP 注册。成功时统一返回 200 和用户 ID；参数错误返回 400；账号重复返回 409；系统异常返回 500。所有业务响应使用现有 `ApiResponse` 结构。

### 6.2 具体设计

#### 6.2.1 接口

```text
POST /api/auth/register
Content-Type: application/json
```

请求：

```json
{
  "account": "Victor_01",
  "rawPassword": "Password123"
}
```

成功：

```text
HTTP 200 OK
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": "123"
  }
}
```

#### 6.2.2 Controller

Controller 只负责：

1. 使用 `@Valid @RequestBody` 接收请求。
2. 调用 `UserAccountService.register`。
3. 将用户 ID转换为 `RegisterResponse`。
4. 使用 `ApiResponseFactory.success` 封装响应。
5. 返回 HTTP 200。

Controller 不负责：

- 查询重复账号。
- 转换账号大小写。
- 编码密码。
- 设置角色和状态。
- 捕获数据库异常。
- 返回 Entity。

#### 6.2.3 统一异常转换

`GlobalExceptionHandler` 增加与注册请求直接相关的映射：

| 异常 | HTTP | 业务错误码 | 消息规则 |
| --- | --- | --- | --- |
| `MethodArgumentNotValidException` | 400 | `PARAMS_ERROR` | 第一条安全字段校验消息 |
| `HttpMessageNotReadableException` | 400 | `PARAMS_ERROR` | 固定“请求体格式错误” |
| `BusinessException(ACCOUNT_ALREADY_EXISTS)` | 409 | `ACCOUNT_ALREADY_EXISTS` | 固定业务消息 |
| 未知异常 | 500 | `SYSTEM_ERROR` | 固定系统消息 |

校验错误第一版不增加字段错误列表，保持 `code`、`message`、`data` 现有响应结构。

### 6.3 设计思路与取舍

- 第一版成功响应统一使用 HTTP 200，调用方通过业务码 `0` 判断成功；失败响应仍使用真实的 HTTP 4xx/5xx 状态。
- `/api/auth/register` 为后续登录、退出等身份相关入口保留一致路径。
- Controller 保持薄层，避免业务规则散落在 HTTP 层。
- 参数校验异常必须在本模块纳入统一响应，否则注册接口的错误结构会与业务异常不一致。
- 不增加 `Location` 响应头，因为当前尚未提供公开用户详情资源。

### 6.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/controller/AuthController.java
src/main/java/org/victor/stackora/exception/GlobalExceptionHandler.java
src/main/java/org/victor/stackora/model/request/user/RegisterRequest.java
src/main/java/org/victor/stackora/model/vo/user/RegisterResponse.java
```

Controller 依赖：

```text
UserAccountService
```

禁止依赖：

```text
UserAccountServiceImpl
UserAccountMapper
IService<UserAccount>
```

### 6.5 正常流程

```text
POST /api/auth/register
  -> Jackson 解析请求
  -> Jakarta Validation
  -> AuthController
  -> UserAccountService.register
  -> UserAccountMapper
  -> MySQL
  -> RegisterResponse
  -> ApiResponse<RegisterResponse>
  -> HTTP 200
```

### 6.6 异常、权限与并发边界

- 注册接口不要求登录。
- 请求校验失败时 Service 调用次数必须为零。
- 重复账号统一返回 409，不区分预查询发现还是唯一索引发现。
- 客户端不能通过请求指定 ID、昵称、角色、状态或密码哈希。
- 未知异常不得向客户端返回异常类名、SQL、数据库地址、堆栈或原始异常消息。
- 第一版不承诺注册请求幂等；相同账号重复请求返回冲突。
- 当前不做 IP 限流，但注册限流必须在公网部署前进入安全增强计划。

### 6.7 测试设计

MockMvc 接口测试：

- 合法请求返回 200、业务码 0 和字符串 `userId`。
- 请求账号原样传给 Service，由 Service 负责规范化。
- 非法账号返回 400，Service 未被调用。
- 非法密码返回 400，Service 未被调用。
- JSON 缺失或格式错误返回 400 和统一响应。
- Service 抛出重复账号异常时返回 409。
- 未知异常返回 500，响应不包含内部异常消息。
- 成功和失败响应均不包含密码字段。

### 6.8 完成标准

- 注册接口可以通过 Swagger UI、Knife4j、接口工具和 MockMvc 调用。
- 成功、参数错误、账号冲突和系统错误具有正确 HTTP 状态。
- Controller 没有持久化和密码处理逻辑。
- 对外响应没有 Entity 和敏感字段。

---

## 7. 需求五：建立注册自动化验收

### 7.1 需求行为

默认 Maven 测试命令必须能够验证注册核心行为，不能仅依赖接口工具手工测试或只执行没有断言的演示代码。

### 7.2 具体设计

测试分层：

| 测试类型 | 验证重点 | 是否连接 MySQL |
| --- | --- | --- |
| Request 校验测试 | 账号和密码格式 | 否 |
| PasswordEncoder 测试 | 编码与匹配 | 否 |
| Service 单元测试 | 业务顺序、默认字段和异常转换 | 否 |
| Controller MockMvc 测试 | HTTP、JSON 和统一异常响应 | 否 |
| 注册集成测试 | 事务、真实 Mapper、密码哈希和唯一索引 | 是 |
| 注册并发测试 | 同账号最多成功一次 | 是 |

测试类使用 Surefire 默认可发现的 `*Test` 命名。

### 7.3 设计思路与取舍

- Mock 测试适合验证 Controller 和 Service 协作，但不能证明数据库唯一索引真实生效。
- 真实 MySQL 测试验证最终数据和并发行为。
- 手工接口测试可以补充体验验证，但不替代自动化回归测试。
- 不要求本模块立即引入 Testcontainers，继续使用明确的测试数据库配置。

### 7.4 涉及文件与契约

```text
src/test/java/org/victor/stackora/model/request/user/RegisterRequestTest.java
src/test/java/org/victor/stackora/config/PasswordEncoderTest.java
src/test/java/org/victor/stackora/service/UserAccountServiceTest.java
src/test/java/org/victor/stackora/controller/AuthControllerTest.java
src/test/java/org/victor/stackora/service/UserRegistrationIntegrationTest.java
src/test/java/org/victor/stackora/service/UserRegistrationConcurrencyTest.java
```

测试数据必须使用随机合法账号，数据库测试结束后回滚或显式安全清理。

### 7.5 正常流程

```text
./mvnw clean test
  -> 编译注册代码
  -> 执行请求校验和密码编码测试
  -> 执行 Service 单元测试
  -> 执行 Controller MockMvc 测试
  -> 执行真实 MySQL 注册测试
  -> 执行重复账号并发测试
  -> 汇总 failures、errors、skipped
```

### 7.6 异常、权限与并发边界

- 数据库未配置时集成测试必须明确失败，不能静默跳过后声称模块通过。
- 测试不得依赖执行顺序或已有用户数据。
- 测试日志不能打印原始密码和密码哈希。
- 并发测试必须断言成功数、失败类型和最终记录数，不能只断言“发生过异常”。
- 注册测试不得修改角色、状态和注销业务。

### 7.7 测试设计

完整场景：

1. 合法注册成功。
2. 大写账号最终存储为小写。
3. 默认昵称、角色和状态正确。
4. 密码已编码并可验证。
5. 非法请求被 Controller 拒绝。
6. 重复账号返回 409。
7. 不同大小写重复账号返回 409。
8. 并发相同账号只有一个成功。
9. 数据库写入异常返回安全的 500。
10. 响应和日志不泄露密码。

### 7.8 完成标准

- 默认测试命令发现并执行所有注册测试。
- 正常、参数错误、重复账号、系统异常和并发场景均有证据。
- 测试结果不依赖手工预置用户。
- 所有测试失败时都能指向明确的业务规则。

---

## 8. 模块级共同约束

### 8.1 分层约束

```text
AuthController
  -> UserAccountService
  -> UserAccountServiceImpl
  -> UserAccountMapper
  -> MySQL
```

- Controller 不直接依赖 Mapper 或 Service 实现类。
- Service 接口不继承 `IService`。
- Service 实现类可以继承 `ServiceImpl`，但通用 CRUD 只作为内部能力。
- Entity 不作为公开接口输入或输出。

### 8.2 安全约束

- 原始密码和密码哈希不得进入响应、普通日志、异常消息、监控标签和 `toString()`。
- 注册者不能指定角色和状态。
- 数据库唯一索引是账号唯一性的最终防线。
- 未知异常统一返回安全系统消息。

### 8.3 事务与并发约束

- `register` 是完整注册事务边界。
- 先查后写不能替代唯一索引。
- 唯一键冲突必须转换为稳定业务错误。
- 当前不引入 JVM 锁、Redis 锁或 Redisson。

### 8.4 兼容性约束

- 使用现有 `ApiResponse` 的 `code`、`message`、`data` 结构。
- 成功统一使用 HTTP 200，失败使用真实 HTTP 400、409 和 500。
- 使用现有 MySQL 用户表，不新增迁移。
- JDK 和 MySQL 环境版本继续由开发者配置。

---

## 9. 完整验收清单

### 9.1 注册契约

- [x] `RegisterRequest` 只包含账号和原始密码。
- [x] 账号和密码校验规则明确且可测试。
- [x] `RegisterResponse` 只返回字符串用户 ID。
- [x] Entity 不进入公开接口。

### 9.2 密码安全

- [x] 使用 Spring `PasswordEncoder`。
- [x] 数据库存储值不等于原始密码。
- [x] 正确和错误密码匹配行为通过测试。
- [x] 密码不进入响应、日志和异常消息。

### 9.3 注册业务

- [x] Service 接口不继承 `IService`。
- [x] 实现类继承 `ServiceImpl`。
- [x] 第一版不拆分 `createAccount`。
- [x] 账号使用 `Locale.ROOT` 转小写。
- [x] 默认昵称、角色和状态正确。
- [x] 预查询重复账号不继续编码和写入。
- [x] 数据库唯一键冲突转换为 409。
- [x] 注册成功返回有效用户 ID。

### 9.4 HTTP 接口

- [x] `POST /api/auth/register` 可以调用。
- [x] 成功返回 HTTP 200。
- [x] 参数错误返回 HTTP 400。
- [x] 重复账号返回 HTTP 409。
- [x] 未知异常返回 HTTP 500。
- [x] 所有响应符合统一结构。

### 9.5 测试

- [ ] Request 校验测试通过。
- [ ] PasswordEncoder 测试通过。
- [ ] Service 单元测试通过。
- [ ] Controller MockMvc 测试通过。
- [ ] 真实 MySQL 注册测试通过。
- [ ] 重复账号并发测试通过。
- [ ] 默认 Maven 测试命令执行全部测试。

---

## 10. 测试命令与预期结果

在仓库根目录加载测试环境后执行：

```bash
env $(grep -vE '^(#|$)' .env.test | xargs) \
  ./mvnw clean test
```

针对性命令：

```bash
env $(grep -vE '^(#|$)' .env.test | xargs) \
  ./mvnw -Dtest=UserAccountServiceTest,AuthControllerTest test
```

提交前检查：

```bash
git diff --check
git status --short
```

预期：

- Maven 退出码为 0。
- 注册测试被实际发现并执行。
- failures、errors、skipped 均为 0。
- 测试数据库中不遗留随机注册数据。
- Git 变更不包含本地凭据和范围外功能。

---

## 11. 已知风险与后续技术债

### 11.1 注册滥用

第一版没有验证码、IP 限流和设备风控，不适合直接暴露在不受保护的公网。公网部署前必须结合真实流量和攻击模型增加限流及审计。

### 11.2 账号枚举

重复注册返回“账号已存在”，会向调用方确认账号存在。当前为了提供清晰注册体验接受该风险；涉及高敏感身份体系时应重新评估统一模糊消息。

### 11.3 密码策略

第一版只限制长度和非空，不检查泄露密码库或密码强度。后续有真实安全要求时增加弱密码检测，不能在日志或外部服务中泄露原始密码。

### 11.4 测试环境

当前真实 MySQL 测试依赖开发者配置的测试数据库。后续工程化阶段可引入 Testcontainers，提高 CI 和本地环境一致性。

### 11.5 当前未跟踪草稿

工作区中已经存在注册 DTO、PasswordConfig、Service 和测试草稿。它们只作为实现参考，必须以本文档最终确认版本为准，不因为已经存在就跳过设计和测试要求。

---

## 12. 后续模块边界

用户注册完成后，下一模块依次设计：

1. 用户登录与密码验证。
2. HttpSession 登录态。
3. 当前用户查询与退出登录。
4. 用户资料、昵称和头像。
5. 管理员用户治理。
6. 用户注销和数据保留。

登录模块再引入：

- `PasswordEncoder.matches`。
- Session 用户标识。
- 登录失败统一消息。
- 禁用和注销状态的登录限制。

本注册模块不提前实现上述能力。

---

## 13. Paicoding 实现对比

本节基于工作区 `paicoding-reference/paicoding-main` 中的真实源码快照进行只读对比。该目录当前不包含 `.git` 元数据，因此只能确认本地快照内容，不能据此声明对应 Paicoding 官方仓库的具体分支或提交。文件路径以 Paicoding 源码根目录为起点；对比结论只用于说明取舍，不复制其源码，也不把参考项目视为唯一正确答案。

### 13.1 对应源码证据

| 关注点 | Paicoding 源码路径与位置 | 已确认事实 |
| --- | --- | --- |
| 技术基线 | `pom.xml:13-27` | 本地快照使用 Spring Boot 2.7.1 和 Java 8；Stackora 使用 Spring Boot 3 与 Java 17，因此可以比较业务和架构思想，但不能直接复制 Servlet、Validation 等框架代码。 |
| HTTP 注册入口 | `paicoding-web/src/main/java/com/github/paicoding/forum/web/front/login/pwd/LoginRestController.java:64-80` | `POST /login/register` 接收 `UserPwdLoginReq`，注册成功后立即创建 Session Cookie，并返回用户 ID。 |
| 注册、绑定与登录编排 | `paicoding-service/src/main/java/com/github/paicoding/forum/service/user/service/user/LoginServiceImpl.java:157-190` | 同一流程会判断当前登录态、绑定已有用户、匹配已有账号、创建新用户并生成 Session，不是纯粹的“只创建账号”接口。 |
| 注册前置校验 | `paicoding-service/src/main/java/com/github/paicoding/forum/service/user/service/user/LoginServiceImpl.java:199-235` | 除用户名和密码外，还强制校验星球编号，并处理邀请码和已有绑定关系。 |
| 注册 Service 抽象 | `paicoding-service/src/main/java/com/github/paicoding/forum/service/user/service/RegisterService.java:11-37` | 独立 `RegisterService` 同时承载系统账号、用户名密码和微信三种创建渠道。 |
| 用户名密码注册事务 | `paicoding-service/src/main/java/com/github/paicoding/forum/service/user/service/user/RegisterServiceImpl.java:69-103` | 注册方法使用事务，先查用户名，再写登录表、用户资料表和 AI 绑定数据，最后触发注册后动作。 |
| 密码编码 | `paicoding-service/src/main/java/com/github/paicoding/forum/service/user/service/help/UserPwdEncoder.java:16-44`、`paicoding-service/src/main/java/com/github/paicoding/forum/service/config/security/SecurityConfig.java:8-14` | 用户名密码注册调用链实际使用固定盐 MD5 的 `UserPwdEncoder`。项目虽然另有 BCrypt `PasswordEncoder` Bean，但该注册调用链没有使用它；`UserPwdEncoder` 注释也计划将来替换为 Spring Security `PasswordEncoder`。 |
| 请求模型 | `paicoding-api/src/main/java/com/github/paicoding/forum/api/model/vo/user/UserPwdLoginReq.java:14-64` | 可变 DTO 同时包含登录、注册、显示名、头像、邀请码、星球和第三方登录字段。 |
| 数据模型 | `paicoding-service/src/main/java/com/github/paicoding/forum/service/user/repository/entity/UserDO.java:16-71` | 登录凭证保存在 `user`，资料保存在独立 `user_info`；账号表还包含登录方式、逻辑删除和封禁信息。 |
| DAO 分层 | `paicoding-service/src/main/java/com/github/paicoding/forum/service/user/repository/dao/UserDao.java:27-117` | `UserDao` 位于 Service 和 Mapper 之间，同时操作 `UserDO` 与 `UserInfoDO` 并封装查询。 |
| 数据库防重 | `paicoding-web/src/main/resources/liquibase/changelog/000_initial_schema.xml:10-12`、`paicoding-web/src/main/resources/liquibase/data/init_schema_221209.sql:211-223` | Liquibase 实际执行的初始 `user` 表中，`user_name` 没有唯一索引；注册代码主要依赖“先查询再写入”。这不能作为并发防重的最终保证。 |
| 注册后事件 | `paicoding-service/src/main/java/com/github/paicoding/forum/service/user/service/user/RegisterServiceImpl.java:131-143`、`paicoding-core/src/main/java/com/github/paicoding/forum/core/util/TransactionUtil.java:62-84` | 注册成功后在事务提交完成后发布注册事件，避免数据库回滚时提前发送欢迎通知。 |
| 欢迎通知 | `paicoding-service/src/main/java/com/github/paicoding/forum/service/notify/service/impl/NotifyMsgListener.java:61-91` | 异步监听 `REGISTER` 事件并写入首次注册欢迎消息。 |
| 自动化测试 | `paicoding-web/src/test/java/com/github/paicoding/forum/test/user/UserServiceTest.java:18-31` | 名为 `testRegister` 的测试实际只调用资料保存且没有断言；当前未找到直接覆盖用户名密码注册、防重或并发注册的自动化测试。 |

### 13.2 逐维度对比与当前决策

| 维度 | Stackora 当前设计 | Paicoding 实现 | 差异原因 | 当前是否借鉴 |
| --- | --- | --- | --- | --- |
| 业务流程 | 注册只创建账号，不自动登录或绑定外部身份 | 注册入口同时处理星球绑定、已有账号复用和 Session 创建 | Stackora 当前需要先建立单一、可验证的注册闭环 | 不合并流程；登录和 Session 留到下一模块 |
| 输入模型 | 独立、不可变且字段最小化的 `RegisterRequest` | `UserPwdLoginReq` 同时服务登录、注册、资料和第三方身份 | Stackora 当前没有星球、邀请码或第三方登录需求 | 保留窄 DTO，不照搬宽模型 |
| Service 边界 | `UserAccountService.register` 直接表达当前唯一创建用例 | 独立 `RegisterService` 支持三种注册渠道 | Paicoding 已存在多渠道创建和多表初始化，抽象有真实复用价值 | 当前不拆 `RegistrationService`；出现第二种创建渠道后再评估 |
| 持久化分层 | ServiceImpl 通过 Mapper 写一张 `user_account` 表 | RegisterService 通过 `UserDao` 写登录表、资料表和 AI 数据 | Paicoding 的 DAO 需要协调更多实体和复合查询 | 当前不增加 DAO/Repository 层 |
| 用户资料 | MVP 在 `user_account` 保存默认昵称、角色和状态 | 凭证与 `user_info` 资料分表，注册时同时写入 | Stackora 的个人资料字段尚少，立即拆表会增加事务和关联查询成本 | 后续资料复杂度上升时再拆分 |
| 密码安全 | Spring Security `DelegatingPasswordEncoder`，编码值带算法标识并可升级 | 固定盐 MD5 | MD5 速度快且不适合保存用户密码；固定盐也不能隔离单用户泄露风险 | 明确不照搬，Stackora 方案更安全 |
| 事务 | 单次注册使用事务，当前主要保护注册用例边界 | 事务覆盖登录信息、资料、AI 数据等多次写入 | Paicoding 的多表写入对原子性要求更强 | 借鉴“完整业务动作一个事务”的原则 |
| 并发防重 | 预查询改善提示，数据库唯一索引负责最终防重，并转换 `DuplicateKeyException` | 先查询用户名；已核对的初始化表没有用户名唯一索引 | 先查后写存在竞态，无法独立保证并发唯一性 | 不照搬；坚持数据库唯一约束 |
| 注册后动作 | 当前不发送通知或消息 | 事务提交后发布事件，异步创建欢迎通知 | Stackora 当前没有注册后副作用，引入事件基础设施没有收益 | 借鉴提交后执行原则；事件机制以后按需引入 |
| 响应 | `RegisterResponse` 只返回用户 ID，通过统一 `ApiResponse` 封装 | 返回用户 ID，同时通过 Cookie 建立会话 | Stackora 将注册和登录状态拆分，接口副作用更少 | 保留当前设计 |
| 测试 | 要求 Service、MockMvc 和真实 MySQL 并发验证 | 当前未找到直接覆盖用户名密码注册和并发防重的测试证据 | Stackora 需要用测试证明自身设计，而不是继承参考项目缺口 | 不降低验收标准 |

### 13.3 现在借鉴

1. 注册用例由 Service 统一编排，Controller 不直接执行持久化。
2. 当一个业务动作包含多次数据库写入时，事务必须覆盖完整动作。
3. 密码处理封装为独立能力，业务代码不直接实现散列细节；Stackora 使用更安全的 Spring Security `PasswordEncoder`。
4. 默认昵称等服务端字段由注册流程决定，不信任客户端提交角色或状态。
5. 将来出现欢迎通知、消息发送等注册后副作用时，只能在事务成功提交后触发。

### 13.4 以后按需借鉴

1. 出现微信、管理员创建或其他真实创建渠道后，再拆出独立 `RegistrationService` 或公共账号创建能力。
2. 用户资料字段、访问模式和生命周期明显独立后，再评估凭证表与资料表分离。
3. 注册完成后确实需要欢迎通知、审计或异步任务时，再引入事务后事件；需要可靠跨进程投递时还应进一步设计 Outbox 或消息可靠性，不能只依赖进程内异步事件。
4. 登录模块可以借鉴注册成功后建立 Session 的调用关系，但是否“注册即登录”必须由 Stackora 自己的产品需求决定。

### 13.5 当前不建议照搬

1. 不使用固定盐 MD5 保存密码。
2. 不把登录、注册、用户资料、邀请码和第三方身份字段放进同一个可变 DTO。
3. 不把注册、账号绑定和自动登录混成一个难以独立验收的 Service 方法。
4. 不引入 Stackora 尚不存在的星球编号、邀请码、AI 信息或微信登录逻辑。
5. 不因为参考项目存在 DAO 层就增加无实际职责的新分层。
6. 不依赖“先查后写”保证账号唯一；数据库唯一约束仍是并发防重最后防线。
7. 不降低 Stackora 的注册自动化测试要求。参考项目缺少直接测试证据，不代表这些场景不重要。

### 13.6 对 Stackora 方案的结论

真实源码对比后，Stackora 当前注册方案无需改成 Paicoding 的复合注册流程。应继续保持：

- 一个独立、窄职责的注册接口。
- 一个不可变的注册请求模型。
- `DelegatingPasswordEncoder`。
- Service 统一业务编排。
- 数据库唯一约束兜底并发防重。
- 注册和登录/Session 分阶段实现。
- 使用自动化测试证明正常、异常和并发行为。

Paicoding 最值得当前阶段学习的是多表注册事务和事务提交后再触发副作用的边界意识；其固定盐 MD5、宽 DTO、复合注册流程及缺少账号唯一约束的部分不应照搬。
