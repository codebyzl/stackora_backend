# Stackora OpenAPI 接口文档需求与技术设计

## 0. 文档使用方式

本文档描述 Stackora 的接口文档基础能力。该能力属于全局工程基础设施，先于用户注册 HTTP 接口开发完成，后续所有业务模块复用同一套 OpenAPI 规范生成、Swagger UI 和 Knife4j UI，不在每个业务模块中重复配置。

开发顺序固定为：

```text
确认 Spring Boot 4 兼容依赖
  -> 接入 Springdoc 并生成 OpenAPI 文档
  -> 接入 Knife4j 纯 UI
  -> 按环境控制文档暴露
  -> 完成自动化与人工验收
```

---

## 1. 模块概览

### 1.1 用户价值

开发者需要在没有前端页面的阶段查看接口契约、请求模型、响应模型和错误状态，并能够直接发起调试请求。统一的接口文档可以减少接口约定歧义，也为用户注册、登录、文章和评论等后续模块提供可持续维护的调试入口。

### 1.2 最终能力

完成本模块后，系统具备以下能力：

1. Springdoc 根据 Spring MVC Controller 和 OpenAPI 3 注解生成 JSON 规范。
2. 开发和测试环境可以访问 Swagger UI。
3. 开发和测试环境可以访问 Knife4j UI。
4. 文档只收录 `org.victor.stackora.controller` 包内、路径为 `/api/**` 的业务接口。
5. 文档具有统一的项目名称、说明和 API 版本。
6. 生产环境不生成 OpenAPI JSON，两个 UI 均无法读取接口规范。
7. 自动化测试能够证明 OpenAPI JSON 可生成、扫描范围正确且不包含敏感示例。

### 1.3 当前范围

- Springdoc WebMVC UI 依赖和版本固定。
- Knife4j OpenAPI 3 纯 UI 依赖和版本固定。
- OpenAPI 元数据与接口分组配置。
- Swagger UI、Knife4j UI 和 OpenAPI JSON 的环境配置。
- API 文档集成测试与人工访问验证。
- 后续业务接口使用 OpenAPI 3 注解的最小规范。

### 1.4 明确不做

本模块不实现：

- 用户注册、登录或其他业务接口。
- JWT、OAuth2、Session 或接口鉴权方案。
- Knife4j 服务端增强、网关聚合和离线文档。
- 多服务文档聚合、SDK 生成和外部开发者门户。
- 在生产环境开放在线调试能力。
- 为尚不存在的业务接口批量编写注解。
- 修改统一响应、异常处理或数据库结构。

### 1.5 前置依赖

- 项目使用 Spring Boot `4.0.7`、JDK 17 和 Spring MVC。
- Controller 基础包为 `org.victor.stackora.controller`。
- 业务接口统一使用 `/api/**` 路径。
- Maven 可以从已配置仓库解析新增依赖。

---

## 2. 方案选择与开发顺序

### 2.1 可选方案

#### 方案 A：Springdoc 3.x + Knife4j 纯 UI

```text
Spring MVC Controller
  -> Springdoc 3.x 生成 OpenAPI JSON
  -> Swagger UI 展示和调试
  -> Knife4j 纯 UI 展示和调试
```

特点：

- Springdoc 是唯一的 OpenAPI 服务端解析器。
- Knife4j 只提供静态 UI，不接管 Springdoc 版本。
- Swagger UI 作为标准基线，Knife4j 作为中文友好的增强入口。
- 依赖职责单一，出现 Knife4j UI 兼容问题时不会影响 OpenAPI JSON 和 Swagger UI。

#### 方案 B：只使用 Knife4j Jakarta starter

Knife4j starter 会传递引入 Springdoc。Knife4j 官方快速开始主要给出 Spring Boot 3 的 starter 用法，没有给出其内置 Springdoc 与 Spring Boot 4 所需 Springdoc 3.x 的明确版本保证。直接使用可能产生 Springdoc 2.x/3.x 冲突或启动期错误。

#### 方案 C：只使用 Springdoc 和 Swagger UI

兼容路径最短，但没有 `/doc.html` 和 Knife4j 的中文 UI。若 Knife4j 纯 UI 不能读取本项目生成的 OpenAPI 规范，则以本方案作为可回退的最低可用能力。

### 2.2 采用方案

第一版采用方案 A：

```text
org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3
com.github.xiaoymin:knife4j-openapi3-ui:4.5.0
```

选择原因：

- Springdoc 官方兼容矩阵要求 Spring Boot 4 使用 Springdoc 3.x。
- Springdoc 3.0.3 是本次设计时明确支持 Spring Boot 4 的稳定版本。
- Knife4j 官方说明纯 UI 版本不受 Spring Boot 版本限制。
- 不使用 Knife4j starter，避免它传递管理 Springdoc 后产生版本冲突。
- 将 OpenAPI 输出固定为 OpenAPI 3.0，降低 Knife4j UI 对 OpenAPI 3.1 解析差异的风险。

若 Knife4j `/doc.html` 在真实集成测试中无法读取规范，本任务不得通过替换或降级 Springdoc 来迁就 UI；保留 Springdoc 3.0.3 和 Swagger UI，将 Knife4j 兼容问题记录为后续 UI 任务。

### 2.3 需求与开发顺序

| 顺序 | 需求 | 可验证交付物 | 依赖 |
| --- | --- | --- | --- |
| 1 | 建立无冲突的 OpenAPI 依赖 | 项目编译，依赖树只有一套 Springdoc 3.x | Maven 基础 |
| 2 | 生成范围明确的 OpenAPI 规范 | `/v3/api-docs/stackora` 返回合法规范 | 需求 1 |
| 3 | 提供 Swagger UI 与 Knife4j UI | 两个开发文档入口均能读取同一份规范 | 需求 2 |
| 4 | 按环境控制文档暴露 | dev/test 可用，prod 不生成规范 | 需求 2～3 |
| 5 | 建立可重复验收 | 自动化测试和人工命令均通过 | 需求 1～4 |

---

## 3. 需求一：建立无冲突的 OpenAPI 依赖

### 3.1 需求行为

项目引入 OpenAPI 生成和 UI 依赖后必须能够编译、启动，不得同时出现 Springdoc 2.x 与 3.x，也不得引入 Springfox 或 Knife4j 后端 starter。

### 3.2 具体设计

在 `pom.xml` 中显式固定：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.0.3</version>
</dependency>

<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-openapi3-ui</artifactId>
    <version>4.5.0</version>
</dependency>
```

不得同时引入：

```text
knife4j-openapi3-jakarta-spring-boot-starter
knife4j-openapi3-spring-boot-starter
springfox-*
springdoc-openapi 2.x
```

### 3.3 设计思路与取舍

- Springdoc 负责从 Spring MVC 运行时模型生成 OpenAPI 规范，是核心能力。
- Swagger UI 由 Springdoc starter 提供，用于验证标准链路。
- Knife4j 只使用 WebJar 形式的纯 UI，避免第二套服务端解析器和自动配置。
- 版本显式固定，避免构建时间不同导致不可重复结果。

### 3.4 涉及文件与契约

```text
pom.xml
```

依赖契约：Springdoc 是唯一 OpenAPI 生成器；Knife4j 的故障不能阻塞 `/v3/api-docs/stackora` 和 Swagger UI。

### 3.5 正常流程

```text
Maven 解析依赖
  -> Springdoc 3.0.3 加入运行时
  -> Swagger UI WebJar 加入运行时
  -> Knife4j OpenAPI3 UI WebJar 加入运行时
  -> Spring Boot 应用正常启动
```

### 3.6 异常与兼容边界

- 出现多个 Springdoc 主版本：停止实现并清理依赖，不使用 exclusions 掩盖未知冲突。
- 出现 Springfox：视为依赖选型错误。
- Knife4j UI 不兼容：保留 Springdoc，不降级 Spring Boot 或 Springdoc。
- 新版本升级必须单独验证，不使用动态版本或 `LATEST`。

### 3.7 测试设计

执行依赖树检查：

```bash
./mvnw dependency:tree \
  -Dincludes=org.springdoc:*,com.github.xiaoymin:*,io.swagger.core.v3:*
```

检查结果必须满足：

- Springdoc 版本全部属于 3.0.3 依赖线。
- 不包含 `springdoc-openapi` 2.x。
- 不包含 `springfox-*`。
- 不包含 Knife4j Spring Boot starter。

### 3.8 完成标准

- `./mvnw -DskipTests compile` 成功。
- 依赖树满足唯一 Springdoc 版本约束。
- Maven 未报告重复或冲突版本。

---

## 4. 需求二：生成范围明确的 OpenAPI 规范

### 4.1 需求行为

系统在启用接口文档的环境中生成 Stackora OpenAPI 规范，只收录公开业务 Controller，不把 Actuator、静态资源或框架内部端点混入业务文档。

### 4.2 具体设计

新增 `OpenApiConfig`，提供两个 Bean：

```java
OpenAPI stackoraOpenAPI()
GroupedOpenApi stackoraApi()
```

OpenAPI 元数据：

```text
title       = Stackora API
description = Stackora 技术社区后端接口
version     = v1
```

分组规则：

```text
group          = stackora
packagesToScan = org.victor.stackora.controller
pathsToMatch   = /api/**
```

规范端点：

```text
GET /v3/api-docs/stackora
```

配置中固定：

```yaml
springdoc:
  api-docs:
    version: OPENAPI_3_0
```

### 4.3 设计思路与取舍

- 同时按包和路径过滤，防止未来新增内部 Controller 时被意外公开。
- 使用独立分组，为后续增加后台管理 API 分组保留边界，但当前只创建一个分组。
- OpenAPI 3.0 对 Knife4j 4.x 的兼容风险更低；本阶段不需要 OpenAPI 3.1 特性。
- 元数据集中配置，业务 Controller 只描述自己的业务语义。

### 4.4 涉及文件与契约

```text
src/main/java/org/victor/stackora/config/OpenApiConfig.java
src/main/resources/application.yml
```

### 4.5 正常流程

```text
应用启动
  -> Springdoc 扫描 controller 包
  -> 过滤 /api/**
  -> 生成 stackora 分组
  -> 返回 OpenAPI 3.0 JSON
```

### 4.6 异常、安全与边界

- 文档生成失败不能改变业务 Controller 的执行语义。
- `/actuator/**`、错误页、静态资源和测试 Controller 不得进入 `paths`。
- 文档描述和示例不得出现数据库连接、环境变量、真实账号、原始密码、密码哈希、Cookie 或 Session ID。
- 文档不是鉴权措施；生产环境控制由需求四负责。

### 4.7 测试设计

- `/v3/api-docs/stackora` 返回 HTTP 200。
- 响应字段 `openapi` 以 `3.0` 开头。
- `info.title` 等于 `Stackora API`，`info.version` 等于 `v1`。
- `paths` 不包含 `/actuator/health`。
- 后续注册接口完成后，`paths` 包含 `/api/auth/register`。

### 4.8 完成标准

- OpenAPI JSON 可以被标准 JSON 解析器读取。
- 扫描包、路径和元数据均符合约定。
- 未引入第二个默认或无命名业务分组。

---

## 5. 需求三：提供 Swagger UI 与 Knife4j UI

### 5.1 需求行为

开发者可以从两个 UI 查看同一份 Stackora OpenAPI 规范。Swagger UI 用于验证标准兼容性，Knife4j 用于日常中文界面调试。

### 5.2 具体设计

开发和测试环境开放：

```text
Swagger UI: /swagger-ui.html
Knife4j UI: /doc.html
OpenAPI:    /v3/api-docs/stackora
```

Swagger UI 默认选择 `stackora` 分组，并按方法名稳定排序。Knife4j 从 Springdoc 的 OpenAPI 和 Swagger config 端点读取分组，不额外生成规范。

### 5.3 设计思路与取舍

- 两个 UI 共用同一个事实来源，避免出现两份接口文档不一致。
- Swagger UI 是故障定位基线：若 Swagger UI 可用而 Knife4j 不可用，问题位于 Knife4j 展示层。
- 不使用 Knife4j 服务端增强注解和配置，避免将业务接口绑定到特定 UI。

### 5.4 涉及文件与契约

```text
pom.xml
src/main/resources/application-dev.yml
src/test/resources/application-test.yml
```

### 5.5 正常流程

```text
浏览器访问 UI
  -> UI 读取 Springdoc swagger-config
  -> 选择 stackora 分组
  -> 获取 /v3/api-docs/stackora
  -> 展示接口并发起开发环境请求
```

### 5.6 异常、安全与边界

- Swagger UI 正常但 Knife4j 空白：检查浏览器 Network、OpenAPI 版本和静态资源，不调整业务代码。
- 两个 UI 都无法加载：先直接检查 OpenAPI JSON，再检查 Springdoc 配置。
- `Try it out` 只用于本地开发和测试环境，不作为自动化验收替代品。
- UI 中不得预填真实密码、生产域名凭据或有效 Session。

### 5.7 测试设计

- `/swagger-ui.html` 返回重定向或 HTTP 200，最终页面资源加载成功。
- `/doc.html` 返回 HTTP 200，页面能够加载 `stackora` 分组。
- 两个 UI 展示的接口路径与 `/v3/api-docs/stackora` 一致。
- 浏览器控制台和 Network 不出现 404、500 或规范解析错误。

### 5.8 完成标准

- 开发环境中两个 UI 均可访问并读取规范。
- Knife4j 不产生第二套 OpenAPI JSON。
- 关闭 Knife4j UI 后，Springdoc 和 Swagger UI 仍可独立工作。

---

## 6. 需求四：按环境控制接口文档暴露

### 6.1 需求行为

接口文档默认关闭，只在明确启用的开发和测试环境生成。生产环境不返回 OpenAPI JSON，UI 即使存在静态入口也不能获得任何接口规范。

### 6.2 具体设计

`application.yml` 使用安全默认值：

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

`application-dev.yml` 和 `application-test.yml` 显式启用：

```yaml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
```

`application-prod.yml` 保持或重复声明关闭配置，防止生产环境因配置合并误开启。

### 6.3 设计思路与取舍

- 默认关闭符合安全失败原则；只有激活 `dev` 或 `test` 才开放。
- 生产环境关闭规范生成是第一道边界，未来部署 Nginx 后再显式拦截 `/doc.html`、`/swagger-ui/**` 和 `/v3/api-docs/**`。
- 当前没有完整认证体系，不通过临时口令把生产接口文档暴露到公网。

### 6.4 涉及文件与契约

```text
src/main/resources/application.yml
src/main/resources/application-dev.yml
src/main/resources/application-prod.yml
src/test/resources/application-test.yml
```

### 6.5 正常流程

```text
无 Profile 或 prod -> OpenAPI JSON 关闭
dev                 -> OpenAPI JSON 与两个 UI 可用
test                -> OpenAPI JSON 可用于自动化测试
```

### 6.6 异常、安全与边界

- 未激活 `dev` 时本地访问不到文档属于预期行为，不应删除安全默认值。
- Knife4j 纯 UI 是静态资源；生产环境可能仍能打开空页面，但必须无法读取 OpenAPI 规范。部署阶段再由反向代理彻底屏蔽静态入口。
- 不能仅依赖前端隐藏按钮保护生产接口。

### 6.7 测试设计

- `test` Profile 下 `/v3/api-docs/stackora` 返回 200。
- 默认配置下 OpenAPI 文档端点不可用。
- `prod` 配置文件中明确存在关闭项。
- 配置变更后检查最终打包产物包含正确的 Profile 文件。

### 6.8 完成标准

- 文档能力默认关闭。
- dev/test 必须显式启用。
- prod 不生成 OpenAPI JSON。

---

## 7. 需求五：建立可重复验收

### 7.1 需求行为

接口文档接入必须由自动化测试证明核心规范可以生成，并由一次真实启动验证两个 UI，而不是只凭依赖存在判断完成。

### 7.2 具体设计

新增集成测试：

```text
src/test/java/org/victor/stackora/config/ApiDocumentationIntegrationTest.java
```

测试使用：

```text
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
```

核心断言对象是 `/v3/api-docs/stackora`。UI 静态资源的浏览器渲染由人工启动验证，不用脆弱的 HTML 内容快照代替。

### 7.3 设计思路与取舍

- JSON 规范是机器可验证的核心交付物。
- UI 是否真正读取规范需要浏览器网络请求证据，单纯断言 `/doc.html` 返回 200 不足以证明可用。
- 本模块只验证基础设施；业务接口完成后在各自 Controller 测试中验证接口契约和响应。

### 7.4 涉及文件与契约

```text
src/test/java/org/victor/stackora/config/ApiDocumentationIntegrationTest.java
```

### 7.5 正常流程

```text
启动 test Profile 上下文
  -> MockMvc 请求分组规范
  -> 校验版本、元数据和扫描边界
  -> dev Profile 真实启动
  -> 浏览器验证 Swagger UI 与 Knife4j UI
```

### 7.6 异常与边界

- 测试不得依赖外网或远程文档服务。
- 测试不得要求真实业务账号或生产数据库。
- 如果完整 Spring 上下文仍需要 MySQL，使用现有 `.env.test` 和测试数据库约定，不在测试源码写入凭据。
- UI 人工验证失败时保留浏览器 Network 中的失败 URL 和状态码作为定位证据。

### 7.7 测试与验收命令

```bash
./mvnw dependency:tree \
  -Dincludes=org.springdoc:*,com.github.xiaoymin:*,io.swagger.core.v3:*

./mvnw -Dtest=ApiDocumentationIntegrationTest test

set -a
source .env.test
set +a
./mvnw clean test

SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

curl -i http://localhost:8080/v3/api-docs/stackora
curl -i http://localhost:8080/swagger-ui.html
curl -i http://localhost:8080/doc.html
```

预期结果：

- 依赖树不存在 Springdoc 主版本冲突。
- 指定测试和完整测试全部通过。
- OpenAPI JSON 返回 200 且为 OpenAPI 3.0。
- Swagger UI 与 Knife4j UI 均能加载 `stackora` 分组。

### 7.8 完成标准

- 自动化测试验证规范生成和扫描边界。
- 完整 Maven 测试通过。
- 两个 UI 有真实浏览器访问证据。
- 未在仓库中保存测试密码或生产配置。

---

## 8. 跨需求共同约束

### 8.1 OpenAPI 注解规范

后续业务模块按需使用：

```text
@Tag        -> Controller 业务分组
@Operation  -> 单个接口的行为和结果
@ApiResponse -> 关键 HTTP 状态及响应语义
@Schema     -> Request、Response 和字段约束
```

不得为了让页面看起来丰富而复制 Jakarta Validation 规则；可以由 Springdoc 自动推导的约束不重复维护。密码字段必须标记为 write-only 或 password 格式，且不提供真实示例。

### 8.2 文档与运行时行为边界

- OpenAPI 注解只描述契约，不能替代参数校验、鉴权和业务规则。
- HTTP 状态、字段名和枚举值必须以实际 Controller 行为为准。
- Entity 不作为公开响应模型出现在接口文档中。
- `ApiResponse<T>` 的泛型响应应展示具体 `data` 类型。

### 8.3 可观测性

- 文档加载失败可以记录端点和异常类型，但不得记录请求中的密码、Cookie 或 Authorization。
- 当前不为文档端点增加业务监控指标。

---

## 9. 完整完成清单

- [ ] `pom.xml` 只引入 Springdoc 3.0.3 和 Knife4j OpenAPI3 纯 UI 4.5.0。
- [ ] 依赖树中不存在 Springdoc 2.x、Springfox 或 Knife4j 后端 starter。
- [ ] `OpenApiConfig` 提供统一元数据和 `stackora` 分组。
- [ ] 只扫描 `org.victor.stackora.controller` 和 `/api/**`。
- [ ] 输出规范固定为 OpenAPI 3.0。
- [ ] `/v3/api-docs/stackora` 在 dev/test 环境可用。
- [ ] Swagger UI 在开发环境可加载规范。
- [ ] Knife4j `/doc.html` 在开发环境可加载同一规范。
- [ ] 默认环境和 prod 不生成 OpenAPI JSON。
- [ ] 文档中不存在真实密码、哈希、Cookie、Session ID 或生产凭据。
- [ ] `ApiDocumentationIntegrationTest` 通过。
- [ ] `./mvnw clean test` 通过。

---

## 10. 整体验收

本模块只有在以下条件同时满足时完成：

1. Spring Boot 4.0.7 可以正常启动，无 OpenAPI 自动配置错误。
2. Springdoc 是唯一的服务端规范生成器。
3. OpenAPI JSON 内容、分组和扫描边界正确。
4. Swagger UI 和 Knife4j UI 在开发环境均能读取规范。
5. 默认环境和生产环境不生成规范。
6. 自动化测试与完整项目测试通过。
7. 后续用户注册接口可以直接添加业务注解，不再修改全局文档依赖和扫描策略。

---

## 11. 风险与后续边界

### 11.1 当前风险

- Spring Boot 4 和 Springdoc 3 均处于较新的依赖线，升级时必须重新执行依赖树、启动和文档生成测试。
- Knife4j 官方 starter 文档主要面向 Spring Boot 3，因此本项目不采用其 starter；纯 UI 仍需以真实 `/doc.html` 加载结果作为兼容证据。
- 关闭 Springdoc 后，Knife4j 静态页面可能仍能打开但无法加载规范；生产部署阶段需要在 Nginx 再屏蔽 UI 路径。

### 11.2 后续边界

- 用户注册模块负责为 `POST /api/auth/register` 增加业务描述、请求响应模型和错误状态，不重复配置 Springdoc。
- 登录与权限模块完成后，再设计文档端点是否允许内部认证访问。
- 拆分管理端 API 后，再评估增加独立 OpenAPI 分组。
- 引入 Nginx 时屏蔽 `/doc.html`、`/swagger-ui/**`、`/v3/api-docs/**`。

---

## 12. Paicoding 对比

| 维度 | Stackora 设计 | Paicoding 实现 | 差异原因 | 当前是否借鉴 |
| --- | --- | --- | --- | --- |
| Spring Boot 基线 | Spring Boot 4.0.7 | Spring Boot 2.7.1 | 主版本不同，依赖兼容线不同 | 不照搬依赖 |
| 规范生成 | Springdoc 3.0.3、OpenAPI 3 | Knife4j OpenAPI2 starter、Swagger 注解 | Stackora 使用 Jakarta 与 Springdoc 3 | 使用现代规范 |
| UI | Swagger UI + Knife4j OpenAPI3 纯 UI | Knife4j `/doc.html` | Stackora 避免 starter 管理旧 Springdoc | 借鉴双入口思路 |
| 扫描范围 | 单一业务分组，包和路径双重过滤 | 配置多个管理端分组 | Stackora 尚未形成管理端 API | 以后借鉴分组 |
| 生产控制 | 默认关闭规范，prod 明确关闭 | `knife4j.production: true` | Stackora 未采用 Knife4j starter 增强 | 借鉴生产屏蔽目标 |
| 静态资源 | 先使用 WebJar 默认映射 | 手工注册 `doc.html` 和 `/webjars/**` | 仅在真实 404 时才增加定制 | 不提前照搬 |

Paicoding 证据路径：

```text
paicoding-reference/paicoding-main/pom.xml
paicoding-reference/paicoding-main/paicoding-web/src/main/resources/application.yml
paicoding-reference/paicoding-main/paicoding-web/src/main/resources-env/prod/application-config.yml
paicoding-reference/paicoding-main/paicoding-web/src/main/java/com/github/paicoding/forum/web/QuickForumApplication.java
```

Paicoding 的配置证明了 `/doc.html`、接口分组和生产屏蔽的实际价值，但其 Spring Boot 2.7.1、OpenAPI2 starter 和 Swagger 2 注解不适合直接复制到 Stackora。

---

## 13. 参考资料

- [Springdoc OpenAPI 3.0.3 官方文档](https://springdoc.org/v4/index.html)
- [Springdoc OpenAPI 3.0.3 发布说明](https://github.com/springdoc/springdoc-openapi/releases/tag/v3.0.3)
- [Knife4j 快速开始](https://doc.xiaominfo.com/docs/quick-start)
- [Knife4j 版本兼容参考](https://doc.xiaominfo.com/docs/quick-start/start-knife4j-version)
- [Knife4j 4.x 项目结构与纯 UI 说明](https://doc.xiaominfo.com/docs/upgrading/upgrading-to-v4)
