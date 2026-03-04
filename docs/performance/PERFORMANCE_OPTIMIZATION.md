# 后端性能优化方案

## 📊 压测数据分析

### 关键性能指标

| 场景 | 并发用户 | 登录接口 | 项目列表 | 任务列表 | 总RPS |
|------|---------|---------|---------|---------|-------|
| **Baseline** | 5 | 133ms | 17ms | 49ms | 29.41 |
| **Standard** | 20 | 655ms (↑391%) | 76ms (↑347%) | 244ms (↑394%) | 45.13 |
| **Peak** | 50 | 1578ms (↑1083%) | 459ms (↑2594%) | 546ms (↑1001%) | 46.99 |

### 核心问题

1. **响应时间随并发数急剧增长**：从5用户到50用户，响应时间增长10-25倍
2. **RPS增长停滞**：从20用户到50用户，RPS几乎没有增长（45.13 → 46.99）
3. **P95响应时间过高**：峰值场景下登录接口P95达到2469ms

---

## 🔴 高优先级优化（预期改进50-70%）

### 1. 解决权限加载的N+1查询问题

**问题代码**：
```java
// AuthService.java
public Set<String> getUserPermissions(Long userId) {
    List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
    List<Role> roles = roleRepository.findAllById(roleIds);

    Set<String> permissions = new HashSet<>();
    for (Role role : roles) {
        for (Permission permission : role.getPermissions()) {  // N+1查询
            permissions.add(permission.getCode());
        }
    }
    return permissions;
}
```

**优化方案**：

```java
// UserRoleRepository.java - 添加自定义查询
@Query("SELECT DISTINCT p.code FROM User u " +
       "JOIN u.roles r " +
       "JOIN r.permissions p " +
       "WHERE u.id = :userId")
Set<String> findPermissionCodesByUserId(@Param("userId") Long userId);

// AuthService.java - 使用优化后的查询
public Set<String> getUserPermissions(Long userId) {
    return userRoleRepository.findPermissionCodesByUserId(userId);
}
```

**预期效果**：
- 查询次数：从 3 + N 次减少到 1 次
- 登录响应时间：1578ms → 200-300ms（减少80%）

---

### 2. 实现Redis缓存策略

**当前问题**：Redis已配置但完全未使用

**优化方案**：

#### 2.1 启用Spring Cache

```java
// Application.java
@SpringBootApplication
@EnableCaching  // 添加此注解
public class Application {
    // ...
}
```

#### 2.2 配置Redis缓存管理器

```java
// config/CacheConfig.java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))  // 默认10分钟过期
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 权限缓存 - 30分钟
        cacheConfigurations.put("permissions", config.entryTtl(Duration.ofMinutes(30)));

        // 角色缓存 - 30分钟
        cacheConfigurations.put("roles", config.entryTtl(Duration.ofMinutes(30)));

        // 项目列表缓存 - 5分钟
        cacheConfigurations.put("projects", config.entryTtl(Duration.ofMinutes(5)));

        // 用户信息缓存 - 15分钟
        cacheConfigurations.put("users", config.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
```

#### 2.3 添加缓存注解

```java
// UserRoleService.java
@Cacheable(value = "permissions", key = "#userId")
public Set<String> getUserPermissions(Long userId) {
    return userRoleRepository.findPermissionCodesByUserId(userId);
}

@CacheEvict(value = "permissions", key = "#userId")
public void clearUserPermissionsCache(Long userId) {
    // 当用户角色变更时清除缓存
}

// ProjectService.java
@Cacheable(value = "projects", key = "'all'")
public List<Project> getAllProjects() {
    return projectRepository.findAll();
}

@Cacheable(value = "projects", key = "'owner:' + #ownerId")
public List<Project> getProjectsByOwner(Long ownerId) {
    return projectRepository.findByOwnerId(ownerId);
}

@CacheEvict(value = "projects", allEntries = true)
public Project createProject(Project project) {
    return projectRepository.save(project);
}

// UserService.java
@Cacheable(value = "users", key = "#username")
public User getUserByUsername(String username) {
    return userRepository.findByUsername(username)
        .orElseThrow(() -> new RuntimeException("User not found"));
}
```

**预期效果**：
- 权限查询：首次200ms，后续 < 5ms（缓存命中）
- 项目列表：首次459ms，后续 < 10ms
- 整体响应时间减少：50-70%

---

### 3. 优化数据库连接池配置

**当前配置**：
```yaml
hikari:
  maximum-pool-size: 20
  minimum-idle: 5
  connection-timeout: 30000
```

**优化配置**：
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # 增加到50
      minimum-idle: 20       # 增加到20，减少冷启动延迟
      connection-timeout: 30000
      idle-timeout: 600000   # 10分钟
      max-lifetime: 1800000  # 30分钟
      connection-test-query: SELECT 1  # 连接验证
      leak-detection-threshold: 60000  # 连接泄漏检测
```

**计算依据**：
- 峰值并发：50用户
- 每个请求平均持有连接时间：~100ms
- 推荐连接池大小：并发数 × 1.5 = 75（取50为安全值）

**预期效果**：
- 减少连接等待时间
- 提高并发处理能力
- RPS提升：46.99 → 60-70

---

## 🟠 中优先级优化（预期改进20-30%）

### 4. 实现分页和查询优化

#### 4.1 项目列表分页

```java
// ProjectController.java
@GetMapping
public ResponseEntity<Page<Project>> getAllProjects(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "id,desc") String sort
) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    return ResponseEntity.ok(projectService.getAllProjects(pageable));
}

// ProjectService.java
@Cacheable(value = "projects", key = "'page:' + #pageable.pageNumber + ':' + #pageable.pageSize")
public Page<Project> getAllProjects(Pageable pageable) {
    return projectRepository.findAll(pageable);
}
```

#### 4.2 任务列表分页和优化

```java
// TaskController.java
@GetMapping("/project/{projectId}")
public ResponseEntity<Page<Task>> getTasksByProject(
    @PathVariable Long projectId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size
) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    return ResponseEntity.ok(taskService.getTasksByProject(projectId, pageable));
}

// TaskService.java
public Page<Task> getTasksByProject(Long projectId, Pageable pageable) {
    return taskRepository.findByProjectId(projectId, pageable);
}

// TaskRepository.java
Page<Task> findByProjectId(Long projectId, Pageable pageable);
```

**预期效果**：
- 任务列表响应时间：546ms → 100-150ms（减少70%）
- 减少网络传输数据量
- 提升前端渲染性能

---

### 5. 优化TaskKey生成逻辑

**当前问题**：每次创建任务都执行复杂的SQL查询

**优化方案**：

```java
// TaskService.java
@Transactional
public Task createTask(Task task) {
    // 使用数据库序列或Redis计数器
    String taskKey = generateTaskKeyOptimized(task.getProjectId());
    task.setTaskKey(taskKey);
    return taskRepository.save(task);
}

private String generateTaskKeyOptimized(Long projectId) {
    // 方案1：使用Redis原子递增
    String redisKey = "task:counter:" + projectId;
    Long counter = redisTemplate.opsForValue().increment(redisKey);

    Project project = projectRepository.findById(projectId)
        .orElseThrow(() -> new RuntimeException("Project not found"));

    return project.getProjectKey() + "-" + counter;
}

// 或方案2：使用数据库序列（PostgreSQL）
@Query(value = "SELECT nextval('task_seq_' || :projectId)", nativeQuery = true)
Long getNextTaskNumber(@Param("projectId") Long projectId);
```

**预期效果**：
- 创建任务性能提升：减少1次复杂SQL查询
- 支持高并发创建任务

---

### 6. 添加数据库索引

```sql
-- 用户表索引
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

-- 项目表索引
CREATE INDEX idx_projects_owner_id ON projects(owner_id);
CREATE INDEX idx_projects_key ON projects(project_key);

-- 任务表索引
CREATE INDEX idx_tasks_project_id ON tasks(project_id);
CREATE INDEX idx_tasks_sprint_id ON tasks(sprint_id);
CREATE INDEX idx_tasks_assignee_id ON tasks(assignee_id);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_created_at ON tasks(created_at DESC);
CREATE INDEX idx_tasks_task_key ON tasks(task_key);

-- 用户角色关联表索引
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- 角色权限关联表索引
CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- 复合索引
CREATE INDEX idx_tasks_project_status ON tasks(project_id, status);
CREATE INDEX idx_tasks_sprint_status ON tasks(sprint_id, status);
```

**预期效果**：
- 查询性能提升：30-50%
- 特别是 `findByProjectId`、`findByOwnerId` 等查询

---

## 🟡 低优先级优化（预期改进5-15%）

### 7. 异步处理非关键操作

```java
// config/AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}

// AuthService.java
@Async("taskExecutor")
public void updateLastLoginTime(Long userId) {
    User user = userRepository.findById(userId).orElse(null);
    if (user != null) {
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
    }
}

// 在login方法中异步调用
public LoginResponse login(LoginRequest request) {
    // ... 验证逻辑

    // 异步更新最后登录时间
    updateLastLoginTime(user.getId());

    // 立即返回响应
    return new LoginResponse(token, user.getId(), user.getUsername());
}
```

**预期效果**：
- 登录响应时间减少：10-20ms
- 提升用户体验

---

### 8. 优化JPA配置

```yaml
spring:
  jpa:
    show-sql: false  # 生产环境关闭
    properties:
      hibernate:
        format_sql: false  # 生产环境关闭
        jdbc:
          batch_size: 20  # 启用批量操作
          fetch_size: 50  # 优化查询性能
        order_inserts: true  # 优化插入顺序
        order_updates: true  # 优化更新顺序
        query:
          in_clause_parameter_padding: true  # 优化IN查询
```

**预期效果**：
- 减少日志I/O开销
- 批量操作性能提升

---

### 9. 实体关系优化

```java
// Role.java - 优化权限加载策略
@Entity
@Table(name = "roles")
public class Role {

    @ManyToMany(fetch = FetchType.EAGER)  // 改为EAGER，配合缓存使用
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @BatchSize(size = 10)  // 批量加载，减少查询次数
    private Set<Permission> permissions = new HashSet<>();
}

// User.java - 优化角色加载
@Entity
@Table(name = "users")
public class User {

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @BatchSize(size = 10)  // 批量加载
    private Set<Role> roles = new HashSet<>();
}
```

---

### 10. 添加监控和日志

```java
// config/PerformanceMonitorConfig.java
@Configuration
public class PerformanceMonitorConfig {

    @Bean
    public FilterRegistrationBean<PerformanceMonitorFilter> performanceMonitorFilter() {
        FilterRegistrationBean<PerformanceMonitorFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new PerformanceMonitorFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}

// filter/PerformanceMonitorFilter.java
public class PerformanceMonitorFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitorFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        long startTime = System.currentTimeMillis();

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String uri = httpRequest.getRequestURI();

            if (duration > 1000) {  // 记录超过1秒的请求
                logger.warn("Slow request: {} {} - {}ms",
                    httpRequest.getMethod(), uri, duration);
            }
        }
    }
}
```

---

## 📈 预期优化效果

### 优化前后对比

| 场景 | 优化前 | 优化后（预期） | 改进 |
|------|--------|---------------|------|
| **Baseline (5用户)** |
| 登录接口 | 133ms | 50-80ms | ↓60% |
| 项目列表 | 17ms | 5-10ms | ↓50% |
| 任务列表 | 49ms | 20-30ms | ↓50% |
| RPS | 29.41 | 40-50 | ↑50% |
| **Standard (20用户)** |
| 登录接口 | 655ms | 100-150ms | ↓77% |
| 项目列表 | 76ms | 15-25ms | ↓75% |
| 任务列表 | 244ms | 50-80ms | ↓75% |
| RPS | 45.13 | 80-100 | ↑100% |
| **Peak (50用户)** |
| 登录接口 | 1578ms | 200-300ms | ↓81% |
| 项目列表 | 459ms | 30-50ms | ↓90% |
| 任务列表 | 546ms | 80-120ms | ↓80% |
| RPS | 46.99 | 120-150 | ↑200% |

---

## 🚀 实施计划

### 第一阶段（1-2天）- 高优先级
1. ✅ 修复权限加载N+1查询
2. ✅ 实现Redis缓存
3. ✅ 优化数据库连接池

**预期改进**：响应时间减少60-70%

### 第二阶段（2-3天）- 中优先级
4. ✅ 实现分页功能
5. ✅ 优化TaskKey生成
6. ✅ 添加数据库索引

**预期改进**：响应时间再减少20-30%

### 第三阶段（1-2天）- 低优先级
7. ✅ 异步处理优化
8. ✅ JPA配置优化
9. ✅ 实体关系优化
10. ✅ 添加性能监控

**预期改进**：响应时间再减少5-15%

---

## 📝 验证方法

优化完成后，再次运行压力测试：

```bash
cd /myapp/test/pressure
python3 scenario_pressure_test.py
```

对比优化前后的报告：
- 查看 `summary_report.html` 对比历史数据
- 重点关注P95响应时间和RPS指标
- 确认峰值场景下成功率保持100%

---

## ⚠️ 注意事项

1. **缓存一致性**：更新数据时记得清除相关缓存
2. **连接池监控**：观察连接池使用情况，避免连接泄漏
3. **Redis内存**：监控Redis内存使用，设置合理的过期时间
4. **索引维护**：定期分析索引使用情况，删除无用索引
5. **渐进式部署**：先在测试环境验证，再逐步部署到生产环境

---

**生成时间**: 2026-03-02
**基于压测版本**: v5
**文档版本**: 1.0
