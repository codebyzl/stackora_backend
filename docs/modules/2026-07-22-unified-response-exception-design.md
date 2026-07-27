# Stackora 统一响应与异常处理需求与技术设计

## 文档说明

本文档同时描述 Stackora 业务 API 统一响应与异常处理模块的正式需求、技术设计、开发顺序和整体验收要求。

阅读顺序如下：

1. 第一部分说明调用方价值、响应契约、异常边界和当前范围。
2. 第二部分按照依赖顺序设计 `ApiResponse`、响应工厂、错误码、业务异常、全局异常处理器和测试。
3. 第三部分汇总完整完成清单、交付文件、调用链、测试命令、已知风险和后续模块前置条件。

本文档以当前已确认决策为准：

- 第一版业务响应只使用 `code`、`message`、`data`。
- `data` 为 `null` 时允许在 JSON 中省略。
- 失败使用真实 HTTP `4xx/5xx` 状态，不把所有业务失败包装成 HTTP `200`。
- 当前全局异常处理器只覆盖 `BusinessException` 和未知 `RuntimeException`。
- 参数校验、请求体解析失败和参数类型错误的专用映射在相关 Web 入参能力接入后实现。
- 第一版参数校验错误只返回首个安全消息，不增加字段错误列表。

# 第一部分：需求说明

## 1. 背景与用户价值

Stackora 后续会增加用户注册、登录、文章发布、评论、互动和后台治理接口。如果每个 Controller 自行决定响应字段、错误码和异常处理方式，将出现以下问题：

- 前端无法稳定判断成功、参数错误、认证失败、权限不足和系统故障。
- Controller 中重复出现 `try-catch` 和手工拼装响应。
- Service 通过 `null`、布尔值或魔法数字表达失败，业务语义不明确。
- HTTP 状态与业务错误码混用，监控和调用方无法正确识别失败。
- 未知异常可能向客户端泄露 SQL、路径、Java 类型或第三方异常信息。
- 后续接口测试无法复用统一断言。

本模块的直接用户包括：

- 调用 Stackora API 的前端和接口测试工具。
- 编写 Controller 与 Service 的后端开发者。
- 通过日志和 HTTP 状态排查故障的运维人员。

统一响应与异常处理的价值是建立稳定协议边界：成功结果结构一致，可预期失败具有明确业务语义，未知异常被安全收敛，框架管理端点保持独立。

## 2. 模块最终目标

本模块最终需要提供以下能力：

1. 使用 `ApiResponse<T>` 统一所有业务 API 的响应体。
2. 使用 `ApiResponseFactory` 创建成功和失败响应。
3. 使用 `ErrorCode` 统一维护业务码、默认安全消息和 HTTP 状态。
4. 使用 `BusinessException` 表达 Service 中可预期的业务失败。
5. 使用 `GlobalExceptionHandler` 将业务异常转换为真实 HTTP 状态和统一响应体。
6. 将未知运行时异常转换为 HTTP `500` 和安全系统错误响应。
7. 服务端日志保留排障证据，客户端不接收内部异常信息。
8. 保持 Actuator 等框架端点的标准响应，不强制包装为业务响应。
9. 通过自动化测试保护 HTTP 状态、业务码、消息和敏感信息边界。
10. 为后续认证、参数校验、用户和文章模块提供可复用的错误表达基础。

## 3. 功能范围

### 3.1 本次实现

本模块包含：

- `ApiResponse<T>`。
- `ApiResponseFactory`。
- `ErrorCode` 基础枚举。
- `BusinessException`。
- `GlobalExceptionHandler`。
- 业务异常的 HTTP 状态与响应体映射。
- 未知运行时异常的安全转换和服务端日志。
- 统一响应和异常处理的 Web 层测试。
- Actuator 响应不被业务响应包装的边界。

### 3.2 本次不实现

本模块不包含：

- 用户、文章、评论等业务接口。
- 登录、Session、权限拦截器或认证框架。
- Jakarta Validation 依赖和 Bean Validation 注解。
- `MethodArgumentNotValidException` 专用处理器。
- `HttpMessageNotReadableException` 专用处理器。
- `MethodArgumentTypeMismatchException` 专用处理器。
- 字段错误列表。
- 国际化和多语言消息。
- Trace ID、请求 ID 或分布式链路追踪。
- 分页响应结构。
- 数据库表、SQL、事务写入、缓存、消息队列或搜索。
- Actuator 业务包装。

以上非目标只有在后续模块出现真实需要时才扩展，不能为了显得完整而提前引入。

## 4. 核心业务与工程规则

### 4.1 响应契约

1. 业务响应只包含 `code`、`message`、`data` 三个属性。
2. `code` 使用整数。
3. 成功业务码为 `0`。
4. 成功消息为 `ok`。
5. `data` 使用泛型 `T` 表达不同业务结果。
6. `data=null` 时允许通过 Jackson `NON_NULL` 省略该字段。
7. 第一版不增加 `timestamp`、`path`、`traceId` 或 `errors`。
8. Entity 不因统一响应存在而可以直接作为公开接口数据；业务接口仍使用 VO。

### 4.2 HTTP 状态与业务码

1. HTTP 状态表达协议层结果。
2. 业务码表达应用层可区分原因。
3. 正常成功返回 HTTP `200 OK`。
4. 参数或通用请求失败返回 HTTP `400 Bad Request`。
5. 未登录返回 HTTP `401 Unauthorized`。
6. 已登录但无权限返回 HTTP `403 Forbidden`。
7. 未知系统异常返回 HTTP `500 Internal Server Error`。
8. 不允许所有失败都返回 HTTP `200`。
9. 调用方应先判断 HTTP 状态，再结合业务码处理具体原因。

### 4.3 业务异常

1. Service 使用 `BusinessException` 表达可预期失败。
2. `BusinessException` 必须包含非空 `ErrorCode`。
3. 未传自定义消息或消息为空白时，使用错误码默认消息。
4. 自定义消息必须是允许暴露给客户端的安全业务描述。
5. 禁止把底层异常的 `getMessage()` 直接作为安全消息。
6. Controller 不重复捕获 `BusinessException`。

### 4.4 未知异常

1. 未被更具体规则处理的 `RuntimeException` 视为未知系统异常。
2. 客户端只接收 `SYSTEM_ERROR` 的通用消息。
3. 服务端使用 ERROR 级别记录完整异常对象。
4. 响应不得包含异常类名、堆栈、SQL、路径或敏感配置。
5. 未知异常不能被吞掉后返回成功响应。

### 4.5 工厂与职责边界

1. `ApiResponseFactory` 只创建响应对象，不执行业务判断。
2. Controller 正常路径使用 `success`。
3. 失败响应主要由全局异常处理器创建。
4. Service 不返回 `ApiResponse`。
5. `GlobalExceptionHandler` 只负责异常到 HTTP 响应的转换，不执行业务补偿。
6. Actuator 响应不经过 `ApiResponseFactory`。

## 5. 正常业务流程

### 5.1 有数据成功响应

```text
Client
  -> Controller 接收请求
  -> Controller 调用 Service
  -> Service 返回业务结果
  -> Controller 调用 ApiResponseFactory.success(data)
  -> Jackson 序列化 ApiResponse<T>
  -> HTTP 200 OK
```

响应示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1
  }
}
```

### 5.2 无数据成功响应

适用于退出登录、删除成功或只需要确认动作完成的场景：

```text
Controller
  -> Service 完成业务动作
  -> ApiResponseFactory.success()
  -> HTTP 200 OK
```

由于 `data=null` 使用 `NON_NULL` 序列化策略，JSON 为：

```json
{
  "code": 0,
  "message": "ok"
}
```

### 5.3 直接使用响应工厂

`ApiResponseFactory` 的方法应通过类名调用：

```java
ApiResponseFactory.success(data);
ApiResponseFactory.success();
```

工具类不需要创建实例，也不作为 Spring Bean 注入。

## 6. 异常流程

### 6.1 默认业务异常

```text
Service
  -> throw new BusinessException(ErrorCode.ERROR)
  -> GlobalExceptionHandler.handleBusinessException
  -> 读取 ErrorCode.httpStatus
  -> ApiResponseFactory.error(errorCode, exceptionMessage)
  -> HTTP 400 + 统一错误响应
```

示例：

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json
```

```json
{
  "code": -1,
  "message": "请求失败"
}
```

### 6.2 带安全消息的业务异常

```java
throw new BusinessException(
        ErrorCode.ERROR,
        "账号格式不正确"
);
```

只有可以直接展示给调用方的业务消息才能通过该构造方法传入。

### 6.3 空 ErrorCode

```java
new BusinessException(null);
```

构造过程必须通过 `Objects.requireNonNull` 立即失败，避免产生一个无法确定业务码和 HTTP 状态的异常对象。

这个 `NullPointerException` 不属于正常业务失败，而是开发者违反构造契约造成的程序错误。

### 6.4 未知运行时异常

```text
Application Code
  -> throw RuntimeException
  -> GlobalExceptionHandler.handleRuntimeException
  -> log.error("系统异常", exception)
  -> ApiResponseFactory.error(SYSTEM_ERROR)
  -> HTTP 500
```

即使原始异常消息包含数据库密码、SQL 或服务器路径，客户端也只能收到：

```json
{
  "code": 99999,
  "message": "系统异常"
}
```

### 6.5 参数校验失败

当前版本不提供专用处理器。

后续接入 Bean Validation 后，目标行为为：

- HTTP `400 Bad Request`。
- 业务码 `10000`。
- 只返回首个安全校验消息。
- 不增加字段错误列表。
- 不暴露 Java 字段绑定和校验器内部细节。

### 6.6 请求体解析失败

当前版本不提供 `HttpMessageNotReadableException` 专用处理器。

后续出现 JSON Request 对象时，目标行为为：

- HTTP `400 Bad Request`。
- 业务码 `10000`。
- 返回通用安全消息。
- 不暴露 Jackson 异常、目标 Java 类型和反序列化路径。

### 6.7 参数类型错误

当前版本不提供 `MethodArgumentTypeMismatchException` 专用处理器。

后续出现路径参数或查询参数类型转换需求时，目标行为为：

- HTTP `400 Bad Request`。
- 业务码 `10000`。
- 返回安全的参数错误消息。
- 不暴露 Java 内部类型转换信息。

## 7. 权限、数据与安全规则

### 7.1 权限规则

本模块不实现权限校验，但定义认证模块后续使用的错误表达：

| 场景 | ErrorCode | HTTP 状态 |
| --- | --- | --- |
| 未登录或登录态失效 | `NOT_LOGIN` | `401 Unauthorized` |
| 已登录但没有权限 | `NO_AUTH` | `403 Forbidden` |

后续认证和授权必须在后端生效，不能只依赖前端隐藏按钮或页面。

### 7.2 数据规则

- 本模块不创建数据库表。
- 本模块不读写业务数据。
- `ApiResponse` 只承载已经由业务层转换完成的公开数据。
- Entity、密码哈希、内部状态和审计字段不能因为响应泛型而直接暴露。
- 事务、并发和幂等由具体业务 Service 负责。

### 7.3 客户端消息边界

客户端消息允许包含：

- 稳定的错误码默认消息。
- 已经过业务判断的安全提示。
- 调用方可以采取行动的错误描述。

客户端消息禁止包含：

- 密码、密码哈希、Token、Cookie、Session ID。
- 完整 JDBC URL、数据库账号、SQL 和 SQL 参数。
- Java 类名、包名、服务器路径和异常堆栈。
- Redis、RabbitMQ、Elasticsearch 或第三方 SDK 原始异常。
- 请求体中的敏感字段。

### 7.4 服务端日志边界

- 业务异常默认使用 WARN 级别记录安全业务消息。
- 未知异常使用 ERROR 级别记录异常对象和堆栈。
- 当前阶段不记录完整请求体。
- 后续注册、登录模块必须对账号、密码、Token 等字段建立脱敏规则。

## 8. 需求级验收标准

满足以下条件后，本模块在需求层面成立：

1. 所有业务 API 采用 `code`、`message`、`data` 契约。
2. 成功和失败的 HTTP 状态语义明确。
3. 可预期业务失败通过 `BusinessException` 表达。
4. 错误码同时提供业务码、默认消息和 HTTP 状态。
5. 未知异常不会把内部信息返回客户端。
6. Service、Controller、响应工厂和异常处理器职责清晰。
7. Actuator 保持标准响应。
8. 当前实现范围与后续参数错误扩展明确分离。
9. 测试场景能够验证响应结构、状态码和安全边界。

# 第二部分：技术设计与开发步骤

## 步骤一：统一响应对象

### 9.1 目标

定义稳定、泛型化且可序列化的业务 API 响应结构。

### 9.2 涉及文件

```text
src/main/java/org/victor/stackora/common/ApiResponse.java
```

### 9.3 类型设计

```java
public class ApiResponse<T> implements Serializable {
    private final int code;
    private final String message;
    private final T data;
}
```

`T` 由具体业务接口决定，例如：

```text
ApiResponse<UserProfileVO>
ApiResponse<ArticleDetailVO>
ApiResponse<List<ArticleSummaryVO>>
ApiResponse<Void>
```

### 9.4 字段设计

| 字段 | Java 类型 | JSON 必需性 | 规则 |
| --- | --- | --- | --- |
| `code` | `int` | 必须出现 | 成功为 `0`，失败使用 `ErrorCode.code` |
| `message` | `String` | 必须出现 | 面向调用方的安全消息 |
| `data` | `T` | 可省略 | 有数据时返回；为 `null` 时通过 `NON_NULL` 省略 |

第一版只有这三个字段，不增加其他元数据。

### 9.5 不可变性与构造方式

- 三个实例字段使用 `final`，构造后不允许被修改。
- 使用 Getter 提供只读访问。
- 全参数构造器用于创建完整响应。
- 两参数构造器将 `data` 固定为 `null`。
- 实现 `Serializable` 并声明 `serialVersionUID`。

`ApiResponse` 不强制声明为 `final class`，当前没有通过继承扩展响应体的需求；团队规范仍禁止为单个接口创建继承层级破坏统一契约。

### 9.6 序列化规则

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
```

该规则意味着失败响应和无数据成功响应可以省略 `data`，这是当前已接受的 API 契约。

### 9.7 异常与边界

- `data` 可以为 `null`，但 `code` 和 `message` 不应为空。
- 泛型不能替代 VO 边界，禁止直接返回带密码哈希的 Entity。
- 不提供 Setter，避免响应创建后被修改。
- 不通过继承为不同业务增加字段。

### 9.8 本步骤测试

- 构造有数据响应，三个字段读取正确。
- 构造无数据响应，`data` 为 `null`。
- JSON 序列化包含 `code` 和 `message`。
- `data=null` 时 JSON 不包含 `data`。
- `data` 为对象或集合时保持对应 JSON 结构。

### 9.9 本步骤完成标准

- 类位于 `common` 包。
- 类型使用泛型。
- 字段名称固定为 `code`、`message`、`data`。
- 字段只读。
- `null data` 序列化行为符合已确认契约。

## 步骤二：响应工厂

### 10.1 目标

集中创建成功和失败响应，避免 Controller 重复填写业务码和消息。

### 10.2 涉及文件

```text
src/main/java/org/victor/stackora/common/ApiResponseFactory.java
```

### 10.3 类结构

`ApiResponseFactory` 使用不可实例化的工具类结构：

```java
public final class ApiResponseFactory {
    private ApiResponseFactory() {
    }
}
```

`final` 防止通过继承改变工厂语义；私有构造器防止无意义实例化。

本类不使用接口承载静态方法，因为接口的主要职责是定义对象行为契约，而该类只是无状态的响应对象工厂。

### 10.4 成功方法

```java
public static <T> ApiResponse<T> success(T data);

public static ApiResponse<Void> success();
```

固定行为：

```text
code = 0
message = "ok"
data = 调用方数据或 null
```

### 10.5 失败方法

```java
public static ApiResponse<Void> error(ErrorCode errorCode);

public static ApiResponse<Void> error(
        ErrorCode errorCode,
        String message
);
```

默认错误方法使用 `ErrorCode.message`。自定义方法只允许接收经过业务判断的安全消息。

### 10.6 职责边界

响应工厂不得：

- 查询数据库。
- 判断用户是否登录。
- 决定权限。
- 捕获异常。
- 记录业务日志。
- 修改 HTTP 状态。
- 把底层异常消息自动写入响应。

HTTP 状态由 Controller 正常返回机制或 `GlobalExceptionHandler` 的 `ResponseEntity` 决定。

### 10.7 异常与边界

- `errorCode` 必须由调用方提供。
- 工厂本身不替代 `BusinessException`。
- Controller 不应在每个业务失败分支直接返回 `error`。
- 自定义消息为空白时的回退由 `BusinessException` 处理；直接调用工厂时调用方必须保证消息符合契约。

### 10.8 本步骤测试

- `success(data)` 返回数据原引用或等价值。
- `success()` 返回成功码、成功消息和 `null data`。
- `error(errorCode)` 使用默认码和默认消息。
- `error(errorCode, safeMessage)` 使用同一业务码和自定义安全消息。
- 工厂类不能被外部实例化。

### 10.9 本步骤完成标准

- 工厂类不可实例化。
- 成功码和成功消息只有一个定义位置。
- 方法泛型返回类型正确。
- 工厂不包含业务逻辑。

## 步骤三：基础错误码

### 11.1 目标

统一维护客户端可识别的业务码、默认安全消息和对应 HTTP 状态。

### 11.2 涉及文件

```text
src/main/java/org/victor/stackora/common/ErrorCode.java
```

### 11.3 枚举字段

```java
private final int code;
private final String message;
private final HttpStatus httpStatus;
```

错误码实例创建后不可修改。

### 11.4 第一版错误码

| 枚举 | 业务码 | 默认消息 | HTTP 状态 | 使用场景 |
| --- | ---: | --- | --- | --- |
| `ERROR` | `-1` | `请求失败` | `400 Bad Request` | 通用可预期请求失败 |
| `PARAMS_ERROR` | `10000` | `请求参数错误` | `400 Bad Request` | 后续参数校验、解析或类型错误 |
| `NOT_LOGIN` | `10001` | `用户未登录` | `401 Unauthorized` | 未登录或登录态失效 |
| `NO_AUTH` | `10002` | `用户无权限` | `403 Forbidden` | 已登录但权限不足 |
| `SYSTEM_ERROR` | `99999` | `系统异常` | `500 Internal Server Error` | 未知系统异常 |

### 11.5 编码规则

1. 已发布业务码含义保持稳定。
2. 一个业务码不能在不同模块表达不同含义。
3. 只有调用方需要采取不同处理时才新增错误码。
4. 不为每条动态错误文本创建独立业务码。
5. HTTP 状态不能全部固定为 `200`。
6. `SYSTEM_ERROR` 不能携带底层异常消息。

### 11.6 异常与边界

- `PARAMS_ERROR` 当前作为后续扩展预留，不代表专用处理器已经实现。
- 资源不存在、数据冲突等业务错误码在对应模块出现时再增加。
- 错误码枚举不包含日志、异常捕获或响应创建逻辑。

### 11.7 本步骤测试

- 所有业务码唯一。
- 所有默认消息非空白。
- 所有枚举都有非空 HTTP 状态。
- `NOT_LOGIN` 映射 `401`。
- `NO_AUTH` 映射 `403`。
- `SYSTEM_ERROR` 映射 `500`。

### 11.8 本步骤完成标准

- 三个字段完整且只读。
- 第一版错误码与已确认契约一致。
- 业务码和 HTTP 状态职责分离。
- 未提前增加没有调用方需求的细粒度错误码。

## 步骤四：业务异常

### 12.1 目标

使用一个具有明确错误码和安全消息的运行时异常表达可预期业务失败。

### 12.2 涉及文件

```text
src/main/java/org/victor/stackora/exception/BusinessException.java
```

### 12.3 类型设计

```java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
}
```

继承 `RuntimeException` 的原因：

- 业务方法签名不需要为每个可预期失败声明受检异常。
- 在 Spring 事务中默认触发回滚。
- 可以被 `@RestControllerAdvice` 统一处理。

### 12.4 构造方式

```java
public BusinessException(ErrorCode errorCode);

public BusinessException(
        ErrorCode errorCode,
        String message
);
```

单参数构造器委托给双参数构造器：

```java
this(errorCode, null);
```

### 12.5 消息解析

```java
private static String resolveMessage(
        ErrorCode errorCode,
        String message
) {
    Objects.requireNonNull(
            errorCode,
            "errorCode must not be null"
    );

    return message == null || message.isBlank()
            ? errorCode.getMessage()
            : message;
}
```

执行顺序保证 `errorCode=null` 时，异常对象不会构造成功。

### 12.6 可暴露消息边界

允许：

```text
账号格式不正确
当前状态不允许执行该操作
请先登录
```

禁止：

```java
throw new BusinessException(
        ErrorCode.ERROR,
        databaseException.getMessage()
);
```

底层异常消息可能包含 SQL、表名、连接信息、第三方地址或内部实现细节。

### 12.7 事务边界

`BusinessException` 是运行时异常，默认触发 Spring 声明式事务回滚。

Service 不得捕获后只记录日志并正常返回，否则事务可能提交部分数据。

全局异常处理器位于 Web 调用链外层，只负责生成 HTTP 响应，不改变已经发生的事务回滚。

### 12.8 异常与边界

- `ErrorCode` 为空属于开发错误，不转换成正常业务失败。
- 空白自定义消息回退到默认消息。
- 不在异常对象中保存 Request、Entity 或敏感上下文。
- `BusinessException` 不负责记录日志，避免多层重复打印。

### 12.9 本步骤测试

- 只传错误码时使用默认消息。
- 传安全消息时使用自定义消息。
- `null`、空串、空格、Tab、换行作为消息时回退默认消息。
- `errorCode=null` 时立即抛出 `NullPointerException`。
- `getErrorCode()` 返回构造时的枚举。

### 12.10 本步骤完成标准

- 业务异常继承 `RuntimeException`。
- 错误码字段不可变且非空。
- 消息回退逻辑明确。
- 客户端安全消息边界有测试保护。

## 步骤五：全局异常处理

### 13.1 目标

将当前范围内的业务异常和未知运行时异常统一转换为正确的 HTTP 状态和安全响应体。

### 13.2 涉及文件

```text
src/main/java/org/victor/stackora/exception/GlobalExceptionHandler.java
```

### 13.3 类级设计

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
}
```

`@RestControllerAdvice` 使处理方法对业务 Controller 生效，并直接序列化响应体。

### 13.4 业务异常处理

方法签名：

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ApiResponse<Void>>
handleBusinessException(BusinessException exception);
```

处理规则：

1. 使用 WARN 级别记录安全业务消息。
2. 从 `exception.getErrorCode()` 获取 HTTP 状态。
3. 从异常消息获取默认或安全覆盖消息。
4. 使用 `ApiResponseFactory.error(...)` 创建响应体。
5. 不打印业务异常完整堆栈作为默认行为。

### 13.5 未知运行时异常处理

方法签名：

```java
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiResponse<Void>>
handleRuntimeException(RuntimeException exception);
```

处理规则：

1. 使用 ERROR 级别记录完整异常对象。
2. HTTP 状态固定来自 `SYSTEM_ERROR.httpStatus`。
3. 响应体固定使用 `SYSTEM_ERROR`。
4. 不使用原始异常消息创建客户端响应。

### 13.6 当前异常映射表

| 异常 | HTTP 状态 | 业务码 | 客户端消息 | 日志 |
| --- | --- | ---: | --- | --- |
| `BusinessException` | `ErrorCode.httpStatus` | `ErrorCode.code` | 默认消息或安全覆盖消息 | WARN，记录业务消息 |
| `RuntimeException` | `500` | `99999` | `系统异常` | ERROR，记录完整异常 |

### 13.7 处理顺序

具体异常处理器优先于通用运行时异常处理器。

未来增加参数校验、解析和类型转换处理器时，需要继续遵循从具体到通用的规则，避免全部落入 `RuntimeException`。

### 13.8 当前明确不处理的异常

当前不新增以下处理方法：

```text
MethodArgumentNotValidException
BindException
ConstraintViolationException
HttpMessageNotReadableException
MethodArgumentTypeMismatchException
NoResourceFoundException
```

这些异常是否需要专用转换，由使用它们的后续模块单独评审。

### 13.9 异常与边界

- Controller 不应自己重复捕获上述异常。
- 全局异常处理器不执行事务补偿。
- 不在处理器中返回 Entity。
- 不捕获 `Throwable` 或 `Error`。
- Actuator 的标准错误响应不强制转换为 `ApiResponse`。

### 13.10 本步骤测试

- `BusinessException(ERROR)` 返回 HTTP `400`、`code=-1`、默认消息。
- 带安全消息的业务异常返回同一 HTTP 状态和自定义消息。
- 未知运行时异常返回 HTTP `500`、`code=99999`、`系统异常`。
- 未知异常原始消息不出现在响应体。
- `data` 在错误 JSON 中被省略。

### 13.11 本步骤完成标准

- `@RestControllerAdvice` 生效。
- 业务异常与未知异常使用不同处理方法。
- HTTP 状态与错误码映射正确。
- 未知异常不泄露内部消息。
- 当前范围没有被文档夸大为完整框架异常覆盖。

## 步骤六：自动化测试与框架边界

### 14.1 目标

通过独立测试保护响应对象、错误码、业务异常、Web 异常映射和 Actuator 兼容性。

### 14.2 涉及文件

```text
src/test/java/org/victor/stackora/common/ApiResponseFactoryTest.java
src/test/java/org/victor/stackora/common/ErrorCodeTest.java
src/test/java/org/victor/stackora/exception/BusinessExceptionTest.java
src/test/java/org/victor/stackora/controller/GlobalExceptionHandlerTest.java
src/test/java/org/victor/stackora/actuator/ActuatorResponseTest.java
```

### 14.3 响应与工厂测试

需要验证：

- 成功响应字段。
- 无数据成功响应。
- 默认失败响应。
- 自定义安全消息响应。
- `data=null` 的 JSON 省略行为。
- 工厂不可实例化。

### 14.4 错误码测试

需要验证：

- 业务码唯一。
- 默认消息非空白。
- HTTP 状态非空。
- 认证相关状态码正确。
- 系统错误使用 `500`。

### 14.5 业务异常测试

需要验证：

- 默认消息。
- 安全覆盖消息。
- 空白消息回退。
- 空错误码立即失败。
- 错误码能够被读取。

### 14.6 Web 层测试

测试 Controller 只用于触发异常：

```text
GET /test/business-error
GET /test/business-error-custom-message
GET /test/runtime-error
```

MockMvc 至少断言：

- HTTP 状态。
- `code`。
- `message`。
- 错误响应不包含 `data`。
- 未知异常原始消息不在响应体中。

### 14.7 测试切片边界

`@WebMvcTest` 只应加载 Web 层所需组件。

如果项目的全局 `@MapperScan` 导致测试尝试创建 Mapper，而测试切片没有 `SqlSessionFactory`，应调整测试导入或配置边界。不得通过连接真实开发数据库解决 Web 层测试配置错误。

### 14.8 Actuator 兼容性测试

需要验证：

- `/actuator/health` 根字段是 `status`。
- 响应不包含业务字段 `code`、`message`、`data`。
- `GlobalExceptionHandler` 不改变 Actuator 标准结构。

### 14.9 测试命令

```bash
./mvnw -Dtest=ApiResponseFactoryTest test
./mvnw -Dtest=ErrorCodeTest test
./mvnw -Dtest=BusinessExceptionTest test
./mvnw -Dtest=GlobalExceptionHandlerTest test
./mvnw -Dtest=ActuatorResponseTest test
./mvnw clean test
```

### 14.10 本步骤完成标准

- 公共类具有独立单元测试。
- Web 映射通过 MockMvc 行为测试验证。
- 未知异常安全边界有断言。
- Actuator 标准响应有回归测试。
- 干净测试不依赖长期开发数据库。
- Maven 测试实际返回成功。

## 步骤七：业务模块接入规则

### 15.1 目标

确保后续用户、文章和互动模块正确使用统一响应与异常处理，而不是绕过基础契约。

### 15.2 Controller 接入

Controller 正常路径：

```java
return ApiResponseFactory.success(result);
```

Controller 不应：

- 捕获通用 `Exception` 后返回成功。
- 直接拼装业务错误码。
- 返回底层异常消息。
- 把 Entity 直接放入响应。

### 15.3 Service 接入

Service 可预期失败：

```java
throw new BusinessException(
        ErrorCode.ERROR,
        "安全业务消息"
);
```

Service 不应：

- 返回 `ApiResponse`。
- 依赖 HTTP `ResponseEntity`。
- 吞掉运行时异常。
- 使用 `null` 表达所有失败。

### 15.4 新错误码接入

新增错误码前必须回答：

1. 调用方是否需要区分该错误？
2. 应使用哪个真实 HTTP 状态？
3. 默认消息是否安全？
4. 是否会与现有业务码冲突？
5. 是否属于当前业务模块范围？

### 15.5 本步骤测试

每个新业务接口至少验证：

- 成功响应契约。
- 一个关键业务异常。
- 未知异常不泄露。
- 认证或权限失败使用正确 HTTP 状态。
- 响应数据不包含敏感字段。

### 15.6 本步骤完成标准

- 新 Controller 复用 `ApiResponseFactory`。
- 新 Service 使用 `BusinessException` 表达可预期失败。
- 没有重复异常处理代码。
- 新增错误码具有真实调用方需求。

# 第三部分：整体总结与验收

## 16. 完整完成清单

### 16.1 ApiResponse

- [x] 类位于 `common` 包。
- [x] 使用泛型 `T`。
- [x] 只包含 `code`、`message`、`data`。
- [x] 三个字段为只读字段。
- [x] 实现 `Serializable`。
- [x] `data=null` 时允许省略 JSON 字段。
- [ ] JSON 序列化行为有独立测试。
- [ ] 公开业务 API 不直接返回敏感 Entity。

### 16.2 ApiResponseFactory

- [x] 工厂类声明为 `final`。
- [x] 构造器为私有。
- [x] 提供有数据成功方法。
- [x] 提供无数据成功方法。
- [x] 提供默认错误方法。
- [x] 提供安全消息错误方法。
- [ ] 工厂行为有独立单元测试。

### 16.3 ErrorCode

- [x] 包含 `code`。
- [x] 包含默认 `message`。
- [x] 包含 `httpStatus`。
- [x] 成功与失败不混用同一业务码。
- [x] `NOT_LOGIN` 使用 HTTP `401`。
- [x] `NO_AUTH` 使用 HTTP `403`。
- [x] `SYSTEM_ERROR` 使用 HTTP `500`。
- [ ] 业务码唯一性有自动化测试。

### 16.4 BusinessException

- [x] 继承 `RuntimeException`。
- [x] 保存 `ErrorCode`。
- [x] 拒绝空 `ErrorCode`。
- [x] 支持默认消息。
- [x] 支持安全覆盖消息。
- [x] 空白覆盖消息回退默认消息。
- [ ] 构造边界有完整单元测试。

### 16.5 GlobalExceptionHandler

- [x] 使用 `@RestControllerAdvice`。
- [x] 处理 `BusinessException`。
- [x] 处理未知 `RuntimeException`。
- [x] 业务异常使用错误码 HTTP 状态。
- [x] 未知异常返回 HTTP `500`。
- [x] 未知异常响应不使用原始消息。
- [x] 未知异常在服务端记录完整异常对象。
- [ ] 错误响应缺少 `data` 的行为有自动化断言。
- [ ] Web 层测试在当前工程配置下实际通过。

### 16.6 当前非目标

- [x] 未实现字段错误列表。
- [x] 未引入 Trace ID。
- [x] 未引入国际化。
- [x] 未包装 Actuator 响应。
- [x] 未把参数校验专用处理器误写为现行能力。
- [x] 未创建数据库表或事务逻辑。

### 16.7 测试与安全

- [x] 已编写业务异常 HTTP 状态测试场景。
- [x] 已编写安全覆盖消息测试场景。
- [x] 已编写未知异常安全响应测试场景。
- [ ] 已补充响应工厂单元测试。
- [ ] 已补充错误码单元测试。
- [ ] 已补充业务异常单元测试。
- [ ] 已补充 Actuator 兼容性测试。
- [ ] `./mvnw clean test` 实际通过。
- [ ] 客户端响应经测试确认不包含内部异常信息。

## 17. 交付文件清单

```text
src/main/java/org/victor/stackora/common/ApiResponse.java
src/main/java/org/victor/stackora/common/ApiResponseFactory.java
src/main/java/org/victor/stackora/common/ErrorCode.java
src/main/java/org/victor/stackora/exception/BusinessException.java
src/main/java/org/victor/stackora/exception/GlobalExceptionHandler.java
src/test/java/org/victor/stackora/common/ApiResponseFactoryTest.java
src/test/java/org/victor/stackora/common/ErrorCodeTest.java
src/test/java/org/victor/stackora/exception/BusinessExceptionTest.java
src/test/java/org/victor/stackora/controller/GlobalExceptionHandlerTest.java
src/test/java/org/victor/stackora/actuator/ActuatorResponseTest.java
docs/modules/2026-07-22-unified-response-exception-design.md
```

尚未创建的测试文件仍属于目标交付物，不能因为主代码存在就视为自动完成。

## 18. 整体调用链

### 18.1 正常响应

```text
Client
  -> Controller
  -> Service
  -> Result / VO
  -> ApiResponseFactory.success
  -> ApiResponse<T>
  -> HTTP 200
```

### 18.2 业务异常

```text
Client
  -> Controller
  -> Service
  -> BusinessException
  -> GlobalExceptionHandler
  -> ApiResponseFactory.error
  -> ResponseEntity<ApiResponse<Void>>
  -> HTTP 4xx
```

### 18.3 未知异常

```text
Application
  -> RuntimeException
  -> GlobalExceptionHandler
  -> Server ERROR Log
  -> SYSTEM_ERROR Response
  -> HTTP 500
```

### 18.4 Actuator

```text
Client
  -> Actuator Endpoint
  -> Actuator Standard Response
```

Actuator 不进入业务响应和异常转换链路。

## 19. 测试命令与预期结果

### 19.1 公共组件单元测试

```bash
./mvnw -Dtest=ApiResponseFactoryTest,ErrorCodeTest,BusinessExceptionTest test
```

预期：

- 响应工厂正常与异常场景通过。
- 错误码唯一性和 HTTP 映射通过。
- 业务异常空值和消息回退场景通过。

### 19.2 Web 异常映射测试

```bash
./mvnw -Dtest=GlobalExceptionHandlerTest test
```

预期：

- 三个异常场景全部通过。
- 没有 ApplicationContext 加载错误。
- 未连接长期开发数据库。

### 19.3 Actuator 兼容性测试

```bash
./mvnw -Dtest=ActuatorResponseTest test
```

预期：

- Actuator 保持标准响应。
- 不出现业务响应包装字段。

### 19.4 全量测试

```bash
./mvnw clean test
```

预期：

- Maven 返回退出码 `0`。
- 测试报告没有 Failure 或 Error。

## 20. 模块整体验收标准

统一响应与异常处理模块只有同时满足以下条件才算完成：

1. `ApiResponse<T>` 的字段、泛型和序列化结构符合已确认契约。
2. `ApiResponseFactory` 提供完整成功与失败工厂方法。
3. 第一版错误码业务码唯一、消息安全、HTTP 状态正确。
4. `BusinessException` 不允许空错误码，消息回退规则经过测试。
5. 业务异常返回对应真实 HTTP 状态。
6. 未知异常返回 HTTP `500` 和 `SYSTEM_ERROR`。
7. 未知异常客户端响应不包含内部异常消息。
8. Service 不依赖 Web 响应类型，Controller 不重复捕获异常。
9. Actuator 响应保持框架标准格式。
10. 当前范围没有错误宣称参数校验等专用处理器已经实现。
11. 关键单元测试和 Web 行为测试实际通过。
12. `./mvnw clean test` 实际返回成功。

## 21. 已知风险与技术债

### 21.1 参数错误映射尚未实现

参数校验、JSON 解析和类型转换当前可能进入 Spring 默认处理或更通用异常流程。出现正式 Request DTO 时，需要单独设计并补充具体异常处理器。

### 21.2 Web Slice 测试配置

项目增加 Mapper 扫描后，`@WebMvcTest` 可能尝试注册 Mapper，但测试切片没有 `SqlSessionFactory`。这是测试上下文边界问题，不能通过连接开发数据库绕过。

### 21.3 客户端消息依赖开发者判断

自定义安全消息仍可能被错误地传入底层异常文本。后续代码评审必须检查 `BusinessException` 的自定义消息来源。

### 21.4 错误码扩展

当前错误码集合只覆盖基础类别。用户注册、资源不存在和状态冲突等场景需要在相应模块按调用方需求增加，不能在本模块提前枚举全部未来错误。

### 21.5 可观测性

当前没有 Trace ID。单体基础阶段可以通过日志排查，后续链路复杂时再统一设计请求 ID 和日志上下文。

### 21.6 参数错误粒度

第一版参数校验只返回首个安全消息，不返回字段错误列表。只有前端出现批量表单错误展示需求时再扩展响应契约。

## 22. 后续模块边界

本模块稳定后，后续模块可以按以下顺序复用和扩展：

1. 用户持久化基础使用 `BusinessException` 表达可预期业务失败。
2. Swagger / Knife4j 描述统一响应和 HTTP 状态。
3. 用户注册引入 Request DTO 和 Bean Validation。
4. 参数校验模块增加具体框架异常映射。
5. 登录和权限模块使用 `NOT_LOGIN` 与 `NO_AUTH`。
6. 文章、评论等模块按真实需求增加资源不存在和状态冲突错误码。
7. 日志监控阶段评估 Trace ID。

## 23. 进入用户业务模块的前置条件

- 业务响应契约已经确定。
- HTTP 状态与业务码映射已经确定。
- `BusinessException` 的创建和安全消息边界已经确定。
- `GlobalExceptionHandler` 能处理业务异常和未知运行时异常。
- 未知异常不向客户端暴露内部信息。
- Web 层测试能够在隔离配置下执行。
- Actuator 不受业务响应包装影响。
- 全量测试通过后才把本模块视为稳定基础。

## 24. Paicoding 后续对比点

只有在能够读取实际参考源码时，才从以下维度进行证据化比较：

- 统一响应字段和序列化规则。
- 错误码组织与编码策略。
- 业务异常构造方式。
- 全局异常处理器覆盖范围。
- HTTP 状态与业务码的组合使用。
- 参数校验错误粒度。
- Trace ID、国际化和多模块异常设计。

对比必须说明对应源码路径、它解决的真实问题、引入成本和当前是否值得借鉴，不能根据印象推断或复制参考源码。
