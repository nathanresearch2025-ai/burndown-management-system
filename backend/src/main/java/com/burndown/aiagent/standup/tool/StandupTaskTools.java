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
     * - 查询指定 Sprint 中，指定用户的所有"进行中"状态的任务
     * - 返回任务的关键信息：任务编号、标题、优先级、故事点、更新时间
     *
     * AI 调用时机：
     * - 用户询问"我有哪些任务在做"
     * - 用户询问"今天的工作进展"
     * - 用户询问"我负责的任务"
     *
     * @param request 包含 SprintID 和用户ID的请求参数
     * @return 简洁的任务数据字符串，供 AI 理解和生成自然回答
     */
    @Description("获取用户当前进行中的任务列表")
    public String getInProgressTasks(GetInProgressTasksRequest request) {
        log.info("Tool called: getInProgressTasks - sprintId: {}, userId: {}",
                request.sprintId(), request.userId());

        try {
            // 查询任务：Sprint 匹配 + 用户匹配 + 状态为"进行中"
            List<Task> tasks = taskRepository.findBySprintIdAndAssigneeIdAndStatus(
                    request.sprintId(),
                    request.userId(),
                    Task.TaskStatus.IN_PROGRESS
            );

            // 如果没有任务，返回简洁提示
            if (tasks.isEmpty()) {
                return "0个进行中的任务";
            }

            // 构建简洁的数据格式，让 AI 自己组织语言
            StringBuilder result = new StringBuilder();
            result.append(String.format("共%d个任务：", tasks.size()));

            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);
                if (i > 0) {
                    result.append("；");
                }
                result.append(String.format("%s(%s优先级,%.1f故事点,更新于%s)",
                        task.getTitle(),
                        translatePriority(task.getPriority()),
                        task.getStoryPoints() != null ? task.getStoryPoints() : 0.0,
                        formatDateTime(task.getUpdatedAt())));
            }

            return result.toString();

        } catch (Exception e) {
            log.error("Error getting in-progress tasks: ", e.getMessage(), e);
            return "获取任务失败: " + e.getMessage();
        }
    }

    /**
     * 翻译优先级为中文
     */
    private String translatePriority(Task.TaskPriority priority) {
        if (priority == null) return "普通";
        return switch (priority) {
            case HIGH -> "高";
            case MEDIUM -> "中";
            case LOW -> "低";
        };
    }

    /**
     * 格式化日期时间为简洁格式
     */
    private String formatDateTime(java.time.LocalDateTime dateTime) {
        if (dateTime == null) return "未知";

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate taskDate = dateTime.toLocalDate();

        if (taskDate.equals(today)) {
            return "今天" + dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        } else if (taskDate.equals(today.minusDays(1))) {
            return "昨天";
        } else if (taskDate.isAfter(today.minusDays(7))) {
            return taskDate.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"));
        } else {
            return taskDate.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"));
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
            @JsonPropertyDescription("Sprint ID")
            Long sprintId,

            @JsonProperty(required = true)
            @JsonPropertyDescription("用户 ID")
            Long userId
    ) {}
}
