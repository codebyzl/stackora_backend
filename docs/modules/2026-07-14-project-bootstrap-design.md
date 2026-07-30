# Stackora V0 工程骨架与运行基线需求与技术设计

## 0. 文档使用方式

本文档按实际建设顺序说明工程骨架。每个需求都在同一章节内给出需求行为、具体设计、设计原因、涉及文件、运行流程、异常边界、测试和完成标准。

阅读者不需要先读完所有配置章节，再返回寻找对应的启动或测试规则。

## 1. 模块概览

### 1.1 用户价值

工程骨架服务于开发者、测试人员和后续部署环境，使 Stackora 在进入用户、文章等业务开发前具备以下基础：

- 可以使用统一命令构建和启动。
- 可以安全地提供环境配置，不向仓库提交真实凭据。
- 可以连接 MySQL 并为后续 Mapper 提供基础能力。
- 可以通过标准端点判断应用健康状态。
- 可以通过自动化测试发现工程回归。
- 新业务模块不需要重复解决启动、配置和健康检查问题。

### 1.2 最终能力

本模块最终交付：

1. Maven Wrapper 和 Spring Boot Web 应用入口。
2. 明确的 Java 版本、Maven 坐标和基础包。
3. 公共、开发和生产配置分层。
4. MySQL 与 MyBatis-Plus 基础接入。
5. Actuator `health`、`info` 端点。
6. 本地启动、测试和安全配置说明。
7. 不依赖长期开发数据库的最小自动化测试入口。

### 1.3 本次范围

- `pom.xml` 和 Maven Wrapper。
- `StackoraBackendApplication`。
- `application.yml`、`application-dev.yml`、`application-prod.yml`。
- `.env.example` 和 `.gitignore`。
- MySQL 驱动、MyBatis-Plus、Lombok、Actuator 和测试依赖。
- Mapper 扫描基础配置。
- README。
- 工程上下文和 Actuator 行为测试设计。

### 1.4 明确不做

- 用户、文章、评论和互动业务。
- 业务表、Entity、Mapper、Service 和业务 SQL。
- 自定义健康检查 Controller。
- 登录认证与权限。
- Swagger / Knife4j。
- Redis、Redisson、RabbitMQ、Elasticsearch。
- Docker、Nginx 和生产编排。
- Paicoding 源码复制。

### 1.5 前置依赖

- 可执行项目配置所声明 Java 版本的 JDK。
- Git。
- 使用真实数据库验证时需要可访问的 MySQL。
- JDK/MySQL 本机安装和版本调整由开发者负责，不作为日常开发任务。

## 2. 需求与开发顺序

| 顺序 | 需求 | 可验证交付物 | 依赖 |
| --- | --- | --- | --- |
| 1 | 统一构建并启动应用 | Maven Wrapper、`pom.xml`、启动类 | JDK |
| 2 | 安全提供环境配置 | YAML、`.env.example`、`.gitignore` | 需求 1 |
| 3 | 建立数据库访问基础 | MySQL 驱动、MyBatis-Plus、Mapper 扫描 | 需求 1、2 |
| 4 | 提供标准健康探测 | Actuator 配置和端点 | 需求 1、2 |
| 5 | 提供可重复验证和开发说明 | 测试、README、验收命令 | 需求 1～4 |

## 3. 需求一：使用统一命令构建并启动应用

### 3.1 需求行为

开发者从正式仓库拉取代码后，应能使用 Maven Wrapper 编译、测试和启动项目，不依赖 IDE 隐式配置，也不要求本机预装特定 Maven 版本。

成功结果：

- Maven 能识别项目坐标和 Java 编译版本。
- Spring Boot 应用入口能够启动 Web 应用。
- 构建失败时保留真实编译或依赖错误。

### 3.2 具体设计

当前工程基线：

```text
groupId: org.victor
artifactId: stackora
version: 0.0.1-SNAPSHOT
java.version: 17
Spring Boot: 4.0.7
base package: org.victor.stackora
```

`pom.xml` 使用 Spring Boot Parent 管理兼容依赖版本。Maven Wrapper 文件进入仓库。

应用入口：

```java
@SpringBootApplication
public class StackoraBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(
                StackoraBackendApplication.class,
                args
        );
    }
}
```

启动类只负责启动应用，不承担数据库初始化和业务逻辑。

### 3.3 设计思路与取舍

- 选择 Maven Wrapper，是为了让本地和 CI 使用相同构建入口。
- 选择单体 Spring Boot，是因为当前业务规模不需要微服务拆分。
- 将启动类放在基础包根目录，可让 Spring 默认组件扫描覆盖项目代码。
- 不在启动类执行初始化逻辑，避免启动过程不可测试、不可回滚。
- 当前实际依赖版本以仓库 `pom.xml` 为准；环境偏差不在本模块强制解决。

### 3.4 涉及文件与契约

```text
pom.xml
mvnw
mvnw.cmd
.mvn/wrapper/maven-wrapper.properties
src/main/java/org/victor/stackora/StackoraBackendApplication.java
```

基础依赖职责：

| 依赖 | 职责 |
| --- | --- |
| Spring Boot Web MVC | HTTP、Controller、JSON Web 基础 |
| Lombok | 减少明确的数据类样板代码 |
| Spring Boot Test | JUnit 和 Spring 测试基础 |

### 3.5 正常流程

```text
Developer
  -> ./mvnw spring-boot:run
  -> Maven 解析 pom.xml
  -> 编译主代码
  -> SpringApplication.run
  -> 创建 ApplicationContext
  -> 启动 HTTP Server
```

### 3.6 异常、权限与并发边界

- Java 版本不兼容：编译失败，不能跳过错误。
- 依赖解析失败：保留 Maven 错误，不能通过删除测试掩盖。
- 启动端口占用：应用启动失败并指出端口冲突。
- 本需求没有用户权限、事务和并发写入。
- 启动 Banner 或控制台输出不属于业务日志，也不能替代健康检查。

### 3.7 测试设计

```bash
./mvnw -version
./mvnw -DskipTests compile
```

预期：

- Wrapper 可以执行。
- Maven 使用的 Java 版本满足编译要求。
- 主代码编译成功。

### 3.8 完成标准

- Maven Wrapper 文件齐全。
- 项目坐标和 Java 版本明确。
- 应用入口位于基础包根目录。
- 启动类没有业务初始化逻辑。
- Maven 编译真实通过。

## 4. 需求二：安全地提供环境配置

### 4.1 需求行为

开发者应能为不同环境提供端口、profile 和数据库连接信息，同时保证真实密码不会进入 Git。

成功结果：

- 公共配置与环境差异分离。
- 环境变量能够覆盖可变配置。
- `.env` 被忽略，`.env.example` 可以提交。
- 开发和生产日志级别不同。

### 4.2 具体设计

配置职责：

| 文件 | 职责 |
| --- | --- |
| `application.yml` | 应用名称、Actuator 等公共非敏感配置 |
| `application-dev.yml` | 开发环境 Mapper 调试日志 |
| `application-prod.yml` | 生产环境较低日志级别 |
| `.env.example` | 环境变量名称和安全示例 |
| `.env` | 本机真实配置，不进入 Git |
| `.gitignore` | 忽略本地环境、日志、IDE 和构建产物 |

环境变量契约：

```text
SPRING_PROFILES_ACTIVE
SERVER_PORT
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

数据库测试需要额外变量时，必须指向独立、可安全清理的测试目标。

### 4.3 设计思路与取舍

- 公共 YAML 保留可审查的非敏感默认值。
- 密码使用环境变量，而不是把开发者个人配置提交到仓库。
- `.env.example` 只定义契约；Spring Boot 不会自动加载根目录 `.env`，因此 README 必须说明实际加载方式。
- 开发环境允许 Mapper DEBUG，生产环境使用 WARN，避免生产日志泄露 SQL 和参数。
- 当前不引入配置中心，因为单体本地开发没有相应复杂度。

### 4.4 涉及文件与契约

```text
src/main/resources/application.yml
src/main/resources/application-dev.yml
src/main/resources/application-prod.yml
.env.example
.gitignore
README.md
```

`.gitignore` 至少覆盖：

```text
target/
.env
.env.*
.idea/
*.iml
*.log
logs/
.DS_Store
```

必须通过例外规则允许 `.env.example` 被跟踪。

### 4.5 正常流程

```text
Developer
  -> 根据 .env.example 准备本地变量
  -> 通过 Shell 或 IDE 加载变量
  -> 设置 SPRING_PROFILES_ACTIVE=dev
  -> Spring 读取 application.yml
  -> Spring 叠加 application-dev.yml
  -> 环境变量覆盖可变配置
  -> 应用启动
```

### 4.6 异常、权限与并发边界

- 缺少 URL、用户名或密码：数据源创建失败并保留可定位错误。
- `.env` 没有被 IDE/Shell 加载：不能误认为 Spring Boot 会自动读取。
- 日志不得打印密码、Token、Cookie、Session ID 或敏感连接串。
- `.env.example` 不得使用真实可用密码。
- 本需求没有业务权限和并发写入。

### 4.7 测试设计

```bash
git check-ignore .env
git status --short
git diff --check
```

人工检查：

- `.env` 未进入 Git。
- `.env.example` 可以进入 Git。
- YAML、Java 和测试代码中没有真实凭据。
- README 对 `.env` 加载方式的描述准确。

### 4.8 完成标准

- 公共、开发和生产配置职责清晰。
- 真实凭据不在 Git 跟踪文件中。
- `.env` 被忽略。
- README 提供可执行的环境变量设置方式。
- 开发和生产日志级别符合安全边界。

## 5. 需求三：建立 MySQL 与 MyBatis-Plus 基础

### 5.1 需求行为

后续持久化模块应能直接创建 Mapper 并访问 MySQL，不需要重新搭建驱动、数据源和扫描规则。

本需求只提供基础能力，不创建业务表或业务模型。

### 5.2 具体设计

`pom.xml` 声明：

- MySQL Connector/J。
- MyBatis-Plus Spring Boot Starter。

Mapper 统一位于：

```text
org.victor.stackora.mapper
```

扫描配置位于 `MybatisPlusConfig`：

```java
@Configuration
@MapperScan("org.victor.stackora.mapper")
public class MybatisPlusConfig {
}
```

数据源由 Spring Boot 根据 `SPRING_DATASOURCE_*` 自动配置。

### 5.3 设计思路与取舍

- MySQL 是核心业务数据事实来源。
- MyBatis-Plus 提供 Mapper 和常用持久化能力，但不能替代业务 Service。
- Mapper 扫描放入 MyBatis-Plus 配置类，而不是启动类，使 Web Slice 测试更容易隔离数据层配置。
- V0 不创建示例 Entity 和表，避免无用代码成为正式模型。
- 分页、自动填充、迁移和业务 SQL由后续模块按真实需求设计。

### 5.4 涉及文件与契约

```text
pom.xml
src/main/java/org/victor/stackora/config/MybatisPlusConfig.java
src/main/resources/application-dev.yml
src/main/resources/application-prod.yml
```

数据库连接至少需要：

```text
JDBC URL
schema
username
password
character encoding
server timezone
```

### 5.5 正常流程

```text
Environment Variables
  -> Spring DataSource Auto-Configuration
  -> HikariDataSource
  -> MyBatis-Plus SqlSessionFactory
  -> @MapperScan
  -> Mapper Bean
```

### 5.6 异常、权限与并发边界

- 数据库不可达：数据源或首次查询失败，不能报告为可用。
- schema 不存在或权限不足：保留真实服务端错误，不返回客户端。
- Web Slice 测试不应因 Mapper 扫描被迫创建真实数据源。
- 测试不得连接并清理长期开发 schema。
- 本需求尚无业务事务和并发写入。

### 5.7 测试设计

```bash
./mvnw -DskipTests compile
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

预期：

- 工程可以编译。
- 正确数据库配置下数据源可以创建。
- 错误配置产生明确失败。
- 不存在业务 Mapper 时不会要求业务表。

### 5.8 完成标准

- MySQL 驱动和 MyBatis-Plus Starter 已声明。
- Mapper 包路径唯一。
- Mapper 扫描配置职责独立。
- 开发与生产 Mapper 日志分离。
- V0 没有业务表、Entity、Mapper 或 SQL。

## 6. 需求四：提供标准健康探测

### 6.1 需求行为

开发者和部署探针应能通过标准 HTTP 端点判断应用及已注册依赖的健康状态，不需要业务 Controller。

### 6.2 具体设计

使用 Spring Boot Actuator：

| 端点 | 方法 | 用途 |
| --- | --- | --- |
| `/actuator/health` | GET | 应用和依赖健康状态 |
| `/actuator/info` | GET | 可公开应用信息 |

配置：

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

Actuator 返回框架标准结构，不包装为业务 `ApiResponse`。

### 6.3 设计思路与取舍

- 使用 Actuator 避免重复实现 `/api/health`。
- 只暴露 `health` 和 `info`，避免 `env`、`beans` 等端点泄露内部信息。
- `when-authorized` 限制详细组件信息。
- Actuator 与业务 API 契约分离，便于部署平台和标准工具识别。

### 6.4 涉及文件与契约

```text
pom.xml
src/main/resources/application.yml
README.md
```

响应示例只保证存在框架定义的 `status`，不保证业务字段。

### 6.5 正常流程

```text
Probe
  -> GET /actuator/health
  -> Actuator Endpoint
  -> Health Contributors
  -> 聚合状态
  -> 标准 Actuator JSON
```

### 6.6 异常、权限与并发边界

- 数据库不可用时不能伪装数据库状态为正常。
- 未授权请求不应看到完整健康详情。
- HTTP 端点成功不等于所有依赖均健康，调用方仍需读取 `status`。
- 生产环境需要在部署阶段结合网络和认证策略重新评审。
- 健康检查是只读操作，不改变系统状态。

### 6.7 测试设计

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/actuator/info
```

自动化测试断言：

- `health` 响应存在 `status`。
- 响应不包含业务字段 `code`。
- 未暴露管理端点不可访问。

### 6.8 完成标准

- Actuator 依赖存在。
- 只暴露 `health`、`info`。
- 健康详情策略符合设计。
- README 提供访问命令。
- 响应保持 Actuator 标准结构。

## 7. 需求五：提供可重复验证和开发说明

### 7.1 需求行为

开发者应能只依据 README 完成本地配置、构建、启动和基础验证；自动化测试不应默认依赖某个开发者的长期 MySQL。

### 7.2 具体设计

README 包含：

1. 项目定位和当前阶段。
2. 实际技术栈。
3. 环境要求。
4. 环境变量设置方式。
5. 构建和启动命令。
6. Actuator 验证命令。
7. 真实项目结构。
8. 配置安全规则。
9. 后续演进路线。

最小自动化测试覆盖：

- 应用上下文在隔离配置下加载。
- Actuator 标准响应。
- 测试不修改长期开发数据库。

### 7.3 设计思路与取舍

- README 是开发入口，必须随项目演进更新。
- 自动化构建与真实数据库连通性分开，保证基础测试可重复。
- 需要真实 MySQL 的测试使用独立测试配置或临时 schema。
- 不以“IDE 可以启动”代替 Maven 测试。

### 7.4 涉及文件与契约

```text
README.md
.env.example
src/test/java/org/victor/stackora/StackoraBackendApplicationTests.java
```

目标测试命令：

```bash
./mvnw clean test
```

### 7.5 正常流程

```text
Developer
  -> 阅读 README
  -> 准备环境变量
  -> ./mvnw clean test
  -> SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
  -> curl Actuator
  -> 检查 Git 状态
```

### 7.6 异常、权限与并发边界

- README 文件名、扩展名或命令与真实仓库不一致，属于文档缺陷。
- 测试依赖本机长期 schema，会导致不可重复和数据污染。
- 测试失败时不能声明工程基线完成。
- 测试日志不得输出真实密码。

### 7.7 测试设计

```bash
./mvnw clean test
git diff --check
git status --short
```

预期：

- Maven 返回退出码 `0`。
- 测试报告没有 Failure 或 Error。
- Git diff 没有空白错误。
- 没有敏感本地文件进入提交。

### 7.8 完成标准

- README 与真实工程一致。
- 配置和启动步骤可以复现。
- 至少存在工程基础自动化测试。
- 全量测试通过。
- 测试不污染长期数据库。

## 8. 模块级公共约束

- 只有 `stackora/stackora_backend` 属于正式 Git 仓库。
- 根 `AGENTS.md`、`supervision`、`.codex` 和 `paicoding-reference` 不进入正式仓库。
- 所有真实凭据通过本地环境提供。
- V0 不引入当前没有需求的基础设施。
- 环境安装和版本调整由开发者自行处理。
- MySQL 是后续业务数据事实来源。

## 9. 完整验收清单

按需求顺序检查：

### 9.1 统一构建并启动

- [x] Maven Wrapper 存在。
- [x] `pom.xml` 声明工程坐标和 Java 版本。
- [x] Spring Boot 启动类存在。
- [ ] 干净环境编译通过。

### 9.2 安全环境配置

- [x] 公共、开发、生产 YAML 分离。
- [x] `.env.example` 存在。
- [x] `.env` 被忽略。
- [x] README 说明 `.env` 的加载方式。
- [ ] 跟踪文件经检查不含真实凭据。

### 9.3 数据库访问基础

- [x] MySQL 驱动已声明。
- [x] MyBatis-Plus Starter 已声明。
- [x] Mapper 扫描配置独立。
- [ ] 开发数据库连通性经过实际验证。
- [ ] 自动化测试不会误连长期开发库。

### 9.4 健康探测

- [x] Actuator 已声明。
- [x] 只暴露 `health`、`info`。
- [x] 健康详情使用 `when-authorized`。
- [ ] Actuator 标准响应有自动化测试。

### 9.5 验证与文档

- [x] README 包含构建、启动和健康检查说明。
- [ ] 工程上下文测试可重复执行。
- [ ] `./mvnw clean test` 实际通过。
- [ ] README 与当前真实目录再次核对一致。

## 10. 测试命令与预期结果

```bash
./mvnw -DskipTests compile
./mvnw clean test
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/actuator/info
git diff --check
```

只有实际执行成功的命令才能作为验收证据。

## 11. 已知风险与技术债

1. 根级项目标识与当前 `org.victor` 工程标识仍有历史差异，应在监督层统一，不能混入业务提交。
2. 当前项目已经存在 Mapper 后，Web Slice 测试需要继续保持数据层隔离。
3. 本地 MySQL 不保证环境完全一致，后续可使用临时 schema 或 Testcontainers。
4. Actuator 的生产网络和认证边界需要在部署阶段复审。
5. README 需要随用户持久化和后续模块持续更新。

## 12. 后续模块边界

工程骨架完成后依次承接：

1. 统一响应与异常处理。
2. MyBatis-Plus 公共配置和用户持久化。
3. Swagger / Knife4j。
4. 用户注册、登录和 Session。
5. 文章、评论和互动。
6. 缓存、消息、搜索和部署。

每个后续模块仍需独立完成需求、设计、测试和审核。

## 13. Paicoding 对比点

可在读取真实源码后比较：

- 工程目录。
- 配置拆分。
- Maven 依赖管理。
- MyBatis-Plus 初始化。
- 健康检查。
- README 与测试基线。

当前缺少可读取的 Paicoding 源码时，结论为“证据不足”，不得凭印象补写。
