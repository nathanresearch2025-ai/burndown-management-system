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

@Slf4j
@Component
@RequiredArgsConstructor
public class StandupTaskTools {

    private final TaskRepository taskRepository;

    @Description("获取用户当前进行中的任务列表")
    public String getInProgressTasks(GetInProgressTasksRequest request) {
        log.info("Tool called: getInProgressTasks - projectId: {}, userId: {}",
                request.projectId(), request.userId());

        try {
            List<Task> tasks = taskRepository.findByProjectId(request.projectId()).stream()
                    .filter(task -> task.getAssigneeId() != null &&
                                  task.getAssigneeId().equals(request.userId()) &&
                                  task.getStatus() == Task.TaskStatus.IN_PROGRESS)
                    .collect(Collectors.toList());

            if (tasks.isEmpty()) {
                return "当前没有进行中的任务";
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("找到 %d 个进行中的任务：\n", tasks.size()));

            for (Task task : tasks) {
                result.append(String.format("- %s: %s (优先级: %s, 故事点: %s, 更新时间: %s)\n",
                        task.getTaskKey(),
                        task.getTitle(),
                        task.getPriority(),
                        task.getStoryPoints(),
                        task.getUpdatedAt()));
            }

            return result.toString();

        } catch (Exception e) {
            log.error("Error getting in-progress tasks: {}", e.getMessage(), e);
            return "获取任务失败: " + e.getMessage();
        }
    }

    public record GetInProgressTasksRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("项目 ID")
            Long projectId,

            @JsonProperty(required = true)
            @JsonPropertyDescription("用户 ID")
            Long userId
    ) {}
}
