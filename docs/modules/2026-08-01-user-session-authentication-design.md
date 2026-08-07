# Stackora 用户登录与 Session 认证需求与技术设计

## 0. 文档使用方式

本文档描述 Stackora 第一版用户登录、当前用户查询和退出登录能力。文档按照真实开发依赖顺序组织，每个需求在同一章节内完成业务行为、具体设计、设计思路、接口与文件、正常流程、异常与安全边界、测试设计和完成标准的闭环。

开发顺序固定为：

```text
定义登录输入输出契约
  -> 实现账号密码认证
  -> 建立安全的 HttpSession
  -> 实现当前用户查询
  -> 实现幂等退出登录
  -> 配置 Session 与 Cookie 安全属性
  -> 完成认证闭环自动化验收
```

本模块完成基础认证闭环，不同时实现登录拦截器、细粒度权限、Redis Session 或多设备会话管理。

---

## 1. 模块概览

### 1.1 用户价值

已注册用户需要使用账号和密码建立可信登录态，在后续请求中识别当前身份，并能够主动退出。系统必须防止密码泄露、基于状态码和响应内容的直接账号枚举、Session 固定和无效账号继续访问，同时保证禁用或注销状态能够及时使登录态失效。

### 1.2 最终能力

完成本模块后，系统具备以下能力：

1. 用户可以通过账号和密码登录。
2. 账号不存在和密码错误使用相同的安全错误响应。
3. 只有 `ACTIVE` 用户可以建立 Session。
4. 登录成功后废弃旧 Session，并创建只保存用户 ID 的新 Session。
5. 已登录用户可以查询自己的安全公开信息。
6. 用户状态变化后，旧 Session 不能继续获得有效用户身份。
7. 用户可以退出当前 Session；重复退出保持成功。
8. 密码、密码哈希、Session ID 和 Entity 不进入公开响应。

### 1.3 当前范围

- `LoginRequest` 登录请求模型。
- `AuthenticatedUser` Service 内部安全用户快照。
- `LoginResponse` 登录响应模型。
- `CurrentUserResponse` 当前用户响应模型。
- 登录、当前用户和退出登录三个 HTTP 接口。
- `HttpSession` 创建、读取、更新和销毁规则。
- Session Cookie 和空闲超时配置。
- 账号密码认证、状态校验和认证相关测试。
- OpenAPI 接口及模型说明。
- 将当前依赖本地环境变量的 `/api` 前缀固化为公共 Servlet context path，并迁移现有认证、Actuator 和接口文档外部地址。

### 1.4 明确不做

本模块不实现：

- 登录拦截器和自定义权限注解。
- 管理员接口和角色授权矩阵。
- Remember Me。
- JWT、Sa-Token、OAuth2 或第三方登录。
- Redis Session、分布式 Session 和多实例共享登录态。
- 单点登录、强制下线和多设备会话管理。
- 登录失败次数锁定、验证码、IP 限流和风控系统。
- 登录审计表、登录历史和最后登录时间。
- 修改密码、找回密码和重置密码。
- 用户资料修改。
- 新增或修改数据库表结构。
- 扩展现有全局异常处理器对畸形 JSON、空请求体和媒体类型错误的映射；该能力继续作为统一异常模块的后续技术债处理。

### 1.5 前置依赖

- 用户注册模块已经完成并通过测试。
- `user_account` 表、账号唯一索引和状态约束已经存在。
- `UserAccountService`、`UserAccountMapper` 和 `PasswordEncoder` 已可使用。
- 用户角色包含 `USER`、`ADMIN`。
- 用户状态包含 `ACTIVE`、`DISABLED`、`CANCELLED`。
- `NOT_LOGIN`、`ACCOUNT_OR_PASSWORD_ERROR`、`ACCOUNT_DISABLED` 和 `ACCOUNT_CANCELLED` 错误码已经存在。
- `ApiResponse`、`ApiResponseFactory`、`BusinessException` 和 `GlobalExceptionHandler` 已可使用。
- Springdoc、Swagger UI 和现有接口文档能力已经接入。

---

## 2. 方案选择与开发顺序

### 2.1 可选方案

#### 方案 A：扩展现有用户账号 Service，Controller 管理 HttpSession

```text
AuthController
  -> UserAccountService
  -> UserAccountServiceImpl
  -> UserAccountMapper
  -> MySQL

AuthController
  -> HttpSession
```

特点：

- Service 负责账号规范化、账号查询、密码匹配和用户状态规则。
- Controller 只在认证成功后处理 Session 生命周期。
- Service 不依赖 Servlet API，仍可脱离 HTTP 独立测试。
- 继续复用当前纯 `UserAccountService` 接口，不向 Controller 暴露 MyBatis-Plus 通用 CRUD。

#### 方案 B：新增独立 AuthenticationService

认证职责更独立，适合同时存在密码、短信、OAuth2 和第三方登录等多种认证方式。当前只有账号密码登录，新建服务会增加接口、实现和调用层级，但没有形成真实复用。

#### 方案 C：自定义 Token 与 Redis 会话

适合多实例部署、设备会话管理和集中强制下线，但会引入 Token 生成、Redis 可用性、过期一致性、Cookie 管理和运维成本。当前单体阶段没有必要。

### 2.2 采用方案

第一版采用方案 A。

选择原因：

- 符合当前单体应用和原生 `HttpSession` 技术路线。
- 认证规则留在业务层，Servlet 细节留在 Web 层。
- 不为尚未出现的第二种认证方式提前抽象。
- 后续增加拦截器时可以直接复用统一 Session Key。
- 将来切换 Redis Session 时可以保持 Controller 和业务接口基本不变。
- 保留现有 `AuthController @RequestMapping("/auth")` 和注册 MockMvc 测试的 Servlet 相对路径。
- 本模块明确实施一次全局外部 URL 迁移：公共配置新增 `/api` context path；项目尚未对外发布，因此旧的无前缀 URL 直接停止使用，不增加兼容别名。

### 2.3 需求与开发顺序

| 顺序 | 需求 | 可验证交付物 | 依赖 |
| --- | --- | --- | --- |
| 1 | 定义登录输入输出契约 | 请求、内部 DTO 和两个响应模型 | 注册契约与统一响应 |
| 2 | 完成账号密码认证 | 正确验证密码、账号状态和安全错误语义 | 需求 1、PasswordEncoder |
| 3 | 建立安全 Session | 登录成功创建新 Session，失败不创建 | 需求 2 |
| 4 | 查询当前用户 | Session 可以解析为最新安全用户信息 | 需求 2～3 |
| 5 | 退出当前登录 | 当前 Session 被销毁，重复退出成功 | 需求 3 |
| 6 | 配置 Session 与 Cookie | 超时和环境安全属性明确 | 需求 3～5 |
| 7 | 建立自动化验收 | 登录、当前用户、退出形成可回归闭环 | 需求 1～6 |

### 2.4 独立开发增量

为保证每次改动可在 1～3 小时内独立验证和提交，实施时拆成七个连续增量：

1. 登录契约、Service 认证和单元测试。
2. login 接口、Session 轮换和 Controller 测试。
3. me、logout、失效 Session 语义和对应测试。
4. Session/Cookie/profile 配置与配置绑定测试。
5. 随机端口真实 HTTP Cookie 闭环。
6. `/api` 全局路径迁移、OpenAPI 契约及项目文档回归。
7. 真实 MySQL 认证闭环和全量回归。

后一个增量必须以前一个增量的测试通过为前置条件，不能把多个增量合并成无法独立审查的大提交。

---

## 3. 需求一：定义登录输入输出契约

### 3.1 需求行为

调用方提交账号和原始密码。系统先校验请求结构，只允许认证所需字段进入业务层。登录成功和当前用户查询分别返回专用响应，不直接暴露 `UserAccount`。

### 3.2 具体设计

#### 3.2.1 LoginRequest

```java
public record LoginRequest(
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
        return "LoginRequest[account=%s, rawPassword=<redacted>]"
                .formatted(account);
    }
}
```

规则：

- 账号格式与注册接口一致。
- 允许账号包含大写，由 Service 使用 `Locale.ROOT` 转为小写。
- 不对账号和密码执行 `trim`。
- 密码不执行大小写转换或其他规范化。
- `LoginRequest` 不复用 `RegisterRequest`，避免两个公开契约互相影响。
- `toString()` 必须对原始密码脱敏，业务日志仍不得记录完整请求对象。

#### 3.2.2 AuthenticatedUser

Service 返回内部不可变安全快照：

```java
public record AuthenticatedUser(
        Long userId,
        String account,
        String nickname,
        UserRole role
) {
}
```

该 DTO 不包含密码哈希、状态内部编码和数据库时间字段。

#### 3.2.3 LoginResponse

```java
public record LoginResponse(
        @Schema(type = "string", description = "用户 ID")
        @JsonSerialize(using = ToStringSerializer.class)
        Long userId,
        String account,
        String nickname,
        UserRole role
) {
}
```

`userId` 延续注册响应的序列化约定：`@JsonSerialize(using = ToStringSerializer.class)` 决定真实 JSON 输出字符串，`@Schema(type = "string")` 只负责 OpenAPI 契约。两个注解都不能省略。

#### 3.2.4 CurrentUserResponse

```java
public record CurrentUserResponse(
        @Schema(type = "string", description = "用户 ID")
        @JsonSerialize(using = ToStringSerializer.class)
        Long userId,
        String account,
        String nickname,
        UserRole role
) {
}
```

登录和当前用户响应暂时字段相同，但保持两个公开类型独立，使两个接口后续能够分别演进。

### 3.3 设计思路与取舍

- Request、内部认证结果、公开响应和 Entity 分离。
- 登录参数和注册参数当前规则相同，不代表两个 API 必须共享同一类型。
- Service 返回业务安全快照，Controller 无需拿到含密码哈希的 Entity。
- 角色以 `USER`、`ADMIN` 字符串返回，不暴露数据库整数编码。
- 不返回 Session ID；浏览器通过 `Set-Cookie` 接收容器生成的 `JSESSIONID`。

### 3.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/model/dto/user/LoginRequest.java
src/main/java/org/victor/stackora/model/dto/user/AuthenticatedUser.java
src/main/java/org/victor/stackora/model/vo/LoginResponse.java
src/main/java/org/victor/stackora/model/vo/CurrentUserResponse.java
```

### 3.5 正常流程

```text
HTTP JSON
  -> Jackson 创建 LoginRequest
  -> Jakarta Validation
  -> Controller 读取 account 和 rawPassword
  -> Service 返回 AuthenticatedUser
  -> Controller 转换为公开 Response
```

### 3.6 异常、安全与边界

- 成功反序列化后的字段校验失败：HTTP 400。
- 畸形 JSON、空请求体和错误字段类型沿用当前全局异常处理行为，本模块不修改或承诺新的转换规则；统一映射为安全 400 响应属于统一异常模块后续修订范围。
- 参数错误响应不得回显密码和完整请求体。
- 客户端不能提交用户 ID、角色、状态或密码哈希。
- 公开响应不包含 `UserAccount` 和 Session ID。

### 3.7 测试设计

- 合法账号密码通过 Bean Validation。
- 非法账号、空密码和不合规长度被拒绝。
- `LoginRequest.toString()` 不包含原始密码。
- 使用大于 JavaScript 安全整数范围的用户 ID，断言两个响应的真实 JSON token 均为字符串。
- OpenAPI 中两个响应的 `userId` schema 均为 string。
- 响应中不出现 `passwordHash`、`status` 和数据库时间字段。

### 3.8 完成标准

- 四个模型职责明确且可以独立编译。
- 登录请求不能控制服务端身份字段。
- 原始密码没有默认 `toString()` 泄露路径。
- Entity 不进入公开接口。

---

## 4. 需求二：完成账号密码认证

### 4.1 需求行为

Service 根据规范化账号查询用户，使用现有 `PasswordEncoder` 验证原始密码，并只允许状态为 `ACTIVE` 的用户通过认证。

### 4.2 具体设计

在 `UserAccountService` 增加：

```java
AuthenticatedUser userLogin(String account, String rawPassword);

AuthenticatedUser getAuthenticatedUser(Long userId);
```

`userLogin` 执行顺序：

1. 使用 `StringUtils.hasText` 对账号和密码做 Service 边界防御。
2. 使用 `account.toLowerCase(Locale.ROOT)` 生成规范化账号。
3. 按规范化账号查询一条用户记录。
4. 用户不存在时抛出 `ACCOUNT_OR_PASSWORD_ERROR`。
5. 使用 `passwordEncoder.matches(rawPassword, passwordHash)` 验证密码。
6. 密码错误时同样抛出 `ACCOUNT_OR_PASSWORD_ERROR`。
7. 密码正确后再检查用户状态。
8. `DISABLED` 抛出 `ACCOUNT_DISABLED`。
9. `CANCELLED` 抛出 `ACCOUNT_CANCELLED`。
10. `ACTIVE` 返回 `AuthenticatedUser`。

`getAuthenticatedUser` 执行顺序：

1. 拒绝 `null` 和非正数用户 ID，抛出 `NOT_LOGIN`。
2. 按主键查询用户。
3. 用户不存在时抛出 `NOT_LOGIN`。
4. `DISABLED` 抛出 `ACCOUNT_DISABLED`。
5. `CANCELLED` 抛出 `ACCOUNT_CANCELLED`。
6. `ACTIVE` 返回最新 `AuthenticatedUser`。

### 4.3 设计思路与取舍

- 账号不存在和密码错误共享 HTTP 状态、业务码、消息和响应结构，防止通过直接响应差异枚举有效账号。
- 只有密码正确后才暴露禁用或注销状态，避免未掌握密码的调用方探测账号状态。
- 使用 `matches`，不能重新编码密码后比较字符串，因为 BCrypt 每次编码使用随机盐。
- Service 只做必要的非空防御，不复制全部 Jakarta Validation 规则。
- 当前不增加登录失败计数和锁定，避免在没有风控数据模型时引入错误封禁逻辑。
- 第一版不强制做用户名不存在时的 BCrypt 等时匹配，因此不宣称消除所有账号枚举侧信道。网络延迟、数据库访问和系统负载仍可能产生可采样的时序差异；公网部署前应与固定 dummy hash 匹配、限流和审计一起重新评估。

### 4.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/service/UserAccountService.java
src/main/java/org/victor/stackora/service/impl/UserAccountServiceImpl.java
src/main/java/org/victor/stackora/mapper/UserAccountMapper.java
src/main/java/org/victor/stackora/common/ErrorCode.java
```

Mapper 继续使用 MyBatis-Plus 查询，不新增只为登录存在的 XML SQL。

### 4.5 正常流程

```text
UserAccountService.userLogin
  -> 非空防御
  -> Locale.ROOT 转小写
  -> 按账号查询
  -> PasswordEncoder.matches
  -> UserStatus 校验
  -> 构造 AuthenticatedUser
  -> 返回认证结果
```

### 4.6 异常、安全、事务与并发边界

- 参数为空：`PARAMS_ERROR`，HTTP 400。
- 账号不存在或密码错误：`ACCOUNT_OR_PASSWORD_ERROR`，HTTP 401。
- 密码正确但用户禁用或注销：对应状态错误，HTTP 403。
- 原始密码、密码哈希和完整 Entity 不写入业务日志。
- 认证只读数据库，不新增 `@Transactional`；Session 创建不属于数据库事务。
- 同一账号可以在不同浏览器建立多个 Session；单设备限制不在本模块范围。
- 密码或状态在查询后瞬间变化存在短暂竞态，后续受保护请求必须重新确认当前用户状态，不能永久信任登录时快照。

### 4.7 测试设计

- 大写账号按小写查询。
- 正确密码调用 `matches` 并返回安全快照。
- 账号不存在和密码错误得到相同错误码。
- 账号不存在和密码错误的 HTTP 状态、业务码、消息和响应结构完全相同。
- 密码错误时不返回状态信息。
- 密码正确后，禁用和注销状态分别返回对应错误。
- 非空防御在访问 Mapper 前生效。
- 返回结果不包含密码哈希。

### 4.8 完成标准

- 正确凭证只允许 `ACTIVE` 用户通过。
- 错误凭证不能区分账号是否存在。
- 认证过程只使用 `PasswordEncoder.matches`。
- Service 接口仍不继承 `IService<UserAccount>`。

---

## 5. 需求三：建立安全的 HttpSession

### 5.1 需求行为

用户通过认证后，系统创建新的服务器 Session，并通过容器管理的 Cookie 返回 Session ID。失败登录不得创建或更新登录 Session。

### 5.2 具体设计

新增常量：

```java
public final class SessionConstants {

    public static final String LOGIN_USER_ID = "LOGIN_USER_ID";

    private SessionConstants() {
    }
}
```

登录成功后由 `AuthController`：

1. 使用 `request.getSession(false)` 获取已有 Session，不主动创建。
2. 若已有 Session，在窄范围内调用 `invalidate()` 废弃旧 Session；若它已被并发请求失效，按旧 Session 已完成清理处理。
3. 使用 `request.getSession(true)` 创建全新 Session。
4. 保存 `LOGIN_USER_ID -> Long userId`。
5. 返回 `LoginResponse`；认证响应的禁止缓存响应头由统一过滤器写入。

对外接口与 Servlet 相对映射：

```text
对外地址：POST /api/auth/login
Controller：POST /auth/login
Content-Type: application/json
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": "123",
    "account": "victor_01",
    "nickname": "victor_01",
    "role": "USER"
  }
}
```

HTTP 状态为 200，响应头由 Servlet 容器写入 `Set-Cookie: JSESSIONID=...`。

### 5.3 设计思路与取舍

- 成功认证后废弃旧 Session，防止攻击者预先固定 Session ID。
- 当前没有匿名购物车等 Session 数据，直接失效并新建比迁移属性更安全清晰。
- Session 只保存用户 ID，避免昵称、角色和状态长期过期。
- Controller 管理 Servlet 会话，Service 不依赖 `HttpServletRequest` 或 `HttpSession`。
- 不把 Session ID 放进 JSON，避免调用方形成 Cookie 和响应字段两套认证方式。

### 5.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/common/SessionConstants.java
src/main/java/org/victor/stackora/controller/AuthController.java
```

Session 属性契约：

```text
key   = LOGIN_USER_ID
type  = java.lang.Long
value = 已通过认证的用户主键
```

### 5.5 正常流程

```text
POST /api/auth/login
  -> 参数校验
  -> Service 认证成功
  -> 失效旧 Session
  -> 创建新 Session
  -> 保存 Long userId
  -> 返回安全用户信息和 JSESSIONID Cookie
```

### 5.6 异常、安全与并发边界

- 参数校验、账号查询、密码匹配或状态校验失败时，不调用 `getSession(true)`。
- 登录失败不能破坏调用方已有的合法 Session；只有新凭证认证成功后才轮换 Session。
- Session 中不得保存密码、密码哈希、Entity 和完整请求。
- 同一浏览器重复登录时，旧 Session 失效并创建新 Session。
- 不同客户端的并发登录请求可以各自获得有效 Session，第一版允许多会话，不实现后登录踢出前登录。
- 同一个旧 Session 发起两个并发登录时，后执行失效操作的一方不得因为 `IllegalStateException` 返回 500；旧会话已失效即视为清理成功，两个已正确认证的请求可以各自创建新 Session。
- 已登录用户 A 使用错误的 B 凭证登录时，原 A Session 保持有效；使用正确的 B 凭证登录时，旧 Session 失效，新 Session 只保存 B 的用户 ID。

### 5.7 测试设计

- 登录成功创建 Session 并保存 Long 类型用户 ID。
- 已存在 Session 时，登录成功后获得不同的 Session ID。
- 登录失败时不创建新 Session。
- Session 不包含密码和完整用户对象。
- 登录接口返回 200 和安全用户信息。
- A 已登录后使用错误 B 凭证，原 Session 仍可查询到 A。
- A 已登录后使用正确 B 凭证，新 Session 查询到 B，旧 Session 不再有效。
- 同一旧 Session 并发登录不返回 500。
- 登录响应包含 `Cache-Control: no-store`。

### 5.8 完成标准

- 登录成功后客户端可以通过 Cookie 维持身份。
- 旧 Session 不能被继续用于相同浏览器的旧登录态。
- 失败登录不会产生伪登录态。
- Service 层没有 Servlet API 依赖。

---

## 6. 需求四：查询当前登录用户

### 6.1 需求行为

已登录调用方可以查询当前用户的最新安全信息。未登录、Session 异常、用户不存在或用户状态不再有效时，不返回用户信息。

### 6.2 具体设计

对外接口与 Servlet 相对映射：

```text
对外地址：GET /api/auth/me
Controller：GET /auth/me
```

Controller 执行顺序：

1. 使用 `request.getSession(false)` 获取 Session，不创建新 Session。
2. Session 不存在时抛出 `NOT_LOGIN`。
3. 读取 `LOGIN_USER_ID`。
4. 属性不是 `Long` 或值非正数时，使 Session 失效并抛出 `NOT_LOGIN`。
5. 调用 `getAuthenticatedUser(userId)` 查询最新用户。
6. 用户不存在、禁用或注销时，在只捕获 Session 失效竞态的窄范围内清理当前 Session，再重新抛出原业务异常；清理失败不得覆盖 `NOT_LOGIN`、`ACCOUNT_DISABLED` 或 `ACCOUNT_CANCELLED`。
7. 用户有效时返回 `CurrentUserResponse`；认证响应的禁止缓存响应头由统一过滤器写入。

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": "123",
    "account": "victor_01",
    "nickname": "Victor",
    "role": "USER"
  }
}
```

### 6.3 设计思路与取舍

- 每次查询数据库，确保昵称、角色和用户状态变化能够立即反映。
- Session 仅作为“用户主键已通过认证”的载体，不作为用户资料事实来源。
- 未登录查询不能调用 `getSession(true)`，避免产生大量无意义匿名 Session。
- 在登录拦截器尚未实现前，当前用户接口显式完成 Session 校验；后续拦截器可以复用相同常量和规则。

### 6.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/controller/AuthController.java
src/main/java/org/victor/stackora/service/UserAccountService.java
src/main/java/org/victor/stackora/service/impl/UserAccountServiceImpl.java
src/main/java/org/victor/stackora/model/vo/CurrentUserResponse.java
```

### 6.5 正常流程

```text
GET /api/auth/me + JSESSIONID
  -> 找到服务器 Session
  -> 读取 LOGIN_USER_ID
  -> 按 ID 查询最新 UserAccount
  -> 校验 ACTIVE
  -> 返回 CurrentUserResponse
```

### 6.6 异常、安全与并发边界

- 没有 Session 或用户 ID：`NOT_LOGIN`，HTTP 401。
- Session 用户已不存在：使 Session 失效并返回 `NOT_LOGIN`。
- Session 用户已禁用：使 Session 失效并返回 `ACCOUNT_DISABLED`，HTTP 403。
- Session 用户已注销：使 Session 失效并返回 `ACCOUNT_CANCELLED`，HTTP 403。
- `getAttribute()` 或失效清理遇到 `IllegalStateException` 时，Controller 只在 Session 操作的窄范围内转换为 `NOT_LOGIN`，不能全局吞掉同类型编程错误，也不能用清理异常覆盖原有账号状态异常。
- 当前用户响应不包含密码哈希、Session ID 和内部状态时间。

### 6.7 测试设计

- 登录后使用相同 Session 查询成功。
- 没有 Session 时返回 401，且不创建 Session。
- Session 缺少用户 ID、属性类型错误或 ID 非法时返回 401。
- 用户不存在时 Session 失效。
- 用户禁用或注销后，旧 Session 失效并返回对应 403。
- 返回字段不包含密码哈希和 Entity 内部字段。
- 构造已失效 Session 或让 Session 操作抛出 `IllegalStateException`，接口稳定返回 401 而不是 500。
- 响应包含 `Cache-Control: no-store`。

### 6.8 完成标准

- 当前用户信息来自数据库最新状态。
- 无效 Session 不会返回用户数据。
- 用户状态变化能够终止旧登录态。
- 未登录查询不会创建匿名 Session。

---

## 7. 需求五：幂等退出当前登录

### 7.1 需求行为

调用方可以主动销毁当前 Session。无 Session、Session 已过期或重复退出均返回成功，使客户端可以安全重复调用退出接口。

### 7.2 具体设计

对外接口与 Servlet 相对映射：

```text
对外地址：POST /api/auth/logout
Controller：POST /auth/logout
```

Controller：

1. 使用 `request.getSession(false)` 获取现有 Session。
2. Session 存在时调用 `invalidate()`。
3. Session 不存在或 `invalidate()` 因已被并发请求失效而抛出 `IllegalStateException` 时，按已退出处理。
4. 返回 `ApiResponse<Boolean>`，`data` 为 `true`；认证响应的禁止缓存响应头由统一过滤器写入。

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": true
}
```

### 7.3 设计思路与取舍

- 退出登录是客户端清理动作，幂等成功比返回“未登录错误”更容易正确重试。
- 使用 POST，避免浏览器预加载、爬虫或外部链接触发退出。
- 当前只销毁发起请求的 Session，不影响其他浏览器和设备。
- 第一版安全退出的完成定义是服务端 Session 失效；不承诺 `invalidate()` 会让浏览器立即删除 `JSESSIONID`。客户端即使继续携带旧 Cookie，也不能恢复身份。

### 7.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/controller/AuthController.java
```

退出不访问数据库，不调用 `UserAccountService`。

### 7.5 正常流程

```text
POST /api/auth/logout + JSESSIONID
  -> 获取现有 Session
  -> invalidate
  -> 返回 true
  -> 再访问 /api/auth/me 返回 401
```

### 7.6 异常、安全与并发边界

- 没有 Cookie、Cookie 无效或 Session 已过期：返回成功。
- 两个并发退出请求最多一个实际执行失效，两个都返回成功。
- 不在 URL、日志和响应中输出 Session ID。
- login 只接受 JSON 请求体，me 是只读 GET，logout 是不带请求体的幂等 POST；三者的媒体类型和请求形式并不相同。
- 本模块不配置允许携带凭据的通配 CORS，也不声称服务器已经通过 `Origin` 校验或 CSRF Token 强制同源。
- `SameSite=Lax` 只降低部分跨站请求自动携带 Cookie 的风险，不能替代完整 CSRF 防护。第一个需要登录且会修改业务数据的接口上线前，必须重新评审 CORS、可信来源与 CSRF 方案。

### 7.7 测试设计

- 已登录退出后 Session 失效。
- 退出后查询当前用户返回 401。
- 未登录退出返回 200 和 `true`。
- 重复退出返回相同成功结果。
- 退出接口没有数据库交互。
- 构造 `invalidate()` 抛出 `IllegalStateException` 的 Session，仍返回 200 和 `true`。
- 响应包含 `Cache-Control: no-store`。

### 7.8 完成标准

- 当前 Session 可以可靠销毁。
- 退出接口幂等。
- GET 请求不能触发退出。
- 退出后旧 Session 不再获得用户身份。

---

## 8. 需求六：配置 Session 与 Cookie 安全属性

### 8.1 需求行为

系统必须为 Session 设置明确空闲超时，并根据环境控制 Cookie 的浏览器安全属性。生产环境不能通过明文 HTTP 发送 Session Cookie。

### 8.2 具体设计

公共配置：

```yaml
server:
  servlet:
    context-path: /api
    session:
      timeout: 30m
      cookie:
        http-only: true
        same-site: lax
```

开发和测试环境：

```yaml
server:
  servlet:
    session:
      cookie:
        secure: false
```

生产环境：

```yaml
server:
  servlet:
    session:
      cookie:
        secure: true
```

第一版继续使用容器默认 Cookie 名 `JSESSIONID`，不自定义第二个认证 Cookie。

路径契约固定为：

```text
Servlet context path = /api
AuthController       = /auth
对外认证接口          = /api/auth/**
MockMvc 相对路径      = /auth/**
```

不得再在 Controller 上重复增加 `/api`，否则会形成 `/api/api/auth/**`。

认证响应禁止缓存由 Web 边界统一实现：

```java
@Component
public final class AuthResponseHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isAuthenticationEndpoint(request.getServletPath())) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        filterChain.doFilter(request, response);
    }
}
```

过滤器使用 `@Component` 作为唯一注册机制。Spring Boot 将被组件扫描发现的 `Filter` Bean 自动注册到 Servlet Filter 链；本模块不得再通过 `@Bean` 或 `FilterRegistrationBean` 重复注册。过滤器只匹配 Servlet 相对路径 `/auth/login`、`/auth/me` 和 `/auth/logout`，使用 `getServletPath()` 避免将全局 context path 重复写进匹配逻辑。响应头在进入 MVC 参数解析之前写入，因此参数校验、业务异常和未知异常产生的 4xx/5xx 响应也具有 `Cache-Control: no-store`；Controller 不重复设置该响应头。

本模块同时完成现有外部地址迁移：

| 能力 | 迁移前外部地址 | 迁移后外部地址 |
| --- | --- | --- |
| 用户注册 | `/auth/register` | `/api/auth/register` |
| Actuator 健康检查 | `/actuator/health` | `/api/actuator/health` |
| Actuator 信息 | `/actuator/info` | `/api/actuator/info` |
| OpenAPI 分组文档 | `/v3/api-docs/default` | `/api/v3/api-docs/stackora` |
| Swagger UI | `/swagger-ui.html` | `/api/swagger-ui.html` |

项目尚未对外发布，不保留无 `/api` 前缀的兼容地址；迁移完成后旧地址预期返回 404。Knife4j 尚未作为当前运行依赖，因此本模块不承诺 `/api/doc.html`；将来启用时必须遵循同一个 context path。

OpenAPI 分组只保留一个配置来源：删除 `application-dev.yml` 中的 `springdoc.group-configs`，在 `OpenApiConfig` 中声明 `GroupedOpenApi`，分组名固定为 `stackora`，扫描 `org.victor.stackora.controller`，匹配 Servlet 相对路径 `/**`。`/api` 只由 `server.servlet.context-path` 和 OpenAPI `servers.url` 表达，不能写进 `pathsToMatch`。

### 8.3 设计思路与取舍

- `HttpOnly` 防止普通前端脚本直接读取 Session Cookie。
- `SameSite=Lax` 兼顾同站 API 调用和基础跨站请求防护。
- `Secure=true` 只适用于 HTTPS；本地 HTTP 开发环境必须关闭，否则浏览器不会回传 Cookie。
- 30 分钟是空闲超时，不是绝对登录期限；每次有效请求可能延长 Session 生命周期。
- 当前不实现持久化 Remember Me Cookie。
- 统一过滤器为三个认证接口的全部响应设置 `Cache-Control: no-store`，避免浏览器、共享代理或未来 CDN 缓存用户身份数据或认证错误。

### 8.4 涉及文件与契约

```text
src/main/resources/application.yml
src/main/resources/application-dev.yml
src/main/resources/application-prod.yml
src/test/resources/application-test.yml
src/main/java/org/victor/stackora/filter/AuthResponseHeaderFilter.java
src/main/java/org/victor/stackora/config/OpenApiConfig.java
README.md
docs/modules/2026-07-14-project-bootstrap-design.md
docs/modules/2026-08-01-api-documentation-design.md
```

### 8.5 正常流程

```text
登录成功
  -> 容器生成 JSESSIONID
  -> 浏览器按 Cookie 属性保存
  -> 后续同站请求自动携带
  -> 30 分钟无活动后服务器 Session 过期
```

### 8.6 异常、安全与运维边界

- 本地 HTTP 环境错误设置 `Secure=true` 会导致登录成功后 `/api/auth/me` 仍表现为未登录。
- 真实生产部署必须通过 HTTPS、显式激活 `prod` profile，并在入口完成 HTTP 到 HTTPS 重定向；这些真实部署验证属于后续部署模块，不作为当前认证模块的可执行验收条件。
- 当前模块只证明：激活 `prod` profile 后，嵌入式 Servlet 容器实际返回带 `Secure` 属性的 Session Cookie。该证据不能替代真实 TLS、代理协议头和重定向验证。
- 应用重启会丢失内存 Session，第一版接受该限制。
- 多实例部署时不同实例无法共享内存 Session；进入多实例阶段后再评估 Spring Session Redis 和负载均衡策略。
- 反向代理部署时需要正确传递协议头，避免应用误判 HTTPS 状态。

### 8.7 测试设计

- 配置绑定测试断言 context path、Session 超时、`HttpOnly` 和 `SameSite=Lax`。
- 配置绑定测试断言 dev/test `Secure=false`、prod `Secure=true`。
- 使用嵌入式 Servlet 容器随机端口执行真实 HTTP 登录，断言响应 Cookie 包含 `JSESSIONID`、`HttpOnly` 和 `SameSite=Lax`。
- 使用随机端口嵌入式容器并激活 prod profile，执行登录请求，断言真实 `Set-Cookie` 包含 `Secure`；不能只检查 YAML 文本。
- 对三个认证接口分别验证成功与代表性失败响应均包含 `Cache-Control: no-store`，至少覆盖 200、400、401 和 403。
- Spring 上下文测试断言恰好存在一个 `AuthResponseHeaderFilter` Bean；注册接口和 Actuator 等非目标路径不得被该过滤器添加认证专用 `Cache-Control: no-store`。
- 对注册、Actuator、OpenAPI 分组文档和 Swagger UI 执行真实 HTTP 路径回归；新 `/api` 地址可用，旧无前缀地址返回 404。
- 解析 OpenAPI JSON，断言只有 `stackora` 分组契约、`servers.url=/api`，且 `paths` 不重复 `/api`。

### 8.8 完成标准

- Session 超时和 Cookie 属性不是依赖框架隐式默认值。
- 开发环境可以通过 HTTP 完成登录闭环。
- prod profile 的容器响应包含 `Secure` Cookie 属性；真实生产 TLS、HTTP 重定向和反向代理验证留给部署模块。
- 三个认证接口的 200、400、401、403 响应均禁止缓存。
- `AuthResponseHeaderFilter` 只注册一次，并且不影响注册、Actuator 等非目标路径。
- 注册、Actuator、OpenAPI 和 Swagger 外部地址已统一迁移到 `/api`，旧地址不再可用。
- 配置中不存在固定 Session ID 或真实凭据。

---

## 9. 需求七：建立认证闭环自动化验收

### 9.1 需求行为

默认 Maven 测试命令必须验证凭证认证、Session 建立、当前用户查询和退出登录的完整行为，不能只依赖 Swagger 或接口工具手工验证。

### 9.2 具体设计

测试分层：

| 测试类型 | 验证重点 | 是否连接 MySQL |
| --- | --- | --- |
| Service 单元测试 | 账号规范化、密码匹配、状态和异常语义 | 否 |
| Controller MockMvc 测试 | Controller 分支、Session 属性和统一响应 | 否 |
| 认证集成测试 | 真实 BCrypt 哈希、Mapper 和完整登录闭环 | 是 |
| 配置绑定测试 | 各 profile 的 context path、超时和 Cookie 属性 | 否 |
| 随机端口真实 HTTP 测试 | 容器 `Set-Cookie`、Cookie 回传和 Session ID 轮换 | 按现有测试配置 |
| OpenAPI 规范测试 | paths、请求响应 schema、状态码和 Cookie 安全方案 | 否 |

建议测试类：

```text
src/test/java/org/victor/stackora/service/UserAccountServiceLoginTest.java
src/test/java/org/victor/stackora/controller/AuthSessionControllerTest.java
src/test/java/org/victor/stackora/service/UserAuthenticationIntegrationTest.java
src/test/java/org/victor/stackora/config/SessionConfigurationTest.java
src/test/java/org/victor/stackora/controller/AuthSessionHttpIntegrationTest.java
src/test/java/org/victor/stackora/config/AuthOpenApiIntegrationTest.java
```

测试账号使用随机合法值，测试结束后回滚或安全清理。

### 9.3 设计思路与取舍

- Service Mock 测试验证业务分支，但不能证明真实 BCrypt 哈希和数据库映射可用。
- MockMvc 无旧 Session 登录后，必须从 `MvcResult` 取得本次登录创建的新 `MockHttpSession`，再用于 `/me` 和 `/logout`；不得继续复用已经被登录流程失效的旧对象。
- Session 轮换测试单独准备旧 Session，断言旧对象失效、新 Session ID 不同，并只用新 Session 继续闭环。
- MockMvc 使用 Servlet 相对路径 `/auth/**`；随机端口真实 HTTP 测试使用对外路径 `/api/auth/**`。
- 集成测试验证注册产生的哈希能够被登录流程匹配，防止注册和登录密码组件不一致。
- MockMvc 不证明真实容器生成的 Cookie 属性；`HttpOnly`、`SameSite`、`Secure` 和真实 Cookie 回传由配置绑定与随机端口 HTTP 测试负责。
- 第一版不引入 Testcontainers，继续使用现有测试数据库约定。

### 9.4 涉及文件与契约

除测试类外，涉及：

```text
.env.test
src/main/java/org/victor/stackora/config/OpenApiConfig.java
src/test/resources/application-test.yml
```

测试源码和配置中不得保存真实生产密码。

### 9.5 正常流程

```text
准备随机 ACTIVE 用户
  -> 真实 HTTP POST /api/auth/login
  -> 客户端保存新 JSESSIONID
  -> 真实 HTTP GET /api/auth/me
  -> 获得相同用户安全信息
  -> 真实 HTTP POST /api/auth/logout
  -> 再次真实 HTTP GET /api/auth/me
  -> 返回 401
```

### 9.6 异常、安全与并发边界

- 测试数据库不可用时集成测试必须明确失败，不能静默跳过。
- 测试不能依赖固定用户 ID 和执行顺序。
- 测试日志不得主动打印原始密码、密码哈希和 Session ID。
- 开发者明确开启 MyBatis DEBUG 时只允许使用测试数据，日志不得上传公开制品或生产日志平台。
- 本模块不测试跨实例 Session 和多设备强制下线。

### 9.7 测试场景

1. 正确账号密码登录成功。
2. 大写账号能够登录小写存储账号。
3. 账号不存在和密码错误响应一致。
4. 登录失败不创建 Session。
5. 禁用和注销用户不能登录。
6. 登录成功轮换已有 Session。
7. Session 只包含用户 ID。
8. 登录后可以查询当前用户。
9. 未登录查询当前用户返回 401。
10. 用户禁用或注销后旧 Session 失效。
11. 退出后当前用户接口返回 401。 
12. 未登录退出和重复退出保持成功。
13. 响应和普通业务日志不泄露认证敏感信息。
14. A 已登录后使用错误 B 凭证，A Session 保持有效。
15. A 已登录后使用正确 B 凭证，旧 Session 失效，新 Session 身份为 B。
16. login、me、logout 遇到已经失效的共享 Session 时分别返回约定结果，不返回 500。
17. 三个认证接口的成功及代表性 400、401、403 失败响应均包含 `Cache-Control: no-store`。
18. 配置绑定和随机端口 HTTP 测试证明 Cookie 属性在对应 profile 下生效。
19. OpenAPI 规范包含认证接口、主要响应状态和 Cookie Session 安全定义。

### 9.8 完成标准

- 默认测试命令发现并执行所有认证测试。
- 登录、当前用户和退出形成自动化闭环。
- 正常、异常、状态变化和 Session 安全行为均有证据。
- 测试失败能够指向明确业务规则。

---

## 10. 跨需求共同约束

### 10.1 分层约束

```text
AuthController
  -> UserAccountService
  -> UserAccountServiceImpl
  -> UserAccountMapper
  -> MySQL

AuthController
  -> HttpSession
```

- Controller 不查询数据库、不匹配密码、不判断账号状态。
- Service 不接收 `HttpServletRequest`、`HttpServletResponse` 或 `HttpSession`。
- Mapper 不管理 Session。
- Service 接口不继承 `IService`。
- Entity 不作为 HTTP 请求或响应。

### 10.2 安全约束

- 原始密码、密码哈希和 Session ID 不进入响应、业务异常消息和普通业务日志。
- 登录错误的状态码、业务码、消息和响应结构不能区分账号不存在和密码错误；时序侧信道作为已记录风险处理。
- 登录成功后必须废弃已有 Session。
- Session 只保存用户 ID。
- 用户状态变化必须在后续身份查询中生效。
- 生产 Cookie 必须启用 `HttpOnly`、`SameSite` 和 `Secure`。
- login 只接受 JSON 请求体，me 是只读 GET，logout 是不带请求体的幂等 POST；本模块不配置携带凭据的通配 CORS。
- 当前没有实现 `Origin` 强制校验或 CSRF Token，因此不能把应用描述成已经由服务器强制同源；`SameSite=Lax` 只是纵深防御。
- login、me 和 logout 的成功及失败响应必须包含 `Cache-Control: no-store`，由进入 MVC 之前的统一过滤器保证。

### 10.3 事务与并发约束

- 登录、当前用户查询和退出都不修改数据库，不额外增加数据库事务。
- Session 创建发生在认证成功之后。
- 退出当前 Session 是幂等操作。
- 第一版允许同一账号拥有多个有效 Session。
- 当前不引入数据库锁、JVM 锁、Redis 锁或分布式锁。

### 10.4 接口约束

| 接口 | 成功 HTTP | 主要失败 HTTP | 是否需要已有 Session |
| --- | --- | --- | --- |
| `POST /api/auth/login` | 200 | 400、401、403、500 | 否 |
| `GET /api/auth/me` | 200 | 401、403、500 | 是 |
| `POST /api/auth/logout` | 200 | 500 | 否，幂等 |

所有接口使用现有 `ApiResponse<T>` 结构。

Controller 内部相对映射分别为 `/auth/login`、`/auth/me`、`/auth/logout`；统一 `/api` context path 形成上表的对外地址。现有注册接口保持 `Controller /auth/register`、对外 `/api/auth/register`，必须执行回归测试。

### 10.5 OpenAPI 契约

OpenAPI 以 Servlet context path 和 Controller 相对路径分层表达：

```text
servers.url = /api
paths       = /auth/register
              /auth/login
              /auth/me
              /auth/logout
```

不得同时在 `servers.url` 和 `paths` 重复 `/api`。规范必须声明：

- `LoginRequest`、`LoginResponse`、`CurrentUserResponse` 和统一错误响应 schema。
- login 的 200、400、401、403、500 响应。
- me 的 200、401、403、500 响应。
- logout 的 200、500 响应以及“无 Session 也成功”的语义。
- 名为 `cookieAuth` 的安全方案：`type=apiKey`、`in=cookie`、`name=JSESSIONID`。
- login 为匿名接口；me 使用 `cookieAuth`；logout 不强制认证，但存在 Cookie 时会销毁对应 Session。

OpenAPI 集成测试必须解析实际 JSON，验证 server、paths、主要响应和 security scheme，不能只检查 Swagger 页面能够打开。

---

## 11. 完整验收清单

### 11.1 登录契约

- [x] `LoginRequest` 只包含账号和原始密码。
- [x] 登录请求对密码进行 `toString()` 脱敏。
- [x] `AuthenticatedUser` 不包含密码哈希。
- [x] 登录和当前用户响应不返回 Entity。
- [x] 用户 ID 以 JSON 字符串输出。

### 11.2 凭证认证

- [x] 账号使用 `Locale.ROOT` 转小写。
- [x] 使用 `PasswordEncoder.matches` 验证密码。
- [x] 账号不存在和密码错误返回相同响应。
- [x] 只有密码正确后才判断并暴露账号状态。
- [x] 只有 `ACTIVE` 用户可以登录。
- [x] Service 边界防御在 Mapper 调用前生效。

### 11.3 Session

- [x] 登录失败不创建 Session。
- [x] 登录成功废弃旧 Session 并创建新 Session。
- [x] Session 只保存 Long 类型用户 ID。
- [x] A 的失败换号登录保留 A Session，成功换号登录切换为 B。
- [x] 已失效共享 Session 的 login、me、logout 均不返回 500。
- [x] 当前用户查询不创建匿名 Session。
- [x] 禁用、注销或不存在的用户使旧 Session 失效。
- [x] 退出销毁当前 Session。
- [x] 未登录退出和重复退出保持成功。

### 11.4 Cookie 与配置

- [x] Session 空闲超时为 30 分钟。
- [x] Cookie 启用 `HttpOnly`。
- [x] Cookie 使用 `SameSite=Lax`。
- [x] 生产配置启用 `Secure`。
- [x] 激活 prod profile 的嵌入式容器测试证明响应 Cookie 包含 `Secure`。
- [x] 真实生产 TLS、HTTP 重定向和代理协议头验证已明确留给部署模块。
- [x] 开发和测试环境允许本地 HTTP 调试。
- [x] 三个认证接口的 200、400、401、403 响应包含 `Cache-Control: no-store`。
- [x] Spring 上下文中只存在一个 `AuthResponseHeaderFilter` Bean，非认证目标路径不被误加响应头。

### 11.5 接口与测试

- [ ] `POST /api/auth/login` 符合响应和错误契约。
- [ ] `GET /api/auth/me` 返回最新安全用户信息。
- [ ] `POST /api/auth/logout` 保持幂等。
- [ ] OpenAPI 的 `/api` server、四个认证 path、主要响应及 `cookieAuth` 契约正确。
- [ ] OpenAPI 只保留 `OpenApiConfig` 中的 `stackora` 分组配置，paths 不重复 `/api`。
- [ ] 注册、Actuator、OpenAPI 和 Swagger 新 `/api` 地址通过真实 HTTP 回归，旧无前缀地址返回 404。
- [ ] README、项目骨架文档和接口文档设计中的外部地址已同步迁移。
- [ ] Service 单元测试通过。
- [ ] Controller Session 测试通过。
- [ ] 真实 MySQL 认证集成测试通过。
- [ ] 配置绑定测试和随机端口真实 Cookie 测试通过。
- [ ] 现有注册接口路径与行为回归测试通过。
- [ ] `./mvnw clean test` 通过。
- [ ] `git diff --check` 无错误。

---

## 12. 测试与验收命令

加载测试环境：

```bash
while IFS= read -r line || [[ -n "$line" ]]; do
  case "$line" in
    ''|'#'*) continue ;;
  esac
  key=${line%%=*}
  value=${line#*=}
  export "$key=$value"
done < .env.test
```

执行：

```bash
./mvnw -Dtest=UserAccountServiceLoginTest,AuthSessionControllerTest test
./mvnw -Dtest=SessionConfigurationTest,AuthOpenApiIntegrationTest test
./mvnw -Dtest=AuthSessionHttpIntegrationTest test
./mvnw clean test
git diff --check
git status --short
```

预期：

- Maven 退出码为 0。
- failures、errors、skipped 均为 0。
- 认证测试被实际发现并执行。
- 测试数据库不遗留随机认证数据。
- Git 变更不包含 `.env.test`、日志文件和认证敏感信息。

---

## 13. 风险与后续边界

### 13.1 当前风险

- 内存 Session 在应用重启后丢失，用户需要重新登录。
- 多实例部署不能自动共享 Session。
- 没有登录限流时，公网环境存在密码暴力尝试风险。
- 只使用 `SameSite=Lax` 不能覆盖所有未来跨站部署场景。
- 用户不存在时未执行 BCrypt 等时匹配，仍可能存在有限的时序侧信道。
- 当前允许同一账号同时建立多个 Session。
- 第一版 `/me` 可以通过 `selectById` 加载完整 Entity，包括并不需要的密码哈希；在保证不记录、不响应该字段的前提下暂时接受，身份读取路径增多后再改为最小列投影。
- `HttpSession.invalidate()` 只保证服务器会话失效，不保证浏览器立即删除旧 `JSESSIONID`；旧 Cookie 不能恢复身份，因此当前不手工维护第二套 Cookie 清理逻辑。

### 13.2 后续边界

- 下一认证任务设计 Spring MVC Interceptor，统一保护必须登录的接口。
- 增加第一个登录后业务接口时，拦截器与 `/me` 必须共用统一的 Session 身份解析规则，避免复制状态和并发失效逻辑。
- 个人资料模块负责昵称、头像和简介修改。
- 管理员治理模块负责禁用、恢复和权限变更。
- 公网部署前增加登录限流、失败审计和必要的验证码策略。
- 多实例部署阶段评估 Spring Session Redis，不自行维护双写 Session。
- 任何第一个需要登录的业务写接口上线前完成 CORS 与 CSRF 设计；前后端跨站部署前进一步确认 Cookie Domain 和凭据请求策略。

---

## 14. Paicoding 对比

| 维度 | Stackora 设计 | Paicoding 实现 | 差异原因 | 当前是否借鉴 |
| --- | --- | --- | --- | --- |
| 密码验证 | Spring Security `PasswordEncoder.matches` | 自定义 MD5 加盐 `UserPwdEncoder` | Stackora 使用更安全、可演进的密码编码 | 采用 Stackora 方案 |
| 登录凭证 | Servlet `JSESSIONID` | 自定义 `f-session` Token | Stackora 当前是单体学习阶段 | 不照搬 Token |
| 会话存储 | 容器内存 Session | Redis 会话与用户会话索引 | Paicoding 需要多端和集中会话能力 | 以后借鉴 |
| 登录元数据 | 当前不记录 | 设备信息、登录类型、登录时间 | 当前没有审计和风控需求 | 以后借鉴 |
| 登录失败 | 统一凭证错误，状态在密码正确后判断 | 区分用户不存在和密码错误并记录审计 | Stackora 优先避免账号枚举 | 部分借鉴审计思路 |
| 退出登录 | 失效当前 HttpSession，幂等成功 | 删除 Redis 会话、Cookie 并跳转 | Stackora 是纯后端 API | 借鉴销毁当前会话 |
| 当前用户 | Session 用户 ID重新查库 | 请求上下文与 Session Helper | 当前架构规模不同 | 借鉴最新状态校验 |

Paicoding 对应证据路径：

```text
paicoding-reference/paicoding-main/paicoding-service/src/main/java/
  com/github/paicoding/forum/service/user/service/LoginService.java

paicoding-reference/paicoding-main/paicoding-service/src/main/java/
  com/github/paicoding/forum/service/user/service/user/LoginServiceImpl.java

paicoding-reference/paicoding-main/paicoding-service/src/main/java/
  com/github/paicoding/forum/service/user/service/help/UserSessionHelper.java

paicoding-reference/paicoding-main/paicoding-service/src/main/java/
  com/github/paicoding/forum/service/user/service/help/UserPwdEncoder.java

paicoding-reference/paicoding-main/paicoding-web/src/main/java/
  com/github/paicoding/forum/web/front/login/pwd/LoginRestController.java
```

Paicoding 已经解决多登录方式、Redis 会话、设备识别、登录审计和风险控制等成熟项目问题，但这些能力具有额外的数据结构、运维和一致性成本。Stackora 当前只借鉴其登录、Cookie、会话和退出的业务闭环，不复制其旧密码算法和复杂会话基础设施。

---

## 15. 整体验收

本模块只有在以下条件同时满足时完成：

1. 正确账号密码可以建立全新 Session。
2. 错误凭证、禁用账号和注销账号具有安全且稳定的错误响应。
3. Session 只保存用户 ID，旧 Session 在重新登录后失效。
4. 当前用户接口返回数据库中的最新安全用户信息。
5. 用户状态变化能够使旧登录态失效。
6. 退出接口幂等，退出后旧 Session 无法继续使用。
7. Cookie 和超时配置符合开发与生产环境边界。
8. 激活 `prod` profile 的嵌入式容器响应证明 Secure Cookie 配置生效；真实 HTTPS、重定向和代理验证明确留给部署模块。
9. 三个认证接口的成功及失败响应禁止缓存，OpenAPI 的路径、响应和 Cookie 安全方案与运行时一致。
10. 响应、异常和普通业务日志不泄露密码、密码哈希和 Session ID。
11. 相关单元测试、Controller 测试、真实 HTTP/数据库集成测试和完整 Maven 测试全部通过。
12. 注册、Actuator、OpenAPI 和 Swagger 的外部地址完成 `/api` 迁移，旧无前缀地址按决策停止使用。
13. 实现与本文档一致，并且没有引入范围外认证组件。
