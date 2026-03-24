# Agent-to-Agent (A2A) 通信需求文档

## 1. 概述

### 1.1 背景

当前 Burndown Management System 的 LangGraph v2 架构采用 Supervisor 顺序编排模式：

```
Supervisor → DataAgent → Supervisor → AnalystAgent → Supervisor → WriterAgent → END
```

所有 Agent 通过共享 `state` 字典传递数据，Agent 之间**不直接通信**。

### 1.2 当前架构限制

- **被动执行**：Agent 只能等待 Supervisor 调度，无法主动请求其他 Agent
- **数据不足无法补救**：AnalystAgent 发现数据缺失时，只能输出 `UNKNOWN`，无法回调 DataAgent 补充
- **异常无法验证**：DataAgent 发现数据异常时，无法请求 AnalystAgent 验证，只能盲目传递给下游

### 1.3 A2A 定义

Agent-to-Agent (A2A) 通信指：
- Agent 可以**直接调用**其他 Agent 的函数
- Agent 可以**主动请求**其他 Agent 提供服务
- Agent 之间可以**协商、验证、补充数据**

---

## 2. 业务场景

### 2.1 场景1：DataAgent 发现异常，主动请求 AnalystAgent 验证

#### 触发条件

DataAgent 调用 `get_sprint_burndown` 后检测到：
- 最近 3 天 `actual_remaining` 完全相同（进度停滞）
- `actual_remaining` 突然暴增 50% 以上（可能数据错误）
- `ideal_remaining` 为负数（数据异常）

#### 当前行为（无 A2A）

```python
# DataAgent 只能记录异常，无法验证
state["data_summary"] = f"燃尽图数据：{burndown_data}"
state["warning"] = "检测到数据异常"
return state  # 传给 Supervisor，等待 AnalystAgent 被动分析
```

**问题**：AnalystAgent 可能基于错误数据做出错误判断。

#### 期望行为（A2A）

```python
# DataAgent 主动请求 AnalystAgent 验证
if detect_anomaly(burndown_data):
    verification_result = await self.call_agent(
        "analyst",
        {"question": "燃尽图最近3天无变化，是数据错误还是真实停滞？"}
    )

    if verification_result["conclusion"] == "DATA_ERROR":
        state["data_summary"] = "燃尽数据异常，已标记为不可信"
        state["error"] = "burndown_data_stale"
    else:
        state["data_summary"] = f"燃尽图数据（已验证）：{burndown_data}"
```

**收益**：
- 提前发现数据质量问题
- 避免错误数据污染下游分析
- 提升风险评估准确性

---

### 2.2 场景2：AnalystAgent 不确定，请求 DataAgent 补充数据

#### 触发条件

AnalystAgent 收到 DataAgent 的摘要后发现：
- 只有燃尽图数据，缺少任务列表（无法判断剩余工作分布）
- 只有任务列表，缺少燃尽趋势（无法判断整体进度）
- 数据时间范围不足（只有最近 3 天，无法看趋势）

#### 当前行为（无 A2A）

```python
# AnalystAgent 数据不足，只能输出 UNKNOWN
if "任务列表" not in data_summary:
    state["risk_level"] = "UNKNOWN"
    state["analysis"] = "数据不足，无法评估风险"
    return state
```

**问题**：用户得到无意义的 `UNKNOWN` 回复。

#### 期望行为（A2A）

```python
# AnalystAgent 主动请求 DataAgent 补充
if "任务列表" not in data_summary:
    logger.info("[AnalystAgent] 缺少任务数据，请求 DataAgent 补充")

    补充数据 = await self.call_agent(
        "data",
        {
            "tools_filter": ["get_in_progress_tasks"],
            "project_id": state["project_id"],
            "sprint_id": state["sprint_id"],
        }
    )

    state["data_summary"] += "\n\n" + 补充数据["summary"]
    logger.info("[AnalystAgent] 已补充任务数据，继续分析")

# 基于完整数据分析
state["risk_level"] = analyze_with_full_data(state["data_summary"])
```

**收益**：
- 减少 `UNKNOWN` 输出，提升用户体验
- 动态补充数据，无需重新发起请求
- 提升分析质量

---

## 3. 技术方案

### 3.1 A2A 调用接口设计

#### 方案A：直接函数调用

```python
# nodes.py
async def data_agent_node(state):
    if detect_anomaly():
        # 直接调用 analyst_agent_node
        verification_state = {"question": "...", "data_summary": "..."}
        result = await analyst_agent_node(verification_state)
```

**优点**：实现简单，无需框架改动
**缺点**：绕过 Supervisor，无法记录调用链

#### 方案B：通过 Supervisor 中转

```python
# nodes.py
async def data_agent_node(state):
    if detect_anomaly():
        state["a2a_request"] = {
            "target": "analyst",
            "question": "验证燃尽数据异常",
            "callback": "data_agent_continue"
        }
        return state  # 返回 Supervisor

# graph.py
def route_supervisor(state):
    if state.get("a2a_request"):
        return state["a2a_request"]["target"]  # 路由到 analyst
    # 正常路由逻辑...
```

**优点**：保留 Supervisor 控制权，可记录调用链
**缺点**：实现复杂，需要回调机制

#### 方案C：Agent 类封装（推荐）

```python
# agents.py
class BaseAgent:
    def __init__(self, graph):
        self.graph = graph

    async def call_agent(self, agent_name: str, input_state: dict):
        """A2A 调用接口"""
        node_fn = self.graph.nodes[agent_name]
        result = await node_fn(input_state)
        return result

class DataAgent(BaseAgent):
    async def execute(self, state):
        if detect_anomaly():
            result = await self.call_agent("analyst", {...})
```

**优点**：封装清晰，易于测试，支持调用链追踪
**缺点**：需要重构现有节点函数为类

---

### 3.2 调用链追踪

A2A 调用需要记录到 `state` 中，便于调试和审计：

```python
state["a2a_trace"] = [
    {
        "caller": "data_agent",
        "callee": "analyst_agent",
        "reason": "验证燃尽数据异常",
        "timestamp": "2026-03-23T10:15:30",
        "result": "DATA_VALID"
    }
]
```

---

## 4. 实施计划

### 4.1 Phase 1：基础 A2A 支持（2周）

- [ ] 实现 `BaseAgent` 类，封装 `call_agent()` 接口
- [ ] 重构 `data_agent_node` 为 `DataAgent` 类
- [ ] 实现场景1：DataAgent 异常验证
- [ ] 添加 A2A 调用链追踪到 `state["a2a_trace"]`

### 4.2 Phase 2：完整 A2A 场景（2周）

- [ ] 重构 `analyst_agent_node` 为 `AnalystAgent` 类
- [ ] 实现场景2：AnalystAgent 数据补充
- [ ] 添加 A2A 调用次数限制（防止循环调用）
- [ ] 单元测试覆盖 A2A 场景

### 4.3 Phase 3：监控与优化（1周）

- [ ] 添加 A2A 调用耗时监控
- [ ] 优化 A2A 调用性能（缓存、并行）
- [ ] 文档更新：A2A 使用指南

---

## 5. 风险与限制

### 5.1 循环调用风险

**问题**：DataAgent → AnalystAgent → DataAgent → ...

**解决方案**：
- 限制 A2A 调用深度（最多 2 层）
- 记录调用栈，检测循环

```python
if len(state.get("a2a_trace", [])) >= 2:
    raise Exception("A2A 调用深度超限，可能存在循环调用")
```

### 5.2 性能影响

**问题**：A2A 调用增加延迟

**解决方案**：
- 只在必要时触发 A2A（异常检测、数据缺失）
- 缓存 A2A 调用结果
- 监控 A2A 调用频率

### 5.3 调试复杂度

**问题**：调用链变复杂，难以追踪

**解决方案**：
- 完整记录 `a2a_trace`
- 日志中标注 `[A2A]` 前缀
- 提供可视化调用链工具

---

## 6. 附录

### 6.1 当前架构 vs A2A 架构对比

| 维度 | 当前架构 | A2A 架构 |
|------|---------|---------|
| Agent 通信 | 通过 state 单向传递 | 双向直接调用 |
| 数据补充 | 无法补充，只能返回 UNKNOWN | 主动请求补充 |
| 异常处理 | 被动传递异常数据 | 主动验证异常 |
| 调用链 | 线性：S→D→S→A→S→W | 树状：S→D→A(验证)→D→S→A→S→W |
| 复杂度 | 低 | 中 |
| 灵活性 | 低 | 高 |

### 6.2 参考资料

- LangGraph Multi-Agent 文档：https://langchain-ai.github.io/langgraph/tutorials/multi_agent/
- Agent Communication Patterns：https://arxiv.org/abs/2308.08155

---

**文档版本**：v1.0
**创建日期**：2026-03-23
**作者**：AI Agent Team
