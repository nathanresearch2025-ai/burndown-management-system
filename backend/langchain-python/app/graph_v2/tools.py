import logging                        # 等价 Java：import org.slf4j.LoggerFactory;
from langchain_core.tools import tool   # 等价 Java：import 自定义注解 @Tool（LangChain 框架提供，用于注册 Agent 可调用工具）
from .. import tools as backend_tools   # 等价 Java：import com.burndown.tools.BackendTools;（上层包的 HTTP 工具模块）

# 等价 Java：private static final Logger logger = LoggerFactory.getLogger(ToolService.class);
logger = logging.getLogger(__name__)

# ── LangChain Tool 包装 ────────────────────────────────────────────────────────
# 使用 @tool 装饰器将异步函数包装为 LangChain 可识别的工具
# DataAgent 的 ReAct 循环通过这些工具与 Java 后端交互
#
# Java 对应结构：
#   每个 @tool 函数 ≈ 一个 Spring @Service 中的公开方法
#   整个模块 ≈ 一个 ToolService.java，内部持有一个共享的 Map<String,Object> _ctx

# 模块级上下文，由 DataAgent 节点在调用前注入
# dict 相当于 Java 的 HashMap<String, Object>
# 等价 Java 声明：private static final Map<String, Object> _ctx = new HashMap<>();
_ctx: dict = {}   # 等价 Java：Map<String, Object> _ctx = new HashMap<>();


def set_tool_context(project_id: int, sprint_id: int, user_id: int, trace_id: str | None):
    # 等价 Java：void setToolContext(int projectId, int sprintId, int userId, @Nullable String traceId)
    """
    在 DataAgent 节点执行前注入工具调用所需的上下文参数。

    每个 @tool 函数本身不接收业务参数（LangChain 会自动调用它们），
    因此需要在调用前通过本函数将上下文预先写入模块级 _ctx，
    工具函数再从 _ctx 中读取所需的 project_id / sprint_id 等值。

    Java 等价实现：
        void setToolContext(int projectId, int sprintId, int userId, @Nullable String traceId) {
            _ctx.put("project_id", projectId);  // 项目 ID，用于查询该项目下的数据
            _ctx.put("sprint_id",  sprintId);   // Sprint ID，用于燃尽图 / 风险评估
            _ctx.put("user_id",   userId);      // 当前用户 ID，用于过滤个人任务
            _ctx.put("trace_id",  traceId);     // 链路追踪 ID，可为 null
        }

    Args:
        project_id: 当前操作的项目主键（对应 ms_task.projects.id）
        sprint_id:  当前操作的 Sprint 主键（对应 ms_task.sprints.id）
        user_id:    发起请求的用户主键（对应 ms_auth.users.id）
        trace_id:   分布式链路追踪 ID，可为 None；透传给后端 HTTP 请求头
    """
    # 等价 Java：_ctx.put("project_id", projectId);
    _ctx["project_id"] = project_id
    # 等价 Java：_ctx.put("sprint_id", sprintId);
    _ctx["sprint_id"] = sprint_id
    # 等价 Java：_ctx.put("user_id", userId);
    _ctx["user_id"] = user_id
    # 等价 Java：_ctx.put("trace_id", traceId);  // @Nullable String，允许传 null
    _ctx["trace_id"] = trace_id


# ── get_in_progress_tasks ─────────────────────────────────────────────────────
# 等价 Java：@Tool public String getInProgressTasks(String dummy)
@tool   # 等价 Java：@Tool 注解，将此方法注册为 Agent 可调用工具
async def get_in_progress_tasks(dummy: str = "") -> str:
    # 等价 Java：public String getInProgressTasks(String dummy)
    """获取当前用户在指定项目中处于进行中状态的任务列表。
    返回任务名称、优先级、故事点等信息。
    参数 dummy 仅用于占位，无需传入。
    """
    # 等价 Java：int projectId = (int) _ctx.get("project_id");  // 取不到时默认 0
    project_id = _ctx.get("project_id", 0)
    # 等价 Java：int userId = (int) _ctx.get("user_id");        // 取不到时默认 0
    user_id = _ctx.get("user_id", 0)
    # 等价 Java：String traceId = (String) _ctx.get("trace_id"); // 可为 null
    trace_id = _ctx.get("trace_id")
    # 等价 Java：logger.info("[Tool] getInProgressTasks called: projectId={}, userId={}", projectId, userId);
    logger.info(f"[Tool] get_in_progress_tasks called: project_id={project_id}, user_id={user_id}")
    # 等价 Java：try {
    try:
        # 等价 Java：String result = backendTools.getInProgressTasks(projectId, userId, traceId);
        result = await backend_tools.get_in_progress_tasks(project_id, user_id, trace_id=trace_id)
        # 等价 Java：return result;
        return result
    # 等价 Java：} catch (Exception e) {
    except Exception as e:
        # 等价 Java：logger.warn("[Tool] getInProgressTasks failed: {}", e.getMessage());
        logger.warning(f"[Tool] get_in_progress_tasks failed: {e}")
        # 等价 Java：return "[工具调用失败] getInProgressTasks: " + e.getMessage();
        return f"[工具调用失败] get_in_progress_tasks: {e}"


# ── get_sprint_burndown ────────────────────────────────────────────────────────
# 等价 Java：@Tool public String getSprintBurndown(String dummy)
@tool   # 等价 Java：@Tool 注解，将此方法注册为 Agent 可调用工具
async def get_sprint_burndown(dummy: str = "") -> str:
    # 等价 Java：public String getSprintBurndown(String dummy)
    """获取指定 Sprint 的燃尽图数据，包含每日剩余故事点、理想线、实际线和偏差。
    参数 dummy 仅用于占位，无需传入。
    """
    # 等价 Java：int projectId = (int) _ctx.get("project_id");  // 取不到时默认 0
    project_id = _ctx.get("project_id", 0)
    # 等价 Java：int sprintId = (int) _ctx.get("sprint_id");    // 取不到时默认 0
    sprint_id = _ctx.get("sprint_id", 0)
    # 等价 Java：String traceId = (String) _ctx.get("trace_id"); // 可为 null
    trace_id = _ctx.get("trace_id")
    # 等价 Java：logger.info("[Tool] getSprintBurndown called: projectId={}, sprintId={}", projectId, sprintId);
    logger.info(f"[Tool] get_sprint_burndown called: project_id={project_id}, sprint_id={sprint_id}")
    # 等价 Java：try {
    try:
        # 等价 Java：String result = backendTools.getSprintBurndown(projectId, sprintId, traceId);
        result = await backend_tools.get_sprint_burndown(project_id, sprint_id, trace_id=trace_id)
        # 等价 Java：return result;
        return result
    # 等价 Java：} catch (Exception e) {
    except Exception as e:
        # 等价 Java：logger.warn("[Tool] getSprintBurndown failed: {}", e.getMessage());
        logger.warning(f"[Tool] get_sprint_burndown failed: {e}")
        # 等价 Java：return "[工具调用失败] getSprintBurndown: " + e.getMessage();
        return f"[工具调用失败] get_sprint_burndown: {e}"


# ── evaluate_burndown_risk ─────────────────────────────────────────────────────
# 等价 Java：@Tool public String evaluateBurndownRisk(String dummy)
@tool   # 等价 Java：@Tool 注解，将此方法注册为 Agent 可调用工具
async def evaluate_burndown_risk(dummy: str = "") -> str:
    # 等价 Java：public String evaluateBurndownRisk(String dummy)
    """评估指定 Sprint 的燃尽风险等级（LOW/MEDIUM/HIGH）及原因。
    参数 dummy 仅用于占位，无需传入。
    """
    # 等价 Java：int projectId = (int) _ctx.get("project_id");  // 取不到时默认 0
    project_id = _ctx.get("project_id", 0)
    # 等价 Java：int sprintId = (int) _ctx.get("sprint_id");    // 取不到时默认 0
    sprint_id = _ctx.get("sprint_id", 0)
    # 等价 Java：String traceId = (String) _ctx.get("trace_id"); // 可为 null
    trace_id = _ctx.get("trace_id")
    # 等价 Java：logger.info("[Tool] evaluateBurndownRisk called: projectId={}, sprintId={}", projectId, sprintId);
    logger.info(f"[Tool] evaluate_burndown_risk called: project_id={project_id}, sprint_id={sprint_id}")
    # 等价 Java：try {
    try:
        # 等价 Java：String result = backendTools.evaluateBurndownRisk(projectId, sprintId, traceId);
        result = await backend_tools.evaluate_burndown_risk(project_id, sprint_id, trace_id=trace_id)
        # 等价 Java：return result;
        return result
    # 等价 Java：} catch (Exception e) {
    except Exception as e:
        # 等价 Java：logger.warn("[Tool] evaluateBurndownRisk failed: {}", e.getMessage());
        logger.warning(f"[Tool] evaluate_burndown_risk failed: {e}")
        # 等价 Java：return "[工具调用失败] evaluateBurndownRisk: " + e.getMessage();
        return f"[工具调用失败] evaluate_burndown_risk: {e}"


# DataAgent 可用的工具列表
# 等价 Java：List<Tool> DATA_AGENT_TOOLS = List.of(getInProgressTasks, getSprintBurndown, evaluateBurndownRisk);
DATA_AGENT_TOOLS = [get_in_progress_tasks, get_sprint_burndown, evaluate_burndown_risk]
