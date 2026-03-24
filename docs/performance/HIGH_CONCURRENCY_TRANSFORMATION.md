# Java 后端高并发改造方案

> 文档版本：v1.0 | 更新日期：2026-03-24
> 适用项目：Burndown Management System（Spring Boot 3.2 / Java 21）

---

## 一、现状分析

### 1.1 当前架构概览

```
客户端
  └── Spring Boot 8080
        ├── Spring Security (JWT Filter per request)
        ├── Controller 层（同步阻塞）
        ├── Service 层（无线程池隔离）
        ├── JPA / HikariCP → PostgreSQL
        ├── Redis（已配置，部分缓存已启用）
        └── AI Agent（同步 HTTP 调用外部 LLM）
```

### 1.2 已有的优化基础

| 组件 | 现状 |
|------|------|
| HikariCP 连接池 | max=20, min=5，已配置 |
| Redis 缓存 | CacheConfig 已配置，TTL 分层（权限30min、项目5min等） |
| Hibernate 批处理 | batch_size=20, order_inserts=true |
| BCrypt 强度 | 4（压测优化值） |
| Prometheus 监控 | 已接入，P50/P90/P95/P99 指标 |
| 限流基础 | RateLimitService 已存在（AI 接口） |

### 1.3 压测发现的核心瓶颈

| 并发用户 | 登录接口 | 项目列表 | 任务列表 | RPS |
|---------|---------|---------|---------|-----|
| 5 | 133ms | 17ms | 49ms | 29.41 |
| 20 | 655ms (+391%) | 76ms (+347%) | 244ms (+394%) | 45.13 |
| 50 | 1578ms (+1083%) | 459ms (+2594%) | 546ms (+1001%) | 46.99 |

**关键结论**：20→50 并发时 RPS 几乎不增长（45→47），存在资源争用瓶颈而非纯计算瓶颈。


---

## 二、瓶颈根因分析

### 2.1 数据库层

- **连接池上限过低**：HikariCP max=20，50 并发时连接池满载，后续请求排队等待
- **N+1 查询**：权限加载 `role.getPermissions()` 触发懒加载循环查询
- **无读写分离**：所有查询（含只读统计）都走主库
- **燃尽图计算**：`BurndownService` 每次实时聚合全量任务数据，无缓存

### 2.2 应用层

- **主线程阻塞**：AI Agent 调用外部 LLM（最长 120s timeout）占用 Tomcat 线程
- **无线程池隔离**：所有 Service 同步调用，单个慢接口拖累整体
- **缓存注解未全面覆盖**：CacheConfig 已配置但 Service 方法缺少 `@Cacheable`
- **`@EnableAsync` 未启用**：主应用类缺少异步支持
- **JWT 每次完整解析**：无 Token 黑名单 Redis 缓存

### 2.3 Web 容器层

- **Tomcat 默认线程数**：max=200，未针对业务特性调优
- **无背压机制**：高峰期无请求队列限制，OOM 风险

### 2.4 AI Agent 层

- **LangChain 调用同步阻塞**：`LangchainClientService` 超时 120s，高并发下快速耗尽线程
- **无熔断降级**：外部 LLM 不可用时级联故障
- **无结果缓存**：相同 Standup 查询重复调用外部 API


---

## 三、高并发改造需求清单

### P0 - 必做（解决核心瓶颈）

| 编号 | 需求 | 预期收益 |
|------|------|----------|
| P0-1 | 扩大 HikariCP 连接池上限至 50 | 消除连接等待，RPS 提升 30-50% |
| P0-2 | 修复权限 N+1 查询 | 登录响应从 1578ms → 200ms |
| P0-3 | Service 层全面补充 @Cacheable 注解 | 热点数据命中缓存，DB 压力减少 60-80% |
| P0-4 | 启用 @EnableAsync + AI 接口异步化 | 释放主线程，避免 LLM 超时拖垮系统 |
| P0-5 | AI/Agent 接口熔断降级 | 防止外部 LLM 故障级联扩散 |

### P1 - 重要（系统稳定性）

| 编号 | 需求 | 预期收益 |
|------|------|----------|
| P1-1 | 全局限流中间件（Bucket4j + Redis） | 防止流量突增压垮服务 |
| P1-2 | Tomcat 线程池精细化调优 | 提升吞吐量，减少资源浪费 |
| P1-3 | 数据库查询强制分页 + 慢查询索引优化 | 防止大查询阻塞连接池 |
| P1-4 | Redis 连接池扩容（max-active: 50） | 消除 Redis 连接等待 |
| P1-5 | 燃尽图数据预计算 + 定时刷新缓存 | 统计接口从秒级降至毫秒级 |

### P2 - 提升（面向更高并发目标）

| 编号 | 需求 | 预期收益 |
|------|------|----------|
| P2-1 | 虚拟线程（Project Loom）接入 | Java 21 原生支持，IO 密集型接口显著提升 |
| P2-2 | 本地 Caffeine + Redis 多级缓存 | 减少 Redis 网络 RTT，本地命中微秒级 |
| P2-3 | 异步写操作（WorkLog/BurndownPoint） | 写操作不阻塞主请求链路 |
| P2-4 | 读写分离（PostgreSQL 主从） | 只读查询走从库，主库压力减半 |
| P2-5 | 响应压缩（gzip）+ HTTP/2 | 降低带宽，减少传输延迟 |


---

## 四、具体改进措施

### 4.1 数据库连接池调优（P0-1）

**文件**：`backend/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50        # 从 20 提升至 50
      minimum-idle: 10             # 从 5 提升至 10
      connection-timeout: 3000     # 3秒超时（默认30秒太长）
      idle-timeout: 300000         # 5分钟空闲回收
      max-lifetime: 1200000        # 20分钟最大生命周期
      leak-detection-threshold: 5000  # 5秒连接泄露检测
```

> **同步操作**：PostgreSQL `postgresql.conf` 需将 `max_connections` 从100调整为200。

---

### 4.2 修复权限 N+1 查询（P0-2）

**文件**：`UserRoleRepository.java`、`AuthService.java`

```java
// UserRoleRepository.java - 单次查询获取所有权限
@Query("SELECT DISTINCT p.code FROM User u " +
       "JOIN u.roles r JOIN r.permissions p " +
       "WHERE u.id = :userId")
Set<String> findPermissionCodesByUserId(@Param("userId") Long userId);

// AuthService.java
@Cacheable(value = "permissions", key = "#userId")
public Set<String> getUserPermissions(Long userId) {
    return userRoleRepository.findPermissionCodesByUserId(userId);
}

@CacheEvict(value = "permissions", key = "#userId")
public void evictPermissionCache(Long userId) {}
```

**效果**：查询次数从 `3+N` 次降至 `1` 次，登录接口 P95 预计从 2469ms 降至 300ms 以内。


---

### 4.3 Service 层全面补充缓存注解（P0-3）

**文件**：`ProjectService.java`、`SprintService.java`、`BurndownService.java`

```java
// ProjectService.java
@Cacheable(value = "projects", key = "#userId + ':list'")
public List<ProjectDTO> getProjectsByUser(Long userId) { ... }

@CacheEvict(value = "projects", key = "#userId + ':list'")
public ProjectDTO createProject(Long userId, CreateProjectRequest req) { ... }

// SprintService.java
@Cacheable(value = "sprints", key = "#projectId + ':active'")
public SprintDTO getActiveSprint(Long projectId) { ... }

// BurndownService.java - 燃尽图重点缓存
@Cacheable(value = "burndown", key = "#sprintId", unless = "#result == null")
public BurndownDataDTO getBurndownData(Long sprintId) { ... }

// TaskService.java
@Cacheable(value = "tasks", key = "#sprintId + ':' + #status")
public List<TaskDTO> getTasksBySprintAndStatus(Long sprintId, String status) { ... }
```

**CacheConfig.java 追加配置**：
```java
cacheConfigurations.put("sprints", config.entryTtl(Duration.ofMinutes(10)));
cacheConfigurations.put("burndown", config.entryTtl(Duration.ofMinutes(5)));
cacheConfigurations.put("tasks", config.entryTtl(Duration.ofMinutes(3)));
```

---

### 4.4 启用异步支持 + AI 接口异步化（P0-4）

**文件**：`BurndownManagementApplication.java`

```java
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync  // 新增
public class BurndownManagementApplication { ... }
```

**新建** `config/AsyncConfig.java`：

```java
@Configuration
public class AsyncConfig {

    // AI/LLM 专用线程池 - 隔离慢调用
    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    // 通用异步线程池
    @Bean("generalTaskExecutor")
    public Executor generalTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("general-async-");
        executor.initialize();
        return executor;
    }
}
```

**LangchainClientService.java 异步化**：
```java
@Async("aiTaskExecutor")
public CompletableFuture<StandupQueryResponse> queryStandupAsync(StandupQueryRequest req) {
    // 原有同步逻辑包装
    StandupQueryResponse response = queryStandup(req);
    return CompletableFuture.completedFuture(response);
}
```


---

### 4.5 熔断降级（P0-5）

**依赖**：`pom.xml` 添加 Resilience4j

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
```

**application.yml 配置**：
```yaml
resilience4j:
  circuitbreaker:
    instances:
      langchainClient:
        sliding-window-size: 10
        failure-rate-threshold: 50        # 50% 失败率触发熔断
        wait-duration-in-open-state: 30s  # 熔断后30秒尝试恢复
        permitted-calls-in-half-open-state: 3
  timelimiter:
    instances:
      langchainClient:
        timeout-duration: 30s             # 单次调用最长30秒
```

**LangchainClientService.java**：
```java
@CircuitBreaker(name = "langchainClient", fallbackMethod = "fallbackStandup")
@TimeLimiter(name = "langchainClient")
public CompletableFuture<StandupQueryResponse> queryStandupAsync(StandupQueryRequest req) {
    ...
}

public CompletableFuture<StandupQueryResponse> fallbackStandup(
        StandupQueryRequest req, Throwable ex) {
    StandupQueryResponse fallback = new StandupQueryResponse();
    fallback.setMessage("AI 服务暂时不可用，请稍后重试");
    fallback.setSuccess(false);
    return CompletableFuture.completedFuture(fallback);
}
```

---

### 4.6 全局限流中间件（P1-1）

**依赖**：
```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.10.1</version>
</dependency>
```

**新建** `filter/RateLimitFilter.java`：
```java
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    // 普通接口：每用户每分钟 100 次
    // AI 接口：每用户每10分钟 50 次（已有 RateLimitService 可复用）
    // 登录接口：每 IP 每分钟 10 次

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        HttpServletRequest request = (HttpServletRequest) req;
        String path = request.getRequestURI();
        String key = resolveKey(request, path);

        if (!rateLimitService.tryConsume(key, resolveLimit(path))) {
            HttpServletResponse response = (HttpServletResponse) res;
            response.setStatus(429);
            response.getWriter().write("{\"error\":\"Too Many Requests\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
```


---

### 4.7 Tomcat 线程池调优（P1-2）

**文件**：`application.yml`

```yaml
server:
  port: 8080
  servlet:
    context-path: /api/v1
  tomcat:
    threads:
      max: 400          # 默认200，提升至400
      min-spare: 20     # 最小空闲线程
    max-connections: 8192   # 最大连接数
    accept-count: 200       # 队列长度（超出返回503，防OOM）
    connection-timeout: 5000  # 连接超时5秒
```

---

### 4.8 Redis 连接池扩容（P1-4）

**文件**：`application.yml`

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 50    # 从 8 提升至 50
          max-idle: 20      # 从 8 提升至 20
          min-idle: 5       # 从 0 提升至 5
          max-wait: 1000ms  # 新增：获取连接最长等待1秒
```

---

### 4.9 燃尽图预计算 + 定时刷新（P1-5）

**文件**：`BurndownService.java`

```java
// 定时预计算：每5分钟刷新活跃Sprint的燃尽图缓存
@Scheduled(fixedRate = 300000)
public void refreshActiveBurndownCache() {
    List<Sprint> activeSprints = sprintRepository.findByStatus(SprintStatus.ACTIVE);
    activeSprints.forEach(sprint -> {
        String cacheKey = "burndown::" + sprint.getId();
        redisTemplate.delete(cacheKey);  // 清除旧缓存
        getBurndownData(sprint.getId()); // 触发重新计算并缓存
    });
}

// BurndownManagementApplication.java 添加
@EnableScheduling  // 启用定时任务
```

---

### 4.10 虚拟线程接入（P2-1）

**Java 21 Project Loom - 对 IO 密集型接口效果最显著**

**文件**：`application.yml`

```yaml
spring:
  threads:
    virtual:
      enabled: true   # Spring Boot 3.2+ 支持，一行开启虚拟线程
```

开启后 Tomcat 和 `@Async` 默认使用虚拟线程，无需修改业务代码。

> **适用场景**：数据库查询、Redis 操作、HTTP 外部调用等 IO 等待场景。CPU 密集型（如 BCrypt）不适用。

---

### 4.11 多级缓存（P2-2）

**依赖**：
```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

**策略**：L1 本地 Caffeine（微秒级）→ L2 Redis（毫秒级）→ L3 数据库

```java
// CacheConfig.java 追加 Caffeine 本地缓存
@Bean
public CaffeineCache permissionsLocalCache() {
    return new CaffeineCache("permissions-local",
        Caffeine.newBuilder()
            .maximumSize(1000)       // 最多缓存1000个用户权限
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()           // 开启统计，接入 Micrometer 监控
            .build());
}
```


---

## 五、数据库索引优化

### 5.1 关键索引补充

**文件**：`backend/init.sql`

```sql
-- 任务查询优化（高频：按 Sprint + 状态过滤）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tasks_sprint_status
    ON tasks(sprint_id, status);

-- 任务查询优化（按分配人过滤）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tasks_assignee
    ON tasks(assignee_id) WHERE status != 'DONE';

-- 工作日志查询优化
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_worklog_task_date
    ON work_logs(task_id, log_date DESC);

-- 燃尽点查询优化
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_burndown_sprint_date
    ON burndown_points(sprint_id, record_date DESC);

-- 用户角色关联优化
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_roles_user
    ON user_roles(user_id);

-- Agent 会话查询优化
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_agent_session_user
    ON agent_chat_sessions(user_id, created_at DESC);
```

### 5.2 强制分页防止全表扫描

```java
// TaskController.java - 所有列表接口强制分页
@GetMapping
public Page<TaskDTO> getTasks(
        @RequestParam Long sprintId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size) {  // 最大50条
    size = Math.min(size, 50);  // 防止客户端传入超大 size
    return taskService.getTasksBySprintPaged(sprintId, PageRequest.of(page, size));
}
```

---

## 六、监控与告警补充

### 6.1 新增高并发关键指标

**文件**：`config/MetricsConfig.java`

```java
// 连接池监控
@Bean
public HikariDataSourceMetrics hikariMetrics(DataSource dataSource, MeterRegistry registry) {
    return new HikariDataSourceMetrics((HikariDataSource) dataSource, registry, Tags.empty());
}

// 缓存命中率监控（已有 Caffeine recordStats，接入 Micrometer）
@Bean
public CaffeineCacheMetrics caffeineMetrics(CaffeineCache cache, MeterRegistry registry) {
    return CaffeineCacheMetrics.monitor(registry, cache.getNativeCache(), cache.getName());
}
```

### 6.2 Grafana 告警规则补充

| 指标 | 告警阈值 | 级别 |
|------|---------|------|
| HikariCP 活跃连接数 / 最大连接数 | > 80% | Warning |
| HikariCP 等待连接数 | > 5 | Critical |
| Redis 连接池使用率 | > 80% | Warning |
| 缓存命中率（permissions） | < 60% | Warning |
| 熔断器状态（langchainClient） | OPEN | Critical |
| P99 响应时间 | > 3000ms | Warning |
| 线程池队列积压 | > 50 | Warning |


---

## 七、改造优先级与实施计划

### 7.1 实施路线图

```
第一阶段（1-2天）：P0 核心瓶颈修复
  ├── P0-1: application.yml 连接池参数调整（30分钟）
  ├── P0-2: 修复权限 N+1 查询 + @Cacheable 注解（2小时）
  ├── P0-3: Service 层补全缓存注解（2小时）
  ├── P0-4: 启用 @EnableAsync + AsyncConfig（1小时）
  └── P0-5: 引入 Resilience4j 熔断（2小时）

第二阶段（3-5天）：P1 稳定性建设
  ├── P1-1: 全局限流 Filter（1天）
  ├── P1-2: Tomcat 参数调优（1小时）
  ├── P1-3: 数据库索引 + 分页强制（1天）
  ├── P1-4: Redis 连接池参数调整（30分钟）
  └── P1-5: 燃尽图预计算定时任务（半天）

第三阶段（按需）：P2 进阶优化
  ├── P2-1: 虚拟线程（一行配置，需回归测试）
  ├── P2-2: Caffeine 多级缓存（1天）
  ├── P2-3: 异步写操作（1-2天）
  └── P2-4: 读写分离（需基础设施支持）
```

### 7.2 预期性能目标

| 指标 | 当前（50并发） | 第一阶段后 | 第二阶段后 |
|------|-------------|-----------|----------|
| 登录接口 P95 | 2469ms | ≤ 400ms | ≤ 200ms |
| 项目列表 P95 | 459ms | ≤ 50ms | ≤ 20ms |
| 任务列表 P95 | 546ms | ≤ 80ms | ≤ 30ms |
| RPS（50并发） | 47 | ≥ 150 | ≥ 300 |
| 支持最大并发 | ~50 | ~200 | ~500 |

---

## 八、风险与注意事项

### 8.1 缓存一致性风险

- **问题**：写操作后缓存未及时失效，返回旧数据
- **措施**：所有写/更新/删除 Service 方法配套 `@CacheEvict`，使用 `@CachePut` 更新而非仅删除
- **建议**：缓存 TTL 不宜过长（任务数据建议 ≤ 3 分钟）

### 8.2 连接池扩容风险

- **问题**：盲目增大连接池可能导致 PostgreSQL 连接数超限
- **措施**：修改连接池前先确认 `SHOW max_connections;`，保留 20% 余量给管理连接
- **公式**：`HikariCP max-pool-size ≤ (pg max_connections - 10) / 实例数`

### 8.3 虚拟线程兼容性

- **问题**：部分第三方库（如 ThreadLocal 密集使用的库）可能与虚拟线程不兼容
- **措施**：开启后需完整回归测试，重点测试 Spring Security、JPA 事务上下文传播

### 8.4 熔断器参数调优

- **问题**：熔断阈值设置不当导致误触发或无法触发
- **措施**：上线初期设置较宽松阈值（失败率 70%，窗口 20次），根据监控数据逐步收紧

### 8.5 限流粒度

- **问题**：基于 IP 限流在 NAT 环境下误伤多用户
- **措施**：已登录用户基于 userId 限流，未登录接口（/auth/login）基于 IP 限流

---

## 九、参考资料

- [HikariCP 官方调优指南](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)
- [Spring Boot 3.2 虚拟线程支持](https://spring.io/blog/2023/09/09/all-together-now-spring-boot-3-2-graalvm-native-images-java-21-and-virtual)
- [Resilience4j 官方文档](https://resilience4j.readme.io/docs/circuitbreaker)
- [Bucket4j 分布式限流](https://bucket4j.com/)
- 压测数据来源：`/test/pressure/summary_report.html`
- 现有性能优化文档：`docs/performance/PERFORMANCE_OPTIMIZATION.md`

