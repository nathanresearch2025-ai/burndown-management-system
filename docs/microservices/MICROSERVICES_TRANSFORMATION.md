# 后端微服务改造方案

> 文档版本：v1.0 | 更新日期：2026-03-24
> 适用项目：Burndown Management System（Spring Boot 3.2 / Java 21）

---

## 一、现状：单体架构分析

### 1.1 当前单体结构

```
burndown-backend（单体）
├── controller/      AuthController, TaskController, SprintController,
│                    ProjectController, BurndownController, WorkLogController,
│                    UserController, RoleController, PermissionController
├── service/         AuthService, TaskService, SprintService,
│                    ProjectService, BurndownService, WorkLogService,
│                    TaskAiService, AiClientService, RateLimitService ...
├── entity/          User, Project, Sprint, Task, WorkLog, BurndownPoint,
│                    Role, Permission, UserRole, AgentChatSession ...
├── config/          SecurityConfig, CacheConfig, AsyncConfig ...
└── 共享一个 PostgreSQL + Redis
```

### 1.2 单体架构痛点

| 痛点 | 具体表现 |
|------|----------|
| 部署耦合 | 修改任意模块需全量重新部署 |
| 资源争用 | AI Agent（慢）与核心业务（快）共享线程池和连接池 |
| 扩展粒度粗 | 只能整体横向扩展，无法单独扩容热点服务 |
| 技术栈锁定 | 所有模块必须用同一语言/框架 |
| 故障传播 | LLM 调用超时可能拖垮整个应用 |
| 团队协作 | 多人同时修改同一代码库，冲突频繁 |


---

## 二、微服务拆分原则

### 2.1 拆分依据

1. **按业务领域（DDD 限界上下文）**：每个服务对应一个独立业务域
2. **按变更频率**：AI 功能迭代快，独立部署互不影响核心业务
3. **按性能特征**：IO 密集型（AI 调用）与 CPU/DB 密集型（业务查询）隔离
4. **按团队边界**：一个服务由一个小团队负责

### 2.2 拆分粒度原则

- **不要过度拆分**：初期 5-7 个服务为宜，避免分布式复杂性大于收益
- **服务间通信最小化**：高频交互的业务放在同一服务
- **数据库独立**：每个服务拥有自己的数据库 schema，禁止跨服务直接查询

---

## 三、目标微服务架构

### 3.1 服务划分

```
                        ┌─────────────────────┐
                        │    API Gateway       │  :8000
                        │  (Spring Cloud GW)   │
                        └──────────┬──────────┘
                                   │
        ┌──────────────┬───────────┼───────────┬──────────────┐
        ▼              ▼           ▼           ▼              ▼
┌──────────────┐ ┌──────────┐ ┌────────┐ ┌─────────┐ ┌───────────┐
│  用户认证服务  │ │ 项目服务  │ │任务服务 │ │燃尽图服务 │ │ AI智能服务 │
│ auth-service │ │ project  │ │  task  │ │burndown │ │ ai-agent  │
│    :8081     │ │ :8082    │ │  :8083 │ │  :8084  │ │   :8085   │
└──────┬───────┘ └────┬─────┘ └───┬────┘ └────┬────┘ └─────┬─────┘
       │              │           │            │             │
    pg_auth       pg_project   pg_task    pg_burndown    pg_ai
    redis_auth                redis_task
```

### 3.2 各服务职责

| 服务名 | 端口 | 职责 | 对应原模块 |
|--------|------|------|----------|
| **api-gateway** | 8000 | 路由、认证校验、限流、负载均衡 | 无 |
| **auth-service** | 8081 | 用户注册/登录、JWT 签发/验证、RBAC 权限 | AuthController, UserController, RoleController, PermissionController |
| **project-service** | 8082 | 项目 CRUD、Sprint 管理、成员管理 | ProjectController, SprintController |
| **task-service** | 8083 | 任务 CRUD、WorkLog 记录、任务状态流转 | TaskController, WorkLogController |
| **burndown-service** | 8084 | 燃尽图计算、数据点存储、Sprint 预测 | BurndownController, SprintPredictionController |
| **ai-agent-service** | 8085 | AI 任务生成、LangChain Standup Agent、向量检索 | TaskAiController, StandupAgentController, EmbeddingController |


---

## 四、详细架构设计

### 4.1 API Gateway（Spring Cloud Gateway）

**职责**：
- 统一入口，路由转发到各下游服务
- JWT Token 验证（从 auth-service 获取公钥验签，无需每次远程调用）
- 全局限流（Bucket4j + Redis）
- 请求日志、链路追踪注入（TraceId）
- 跨域处理（统一配置 CORS）

```yaml
# gateway application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/v1/auth/**,/api/v1/users/**,/api/v1/roles/**
        - id: project-service
          uri: lb://project-service
          predicates:
            - Path=/api/v1/projects/**,/api/v1/sprints/**
          filters:
            - AuthFilter  # 自定义JWT验证过滤器
        - id: task-service
          uri: lb://task-service
          predicates:
            - Path=/api/v1/tasks/**,/api/v1/worklogs/**
          filters:
            - AuthFilter
        - id: burndown-service
          uri: lb://burndown-service
          predicates:
            - Path=/api/v1/burndown/**,/api/v1/sprint-predictions/**
          filters:
            - AuthFilter
        - id: ai-agent-service
          uri: lb://ai-agent-service
          predicates:
            - Path=/api/v1/tasks/ai/**,/api/v1/agent/**,/api/v1/embeddings/**
          filters:
            - AuthFilter
            - name: RequestRateLimiter  # AI接口单独限流
              args:
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
```

### 4.2 auth-service（用户认证服务）

**数据库**：独立 `pg_auth` schema
```sql
-- 表：users, roles, permissions, user_roles, role_permissions
```

**核心设计**：
- JWT 使用 **非对称加密（RSA）**：auth-service 持有私钥签发 Token，其他服务用公钥验签，无需调用 auth-service
- Token 黑名单存 Redis（用于登出/强制下线场景）
- 对外暴露 `/auth/public-key` 接口供 Gateway 拉取公钥

```java
// 其他服务只需本地验签，无远程调用
public Claims validateToken(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(rsaPublicKey)  // 启动时从 auth-service 拉取一次
        .build()
        .parseClaimsJws(token)
        .getBody();
}
```

### 4.3 task-service（任务服务）

**数据库**：独立 `pg_task` schema
```sql
-- 表：tasks, work_logs, task_embeddings, ai_task_generation_logs
```

**与其他服务交互**：
- 需要 Sprint 信息 → 调用 project-service（同步 Feign）
- 任务状态变更 → 发布事件到 MQ（异步通知 burndown-service 重新计算）

```java
// 任务完成事件
@Data
public class TaskCompletedEvent {
    private Long taskId;
    private Long sprintId;
    private Integer storyPoints;
    private LocalDateTime completedAt;
}
// 发布到 RabbitMQ/Kafka topic: task.status.changed
```

### 4.4 burndown-service（燃尽图服务）

**数据库**：独立 `pg_burndown` schema
```sql
-- 表：burndown_points, sprint_predictions
```

**消费事件驱动**：
- 监听 `task.status.changed` 事件，异步重新计算燃尽数据
- 无需同步调用 task-service，解耦彻底

```java
@RabbitListener(queues = "task.status.changed")
public void onTaskStatusChanged(TaskCompletedEvent event) {
    burndownCalculator.recalculate(event.getSprintId());
}
```

### 4.5 ai-agent-service（AI 智能服务）

**独立部署的核心理由**：
- LLM 调用耗时 5-120 秒，完全隔离避免影响核心业务
- 可单独配置大内存（向量检索）、独立限流
- 技术栈可灵活选择（未来可用 Python FastAPI 替换）

**数据库**：独立 `pg_ai` schema + pgvector 扩展
```sql
-- 表：agent_chat_sessions, agent_chat_messages, agent_tool_call_logs
-- 向量字段：task_embeddings.embedding vector(384)
```


---

## 五、服务间通信设计

### 5.1 通信方式选择

| 场景 | 方式 | 技术 |
|------|------|------|
| 同步查询（需要立即返回结果） | REST / gRPC | OpenFeign / Spring WebClient |
| 异步事件（状态变更通知） | 消息队列 | RabbitMQ 或 Kafka |
| 服务发现 | 注册中心 | Nacos 或 Consul |

### 5.2 Feign 客户端示例

```java
// task-service 中调用 project-service
@FeignClient(name = "project-service", fallback = ProjectClientFallback.class)
public interface ProjectServiceClient {

    @GetMapping("/api/v1/sprints/{sprintId}")
    SprintDTO getSprint(@PathVariable Long sprintId);

    @GetMapping("/api/v1/projects/{projectId}")
    ProjectDTO getProject(@PathVariable Long projectId);
}

// 降级处理
@Component
public class ProjectClientFallback implements ProjectServiceClient {
    @Override
    public SprintDTO getSprint(Long sprintId) {
        return SprintDTO.empty(sprintId); // 返回空对象，不抛异常
    }
}
```

### 5.3 消息队列事件清单

| 事件主题 | 生产者 | 消费者 | 触发时机 |
|---------|--------|--------|----------|
| `task.status.changed` | task-service | burndown-service | 任务状态变更 |
| `task.created` | task-service | ai-agent-service | 新建任务（触发向量索引更新） |
| `sprint.completed` | project-service | burndown-service, ai-agent-service | Sprint 完成 |
| `user.permission.changed` | auth-service | api-gateway | 权限变更（清除网关缓存） |

### 5.4 服务调用链路

```
用户请求 → API Gateway
    │
    ├── GET /tasks?sprintId=1
    │       → task-service
    │           → Feign: project-service.getSprint(1)  [验证Sprint存在]
    │           → pg_task 查询
    │           ← 返回任务列表
    │
    ├── PUT /tasks/1/status (DONE)
    │       → task-service
    │           → pg_task 更新状态
    │           → MQ 发布 task.status.changed 事件
    │           ← 立即返回 200（不等燃尽图计算）
    │                           ↓ 异步
    │                    burndown-service
    │                        → 重新计算燃尽数据
    │                        → 更新 pg_burndown
    │
    └── POST /tasks/ai/generate-description
            → ai-agent-service（独立慢服务，不影响上面的链路）
```

---

## 六、基础设施组件

### 6.1 服务注册与发现（Nacos）

```yaml
# 每个服务的 application.yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        namespace: burndown-prod
  application:
    name: task-service  # 各服务不同
```

### 6.2 配置中心（Nacos Config）

```yaml
# 公共配置统一管理，避免各服务重复配置
# Nacos 中维护：
# - burndown-common.yaml    # 公共配置（日志、监控）
# - task-service.yaml       # 服务专属配置
# - task-service-prod.yaml  # 生产环境覆盖
```

### 6.3 分布式链路追踪（Micrometer Tracing + Zipkin）

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 采样率10%（生产），开发环境设1.0
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

### 6.4 消息队列（RabbitMQ）

```yaml
spring:
  rabbitmq:
    host: rabbitmq
    port: 5672
    virtual-host: /burndown
    listener:
      simple:
        concurrency: 5       # 消费者并发数
        max-concurrency: 20
        prefetch: 10         # 每次预取消息数
```


---

## 七、数据库拆分策略

### 7.1 数据库归属

| 服务 | 数据库 | 包含的表 |
|------|--------|----------|
| auth-service | pg_auth | users, roles, permissions, user_roles, role_permissions |
| project-service | pg_project | projects, sprints, project_members |
| task-service | pg_task | tasks, work_logs, task_embeddings, ai_task_generation_logs |
| burndown-service | pg_burndown | burndown_points, sprint_predictions |
| ai-agent-service | pg_ai | agent_chat_sessions, agent_chat_messages, agent_tool_call_logs |

### 7.2 跨服务数据引用原则

**禁止**跨服务直接 JOIN 查询，替代方案：

```
方案A：冗余存储（推荐，性能最好）
  task-service 本地存储 project_id, sprint_id, assignee_id（只存ID）
  需要展示名称时：批量调用对应服务，或事件驱动同步冗余字段

方案B：API 聚合（Gateway 或 BFF 层）
  前端需要任务+项目信息 → Gateway 并发调用两个服务，聚合后返回

方案C：事件驱动数据同步
  project.updated 事件 → task-service 更新本地 project_name 冗余字段
```

### 7.3 分布式事务处理

**场景**：创建任务时同时需要更新 Sprint 的任务计数

```
方案：SAGA 模式（补偿事务）

Step1: task-service 创建任务（本地事务）
Step2: 发布 task.created 事件到 MQ
Step3: project-service 消费事件，更新 sprint.task_count
Step4: 如果 Step3 失败，project-service 发布失败事件
Step5: task-service 消费失败事件，执行补偿（删除任务）

原则：最终一致性，不追求强一致性
```

---

## 八、改造需求清单

### P0 - 基础设施先行

| 编号 | 需求 | 说明 |
|------|------|------|
| P0-1 | 搭建 Nacos 注册中心 + 配置中心 | 所有服务的服务发现基础 |
| P0-2 | 搭建 API Gateway（Spring Cloud Gateway） | 统一入口，JWT 验签迁移到 Gateway |
| P0-3 | 搭建 RabbitMQ | 服务间异步通信基础 |
| P0-4 | JWT 改为 RSA 非对称加密 | 其他服务本地验签，无需调用 auth-service |
| P0-5 | 搭建 Zipkin 链路追踪 | 微服务调试必备 |

### P1 - 服务拆分（按依赖顺序）

| 编号 | 需求 | 拆分顺序理由 |
|------|------|------------|
| P1-1 | 拆分 auth-service | 无依赖其他服务，最先拆 |
| P1-2 | 拆分 project-service | 仅依赖 auth-service |
| P1-3 | 拆分 task-service | 依赖 project-service |
| P1-4 | 拆分 burndown-service | 依赖 task-service（事件驱动） |
| P1-5 | 拆分 ai-agent-service | 依赖 task-service（事件驱动） |

### P2 - 稳定性与可观测性

| 编号 | 需求 | 说明 |
|------|------|------|
| P2-1 | 各服务 Feign 客户端熔断降级 | Resilience4j |
| P2-2 | 统一日志收集（ELK Stack） | Elasticsearch + Logstash + Kibana |
| P2-3 | Prometheus + Grafana 多服务监控面板 | 各服务独立监控 |
| P2-4 | 健康检查 + K8s 探针配置 | liveness/readiness probe |
| P2-5 | API 版本管理策略 | Header/URL 版本控制 |


---

## 九、Kubernetes 部署架构

### 9.1 K8s 资源规划

```yaml
# 各服务 Deployment 资源配置
apiVersion: apps/v1
kind: Deployment
metadata:
  name: task-service
spec:
  replicas: 3              # 核心服务3副本
  template:
    spec:
      containers:
        - name: task-service
          image: burndown/task-service:latest
          resources:
            requests:
              cpu: "500m"
              memory: "512Mi"
            limits:
              cpu: "1000m"
              memory: "1Gi"
          readinessProbe:
            httpGet:
              path: /api/v1/actuator/health/readiness
              port: 8083
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /api/v1/actuator/health/liveness
              port: 8083
            initialDelaySeconds: 60
            periodSeconds: 30
---
# ai-agent-service 单独配置（高内存）
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ai-agent-service
spec:
  replicas: 2              # AI服务2副本即可
  template:
    spec:
      containers:
        - name: ai-agent-service
          resources:
            requests:
              cpu: "1000m"
              memory: "2Gi"   # 向量检索需要更多内存
            limits:
              cpu: "2000m"
              memory: "4Gi"
```

### 9.2 HPA 自动扩缩容

```yaml
# task-service 自动扩缩容
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: task-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: task-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

### 9.3 服务端口规划

| 服务 | 内部端口 | NodePort | 说明 |
|------|---------|----------|------|
| api-gateway | 8000 | 30000 | 唯一对外暴露入口 |
| auth-service | 8081 | - | 仅内部访问 |
| project-service | 8082 | - | 仅内部访问 |
| task-service | 8083 | - | 仅内部访问 |
| burndown-service | 8084 | - | 仅内部访问 |
| ai-agent-service | 8085 | - | 仅内部访问 |
| nacos | 8848 | 30848 | 管理界面 |
| rabbitmq | 5672/15672 | 30672/31672 | AMQP/管理界面 |
| zipkin | 9411 | 30411 | 链路追踪UI |


---

## 十、改造实施路线图

### 10.1 分阶段实施计划

```
第一阶段：基础设施搭建（1-2周）
  ├── 部署 Nacos（注册中心 + 配置中心）
  ├── 部署 RabbitMQ
  ├── 部署 Zipkin
  ├── 搭建 API Gateway（当前单体前面加一层网关）
  └── JWT 改为 RSA 非对称加密
  目标：网关上线，单体仍在运行，验证路由可行性

第二阶段：剥离 auth-service（1周）
  ├── 新建 auth-service 项目（复制相关代码）
  ├── 独立数据库 pg_auth（迁移 users/roles/permissions 表）
  ├── Gateway 路由 /auth/** 到新服务
  ├── 单体中删除对应 Controller/Service
  └── 联调测试
  目标：认证服务独立，其他服务仍是单体

第三阶段：剥离 project-service（1周）
  ├── 新建 project-service（复制 Project/Sprint 相关代码）
  ├── 独立数据库 pg_project
  ├── 实现 Feign Client 供 task-service 调用
  └── 联调测试

第四阶段：剥离 task-service（1-2周）
  ├── 新建 task-service
  ├── 独立数据库 pg_task
  ├── 接入 RabbitMQ，发布 task.status.changed 事件
  └── 联调测试

第五阶段：剥离 burndown-service + ai-agent-service（1-2周）
  ├── burndown-service 改为消费 MQ 事件驱动计算
  ├── ai-agent-service 完全独立（含 LangChain 调用）
  └── 单体退役

第六阶段：稳定性建设（持续）
  ├── 配置 HPA 自动扩缩容
  ├── ELK 日志聚合
  ├── Grafana 多服务监控面板
  └── 压测验证目标并发量
```

### 10.2 并发提升预期

| 阶段 | 支持并发用户 | RPS 目标 | 关键改进 |
|------|------------|---------|----------|
| 当前单体 | ~50 | ~47 | - |
| 第一阶段后（网关+单体） | ~100 | ~100 | 网关限流、快速失败 |
| 第三阶段后（auth+project独立） | ~200 | ~200 | 核心服务独立扩容 |
| 第五阶段后（全微服务） | ~500 | ~500 | AI与业务完全隔离 |
| 配置HPA后 | ~2000+ | ~1000+ | 弹性扩缩容 |

---

## 十一、风险与挑战

### 11.1 主要风险

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| 分布式事务复杂性 | 数据一致性问题 | 优先使用最终一致性（SAGA），避免强一致性需求 |
| 服务间网络延迟 | 响应时间增加 | Feign 连接池复用、本地缓存减少跨服务调用 |
| 运维复杂度大幅增加 | 故障定位困难 | 链路追踪（Zipkin）+ 统一日志（ELK）是前提 |
| 数据库拆分风险 | 数据迁移出错 | 先双写验证，再切换，最后清理旧数据 |
| 团队学习成本 | 进度延误 | 分阶段实施，每阶段充分测试后再进行下一阶段 |

### 11.2 不适合微服务的场景

> **重要提醒**：微服务不是银弹，以下情况建议先做单体优化（见 `PERFORMANCE_OPTIMIZATION.md`）：
>
> - 团队人数 < 5 人
> - 日活用户 < 1 万，并发 < 200
> - 没有专职运维/DevOps 人员
> - 业务需求变化频繁，服务边界尚不清晰

---

## 十二、参考资料

- [Spring Cloud Gateway 官方文档](https://spring.io/projects/spring-cloud-gateway)
- [Spring Cloud OpenFeign](https://spring.io/projects/spring-cloud-openfeign)
- [Nacos 官方文档](https://nacos.io/zh-cn/docs/what-is-nacos.html)
- [Resilience4j 熔断器](https://resilience4j.readme.io/docs/circuitbreaker)
- [SAGA 分布式事务模式](https://microservices.io/patterns/data/saga.html)
- [微服务架构模式](https://microservices.io/patterns/index.html)
- 现有高并发优化文档：`docs/performance/HIGH_CONCURRENCY_TRANSFORMATION.md`
- 现有性能压测报告：`test/pressure/summary_report.html`

