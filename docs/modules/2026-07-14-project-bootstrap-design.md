# Stackora V0 工程骨架与运行基线需求与技术设计

## 文档说明

本文档同时描述 Stackora V0 工程骨架的正式需求、技术设计、开发顺序和整体验收要求。

阅读顺序如下：

1. 第一部分说明为什么需要工程骨架、最终需要具备哪些能力，以及明确不做什么。
2. 第二部分按照实际开发依赖顺序拆分 Maven 工程、配置、持久化接入、健康检查和验证工作。
3. 第三部分提供完整完成清单、交付文件、运行链路、验证命令、风险和后续模块前置条件。

本文档只覆盖工程运行基线，不包含用户、文章等业务能力，也不把后续引入的缓存、消息队列和搜索组件提前纳入当前交付。

# 第一部分：需求说明

## 1. 背景与用户价值

Stackora 是一个以后端为主的技术社区项目，后续将持续增加用户、文章、评论、互动、通知、搜索和后台治理能力。业务模块开始前，需要先建立统一、可重复、可维护的工程运行基线。

工程骨架的直接用户是项目开发者、测试人员和后续部署环境。它应解决以下问题：

- 新开发者能够根据正式文档配置并启动项目。
- 项目能够通过 Maven Wrapper 在不同开发环境执行一致的构建命令。
- 数据库连接信息通过环境配置注入，仓库不保存真实凭据。
- 应用存活状态能够通过标准 HTTP 端点探测。
- 自动化测试和开发数据库连通性验证具有明确边界。
- 后续模块复用同一套包结构、配置规则、数据访问基础和验证方式。

V0 的价值不是提供业务功能，而是降低后续每个模块的启动、配置、排障和交付成本。

## 2. 模块最终目标

本模块最终需要建立以下工程能力：

1. 创建基于 Maven Wrapper 的 Spring Boot Web 工程。
2. 统一 Java 基础包、应用名称和构建坐标。
3. 提供 MySQL 驱动和 MyBatis-Plus 基础接入能力。
4. 通过公共配置与环境专用配置区分运行环境。
5. 通过环境变量提供数据库地址、用户名、密码和服务端口。
6. 使用 `.gitignore` 防止本地凭据、构建产物、日志和 IDE 文件进入仓库。
7. 使用 Spring Boot Actuator 提供标准健康检查和应用信息端点。
8. 提供开发者可执行的启动、构建、测试和健康探测命令。
9. 建立最小自动化测试入口，保证工程上下文和基础端点可以被验证。
10. 为后续统一响应、用户持久化和业务 API 模块提供稳定工程基线。

## 3. 功能范围

### 3.1 本次实现

本模块包含：

- Spring Boot Web MVC 应用入口。
- Maven Wrapper 与 Maven 构建配置。
- JDK 编译版本配置。
- MySQL JDBC 驱动。
- MyBatis-Plus Spring Boot Starter。
- Lombok 编译期支持。
- `application.yml` 公共配置。
- `application-dev.yml` 与 `application-prod.yml` 环境专用配置。
- `.env.example` 环境变量契约。
- `.gitignore` 敏感配置与构建产物保护。
- Actuator `health` 和 `info` 端点。
- README 中的本地配置、启动、验证和项目结构说明。
- 工程上下文与管理端点的最小自动化测试设计。

### 3.2 本次不实现

本模块不包含：

- 用户、文章、评论、点赞、收藏、关注和通知等业务接口。
- 业务数据表、业务 SQL、Entity、Mapper 或 Service。
- 自定义 `/api/health` Controller。
- 自定义数据库健康检查业务链路。
- 登录、Session、权限拦截器、JWT、Sa-Token 或 Spring Security。
- Redis、Redisson、RabbitMQ、Elasticsearch。
- Docker、Nginx、Kubernetes 或生产环境部署编排。
- Swagger / Knife4j。
- 复杂前端页面。
- Paicoding 源码复制。

后续模块可以在该工程基线上增加能力，但不能把后续能力反向写成本模块已经交付的内容。

## 4. 核心工程规则

### 4.1 构建规则

1. 项目使用 Maven Wrapper，开发者不需要依赖本机预装的特定 Maven 版本。
2. JDK 编译版本在 `pom.xml` 中明确配置。
3. `./mvnw clean test` 是提交前的最小构建验证命令。
4. 新增生产依赖必须对应当前真实需求，不能为了展示技术栈提前引入。
5. 构建结果和自动化测试不应依赖 IDE 的隐式配置。

### 4.2 配置规则

1. 公共非敏感配置写入 `application.yml`。
2. 开发环境差异写入 `application-dev.yml`。
3. 生产环境差异写入 `application-prod.yml`。
4. 数据库 URL、用户名、密码和可变端口通过环境变量注入。
5. `.env.example` 只描述变量名称和安全示例，不保存可用生产凭据。
6. Spring Boot 不会自动读取项目根目录 `.env`，README 必须说明如何通过 IDE 或 Shell 加载变量。
7. 环境专用配置不能覆盖或弱化仓库级安全规则。

### 4.3 凭据与日志规则

1. `.env` 和其他本地环境文件不得进入 Git。
2. Java、YAML、测试代码和日志不得保存真实数据库密码。
3. 启动失败日志可以说明缺少的配置项，但不得打印密码。
4. 开发环境可以输出 Mapper 调试日志，生产环境必须降低日志级别。
5. 日志不得输出 Token、Cookie、Session ID 或包含真实密码的完整连接信息。

### 4.4 健康检查规则

1. 基础健康检查使用 Spring Boot Actuator，不重复实现自定义 Controller。
2. 对外只暴露当前需要的 `health` 和 `info`。
3. 健康详情使用受控显示策略，不能默认向匿名调用方暴露全部组件细节。
4. Actuator 响应保持框架标准结构，不使用业务统一响应对象包装。
5. 数据库不可用时不能返回虚假的数据库健康状态。

### 4.5 测试边界

1. 自动化构建与开发数据库连通性是两个独立验收项。
2. 基础单元或 Web 测试不得默认要求开发者本机存在固定 MySQL schema。
3. 真实数据库验证通过明确的开发 profile 或后续集成测试执行。
4. 未执行测试时不能仅凭应用可以启动就认定工程基线全部通过。

## 5. 正常运行流程

### 5.1 开发者首次启动

```text
Developer
  -> 拉取正式仓库
  -> 阅读 README 和 .env.example
  -> 在本地提供环境变量
  -> 选择 dev profile
  -> 执行 ./mvnw spring-boot:run
  -> Spring Boot 加载公共配置和开发配置
  -> 创建 Web、Actuator、数据源和 MyBatis-Plus 组件
  -> 应用监听 HTTP 端口
  -> Developer 访问 /actuator/health
```

启动成功必须以应用日志、HTTP 端点和依赖健康状态作为证据，不能只以 IDE 进程仍在运行为依据。

### 5.2 自动化构建

```text
Developer / CI
  -> 执行 ./mvnw clean test
  -> Maven 清理旧构建产物
  -> 编译主代码
  -> 编译测试代码
  -> 执行自动化测试
  -> 输出明确的成功或失败结果
```

构建失败时应根据 Maven 输出定位依赖、编译或测试问题，不允许跳过失败测试后宣称工程稳定。

### 5.3 健康检查

```text
Client / Deployment Probe
  -> GET /actuator/health
  -> Actuator 聚合应用和已注册依赖的健康状态
  -> 返回 Actuator 标准响应
```

`/actuator/info` 用于提供可公开的应用基础信息。当前阶段不通过该端点暴露环境变量、数据库配置或服务器内部路径。

### 5.4 环境切换

```text
Runtime
  -> 设置 SPRING_PROFILES_ACTIVE
  -> 加载 application.yml
  -> 叠加 application-{profile}.yml
  -> 使用环境变量覆盖可变配置
  -> 启动对应环境实例
```

开发与生产 profile 只能承载环境差异，不能产生不同的业务规则。

## 6. 异常与故障流程

### 6.1 缺少配置

触发场景：

- 未设置数据库 URL。
- 未设置数据库用户名或密码。
- profile 名称错误。
- 环境变量名称与配置占位符不一致。

预期行为：

- 应用启动或数据源初始化明确失败。
- 日志指出配置或连接问题。
- 日志不得输出真实密码。

### 6.2 数据库不可用

触发场景：

- MySQL 未启动。
- schema 不存在。
- 账号无连接权限。
- 网络不可达。
- JDBC 参数不兼容。

预期行为：

- 数据库相关健康状态不能被报告为可用。
- 应用日志保留可排障的异常信息。
- 管理端点不向未授权调用方暴露敏感连接细节。

### 6.3 自动化测试误连开发库

如果测试默认读取开发环境数据库变量，测试可能修改长期开发数据，或者在其他机器上不可重复。

预期行为：

- 工程基础测试与真实数据库验证分离。
- 需要数据库的测试使用独立配置和独立 schema。
- 测试不得删除或重建长期开发 schema。

### 6.4 凭据误提交

如果 `.env`、IDE 运行配置、YAML、测试源码或日志包含真实凭据，应立即视为安全问题处理。

预期行为：

- `.gitignore` 阻止常见本地文件进入版本控制。
- 提交前检查 Git diff。
- 已泄露凭据需要轮换，删除 Git 文件并不能使旧凭据重新安全。

### 6.5 管理端点暴露过多

如果 Actuator 暴露全部端点或完整健康详情，可能泄露依赖、环境和运行状态。

预期行为：

- 当前只暴露 `health` 和 `info`。
- 生产部署前重新评审网络边界和认证策略。
- 不在 V0 为展示效果开放不必要端点。

## 7. 权限、数据与安全规则

### 7.1 权限规则

- 本模块没有用户体系，不实现业务登录和角色权限。
- Actuator 端点属于运维能力，不等同于业务公开 API。
- 当前健康详情使用 `when-authorized`，生产环境仍需结合实际认证和网络策略复审。

### 7.2 数据规则

- 本模块不创建或修改业务表。
- 本模块不写入用户、文章等业务数据。
- MySQL 只作为后续持久化模块的数据源能力准备。
- Flyway 迁移、业务表和数据库约束由对应业务模块设计。

### 7.3 安全规则

- Git 跟踪文件不保存真实密码。
- 示例连接地址不得指向生产或共享敏感环境。
- 日志不得输出敏感配置。
- Actuator 不返回业务数据。
- README 中的命令使用占位值，不提供真实凭据。

## 8. 需求级验收标准

满足以下条件后，工程骨架在需求层面成立：

1. 开发者能够仅根据仓库文档理解构建、配置、启动和健康检查流程。
2. Maven Wrapper、Spring Boot Web、MySQL 驱动、MyBatis-Plus 和 Actuator 的用途清晰。
3. 公共配置、环境配置和敏感变量的职责不重叠。
4. 真实凭据不会进入 Git 跟踪文件。
5. `/actuator/health` 和 `/actuator/info` 的用途与暴露边界明确。
6. 自动化测试与开发数据库连通性验证分离。
7. 本模块没有夹带业务表、业务接口或高级基础设施。
8. 后续模块能够在不重建工程基线的情况下继续开发。

# 第二部分：技术设计与开发步骤

## 步骤一：Maven 工程与应用入口

### 9.1 目标

建立可编译、可运行、包结构明确的 Spring Boot Web 应用。

### 9.2 涉及文件

```text
pom.xml
mvnw
mvnw.cmd
.mvn/wrapper/maven-wrapper.properties
src/main/java/org/victor/stackora/StackoraBackendApplication.java
```

### 9.3 Maven 坐标与编译基线

当前仓库实际工程坐标：

```text
groupId: org.victor
artifactId: stackora
version: 0.0.1-SNAPSHOT
java.version: 17
```

项目标识必须以正式仓库中的 Maven 配置和 Java 包结构保持一致。后续如需调整坐标或包名，应作为独立重构执行，不能在业务模块中零散修改。

### 9.4 基础依赖职责

| 依赖 | 当前用途 |
| --- | --- |
| Spring Boot Web MVC | 提供 HTTP、Controller 和 JSON Web 基础能力 |
| MySQL Connector/J | 提供 MySQL JDBC 驱动 |
| MyBatis-Plus Starter | 提供 Mapper 与基础持久化集成 |
| Lombok | 减少明确、稳定的数据类样板代码 |
| Spring Boot Actuator | 提供健康检查和应用信息 |
| Spring Boot Test | 提供 JUnit 与 Spring 测试基础 |

Flyway、分页解析等后来增加的依赖属于后续持久化能力扩展，不改变本步骤的应用入口职责。

### 9.5 应用入口

`StackoraBackendApplication` 只负责启动 Spring Boot：

```java
SpringApplication.run(StackoraBackendApplication.class, args);
```

启动类不得承载数据库初始化、业务数据创建或运行时业务逻辑。

### 9.6 异常与边界

- JDK 与编译版本不兼容时，Maven 编译必须失败并给出真实错误。
- 依赖版本冲突不能通过删除测试或绕过编译解决。
- 启动类中的展示性日志不能替代结构化应用日志。
- 本步骤不创建 Controller、Service、Mapper 或业务 Entity。

### 9.7 本步骤测试

```bash
./mvnw -version
./mvnw -DskipTests compile
```

预期结果：

- Maven Wrapper 可以执行。
- Java 版本满足项目编译配置。
- 主代码编译成功。

### 9.8 本步骤完成标准

- Maven Wrapper 文件齐全。
- `pom.xml` 包含明确构建坐标和 Java 版本。
- Spring Boot 应用入口位于基础包根目录。
- 主代码可以通过 Maven 编译。
- 启动类不包含业务初始化逻辑。

## 步骤二：配置分层与凭据保护

### 10.1 目标

建立公共配置、环境差异和敏感变量之间的清晰边界。

### 10.2 涉及文件

```text
src/main/resources/application.yml
src/main/resources/application-dev.yml
src/main/resources/application-prod.yml
.env.example
.gitignore
README.md
```

### 10.3 公共配置

`application.yml` 只保存跨环境通用且非敏感的设置，例如：

- `spring.application.name`。
- Actuator 端点暴露范围。
- 健康详情显示策略。

数据库密码不得直接写入公共配置。

### 10.4 环境配置

`application-dev.yml`：

- 允许启用 MyBatis Mapper 调试日志。
- 只用于本地开发排障。
- 不保存本地真实密码。

`application-prod.yml`：

- 降低 Mapper 日志级别。
- 不启用开发专用调试输出。
- 不把生产凭据提交到仓库。

### 10.5 环境变量契约

`.env.example` 至少描述：

```text
SPRING_PROFILES_ACTIVE
SERVER_PORT
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

需要数据库测试变量时，应使用独立 `TEST_DB_*` 契约，并确保测试目标不是长期开发 schema。

示例密码只使用不可直接部署的占位文本。

### 10.6 Git 忽略规则

`.gitignore` 至少覆盖：

- Maven `target/`。
- `.env` 和本地环境变体。
- IDE 工程文件。
- 日志目录和 `*.log`。
- macOS 与临时文件。

必须显式允许 `.env.example` 进入仓库。

### 10.7 异常与边界

- `.env.example` 不是 Spring Boot 自动加载机制，只是配置契约。
- README 必须说明 Shell 或 IDE 如何实际注入变量。
- 不能通过提交个人 IDE 配置解决团队环境问题。
- 生产配置不能依赖开发者本机文件。

### 10.8 本步骤测试

```bash
git check-ignore .env
git check-ignore -v .env.example
git grep -nE 'password[[:space:]]*[:=][[:space:]]*[^${<]' -- \
  '*.yml' '*.yaml' '*.properties' ':!*.example'
```

预期结果：

- `.env` 被忽略。
- `.env.example` 未被忽略并可以被 Git 跟踪。
- 跟踪配置中没有硬编码真实密码。

### 10.9 本步骤完成标准

- 公共、开发和生产配置职责清晰。
- 可变配置通过环境变量提供。
- 本地环境文件被 Git 忽略。
- 示例文件不包含真实凭据。
- README 说明 `.env` 不会被 Spring Boot 自动读取。

## 步骤三：MySQL 与 MyBatis-Plus 基础接入

### 11.1 目标

为后续持久化模块提供数据源驱动、Mapper 扫描和 MyBatis-Plus 运行基础，但不提前创建业务数据模型。

### 11.2 涉及文件

```text
pom.xml
src/main/java/org/victor/stackora/config/MybatisPlusConfig.java
src/main/resources/application-dev.yml
src/main/resources/application-prod.yml
```

### 11.3 数据源职责

MySQL Connector/J 负责 JDBC 驱动。连接池和数据源由 Spring Boot 根据环境变量自动配置。

连接参数需要明确：

- schema 名称。
- 字符编码。
- 服务端时区。
- 用户名。
- 密码。

JDBC URL 不应在日志或客户端响应中完整暴露。

### 11.4 Mapper 扫描

Mapper 统一位于：

```text
org.victor.stackora.mapper
```

`@MapperScan` 负责注册 Mapper 接口。V0 本身不要求存在业务 Mapper；用户等业务 Mapper 由后续模块增加。

### 11.5 MyBatis-Plus 配置边界

本步骤只建立 MyBatis-Plus 基础能力。

以下内容不属于 V0 验收：

- 业务 Entity 映射。
- 分页业务行为。
- 字段自动填充。
- 逻辑删除。
- 状态条件更新。
- Mapper XML 业务 SQL。

这些能力必须在对应业务模块中设计和验证。

### 11.6 异常与边界

- 数据源配置错误时应用可能在上下文初始化阶段失败。
- Mapper 扫描范围过宽可能把非 Mapper 接口错误注册为 Bean。
- Web Slice 测试不应因全局 Mapper 扫描而被迫加载真实 `SqlSessionFactory`。
- 不得为了让无数据库测试通过而连接长期开发库。

### 11.7 本步骤测试

基础编译验证：

```bash
./mvnw -DskipTests compile
```

开发库连通性验证：

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

预期结果：

- 无业务 Mapper 时工程仍可编译。
- 提供正确开发环境变量时可以创建数据源。
- 数据库不可用时产生真实失败证据。

### 11.8 本步骤完成标准

- MySQL 驱动和 MyBatis-Plus Starter 已声明。
- Mapper 包路径唯一且明确。
- 开发和生产 Mapper 日志级别分离。
- V0 不包含业务 Entity、Mapper 或 SQL。
- 数据库验证不污染长期数据。

## 步骤四：Actuator 健康检查

### 12.1 目标

使用框架标准能力提供应用存活和依赖健康状态，不重复实现自定义健康接口。

### 12.2 涉及文件

```text
pom.xml
src/main/resources/application.yml
README.md
```

### 12.3 端点设计

| 端点 | 方法 | 用途 | 响应格式 |
| --- | --- | --- | --- |
| `/actuator/health` | `GET` | 查看应用和已注册依赖的健康状态 | Actuator 标准响应 |
| `/actuator/info` | `GET` | 查看允许公开的应用基础信息 | Actuator 标准响应 |

如果后续配置 context path，真实路径需要包含该前缀。

### 12.4 暴露规则

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized
```

当前不开放 `env`、`beans`、`configprops` 等可能暴露内部配置的端点。

### 12.5 调用链

```text
HTTP Client
  -> Actuator Endpoint
  -> Health Contributors
  -> Aggregate Health
  -> Actuator Standard Response
```

Actuator 不经过业务 Controller、`ApiResponse` 或业务异常处理器。

### 12.6 异常与边界

- HTTP `200` 只说明当前端点返回成功，仍需检查响应中的健康状态。
- 数据库健康贡献者是否存在取决于数据源是否被创建。
- 开发阶段的端点暴露策略不能直接等同于生产安全方案。
- `/actuator/info` 不得配置敏感属性。

### 12.7 本步骤测试

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/actuator/info
```

自动化测试至少断言：

- `health` 根字段存在 `status`。
- 响应不包含业务响应字段 `code`。
- 未暴露的管理端点不可访问。

### 12.8 本步骤完成标准

- Actuator 依赖已声明。
- 只暴露 `health` 和 `info`。
- 健康详情使用受控策略。
- README 给出探测命令。
- Actuator 响应保持标准结构。

## 步骤五：开发者文档与最小测试入口

### 13.1 目标

使开发者能够在没有口头指导的情况下完成本地配置、构建、启动和基础验证。

### 13.2 涉及文件

```text
README.md
.env.example
src/test/java/org/victor/stackora/StackoraBackendApplicationTests.java
```

### 13.3 README 内容

README 至少包含：

1. 项目定位与当前阶段。
2. 当前技术栈。
3. 环境要求。
4. 环境变量配置方式。
5. Maven Wrapper 启动命令。
6. Actuator 验证命令。
7. 构建与测试命令。
8. 真实项目目录结构。
9. 配置安全规则。
10. 后续演进路线。

README 中的“当前能力”必须随项目演进更新，不能长期保留与代码不一致的 V0 描述。

### 13.4 最小自动化测试

工程骨架测试至少覆盖：

- 应用上下文在隔离测试配置下能够加载。
- Actuator `health` 端点可访问。
- Actuator 响应保持标准结构。
- 测试不要求固定本地 MySQL。

如果全局 Mapper 扫描影响 Web Slice 测试，应通过合理的测试切片或配置边界解决，不能让测试默默连接开发库。

### 13.5 异常与边界

- README 命令必须能从正式仓库根目录执行。
- 文档中的文件扩展名必须与真实文件一致。
- 自动化测试没有行为断言时，只能证明上下文或编译，不足以证明业务正确。
- 本步骤不要求创建业务 Controller 作为健康检查替代品。

### 13.6 本步骤测试

```bash
./mvnw clean test
```

预期结果：

- Maven 返回退出码 `0`。
- 测试报告中没有 Failure 或 Error。
- 测试不会创建、修改或删除长期开发数据库。

### 13.7 本步骤完成标准

- README 与真实目录、配置文件和命令一致。
- `.env.example` 能说明必要变量。
- 至少存在一个可重复执行的工程基础自动化测试。
- `./mvnw clean test` 在隔离环境下通过。

## 步骤六：提交前工程基线检查

### 14.1 目标

在进入业务模块前，集中验证构建、配置安全、管理端点和仓库边界。

### 14.2 检查范围

```text
pom.xml
README.md
.gitignore
.env.example
src/main/java/
src/main/resources/
src/test/java/
```

### 14.3 检查顺序

1. 检查 Git 状态和待提交文件范围。
2. 检查 `.env`、日志和构建产物未被跟踪。
3. 检查 README 路径与真实文件一致。
4. 执行干净构建和测试。
5. 使用开发 profile 启动应用。
6. 请求 Actuator 端点。
7. 验证错误数据库配置不会被报告为健康。
8. 停止应用并检查日志中没有敏感信息。

### 14.4 验证命令

```bash
git status --short
git diff --check
./mvnw clean test
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/actuator/info
```

### 14.5 本步骤完成标准

- Git 变更只包含项目所需文件。
- 没有真实凭据和构建产物。
- 干净构建与测试通过。
- 开发环境可以启动。
- Actuator 端点符合设计。
- 数据库异常具有真实、可定位且不泄密的表现。

# 第三部分：整体总结与验收

## 15. 完整完成清单

### 15.1 Maven 与应用入口

- [x] Maven Wrapper 文件存在。
- [x] `pom.xml` 声明 Java 编译版本。
- [x] Spring Boot Web 应用入口存在。
- [x] Java 基础包与现有源码一致。
- [ ] 干净环境下主代码编译成功。

### 15.2 配置与凭据

- [x] 公共配置使用 `application.yml`。
- [x] 开发和生产环境配置分离。
- [x] `.env.example` 描述环境变量。
- [x] `.gitignore` 忽略本地 `.env`。
- [x] README 说明 `.env` 不会被 Spring Boot 自动读取。
- [ ] Git 跟踪文件经检查不包含真实凭据。

### 15.3 MySQL 与 MyBatis-Plus

- [x] MySQL JDBC 驱动已声明。
- [x] MyBatis-Plus Starter 已声明。
- [x] Mapper 扫描包路径明确。
- [x] 开发和生产 Mapper 日志级别分离。
- [ ] 开发数据库连通性经过实际验证。
- [ ] 自动化测试不会误连长期开发数据库。

### 15.4 Actuator

- [x] Actuator 依赖已声明。
- [x] 配置只暴露 `health` 和 `info`。
- [x] 健康详情使用 `when-authorized`。
- [x] README 提供管理端点访问命令。
- [ ] 自动化测试验证 Actuator 标准响应。
- [ ] 未开放端点的访问边界经过验证。

### 15.5 测试与文档

- [x] README 包含配置、启动、构建和健康检查说明。
- [x] README 说明当前能力与后续路线。
- [ ] 工程上下文测试可重复执行。
- [ ] `./mvnw clean test` 返回成功。
- [ ] 测试报告没有 Failure 或 Error。
- [ ] README 中的项目结构与当前仓库再次核对一致。

### 15.6 安全与仓库边界

- [x] 本模块未创建业务接口。
- [x] 本模块未提前引入 Redis、MQ 或 Elasticsearch。
- [x] Actuator 不使用业务响应包装。
- [ ] 日志中没有数据库密码或完整敏感连接信息。
- [ ] 提交范围不包含 IDE、本地环境、日志或构建产物。

## 16. 交付文件清单

```text
pom.xml
mvnw
mvnw.cmd
.mvn/wrapper/maven-wrapper.properties
.gitignore
.env.example
README.md
src/main/java/org/victor/stackora/StackoraBackendApplication.java
src/main/java/org/victor/stackora/config/MybatisPlusConfig.java
src/main/resources/application.yml
src/main/resources/application-dev.yml
src/main/resources/application-prod.yml
src/test/java/org/victor/stackora/StackoraBackendApplicationTests.java
docs/modules/2026-07-14-project-bootstrap-design.md
```

测试文件只有在实际创建后才算完成；交付清单表示该模块的目标文件集合，不表示所有文件已经通过验收。

## 17. 整体运行链路

### 17.1 应用启动链路

```text
Environment Variables
  -> application.yml
  -> application-{profile}.yml
  -> SpringApplication
  -> Web MVC / Actuator / DataSource / MyBatis-Plus
  -> HTTP Server
```

### 17.2 健康检查链路

```text
Client
  -> GET /actuator/health
  -> Actuator Endpoint
  -> Health Contributors
  -> Standard Health Response
```

### 17.3 构建验证链路

```text
Maven Wrapper
  -> clean
  -> compile
  -> testCompile
  -> test
  -> Surefire Report
```

## 18. 测试命令与预期结果

### 18.1 编译

```bash
./mvnw -DskipTests compile
```

预期：Maven 返回退出码 `0`，没有编译错误。

### 18.2 自动化测试

```bash
./mvnw clean test
```

预期：Maven 返回退出码 `0`，测试报告没有 Failure 或 Error。

### 18.3 开发环境启动

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

预期：应用成功监听配置端口；如果数据库配置错误，应产生真实、可定位的失败。

### 18.4 管理端点

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/actuator/info
```

预期：端点返回 Actuator 标准结构，不包含业务响应包装。

## 19. 模块整体验收标准

工程骨架只有同时满足以下条件才算完成：

1. Maven Wrapper 可以执行，主代码和测试代码能够编译。
2. `./mvnw clean test` 实际通过。
3. 应用可以在明确的开发 profile 和环境变量下启动。
4. 数据库配置不依赖已提交的真实密码。
5. `.env`、日志、IDE 文件和构建产物没有进入正式提交。
6. `/actuator/health` 与 `/actuator/info` 可以访问并保持标准响应。
7. 健康详情和管理端点暴露范围符合当前安全规则。
8. 测试不默认依赖或修改长期开发数据库。
9. README 与真实文件、配置和命令一致。
10. 本模块没有夹带业务功能或当前不需要的基础设施。

## 20. 已知风险与技术债

### 20.1 工程标识一致性

根级项目说明与当前 Maven 坐标、Java 包名可能存在历史差异。正式代码以当前仓库实际结构运行，但后续应通过独立决策统一权威项目标识，避免文档、包名和生成器配置继续分叉。

### 20.2 测试隔离

当前项目已经增加 Mapper 和数据库迁移后，Web Slice 或上下文测试可能受数据访问自动配置影响。需要在测试模块中明确切片边界，不能通过连接开发库掩盖测试配置问题。

### 20.3 环境可重复性

开发者自行配置本地 MySQL 可以降低初期成本，但不能提供完全一致的数据库环境。基础功能稳定后应引入独立测试 schema或 Testcontainers。

### 20.4 Actuator 生产安全

开发阶段的 `health` 和 `info` 暴露策略不是最终生产方案。部署阶段需要结合反向代理、网络边界和认证策略重新评审。

### 20.5 README 演进

README 当前仍以 V0 阶段描述为主。随着用户持久化等模块加入，需要同步更新当前能力和真实目录，避免入口文档过时。

## 21. 后续模块边界

工程骨架完成后，后续按实际依赖顺序增加：

1. 统一业务响应和异常处理。
2. MyBatis-Plus 分页及持久化公共配置。
3. 用户持久化基础。
4. Swagger / Knife4j 接口文档。
5. 用户注册、登录和 Session 认证。
6. 文章、评论和互动能力。
7. Redis、Redisson、RabbitMQ、Elasticsearch。
8. 日志监控和部署工程化。

每个后续模块必须单独设计，不能因为工程骨架已经存在就跳过业务、数据、事务、安全和测试评审。

## 22. 进入统一响应模块的前置条件

- Maven 工程可以编译。
- Spring Boot 应用入口可以运行。
- Web MVC 基础依赖可用。
- 公共配置和环境配置边界明确。
- Git 不跟踪真实数据库凭据。
- Actuator 与业务响应的职责边界已经明确。

## 23. Paicoding 后续对比点

只有在能够读取实际参考源码时，才从以下维度进行证据化比较：

- 工程目录和模块组织。
- 配置文件拆分。
- Maven 依赖管理。
- MyBatis-Plus 初始化方式。
- Actuator 或自定义健康检查。
- 本地启动与测试文档。

对比结果必须说明真实代码路径、解决的问题、复杂度和当前是否值得借鉴，不能仅因参考项目更成熟就直接照搬。
