"""
MCP Prompts — 预置提示模板
- daily_standup      每日站会报告
- sprint_health_check Sprint 健康度分析
- task_breakdown      任务拆解
"""

from typing import Callable

from mcp.types import Prompt, PromptArgument, PromptMessage, TextContent


def register_prompts() -> list[tuple[Prompt, Callable]]:
    return [
        _daily_standup(),
        _sprint_health_check(),
        _task_breakdown(),
    ]


def _daily_standup():
    prompt = Prompt(
        name="daily_standup",
        description="生成每日站会报告，汇总 Sprint 任务进展",
        arguments=[
            PromptArgument(
                name="sprint_id",
                description="Sprint ID",
                required=True,
            )
        ],
    )

    async def handler(args: dict) -> list[PromptMessage]:
        sprint_id = args.get("sprint_id", "?")
        text = f"""请帮我生成 Sprint {sprint_id} 的每日站会报告。

请按以下步骤操作：
1. 调用 get_active_sprint_tasks(sprint_id={sprint_id}) 获取所有任务
2. 调用 get_burndown_data(sprint_id={sprint_id}) 获取燃尽图数据
3. 基于数据生成站会报告，格式如下：

## 每日站会报告 - Sprint {sprint_id}

### 昨天完成
- 列出状态为 DONE 的任务（最近更新）

### 今天计划
- 列出状态为 IN_PROGRESS 和 IN_REVIEW 的任务

### 阻碍问题
- 列出状态为 BLOCKED 的任务

### Sprint 进度
- 燃尽图趋势分析
- 完成率预测
"""
        return [PromptMessage(role="user", content=TextContent(type="text", text=text))]

    return prompt, handler


def _sprint_health_check():
    prompt = Prompt(
        name="sprint_health_check",
        description="分析 Sprint 健康度，给出风险评估和建议",
        arguments=[
            PromptArgument(
                name="sprint_id",
                description="Sprint ID",
                required=True,
            )
        ],
    )

    async def handler(args: dict) -> list[PromptMessage]:
        sprint_id = args.get("sprint_id", "?")
        text = f"""请对 Sprint {sprint_id} 进行健康度分析。

请按以下步骤操作：
1. 调用 get_active_sprint(project_id=<相关项目ID>) 获取 Sprint 基本信息
2. 调用 get_burndown_data(sprint_id={sprint_id}) 获取燃尽图数据
3. 调用 get_sprint_completion_probability(sprint_id={sprint_id}) 获取完成概率
4. 调用 get_active_sprint_tasks(sprint_id={sprint_id}, status=BLOCKED) 获取阻断任务

基于以上数据，生成健康度报告：

## Sprint {sprint_id} 健康度分析

### 总体评估（🟢/🟡/🔴）

### 关键指标
- 完成概率
- 燃尽趋势
- 阻断任务数

### 风险因素

### 建议行动
"""
        return [PromptMessage(role="user", content=TextContent(type="text", text=text))]

    return prompt, handler


def _task_breakdown():
    prompt = Prompt(
        name="task_breakdown",
        description="将用户描述的功能需求拆解为具体子任务",
        arguments=[
            PromptArgument(
                name="description",
                description="需要拆解的功能描述",
                required=True,
            ),
            PromptArgument(
                name="project_id",
                description="项目 ID",
                required=True,
            ),
        ],
    )

    async def handler(args: dict) -> list[PromptMessage]:
        description = args.get("description", "")
        project_id = args.get("project_id", "?")
        text = f"""请将以下功能需求拆解为 Scrum 任务：

需求描述：{description}
项目 ID：{project_id}

请按以下步骤：
1. 调用 search_similar_tasks(query="{description}", project_id={project_id}) 查找参考任务
2. 将需求拆解为 3-5 个子任务，每个任务包含：
   - 标题（简洁明了）
   - 类型（FEATURE/BUG/TASK/TECH_DEBT）
   - 优先级（LOW/MEDIUM/HIGH/CRITICAL）
   - 故事点（1-13，斐波那契数列）
   - 简要描述
3. 对每个子任务，可调用 ai_generate_task_description 生成完整描述
4. 确认后调用 create_task 创建任务
"""
        return [PromptMessage(role="user", content=TextContent(type="text", text=text))]

    return prompt, handler
