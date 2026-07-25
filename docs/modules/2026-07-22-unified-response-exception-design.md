# Stackora 统一响应与异常处理设计

## 1. 背景与目标

Stackora 后续会持续增加用户注册、登录、文章发布、评论、互动和后台治理接口。不同接口如果自行定义响应结构、错误码和异常处理方式，会直接导致以下问题：

- 调用方无法稳定判断请求成功、参数错误、认证失败、权限不足或系统异常。
- Controller 中出现大量重复 `try-catch`、手工拼装错误响应和不一致的 HTTP 状态。
- Service 层可能用 `null`、布尔值或魔法数字表达失败，业务语义不清晰。
- 未知异常可能把 Java 类名、SQL、服务器路径或第三方错误暴露给客户端。
- Web 层测试难以建立统一断言，后续模块质量不可控。

本模块的目标是定义 Stackora 业务 API 的统一响应和异常处理契约：成功响应结构固定，可预期失败由业务异常表达，框架级请求错误和未知异常由全局异常处理器转换为真实 HTTP 状态与安全响应体。

## 2. 范围

### 2.1 本次实现

本模块包含：

- `ApiResponse<T>` 统一业务响应体。
- `ApiResponseFactory` 成功与失败响应工厂。
- `ErrorCode` 基础错误码、默认消息和 HTTP 状态映射。
- `BusinessException` 业务异常。
- `GlobalExceptionHandler` 对业务异常和未知运行时异常的转换。
- 参数校验、请求体解析和参数类型错误的专用转换规则作为后续参数校验能力的扩展点。
- 服务端日志与客户端错误消息的安全边界。
- 统一响应与异常处理的 Web 层测试设计。

### 2.2 本次不实现

本模块不实现：

- 用户、文章、评论或其他业务接口。
- 登录、Session、权限拦截器或认证框架。
- 分页响应结构。
- 字段级错误详情列表。
- Trace ID、国际化或多语言错误消息。
- 数据库表、SQL、事务、缓存、消息队列或搜索。
- Actuator 响应包装。

## 3. 企业级使用场景与需求

### 3.1 前后端接口协作

前端、接口测试脚本和后续外部调用方需要依赖稳定响应契约判断请求结果。业务响应对象由 `code`、`message`、`data` 三个属性组成；当 `data` 为 `null` 时允许在 JSON 中省略该字段。失败结果通过 HTTP 状态和业务码共同表达原因。

### 3.2 后端业务规则表达

Service 层遇到可预期业务失败时，应抛出 `BusinessException`，例如参数语义不合法、用户未登录、无权限或请求状态冲突。Controller 不应通过大量分支手工返回失败响应，也不应吞掉异常后返回假成功。

### 3.3 框架级请求错误处理

请求参数校验失败、JSON 请求体不可读、查询参数类型不匹配等问题，后续接入参数校验能力时应增加专用 `400 Bad Request` 转换。当前版本不实现这些专用映射。

### 3.4 线上排障与安全边界

未知异常需要在服务端记录完整异常对象，便于开发者排查；客户端只接收通用系统错误，不暴露服务器路径、SQL、数据库连接信息、第三方 SDK 原始错误或异常堆栈。

### 3.5 自动化测试约束

统一响应和异常处理属于所有业务接口的基础设施。当前版本使用 Web 层测试验证业务异常和未知运行时异常；参数校验相关测试随对应能力接入时补充。

## 4. 业务与工程规则

1. 业务响应对象统一定义 `code`、`message`、`data` 属性；`data=null` 时允许序列化省略。
2. 成功响应使用业务码 `0`、消息 `ok` 和 HTTP `200 OK`。
3. 错误响应的 `data` 语义为 `null`，序列化时允许省略该字段。
4. HTTP 状态表示协议层结果，业务错误码表示应用层原因，两者不能互相替代。
5. Service 使用 `BusinessException` 表达可预期业务失败。
6. Controller 不捕获通用 `Exception`，也不将底层异常消息直接返回客户端。
7. `GlobalExceptionHandler` 负责把异常转换为统一业务响应。
8. 未知异常统一返回 `SYSTEM_ERROR`，服务端记录完整异常。
9. Actuator 使用 Spring Boot 标准响应，不经过业务响应封装。
10. 当前版本不新增 Jakarta Validation 或其他生产依赖。

## 5. 依赖边界

后续接入参数校验能力时需要 Jakarta Validation API 与校验实现，届时允许增加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

该依赖只用于 Bean Validation，包括 Controller 入参校验、请求体对象校验和方法参数校验。当前版本不增加该依赖，也不引入额外异常框架、认证框架或日志链路追踪组件。

## 6. 正常流程

```text
Client
  -> Controller 接收请求并触发参数校验
  -> Service 执行业务规则
  -> Service 返回业务结果
  -> Controller 调用 ApiResponseFactory.success(data)
  -> 返回 ApiResponse<T>
  -> HTTP 200 OK
```

示例响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1
  }
}
```

无业务数据的成功操作返回：

```json
{
  "code": 0,
  "message": "ok"
}
```

## 7. 异常流程

### 7.1 业务异常

```text
Service 判断业务规则不满足
  -> throw BusinessException(ErrorCode)
  -> GlobalExceptionHandler 捕获 BusinessException
  -> 按 ErrorCode.httpStatus 设置 HTTP 状态
  -> 使用 ApiResponseFactory.error(...) 返回 ApiResponse<Void>
```

业务异常适用于可预期失败，例如未登录、无权限、参数语义不合法或后续业务状态冲突。

### 7.2 参数校验失败

该映射在后续接入 Bean Validation 时实现。目标行为为返回 HTTP `400 Bad Request` 和 `PARAMS_ERROR`，并只返回首个安全校验消息。

### 7.3 请求体解析失败

该映射在后续增加请求对象和 JSON 入参校验时实现。目标行为为返回 HTTP `400 Bad Request` 和 `PARAMS_ERROR`，且不透出 Jackson 原始异常、Java 类名或反序列化路径。

### 7.4 参数类型错误

该映射在后续出现路径参数或查询参数类型转换需求时实现。目标行为为返回 HTTP `400 Bad Request` 和 `PARAMS_ERROR`，且不暴露 Java 内部类型转换细节。

### 7.5 未知异常

未被其他规则覆盖的异常返回 HTTP `500 Internal Server Error` 和 `SYSTEM_ERROR`。服务端使用 ERROR 级别记录完整异常对象，客户端只收到通用系统错误消息。

## 8. 权限与数据归属

本模块不实现登录和权限，但需要预留认证相关错误码：

- `NOT_LOGIN`：未登录或登录态失效。
- `NO_AUTH`：已登录但没有访问权限。

后续认证模块必须在后端抛出对应业务异常，不能只依赖前端隐藏按钮或路由限制。当前模块不读写数据库，不涉及数据归属、事务回滚、并发写入或幂等处理。

## 9. 数据模型

本模块不创建或修改数据库表。

统一响应、错误码、业务异常和异常处理器均属于应用层公共能力，不产生业务数据，不需要唯一约束、索引、逻辑删除或数据生命周期设计。

## 10. API 设计

### 10.1 ApiResponse

`ApiResponse<T>` 是所有业务 API 的响应体。

```text
ApiResponse<T>
├── int code
├── String message
└── T data
```

字段规则：

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `code` | `int` | 是 | 成功为 `0`；失败使用 `ErrorCode.code` |
| `message` | `String` | 是 | 面向调用方的安全消息 |
| `data` | `T` | 否 | 成功时为业务数据；为 `null` 时允许从 JSON 中省略 |

第一版不增加 `timestamp`、`path`、`traceId`、`errors`。这些字段只有在出现真实前端展示、排障链路或多服务协作需求后再扩展。

### 10.2 ApiResponseFactory

`ApiResponseFactory` 只负责创建响应对象，不包含业务判断。

```text
success(T data) -> ApiResponse<T>
success() -> ApiResponse<Void>
error(ErrorCode errorCode) -> ApiResponse<Void>
error(ErrorCode errorCode, String safeMessage) -> ApiResponse<Void>
```

Controller 正常返回时使用 `success`。失败响应主要由 `GlobalExceptionHandler` 创建，Controller 不应把 `error` 当作替代异常流程的通用分支。

### 10.3 ErrorCode

`ErrorCode` 为枚举，每项包含：

- `int code`
- `String message`
- `HttpStatus httpStatus`

第一版错误码：

| 枚举 | 业务码 | HTTP 状态 | 默认消息 | 使用场景 |
| --- | ---: | --- | --- | --- |
| `ERROR` | `-1` | `400 Bad Request` | `请求失败` | 通用可预期请求失败 |
| `PARAMS_ERROR` | `10000` | `400 Bad Request` | `请求参数错误` | 校验、解析或类型错误 |
| `NOT_LOGIN` | `10001` | `401 Unauthorized` | `用户未登录` | 未登录或登录态失效 |
| `NO_AUTH` | `10002` | `403 Forbidden` | `用户无权限` | 已登录但权限不足 |
| `SYSTEM_ERROR` | `99999` | `500 Internal Server Error` | `系统异常` | 未知系统异常 |

错误码发布后应保持含义稳定。后续模块只有在调用方需要区分处理时才新增错误码，不能为每条异常文本创建独立编码。

### 10.4 BusinessException

`BusinessException` 继承 `RuntimeException`，至少保存一个非空 `ErrorCode`。

允许构造方式：

```text
BusinessException(ErrorCode errorCode)
BusinessException(ErrorCode errorCode, String safeMessage)
```

`safeMessage` 只能用于可以暴露给客户端的业务描述。禁止直接传入数据库、HTTP 客户端、Redis、RabbitMQ、Elasticsearch 或第三方 SDK 的原始异常消息。

### 10.5 GlobalExceptionHandler

`GlobalExceptionHandler` 使用 `@RestControllerAdvice` 和 `@ExceptionHandler` 处理 `BusinessException` 与未知 `RuntimeException`，返回 `ResponseEntity<ApiResponse<Void>>`。

后续增加参数校验相关异常时，处理方法应从具体异常到通用异常排序。

## 11. 全局异常映射

| 异常类型 | HTTP 状态 | 业务码 | 客户端消息 | 服务端日志 |
| --- | --- | ---: | --- | --- |
| `BusinessException` | `ErrorCode.httpStatus` | `ErrorCode.code` | 默认消息或安全覆盖消息 | WARN，默认不打印完整堆栈 |
| `RuntimeException` | 500 | 99999 | `系统异常` | ERROR，记录异常对象 |

参数校验、请求体解析和参数类型错误的专用映射在对应能力接入后补充。

示例业务异常响应：

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

## 12. 调用链与职责

```text
Controller
  -> 参数接收与校验触发
  -> Service
      -> 正常返回业务结果
      -> 或抛出 BusinessException
  -> ApiResponseFactory
  -> GlobalExceptionHandler
  -> ResponseEntity<ApiResponse<?>>
```

职责划分：

- Controller：接收请求、触发参数校验、调用 Service、包装正常结果。
- Service：执行业务规则，在可预期失败时抛出 `BusinessException`。
- `ApiResponseFactory`：创建统一响应对象。
- `ErrorCode`：维护稳定业务码、默认消息和 HTTP 状态。
- `BusinessException`：表达可预期业务失败。
- `GlobalExceptionHandler`：将异常转换为 HTTP 状态和安全响应体。

## 13. 事务边界

本模块不读写数据库，不使用事务。

后续业务 Service 中抛出的 `BusinessException` 属于 `RuntimeException`，默认可触发 Spring 声明式事务回滚。异常处理发生在 Controller 调用链外层，不应在 Service 内部吞掉异常，否则会破坏事务回滚语义。

## 14. 并发、幂等与重复提交

本模块没有写操作，不涉及并发写入、重复提交或幂等处理。

后续业务模块出现重复提交、唯一约束冲突或状态冲突时，可以基于真实业务需求新增专门错误码；数据正确性必须由数据库约束、事务或并发控制保证，错误码只负责表达结果。

## 15. 安全与隐私

客户端错误响应禁止包含：

- 密码、Token、Cookie、Session ID。
- 完整 JDBC URL、数据库账号、SQL 或 SQL 参数。
- Java 类名、服务器文件路径、异常堆栈。
- Redis、RabbitMQ、Elasticsearch 或第三方 SDK 原始异常消息。
- 请求体中的敏感字段。

未知异常日志应记录完整异常对象，但当前阶段不记录完整请求体。后续登录、注册等模块需要对密码、手机号、邮箱、Token 等字段建立日志脱敏规则。

## 16. 测试设计

### 16.1 响应工厂测试

- `ApiResponseFactory.success(data)` 返回 `code=0`、`message=ok`、`data` 等于原始对象。
- `ApiResponseFactory.success()` 返回 `code=0`、`message=ok`、`data=null`。
- `ApiResponseFactory.error(errorCode)` 返回错误码默认消息。
- `ApiResponseFactory.error(errorCode, safeMessage)` 返回覆盖后的安全消息。

### 16.2 业务异常测试

- `BusinessException(ERROR)` 保留 `ErrorCode.ERROR`，异常消息为默认消息。
- `BusinessException(ERROR, safeMessage)` 保留错误码和 HTTP 状态，只覆盖客户端消息。
- 空白 `safeMessage` 回退到错误码默认消息。

### 16.3 Web 层异常映射测试

- 业务异常返回对应 HTTP 状态、业务码和消息。
- 未知异常返回 HTTP `500`、业务码 `99999` 和 `系统异常`。
- 参数校验、JSON 解析和参数类型错误测试随对应能力接入时补充。

### 16.4 Actuator 兼容性测试

- `/actuator/health` 保持 Spring Boot 标准响应，例如根字段 `status`。
- Actuator 响应不出现业务响应体的 `code` 和 `data` 字段。

## 17. 验收标准

- 所有业务响应对象定义 `code`、`message`、`data` 属性，`data=null` 时允许序列化省略。
- 成功响应为 HTTP `200 OK`、业务码 `0`、消息 `ok`。
- 每个基础错误码都有唯一 HTTP 状态和默认安全消息。
- `BusinessException` 不允许保存空 `ErrorCode`。
- `GlobalExceptionHandler` 当前覆盖业务异常和未知运行时异常。
- 未知异常客户端响应不包含内部异常信息，服务端日志保留完整异常对象。
- Controller 不重复捕获通用异常，Service 不返回魔法值表达失败。
- Actuator 响应不被 `ApiResponse` 包装。
- 当前 Web 层测试覆盖业务异常和未知运行时异常；其他测试随对应能力演进。
- 本模块不新增数据库结构或业务功能。

## 18. Paicoding 预对比点

后续可在有可读取源码时对比 Paicoding 的以下内容：

- 统一响应字段和错误码组织方式。
- 业务异常与全局异常处理器职责边界。
- 参数校验错误返回粒度。
- HTTP 状态与业务码的使用策略。
- Trace ID、国际化和多模块异常设计是否解决当前项目尚未遇到的问题。

当前项目不根据印象推断 Paicoding 实现，也不复制其源码。

## 19. 风险与技术债

- 当前错误码集合偏小，后续用户、文章和评论模块可能需要补充资源不存在、状态冲突等更细粒度错误码。
- 第一版只返回首个参数校验错误，批量表单体验有限；出现真实前端需求后再扩展字段错误列表。
- 当前没有 Trace ID，复杂链路排障能力有限；日志与可观测性阶段再统一设计。
- 自定义 `safeMessage` 依赖开发者判断，代码评审必须检查是否包含底层异常或敏感信息。
- 没有行为测试时，Maven 构建成功只能证明编译，不能证明异常映射正确。

## 20. 后续演进

本模块稳定后，后续模块按以下顺序承接：

1. 补齐 Web 层行为测试。
2. 在用户注册模块中首次复用统一响应和业务异常。
3. 根据真实业务场景补充资源不存在、数据冲突等错误码。
4. 在日志与监控阶段评估 Trace ID 和错误响应扩展字段。
