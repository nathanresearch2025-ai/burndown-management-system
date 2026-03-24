# Burndown 微服务启动指南

## 前置条件

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 21+ | 运行 Spring Boot 3.2 |
| Maven | 3.9+ | 项目构建 |
| PostgreSQL | 14+ | 业务数据持久化 |
| Redis | 6+ | 缓存 |
| RabbitMQ | 3.12+ | 异步事件消息 |
| Nacos | 2.3+ | 服务注册与发现 |

---

## 一、启动基础设施

### 1. PostgreSQL
```bash
# 确保 PostgreSQL 运行在 5432 端口
# 创建数据库（首次）
psql -U postgres -c "CREATE DATABASE burndown_db;"

# 初始化所有 Schema（在 sql/ 目录下执行）
cd burndown-backend-ms/sql
psql -U postgres -d burndown_db -f init-all.sql
```

### 2. Redis
```bash
# 默认 localhost:6379，无密码
redis-server
```

### 3. RabbitMQ
```bash
# 默认 localhost:5672，guest/guest
rabbitmq-server
# 启用管理界面（可选）
rabbitmq-plugins enable rabbitmq_management
# 管理界面: http://localhost:15672
```

### 4. Nacos
```bash
# 下载 Nacos 2.3.x，单机模式启动
cd nacos/bin
# Linux/Mac
sh startup.sh -m standalone
# Windows
startup.cmd -m standalone
# 管理界面: http://localhost:8848/nacos (nacos/nacos)
```

---

## 二、构建项目

```bash
cd burndown-backend-ms

# 构建所有模块（跳过测试）
mvn clean package -DskipTests

# 或仅构建 common 模块（其他服务依赖它）
mvn clean install -pl common -DskipTests
```

---

## 三、启动顺序

> **重要：** 必须按以下顺序启动，auth-service 需先于 api-gateway 启动（Gateway 需拉取公钥）

### Step 1: auth-service（端口 8081）
```bash
cd auth-service
mvn spring-boot:run

# 验证
curl http://localhost:8081/api/v1/auth/public-key
```

### Step 2: project-service（端口 8082）
```bash
cd project-service
mvn spring-boot:run

# 验证
curl http://localhost:8082/actuator/health
```

### Step 3: task-service（端口 8083）
```bash
cd task-service
mvn spring-boot:run

# 验证
curl http://localhost:8083/actuator/health
```

### Step 4: burndown-service（端口 8084）
```bash
cd burndown-service
mvn spring-boot:run

# 验证
curl http://localhost:8084/actuator/health
```

### Step 5: ai-agent-service（端口 8085）
```bash
cd ai-agent-service
mvn spring-boot:run

# 验证
curl http://localhost:8085/actuator/health
```

### Step 6: api-gateway（端口 8000）
```bash
cd api-gateway
mvn spring-boot:run

# 验证（Gateway 会自动从 auth-service 拉取 RSA 公钥）
curl http://localhost:8000/actuator/health
```

---

## 四、验证步骤

### 1. 登录获取 Token
```bash
curl -X POST http://localhost:8000/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'

# 返回示例
# {"code":200,"data":{"token":"eyJ...","userId":1,"username":"admin"}}
```

### 2. 使用 Token 访问受保护接口
```bash
TOKEN="eyJ..."

# 查询项目列表
curl http://localhost:8000/api/v1/projects \
  -H "Authorization: Bearer $TOKEN"

# 查询任务列表（sprintId=1）
curl "http://localhost:8000/api/v1/tasks/sprint/1" \
  -H "Authorization: Bearer $TOKEN"

# 查询燃尽图数据
curl "http://localhost:8000/api/v1/burndown/sprint/1" \
  -H "Authorization: Bearer $TOKEN"
```

### 3. 验证 RabbitMQ 事件流
```bash
# 更新任务状态，触发 MQ 事件
curl -X PATCH http://localhost:8000/api/v1/tasks/1/status \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"DONE"}'

# burndown-service 应自动接收事件并记录燃尽点
curl "http://localhost:8000/api/v1/burndown/sprint/1" \
  -H "Authorization: Bearer $TOKEN"
```

### 4. AI 任务描述生成（需配置 ai.enabled=true）
```bash
curl -X POST http://localhost:8000/api/v1/ai/tasks/generate-description \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": 1,
    "sprintId": 1,
    "title": "用户登录优化",
    "type": "FEATURE",
    "priority": "HIGH",
    "storyPoints": 5
  }'
```

---

## 五、服务端口汇总

| 服务 | 端口 | Swagger UI |
|------|------|------------|
| api-gateway | 8000 | 无（路由层） |
| auth-service | 8081 | http://localhost:8081/swagger-ui.html |
| project-service | 8082 | http://localhost:8082/swagger-ui.html |
| task-service | 8083 | http://localhost:8083/swagger-ui.html |
| burndown-service | 8084 | http://localhost:8084/swagger-ui.html |
| ai-agent-service | 8085 | http://localhost:8085/swagger-ui.html |

---

## 六、配置说明

### 数据库连接（各服务 application.yml）
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/burndown_db?currentSchema=pg_xxx
    username: postgres
    password: postgres
```

### AI 功能配置（ai-agent-service/application.yml）
```yaml
ai:
  enabled: true              # 开启 AI 功能
  base-url: https://api.openai.com/v1   # 或兼容 OpenAI 格式的端点
  api-key: sk-xxx
  chat-model: gpt-4
  timeout-seconds: 30
  max-tokens: 2048
```

### Nacos 配置
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
```

---

## 七、故障排除

### api-gateway 启动失败：无法获取公钥
- 确认 auth-service 已启动且健康：`curl http://localhost:8081/api/v1/auth/public-key`
- Gateway 会重试 10 次（每次间隔 3 秒），共等待约 30 秒

### 服务无法注册到 Nacos
- 确认 Nacos 运行：`curl http://localhost:8848/nacos`
- 检查 `spring.cloud.nacos.discovery.server-addr` 配置

### RabbitMQ 连接失败
- 确认 RabbitMQ 运行：`rabbitmq-diagnostics status`
- 检查 `spring.rabbitmq.host/port/username/password` 配置

### 数据库 Schema 不存在
```bash
# 重新初始化
psql -U postgres -d burndown_db -f sql/init-all.sql
```

### 熔断器触发
- 查看 Actuator 端点：`http://localhost:{port}/actuator/health`
- Resilience4j 默认配置：失败率 > 50% 时触发熔断，等待 10 秒后半开

---

## 八、项目结构

```
burndown-backend-ms/
├── pom.xml                  # 父 POM
├── common/                  # 公共模块：DTO、异常、JWT工具、UserContext
├── api-gateway/             # Spring Cloud Gateway：路由、JWT验签
├── auth-service/            # 认证服务：登录、注册、JWT签发
├── project-service/         # 项目服务：Project、Sprint 管理
├── task-service/            # 任务服务：Task、WorkLog，发布 MQ 事件
├── burndown-service/        # 燃尽图服务：消费 MQ 事件，计算燃尽数据
├── ai-agent-service/        # AI 服务：任务描述生成、智能对话
└── sql/                     # 数据库初始化脚本
    ├── init-auth.sql
    ├── init-project.sql
    ├── init-task.sql
    ├── init-burndown.sql
    ├── init-ai.sql
    └── init-all.sql         # 一键初始化
```
