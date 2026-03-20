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
 * Standup Agent task tools.
 *
 * Provides tool functions callable by the AI Agent to query task-related data.
 *
 * How it works (Spring AI Function Calling):
 * 1. @Component makes this tool class managed by Spring.
 * 2. @Description on methods describes the tool's capability (the AI uses this to decide whether to call it).
 * 3. Parameters use record types together with @JsonProperty and @JsonPropertyDescription annotations.
 * 4. During a conversation, if the AI needs task data, it automatically calls these tool functions.
 * 5. Tool functions return string-formatted results; the AI integrates the results into its final answer.
 *
 * Example scenario:
 * User asks: "Which tasks am I currently working on today?"
 * AI detects it needs task data -> calls getInProgressTasks() -> retrieves task list -> generates natural-language answer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandupTaskTools {

    private final TaskRepository taskRepository;

    /**
     * Get the list of tasks currently in progress for a user.
     *
     * - Queries all tasks with IN_PROGRESS status for a given user in a given project.
     * - Returns key task info: task key, title, priority, story points, and last updated time.
     *
     * AI invocation triggers:
     * - User asks "what tasks am I working on"
     * - User asks "what is my progress today"
     * - User asks "which tasks am I responsible for"
     *
     * @param request request containing project ID and user ID
     * @return formatted task list string for the AI to understand and generate an answer
     */
    @Description("Get the list of tasks currently in progress for a user")
    public String getInProgressTasks(GetInProgressTasksRequest request) {
        log.info("Tool called: getInProgressTasks - projectId: {}, userId: {}",
                request.projectId(), request.userId());

        try {
            // Query tasks: matching project + matching user + status = IN_PROGRESS.
            List<Task> tasks = taskRepository.findByProjectIdAndAssigneeIdAndStatus(
                    request.projectId(),
                    request.userId(),
                    Task.TaskStatus.IN_PROGRESS
            );

            // If no tasks found, return a friendly message.
            if (tasks.isEmpty()) {
                return "No tasks currently in progress.";
            }

            // Build the formatted task list string.
            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d task(s) in progress:\n", tasks.size()));

            for (Task task : tasks) {
                result.append(String.format("- %s: %s (priority: %s, story points: %s, updated: %s)\n",
                        task.getTaskKey(),      // task key, e.g. TASK-123
                        task.getTitle(),        // task title
                        task.getPriority(),     // priority: HIGH/MEDIUM/LOW
                        task.getStoryPoints(),  // story points
                        task.getUpdatedAt()));  // last updated time
            }

            return result.toString();

        } catch (Exception e) {
            log.error("Error getting in-progress tasks: {}", e.getMessage(), e);
            return "Failed to retrieve tasks: " + e.getMessage();
        }
    }

    /**
     * Request parameter definition for the tool function.
     *
     * Uses a Java Record type (immutable data class).
     * @JsonProperty and @JsonPropertyDescription annotations are used to:
     * - Tell the AI what each parameter means.
     * - Let the AI know how to construct the call arguments.
     */
    public record GetInProgressTasksRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("Project ID")
            Long projectId,

            @JsonProperty(required = true)
            @JsonPropertyDescription("User ID")
            Long userId
    ) {}
}
