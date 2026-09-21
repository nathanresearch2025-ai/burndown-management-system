package com.burndown.ai.service;

import com.burndown.ai.client.TaskServiceClient;
import com.burndown.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAiService {

    private final AiClientService aiClientService;
    private final TaskServiceClient taskServiceClient;
    private final AiProperties aiProperties;

    public String generateDescription(Long projectId, Long sprintId, String title,
                                      String type, String priority, Integer storyPoints) {
        String context = buildContext(sprintId, title, type, priority, storyPoints);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system",
                "content", "你是一个专业的Scrum项目管理助手，帮助团队生成清晰的任务描述。"));
        messages.add(Map.of("role", "user", "content", context));
        return aiClientService.chat(messages);
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<String> generateDescriptionAsync(Long projectId, Long sprintId,
                                                               String title, String type,
                                                               String priority, Integer storyPoints) {
        return CompletableFuture.completedFuture(
                generateDescription(projectId, sprintId, title, type, priority, storyPoints));
    }

    private String buildContext(Long sprintId, String title, String type,
                                String priority, Integer storyPoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为以下任务生成详细的描述：\n");
        sb.append("任务标题：").append(title).append("\n");
        sb.append("任务类型：").append(type).append("\n");
        sb.append("优先级：").append(priority).append("\n");
        sb.append("故事点：").append(storyPoints).append("\n");

        if (sprintId != null) {
            try {
                var resp = taskServiceClient.getTasksBySprint(sprintId, 0, aiProperties.getMaxSimilarTasks());
                if (resp != null && resp.getData() != null) {
                    sb.append("\n参考同Sprint中的相关任务以保持风格一致。");
                }
            } catch (Exception e) {
                log.warn("Could not fetch sprint tasks for context: {}", e.getMessage());
            }
        }

        sb.append("\n请生成包含：背景说明、验收标准、技术要点的结构化描述。");
        return sb.toString();
    }
}
