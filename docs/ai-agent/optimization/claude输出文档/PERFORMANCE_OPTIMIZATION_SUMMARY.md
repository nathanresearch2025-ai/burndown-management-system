# LangChain 站会查询性能优化总结

## 优化目标

- **P95 < 8s**（单用户、正常网络、无异常重试）
- **P99 < 15s**
- 能够快速失败或降级
- 可量化定位慢在哪里

## 已完成的优化

### 1. Python 侧优化（已完成）

#### 1.1 新增快速流水线 `run_fast_pipeline()`

**位置**: `backend/langchain-python/app/agents.py`

**关键改进**:
- 从 4 次 LLM 调用减少到 1 次（仅汇总阶段）
- 取消了 Planner、Data Agent (ReAct)、Analyst、Writer 的多轮调用
- 直接并发调用工具，然后一次性汇总

**流程对比**:

旧流程（legacy）:
```
Planner LLM → Data Agent (ReAct多轮) → Analyst LLM → Writer LLM
```

新流程（fast）:
```
并发调用3个工具 → Summarizer LLM（一次性输出结构化JSON）
```

#### 1.2 工具调用并发化

**位置**: `backend/langchain-python/app/tools.py`

**关键改进**:
- 使用 `httpx.AsyncClient` 替代 `requests`
- 全局连接池复用（50个连接，20个保活连接）
- 使用 `asyncio.gather()` 并发调用 3 个工具
- 工具调用时间从"串行累加"变为"取最大值"

**连接池配置**:
```python
httpx.AsyncClient(
    timeout=httpx.Timeout(connect=3.0, read=20.0, write=10.0, pool=3.0),
    limits=httpx.Limits(max_connections=50, max_keepalive_connections=20),
)
```

#### 1.3 LLM 客户端单例化

**位置**: `backend/langchain-python/app/llm.py`

**关键改进**:
- 使用 `@lru_cache(maxsize=1)` 实现进程级单例
- 避免每次调用都重新创建 LLM 客户端
- 减少初始化和连接握手开销

#### 1.4 降低 ReAct 开销（legacy 模式）

**位置**: `backend/langchain-python/app/agents.py`

**关键改进**:
- `verbose=False`（关闭详细日志）
- `max_iterations=3`（从 6 降到 3）
- 减少 token 消耗和回合数

#### 1.5 分段耗时统计

**位置**: `backend/langchain-python/app/agents.py`

**关键改进**:
- 记录 `tools_ms_total`（工具调用总耗时）
- 记录 `llm_summarize_ms`（LLM 汇总耗时）
- 记录 `total_ms`（端到端总耗时）
- 通过 `traceId` 贯穿整个调用链

#### 1.6 配置开关

**位置**: `backend/langchain-python/.env`

**环境变量**:
```bash
STANDUP_PIPELINE_MODE=fast        # fast/legacy 切换
TOOLS_CONCURRENT=true             # 工具并发开关
LOG_STEP_TIMING=true              # 分段耗时日志开关
```

### 2. Spring Boot 后端优化（已完成）

#### 2.1 数据库查询优化

**位置**: `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupTaskTools.java`

**关键改进**:
- 使用 `findByProjectIdAndAssigneeIdAndStatus()` 精准查询
- 避免 `findByProjectId()` 后 Java stream 过滤
- 查询下推到数据库，减少内存占用和网络传输

**优化前**:
```java
List<Task> tasks = taskRepository.findByProjectId(projectId).stream()
    .filter(task -> task.getAssigneeId().equals(userId) &&
                    task.getStatus() == IN_PROGRESS)
    .collect(Collectors.toList());
```

**优化后**:
```java
List<Task> tasks = taskRepository.findByProjectIdAndAssigneeIdAndStatus(
    projectId, userId, Task.TaskStatus.IN_PROGRESS
);
```

#### 2.2 Repository 方法新增

**位置**: `backend/src/main/java/com/burndown/repository/TaskRepository.java`

**新增方法**:
```java
List<Task> findByProjectIdAndAssigneeIdAndStatus(
    Long projectId, Long assigneeId, Task.TaskStatus status
);
```

#### 2.3 详细日志和 traceId 支持

**位置**: `backend/src/main/java/com/burndown/aiagent/standup/controller/LangchainToolController.java`

**关键改进**:
- 添加 `@Slf4j` 注解
- 记录请求参数、处理参数、响应内容
- 支持 `X-Trace-Id` header 透传

### 3. 接口兼容性（已完成）

#### 3.1 支持 GET 和 POST 请求

**位置**: `backend/src/main/java/com/burndown/aiagent/standup/controller/LangchainToolController.java`

**关键改进**:
```java
@RequestMapping(value = "/in-progress-tasks",
                method = {RequestMethod.GET, RequestMethod.POST})
public String inProgressTasks(
    @RequestBody(required = false) LangchainToolRequest request,
    @RequestParam(required = false) Long projectId,
    @RequestParam(required = false) Long userId
)
```

#### 3.2 响应格式保持兼容

**位置**: `backend/langchain-python/app/main.py`

**关键改进**:
- 新旧模式返回相同的响应结构
- `StandupQueryResponse` 字段保持不变
- 通过环境变量切换，无需修改调用方

## 性能提升预期

### 理论分析

**优化前**:
```
总耗时 ≈ 4×LLM RTT + 3×工具 HTTP RTT + ReAct 多轮 + DB 查询
```

**优化后**:
```
总耗时 ≈ 1×LLM RTT + max(3个工具 HTTP RTT) + DB 查询
```

### 关键指标改善

1. **LLM 调用次数**: 4 次 → 1 次（减少 75%）
2. **工具调用耗时**: 串行累加 → 并发取最大值（理论减少 60-70%）
3. **DB 查询效率**: 全量扫描 → 精准查询（减少数据传输和内存占用）
4. **连接复用**: 每次新建 → 连接池复用（减少握手开销）

### 预期性能

假设单次 LLM 调用 2-3s，单个工具调用 0.5-1s：

**优化前**:
```
4×2.5s + 3×0.8s = 10s + 2.4s = 12.4s
```

**优化后**:
```
1×2.5s + max(0.8s, 0.8s, 0.8s) = 2.5s + 0.8s = 3.3s
```

**理论提升**: 约 73% 的性能提升

## 可观测性增强

### 1. 分段耗时日志

**Python 侧**:
```
[TIMING] traceId=xxx tools_ms_total=800
[TIMING] traceId=xxx llm_summarize_ms=2500 total_ms=3300
```

**Spring 侧**:
```
=== [LangchainToolController] /in-progress-tasks called ===
Request body: {...}
Final params - projectId: 1, userId: 1
Response: 当前没有进行中的任务
=== [LangchainToolController] /in-progress-tasks completed ===
```

### 2. TraceId 贯穿

- 从 Spring 传递到 Python
- 从 Python 传递到工具调用
- 通过 `X-Trace-Id` header 透传
- 所有日志包含 traceId

## 回滚策略

### 快速回滚到旧模式

修改 `.env` 文件:
```bash
STANDUP_PIPELINE_MODE=legacy      # 切换到旧流程
TOOLS_CONCURRENT=false            # 关闭并发
LOG_STEP_TIMING=false             # 关闭耗时日志
```

重启服务即可生效，无需修改代码。

## 验证方法

### 1. 黑盒压测

```bash
# 使用 Postman 或 curl 压测
for i in {1..100}; do
  curl -X POST http://localhost:8091/agent/standup/query \
    -H "Content-Type: application/json" \
    -d '{"question":"今天的站会总结","projectId":1,"sprintId":1,"userId":1}'
done
```

记录 P50、P95、P99 耗时。

### 2. 白盒分段耗时

查看日志中的 `[TIMING]` 输出：
- `tools_ms_total`: 工具调用总耗时
- `llm_summarize_ms`: LLM 汇总耗时
- `total_ms`: 端到端总耗时

### 3. 对比测试

分别测试 `fast` 和 `legacy` 模式，对比性能差异。

## 后续优化建议

### P1 优化（可选）

1. **工具结果缓存**
   - 使用 Redis 或 Caffeine
   - TTL: 10-60s
   - Key: `(projectId, sprintId, userId)`

2. **Spring 侧超时优化**
   - 调整 `langchain.timeout` 从 120s 到 30s
   - 添加 Micrometer 指标
   - 快速失败和降级

3. **LLM 请求超时**
   - 设置 LLM API 超时
   - 添加重试策略（指数退避）

### P2 优化（可选）

1. **请求去重**
   - 相同参数的并发请求合并
   - 减少重复计算

2. **流式响应**
   - LLM 流式输出
   - 提升用户体验

## 文件清单

### Python 文件
- `backend/langchain-python/app/agents.py` - 新增 fast pipeline
- `backend/langchain-python/app/tools.py` - 异步化 + 连接池
- `backend/langchain-python/app/llm.py` - LLM 单例化
- `backend/langchain-python/app/main.py` - 异步化 + 日志
- `backend/langchain-python/.env` - 配置开关

### Spring Boot 文件
- `backend/src/main/java/com/burndown/repository/TaskRepository.java` - 新增查询方法
- `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupTaskTools.java` - 优化查询
- `backend/src/main/java/com/burndown/aiagent/standup/controller/LangchainToolController.java` - 日志增强

## 总结

本次优化通过以下手段显著提升了性能：

1. **减少 LLM 调用次数**（4 → 1）
2. **工具调用并发化**（串行 → 并发）
3. **连接池复用**（每次新建 → 全局复用）
4. **数据库查询优化**（全量扫描 → 精准查询）
5. **LLM 客户端单例化**（每次创建 → 进程级复用）

理论性能提升约 **70-80%**，实际效果需要通过压测验证。

优化保持了接口兼容性，支持快速回滚，具备良好的可观测性。
