# Stackora

Stackora 是一个面向开发者的技术内容与交流社区后端项目。通过独立设计、独立实现、测试验证和代码复盘，逐步完成一个可部署、可测试、可持续演进的 Java 后端系统。

项目当前处于 **V0 工程骨架阶段**，重点是建立稳定的 Spring Boot、MySQL、MyBatis-Plus、环境配置和健康检查基础。用户、文章、评论、互动、通知、缓存与搜索等业务能力仍在后续规划中。

## 当前能力

- Maven Wrapper 与 JDK 17 构建基线。
- Spring Boot Web MVC 应用骨架。
- MySQL 驱动与 MyBatis-Plus 集成。
- `dev`、`prod` 环境日志配置。
- 通过环境变量注入数据库连接信息。
- Spring Boot Actuator 健康检查与应用信息端点。
- 本地环境文件与敏感配置的 Git 忽略规则。

> 当前仓库尚未实现完整业务闭环，也不应视为可直接部署到生产环境的版本。

## 技术栈

| 类别 | 当前使用 |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 4.0.7 |
| Web | Spring Web MVC |
| Persistence | MyBatis-Plus 3.5.17 |
| Database | MySQL 8 |
| Build | Maven Wrapper |
| Observability | Spring Boot Actuator |
| Utilities | Lombok |

Redis、Redisson、RabbitMQ 和 Elasticsearch 将在出现明确业务需求后分阶段引入，不作为当前工程骨架的运行依赖。

## 环境要求

- JDK 17
- MySQL 8
- Git

项目已包含 Maven Wrapper，无需单独安装 Maven。

## 本地配置

复制环境变量示例文件：

```bash
cp .env.example .env
```

根据本机环境修改 `.env` 中的数据库地址、用户名和密码。`.env` 已被 Git 忽略，禁止将真实凭据提交到仓库。

Spring Boot 不会自动读取项目根目录中的 `.env` 文件。可以通过 IntelliJ IDEA 运行配置加载该文件，也可以在终端显式设置环境变量：

```bash
export SPRING_PROFILES_ACTIVE=dev
export SERVER_PORT=8080
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/stackora?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export SPRING_DATASOURCE_USERNAME='root'
export SPRING_DATASOURCE_PASSWORD='replace-with-your-password'
```

## 启动项目

在仓库根目录执行：

```bash
./mvnw spring-boot:run
```

应用默认监听 `8080` 端口。端口可以通过 `SERVER_PORT` 环境变量覆盖。

## 健康检查

应用启动后，可以访问以下 Actuator 端点：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/info
```

- `/actuator/health` 用于检查应用及已配置依赖的健康状态。
- `/actuator/info` 用于提供应用基础信息。
- 健康详情采用 `when-authorized` 策略，未授权请求不会返回完整组件信息。

## 构建与测试

执行 Maven 测试生命周期：

```bash
./mvnw clean test
```

当前提交尚未包含自动化测试源码。后续模块开发需要为关键正常流程、异常流程、权限规则和数据访问补充可重复执行的测试。

## 项目结构

```text
stackora_backend/
├── .mvn/                         # Maven Wrapper 配置
├── src/main/java/
│   └── org/victor/stackora/     # 当前 Java 基础包
├── src/main/resources/
│   ├── application.yaml         # 通用应用与 Actuator 配置
│   ├── application-dev.yml      # 开发环境日志配置
│   └── application-prod.yml     # 生产环境日志配置
├── .env.example                 # 环境变量示例
├── .gitignore
├── mvnw
├── mvnw.cmd
└── pom.xml
```

## 开发路线

Stackora 将按以下顺序逐步演进：

1. 完善工程基础能力、统一响应、异常处理、参数校验和接口文档。
2. 实现用户注册、登录、退出、个人资料和权限控制。
3. 实现文章发布、编辑、删除、详情、分页、分类和标签。
4. 实现评论、回复、点赞、收藏、关注和站内通知。
5. 根据真实性能与并发问题引入 Redis 和 Redisson。
6. 引入 RabbitMQ 完成可靠异步通知。
7. 引入 Elasticsearch 完成文章搜索和索引同步。
8. 补充后台治理、日志监控、Docker 部署和故障排查文档。

项目坚持单体优先、业务正确性优先和最小可行实现原则，不为展示技术而提前引入复杂基础设施。


## 配置安全

- 不在 Java、YAML、测试代码或日志中写入真实密码。
- 真实数据库凭据只通过环境变量或未提交的本地配置提供。
- `.env.example` 只能保存变量名称和安全的占位值。
- 对外响应和日志不得暴露密码、完整 JDBC URL 或底层异常堆栈。

