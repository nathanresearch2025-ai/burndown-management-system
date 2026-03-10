package com.burndown.aiagent.standup.tool;

import com.burndown.entity.Task;
import com.burndown.repository.TaskRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Standup Agent 任务工具类
 *
 * 功能说明：
 * 提供给 AI Agent 调用的工具函数，用于查询任务相关数据
 *
 * 工作原理（Spring AI Function Calling）：
 * 1. 使用 @Component 注解，让 Spring 管理这个工具类
 * 2. 方法上使用 @Description 注解，描述工具的功能（AI 会根据这个描述决定是否调用）
 * 3. 参数使用 record 类型，配合 @JsonProperty 和 @JsonPropertyDescription 注解
 * 4. AI 在对话过程中，如果需要任务数据，会自动调用这些工具函数
 * 5. 工具函数返回字符串格式的结果，AI 会将结果整合到最终回答中
 *
 * 示例场景：
 * 用户问："我今天有哪些任务在进行中？"
 * AI 识别需要任务数据 -> 调用 getInProgressTasks() -> 获取任务列表 -> 生成自然语言回答
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandupTaskTools {

    private final TaskRepository taskRepository;

    /**
     * 获取用户当前进行中的任务列表
     *
     * 功能：
     * - 查询指定项目中，指定用户的所有"进行中"状态的任务
     * - 返回任务的关键信息：任务编号、标题、优先级、故事点、更新时间
     *
     * AI 调用时机：
     * - 用户询问"我有哪些任务在做"
     * - 用户询问"今天的工作进展"
     * - 用户询问"我负责的任务"
     *
     * @param request 包含项目ID和用户ID的请求参数
     * @return 格式化的任务列表字符串，供 AI 理解和生成回答
     */
    @Description("获取用户当前进行中的任务列表")
    public String getInProgressTasks(GetInProgressTasksRequest request) {
        log.info("Tool called: getInProgressTasks - projectId: {}, userId: {}",
                request.projectId(), request.userId());

        try {
            // 查询任务：项目匹配 + 用户匹配 + 状态为"进行中"
            List<Task> tasks = taskRepository.findByProjectId(request.projectId()).stream()
                    .filter(task -> task.getAssigneeId() != null &&
                                  task.getAssigneeId().equals(request.userId()) &&
                                  task.getStatus() == Task.TaskStatus.IN_PROGRESS)
                    .collect(Collectors.toList());

            // 如果没有任务，返回友好提示
            if (tasks.isEmpty()) {
                return "当前没有进行中的任务";
            }

            // 构建格式化的任务列表字符串
            StringBuilder result = new StringBuilder();
            result.append(String.format("找到 %d 个进行中的任务：\n", tasks.size()));

            for (Task task : tasks) {
                result.append(String.format("- %s: %s (优先级: %s, 故事点: %s, 更新时间: %s)\n",
                        task.getTaskKey(),      // 任务编号，如 TASK-123
                        task.getTitle(),        // 任务标题
                        task.getPriority(),     // 优先级：HIGH/MEDIUM/LOW
                        task.getStoryPoints(),  // 故事点数
                        task.getUpdatedAt()));  // 最后更新时间
            }

            return result.toString();

        } catch (Exception e) {
            log.error("Error getting in-progress tasks: {}", e.getMessage(), e);
            return "获取任务失败: " + e.getMessage();
        }
    }

    /**
     * 工具函数的请求参数定义
     *
     * 使用 Java Record 类型（不可变数据类）
     * @JsonProperty 和 @JsonPropertyDescription 注解用于：
     * - 告诉 AI 这个参数的含义
     * - 让 AI 知道如何构造调用参数
     */
    public record GetInProgressTasksRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("项目 ID")
            Long projectId,

            @JsonProperty(required = true)
            @JsonPropertyDescription("用户 ID")
            Long userId
    ) {}
}
