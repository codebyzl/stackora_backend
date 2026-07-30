# Stackora 统一响应与异常处理需求与技术设计

## 0. 文档使用方式

本文档按调用链和开发顺序组织。每个需求在同一章节内说明外部行为、具体代码设计、采用原因、异常安全边界和测试，不需要在响应模型、异常流程和测试章节之间反复跳转。

已确认的第一版决策：

- 业务响应只使用 `code`、`message`、`data`。
- `data=null` 时允许省略 `data`。
- 业务失败使用真实 HTTP `4xx/5xx`，不是全部返回 HTTP `200`。
- 当前全局处理器只覆盖 `BusinessException` 和未知 `RuntimeException`。
- 参数校验、JSON 解析和参数类型错误的专用处理在正式 Request 接入后增加。
- 第一版参数校验错误只返回首个安全消息，不增加字段错误列表。

## 1. 模块概览

### 1.1 用户价值

本模块为 API 调用方和后端开发者提供稳定协议：

- 调用方可以通过 HTTP 状态和业务码判断结果。
- Controller 不需要重复拼装响应和捕获异常。
- Service 可以使用明确异常表达可预期业务失败。
- 未知异常不会向客户端泄露内部信息。
- 后续用户、文章等模块能够复用同一响应和错误契约。

### 1.2 最终能力

1. 使用 `ApiResponse<T>` 表达业务响应。
2. 使用 `ApiResponseFactory` 创建统一成功与失败结果。
3. 使用 `ErrorCode` 维护业务码、默认消息和 HTTP 状态。
4. 使用 `BusinessException` 表达可预期业务失败。
5. 使用 `GlobalExceptionHandler` 转换业务异常和未知运行时异常。
6. 保护 Actuator 标准响应不被业务包装。
7. 通过自动化测试验证状态码、响应结构和敏感信息边界。

### 1.3 本次范围

```text
ApiResponse<T>
ApiResponseFactory
ErrorCode
BusinessException
GlobalExceptionHandler
GlobalExceptionHandlerTest
```

### 1.4 明确不做

- 业务 Controller。
- 登录与权限实现。
- Jakarta Validation 具体使用。
- 参数校验、请求体解析和类型错误的专用处理器。
- 字段错误列表。
- 分页响应。
- Trace ID、国际化。
- 数据库和事务写入。
- Actuator 业务包装。

### 1.5 前置依赖

- Spring Web MVC。
- Jackson。
- JUnit 5 和 MockMvc。
- 已存在 Spring Boot 应用入口。

## 2. 需求与开发顺序

| 顺序 | 需求 | 可验证交付物 | 依赖 |
| --- | --- | --- | --- |
| 1 | 统一成功响应结构 | `ApiResponse<T>` | Web/Jackson |
| 2 | 统一响应创建方式 | `ApiResponseFactory` | 需求 1 |
| 3 | 统一错误码与 HTTP 状态 | `ErrorCode` | 需求 1、2 |
| 4 | 表达可预期业务失败 | `BusinessException` | 需求 3 |
| 5 | 统一转换业务和未知异常 | `GlobalExceptionHandler` | 需求 1～4 |
| 6 | 验证协议并保护框架端点 | 单元测试、MockMvc、Actuator 测试 | 需求 1～5 |

## 3. 需求一：所有业务接口使用统一响应结构

### 3.1 需求行为

业务接口成功时，调用方必须收到稳定的 `code`、`message` 和可选 `data`。

有数据响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1
  }
}
```

无数据响应：

```json
{
  "code": 0,
  "message": "ok"
}
```

### 3.2 具体设计

```java
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {
    private static final long serialVersionUID = ...;

    private final int code;
    private final String message;
    private final T data;

    public ApiResponse(int code, String message) {
        this(code, message, null);
    }
}
```

字段契约：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `code` | `int` | 成功为 `0`；失败来自 `ErrorCode` |
| `message` | `String` | 面向调用方的安全消息 |
| `data` | `T` | 成功数据；为 `null` 时省略 |

### 3.3 设计思路与取舍

- 泛型允许用户、文章、集合和 `Void` 共用一种响应类型。
- 字段使用 `final`，避免响应创建后被修改。
- `NON_NULL` 减少无数据响应的冗余；这是已接受的第一版契约。
- 第一版不增加时间、路径和 Trace ID，避免调用方过早绑定未使用字段。
- `ApiResponse` 不是 Entity 公开许可，业务数据仍需要 VO。

### 3.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/common/ApiResponse.java
```

常见类型：

```text
ApiResponse<UserProfileVO>
ApiResponse<ArticleDetailVO>
ApiResponse<List<ArticleSummaryVO>>
ApiResponse<Void>
```

### 3.5 正常流程

```text
Controller
  -> 获得 Service 返回的 VO
  -> 创建 ApiResponse<VO>
  -> Jackson 序列化
  -> HTTP 200
```

### 3.6 异常、权限与并发边界

- `data` 可以为空，`code` 和 `message` 必须有明确语义。
- 禁止将密码哈希、Token、内部状态或 Entity 直接放入 `data`。
- 响应模型没有事务和并发写入职责。
- 不通过继承为单个接口添加特殊字段，避免破坏统一契约。

### 3.7 测试设计

- 有数据对象的三个字段正确。
- 无数据对象中 `data=null`。
- JSON 序列化始终包含 `code`、`message`。
- `data=null` 时 JSON 不包含 `data`。
- 泛型对象和集合保持正确 JSON 结构。

### 3.8 完成标准

- 类位于 `common` 包。
- 只包含三个响应字段。
- 字段只读。
- 泛型和序列化行为符合契约。
- 响应中不泄露敏感 Entity 字段。

## 4. 需求二：统一创建成功与失败响应

### 4.1 需求行为

Controller 和异常处理器不应重复填写成功码、成功消息或错误码字段，而应使用统一工厂方法。

### 4.2 具体设计

```java
public final class ApiResponseFactory {
    private ApiResponseFactory() {
    }

    public static <T> ApiResponse<T> success(T data);

    public static ApiResponse<Void> success();

    public static ApiResponse<Void> error(
            ErrorCode errorCode
    );

    public static ApiResponse<Void> error(
            ErrorCode errorCode,
            String safeMessage
    );
}
```

固定成功值：

```text
code = 0
message = "ok"
```

### 4.3 设计思路与取舍

- 使用 `final class + private constructor` 表达无状态工具类，不使用接口承载静态方法。
- 工厂集中固定值，避免不同 Controller 使用 `success`、`ok` 或不同成功码。
- 工厂不判断业务是否成功；业务判断属于 Service。
- 工厂不决定 HTTP 状态；异常处理器使用 `ResponseEntity` 决定。
- 自定义消息仅接受调用方确认安全的业务文本。

### 4.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/common/ApiResponseFactory.java
```

调用方式：

```java
ApiResponseFactory.success(data);
ApiResponseFactory.success();
ApiResponseFactory.error(errorCode);
ApiResponseFactory.error(errorCode, safeMessage);
```

### 4.5 正常流程

```text
Controller
  -> Service 返回结果
  -> ApiResponseFactory.success(result)
  -> ApiResponse<T>
```

失败主要由异常处理器调用：

```text
GlobalExceptionHandler
  -> ApiResponseFactory.error(...)
  -> ApiResponse<Void>
```

### 4.6 异常、权限与并发边界

工厂不得：

- 查询数据库。
- 判断登录和权限。
- 捕获异常。
- 记录业务日志。
- 自动使用底层异常消息。
- 修改业务事务。

### 4.7 测试设计

- `success(data)` 返回固定成功码、消息和原数据。
- `success()` 返回 `Void` 响应。
- `error(errorCode)` 使用默认错误码和消息。
- `error(errorCode, safeMessage)` 只覆盖消息。
- 外部无法实例化工厂。

### 4.8 完成标准

- 工厂不可实例化和继承。
- 成功固定值只有一个定义入口。
- 四个方法返回类型正确。
- 工厂没有业务逻辑。

## 5. 需求三：使用业务码与真实 HTTP 状态表达失败

### 5.1 需求行为

调用方应能先通过 HTTP 状态判断请求类别，再通过业务码区分具体原因。

第一版映射：

| 枚举 | 业务码 | 默认消息 | HTTP 状态 |
| --- | ---: | --- | --- |
| `ERROR` | `-1` | `请求失败` | `400 Bad Request` |
| `PARAMS_ERROR` | `10000` | `请求参数错误` | `400 Bad Request` |
| `NOT_LOGIN` | `10001` | `用户未登录` | `401 Unauthorized` |
| `NO_AUTH` | `10002` | `用户无权限` | `403 Forbidden` |
| `SYSTEM_ERROR` | `99999` | `系统异常` | `500 Internal Server Error` |

后续用户业务错误码只有在相应模块确认后增加。

### 5.2 具体设计

```java
public enum ErrorCode {
    ...

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
```

规则：

1. 已发布业务码含义保持稳定。
2. 一个业务码只表达一种可识别原因。
3. HTTP 状态不能全部固定为 `200`。
4. 默认消息必须能够安全返回客户端。
5. 只有调用方需要区别处理时才新增业务码。

### 5.3 设计思路与取舍

- HTTP 状态用于网关、监控和通用客户端判断。
- 业务码用于应用内部精确分支，两者互补。
- 不为每一条动态文案创建错误码，避免枚举失控。
- `PARAMS_ERROR` 先保留契约，但当前没有专用校验异常处理器。
- `SYSTEM_ERROR` 使用通用消息，防止底层异常泄露。

### 5.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/common/ErrorCode.java
```

每项必须提供：

```text
getCode()
getMessage()
getHttpStatus()
```

### 5.5 正常流程

```text
BusinessException
  -> ErrorCode
  -> GlobalExceptionHandler
  -> HTTP status + business code + safe message
```

### 5.6 异常、权限与并发边界

- `NOT_LOGIN` 表示未认证，不等于权限不足。
- `NO_AUTH` 表示已经识别用户但没有授权。
- 错误码不能代替后端权限判断。
- 错误码不负责事务和并发控制，只表达最终结果。
- 未知异常不能使用原始异常消息覆盖 `SYSTEM_ERROR`。

### 5.7 测试设计

- 业务码唯一。
- 默认消息均非空白。
- HTTP 状态均非空。
- `NOT_LOGIN=401`。
- `NO_AUTH=403`。
- `SYSTEM_ERROR=500`。

### 5.8 完成标准

- 每个枚举包含业务码、默认消息和 HTTP 状态。
- 业务码不重复。
- 认证和授权状态语义正确。
- 未引入当前没有真实需求的错误码。

## 6. 需求四：使用 BusinessException 表达可预期失败

### 6.1 需求行为

Service 判断业务规则不满足时，应抛出带有非空 `ErrorCode` 的 `BusinessException`，而不是返回 `null`、魔法数字或 Web 响应对象。

### 6.2 具体设计

```java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(
            ErrorCode errorCode,
            String message
    ) {
        super(resolveMessage(errorCode, message));
        this.errorCode = errorCode;
    }
}
```

消息解析：

```java
Objects.requireNonNull(
        errorCode,
        "errorCode must not be null"
);

return message == null || message.isBlank()
        ? errorCode.getMessage()
        : message;
```

### 6.3 设计思路与取舍

- 继承 `RuntimeException`，使 Spring 事务默认回滚。
- `ErrorCode` 非空，保证异常能够映射为确定的 HTTP 结果。
- 单参数构造器统一委托，避免两套逻辑。
- 空白自定义消息回退默认消息，防止客户端收到空错误。
- 不保存 Request、Entity 或底层异常对象，减少耦合和敏感数据风险。

### 6.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/exception/BusinessException.java
```

允许：

```java
throw new BusinessException(ErrorCode.ERROR);

throw new BusinessException(
        ErrorCode.ERROR,
        "账号格式不正确"
);
```

禁止：

```java
throw new BusinessException(
        ErrorCode.ERROR,
        databaseException.getMessage()
);
```

### 6.5 正常流程

```text
Service
  -> 判断业务规则失败
  -> new BusinessException(ErrorCode)
  -> 异常向上抛出
  -> Spring 事务回滚
  -> Web 层统一处理
```

### 6.6 异常、权限与并发边界

- `errorCode=null` 属于开发错误，立即抛出 `NullPointerException`。
- 自定义消息必须是安全、稳定的业务文本。
- Service 不捕获后正常返回，否则可能破坏事务回滚。
- 业务异常不自动解决重复提交和并发竞态，数据正确性仍由事务、约束和条件更新保证。

### 6.7 测试设计

- 默认消息正确。
- 安全覆盖消息正确。
- `null`、空串、空格、Tab、换行回退默认消息。
- 空错误码立即失败。
- `getErrorCode()` 返回原枚举。

### 6.8 完成标准

- 异常继承 `RuntimeException`。
- 错误码不可为空。
- 消息回退行为一致。
- 安全消息边界有测试。

## 7. 需求五：统一转换业务异常和未知异常

### 7.1 需求行为

Controller 调用链出现异常时：

- `BusinessException` 返回其业务码、消息和真实 HTTP 状态。
- 未知 `RuntimeException` 返回 HTTP `500` 和 `SYSTEM_ERROR`。
- 未知异常完整信息只保留在服务端日志。

### 7.2 具体设计

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleBusinessException(BusinessException exception);

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleRuntimeException(RuntimeException exception);
}
```

映射：

| 异常 | HTTP 状态 | 响应 | 日志 |
| --- | --- | --- | --- |
| `BusinessException` | `errorCode.httpStatus` | 错误码与安全消息 | WARN |
| `RuntimeException` | `500` | `SYSTEM_ERROR` | ERROR + 完整异常 |

### 7.3 设计思路与取舍

- `@RestControllerAdvice` 集中处理业务 Controller 异常。
- 具体业务异常优先于通用运行时异常。
- 业务异常默认不打印完整堆栈，减少已知失败噪声。
- 未知异常必须记录异常对象，便于排障。
- 客户端只看到通用系统消息，避免泄露 SQL、路径和密码。
- 当前不处理所有框架异常，避免在没有 Request DTO 时提前复杂化。

### 7.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/exception/GlobalExceptionHandler.java
```

当前明确不提供专用处理：

```text
MethodArgumentNotValidException
ConstraintViolationException
HttpMessageNotReadableException
MethodArgumentTypeMismatchException
NoResourceFoundException
```

后续参数校验模块再确定这些异常的安全响应。

### 7.5 正常流程

业务异常：

```text
Service
  -> BusinessException
  -> handleBusinessException
  -> WARN log
  -> ApiResponseFactory.error
  -> ResponseEntity
  -> HTTP 4xx
```

未知异常：

```text
Application
  -> RuntimeException
  -> handleRuntimeException
  -> ERROR log with stack trace
  -> SYSTEM_ERROR
  -> HTTP 500
```

### 7.6 异常、权限与并发边界

客户端禁止接收：

- 密码、Token、Cookie、Session ID。
- JDBC URL、SQL 和 SQL 参数。
- Java 类名、服务器路径和堆栈。
- Redis、MQ、ES 或第三方 SDK 原始异常。

处理器不得：

- 执行业务补偿。
- 开启新业务事务。
- 返回 Entity。
- 捕获 `Throwable` 或 JVM `Error`。

### 7.7 测试设计

- 默认业务异常返回 HTTP `400`、`code=-1`。
- 安全消息业务异常返回自定义消息。
- 未知异常返回 HTTP `500`、`code=99999`。
- 原始未知异常消息不在响应。
- 错误 JSON 不包含 `data`。

### 7.8 完成标准

- 两类异常由不同方法处理。
- HTTP 状态与业务码一致。
- 未知异常不泄露客户端。
- 服务端保留未知异常证据。
- 文档没有把后续框架异常映射写成已完成。

## 8. 需求六：通过测试保护协议并保持 Actuator 独立

### 8.1 需求行为

统一响应变更后，自动化测试必须能够发现：

- 字段或固定值变化。
- HTTP 状态错误。
- 默认消息和安全消息错误。
- 未知异常泄露。
- Actuator 被业务响应包装。

### 8.2 具体设计

目标测试文件：

```text
src/test/java/org/victor/stackora/common/ApiResponseFactoryTest.java
src/test/java/org/victor/stackora/common/ErrorCodeTest.java
src/test/java/org/victor/stackora/exception/BusinessExceptionTest.java
src/test/java/org/victor/stackora/controller/GlobalExceptionHandlerTest.java
src/test/java/org/victor/stackora/actuator/ActuatorResponseTest.java
```

MockMvc 测试 Controller 仅触发异常，不进入生产代码。

### 8.3 设计思路与取舍

- 公共类型使用快速单元测试。
- HTTP 映射使用 MockMvc 行为测试。
- Actuator 单独测试，避免全局 Advice 意外改变框架端点。
- `@WebMvcTest` 应隔离 Mapper 和数据源，不能为 Web 测试连接开发库。
- 日志中的测试异常堆栈不代表测试失败，最终以断言和 Maven 结果为准。

### 8.4 涉及文件与契约

现有异常路径：

```text
GET /test/business-error
GET /test/business-error-custom-message
GET /test/runtime-error
```

每条路径断言：

```text
HTTP status
$.code
$.message
不存在 $.data
不存在原始内部消息
```

### 8.5 正常流程

```text
JUnit
  -> MockMvc
  -> Test Controller
  -> Exception
  -> GlobalExceptionHandler
  -> JSON assertions
```

Actuator：

```text
JUnit
  -> /actuator/health
  -> Actuator standard response
  -> assert $.status
  -> assert no $.code
```

### 8.6 异常、权限与并发边界

- Web Slice 不得加载 `SqlSessionFactory` 或真实数据源。
- 测试不需要用户权限和数据库。
- 测试不能只断言状态码，必须断言安全响应体。
- 未运行全量测试不能宣称模块通过。

### 8.7 测试设计

```bash
./mvnw -Dtest=ApiResponseFactoryTest test
./mvnw -Dtest=ErrorCodeTest test
./mvnw -Dtest=BusinessExceptionTest test
./mvnw -Dtest=GlobalExceptionHandlerTest test
./mvnw -Dtest=ActuatorResponseTest test
./mvnw clean test
```

### 8.8 完成标准

- 公共类型具有独立单元测试。
- 业务和未知异常具有 Web 行为测试。
- Actuator 保持标准响应。
- 测试不依赖长期数据库。
- 全量 Maven 测试通过。

## 9. 模块级公共约束

- Controller 只包装正常结果，不捕获通用异常。
- Service 抛出 `BusinessException`，不依赖 `ApiResponse` 或 `ResponseEntity`。
- Entity 不直接进入公开响应。
- 自定义客户端消息必须经过安全判断。
- 未知异常完整信息只记录在服务端。
- Actuator 不使用业务响应包装。

## 10. 完整验收清单

### 10.1 统一响应结构

- [x] `ApiResponse<T>` 使用泛型。
- [x] 只有 `code`、`message`、`data`。
- [x] 字段只读。
- [x] `data=null` 时省略。
- [ ] 序列化行为有独立测试。

### 10.2 响应创建

- [x] 工厂不可实例化。
- [x] 有数据和无数据成功方法存在。
- [x] 默认和自定义消息错误方法存在。
- [ ] 工厂行为有独立测试。

### 10.3 错误表达

- [x] `ErrorCode` 包含业务码、消息和 HTTP 状态。
- [x] `BusinessException` 拒绝空错误码。
- [x] 空白消息回退默认消息。
- [ ] 错误码唯一性和异常构造有独立测试。

### 10.4 异常转换

- [x] 处理 `BusinessException`。
- [x] 处理未知 `RuntimeException`。
- [x] 使用真实 HTTP 状态。
- [x] 未知异常不返回原消息。
- [ ] 错误响应缺少 `data` 的行为有断言。

### 10.5 测试与框架边界

- [x] 已有三个 MockMvc 异常场景。
- [ ] 公共组件单元测试齐全。
- [ ] Actuator 兼容性测试存在。
- [ ] `./mvnw clean test` 实际通过。

## 11. 测试命令与预期结果

```bash
./mvnw -Dtest=GlobalExceptionHandlerTest test
./mvnw clean test
git diff --check
```

预期：

- 所有测试无 Failure、Error。
- Maven 返回退出码 `0`。
- 未知异常测试的服务端日志允许出现测试堆栈，但响应断言必须安全。

## 12. 已知风险与技术债

1. 参数校验、JSON 解析和参数类型错误尚无专用映射。
2. 自定义安全消息依赖开发者判断，代码评审必须检查来源。
3. 错误码集合会随真实业务扩展，但不能提前枚举全部未来错误。
4. 当前没有 Trace ID，后续日志监控阶段再统一设计。
5. Web Slice 测试必须持续隔离 Mapper 和数据源。

## 13. 后续模块边界

后续模块使用方式：

- 用户持久化和注册使用 `BusinessException`。
- Swagger / Knife4j 描述响应结构和真实 HTTP 状态。
- Request DTO 接入后增加 Jakarta Validation。
- 认证模块使用 `NOT_LOGIN`、`NO_AUTH`。
- 具体业务按调用方需要增加错误码。

进入下一模块前，现有响应契约和异常安全测试必须稳定。

## 14. Paicoding 对比点

可在读取真实源码后比较：

- 响应字段。
- 错误码组织。
- 业务异常结构。
- 全局处理器覆盖范围。
- HTTP 状态策略。
- 参数错误粒度。

当前缺少可读取源码时，结论为“证据不足”。
