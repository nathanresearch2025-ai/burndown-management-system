package com.burndown.service;

import com.burndown.dto.AiGenerationFeedbackRequest;
import com.burndown.dto.GenerateTaskDescriptionRequest;
import com.burndown.dto.GenerateTaskDescriptionResponse;
import com.burndown.entity.AiTaskGenerationLog;
import com.burndown.entity.Project;
import com.burndown.entity.Task;
import com.burndown.exception.BusinessException;
import com.burndown.repository.AiTaskGenerationLogRepository;
import com.burndown.repository.ProjectRepository;
import com.burndown.repository.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TaskAiService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AiTaskGenerationLogRepository aiTaskGenerationLogRepository;
    private final AiClientService aiClientService;
    private final UnifiedEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final Counter requestCounter;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter fallbackCounter;
    private final Timer generationTimer;

    @Value("${ai.max-similar-tasks:5}")
    private int maxSimilarTasks;

    @Value("${ai.enabled:false}")
    private boolean aiEnabled;

    public TaskAiService(TaskRepository taskRepository,
                         ProjectRepository projectRepository,
                         AiTaskGenerationLogRepository aiTaskGenerationLogRepository,
                         AiClientService aiClientService,
                         @Autowired(required = false) UnifiedEmbeddingService embeddingService,
                         ObjectMapper objectMapper,
                         Counter aiGenerationRequestCounter,
                         Counter aiGenerationSuccessCounter,
                         Counter aiGenerationFailureCounter,
                         Counter aiGenerationFallbackCounter,
                         Timer aiGenerationTimer) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.aiTaskGenerationLogRepository = aiTaskGenerationLogRepository;
        this.aiClientService = aiClientService;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.requestCounter = aiGenerationRequestCounter;
        this.successCounter = aiGenerationSuccessCounter;
        this.failureCounter = aiGenerationFailureCounter;
        this.fallbackCounter = aiGenerationFallbackCounter;
        this.generationTimer = aiGenerationTimer;
    }

    @Transactional
    // @Cacheable(value = "aiTaskGeneration", key = "#request.projectId + ':' + #request.title + ':' + #request.type", unless = "#result == null")
    public GenerateTaskDescriptionResponse generateDescription(GenerateTaskDescriptionRequest request, Long userId) {
        requestCounter.increment();

        return generationTimer.record(() -> {
            Project project = projectRepository.findById(request.getProjectId())
                    .orElseThrow(() -> new BusinessException("PROJECT_NOT_FOUND", "project.notFound", HttpStatus.NOT_FOUND));

            // Use vector similarity search to find similar tasks.
            List<ScoredTask> scoredTasks = findSimilarTasksUsingVectorSearch(request);

            String description;
            String generatedBy;

            try {
                String prompt = buildPrompt(project, request, scoredTasks);
                description = aiClientService.generateTaskDescription(prompt);
                generatedBy = "external-llm";
                successCounter.increment();
            } catch (BusinessException ex) {
                // Fallback strategy: AI service unavailable — return a basic template.
                description = buildFallbackDescription(request, scoredTasks);
                generatedBy = "fallback-template";
                fallbackCounter.increment();
                failureCounter.increment();
            }

            List<GenerateTaskDescriptionResponse.SimilarTaskDto> similarTasks = scoredTasks.stream()
                    .map(scoredTask -> GenerateTaskDescriptionResponse.SimilarTaskDto.builder()
                            .id(scoredTask.task().getId())
                            .taskKey(scoredTask.task().getTaskKey())
                            .title(scoredTask.task().getTitle())
                            .similarity(roundSimilarity(scoredTask.similarity()))
                            .build())
                    .toList();

            // Save the generation log; failure must not affect the main flow.
            try {
                saveLog(request, userId, description, similarTasks);
            } catch (Exception ex) {
                // Log save failure must not affect the main flow — record the error only.
                System.err.println("Failed to save AI generation log: " + ex.getMessage());
            }

            return GenerateTaskDescriptionResponse.builder()
                    .description(description)
                    .similarTasks(similarTasks)
                    .generatedBy(generatedBy)
                    .generatedAt(LocalDateTime.now())
                    .build();
        });
    }

    /**
     * Find similar tasks using the vector database.
     */
    private List<ScoredTask> findSimilarTasksUsingVectorSearch(GenerateTaskDescriptionRequest request) {
        if (!aiEnabled || embeddingService == null) {
            // AI is disabled — fall back to keyword matching.
            return findSimilarTasksUsingKeywordMatch(request);
        }

        try {
            // Generate the embedding vector for the query text.
            String queryText = embeddingService.buildTaskEmbeddingText(
                    request.getTitle(),
                    null, // new task has no description yet
                    request.getType(),
                    request.getPriority()
            );

            PGvector queryEmbedding = embeddingService.generateEmbedding(queryText);

            // Use vector similarity search.
            List<Task> similarTasks = taskRepository.findSimilarTasksByEmbedding(
                    request.getProjectId(),
                    queryEmbedding.toString(),
                    request.getTitle(),
                    maxSimilarTasks
            );

            // Compute similarity scores (based on vector distance ranking).
            return similarTasks.stream()
                    .map(task -> {
                        // Vector cosine distance is already sorted in the query; assign a rank-based score here.
                        double similarity = calculateVectorSimilarityScore(task, request);
                        return new ScoredTask(task, similarity);
                    })
                    .filter(scoredTask -> scoredTask.similarity() > 0)
                    .toList();

        } catch (Exception ex) {
            // Vector search failed — fall back to keyword matching.
            System.err.println("Vector search failed, falling back to keyword match: " + ex.getMessage());
            return findSimilarTasksUsingKeywordMatch(request);
        }
    }

    /**
     * Keyword-match fallback strategy (original logic).
     */
    private List<ScoredTask> findSimilarTasksUsingKeywordMatch(GenerateTaskDescriptionRequest request) {
        List<Task> candidateTasks = taskRepository.findByProjectIdOrderByUpdatedAtDesc(
                request.getProjectId(),
                PageRequest.of(0, Math.max(maxSimilarTasks * 4, 12))
        );

        return candidateTasks.stream()
                .filter(task -> !task.getTitle().equalsIgnoreCase(request.getTitle()))
                .map(task -> new ScoredTask(task, calculateSimilarity(task, request)))
                .filter(scoredTask -> scoredTask.similarity() > 0)
                .sorted(Comparator.comparingDouble(ScoredTask::similarity).reversed()
                        .thenComparing(scoredTask -> scoredTask.task().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(maxSimilarTasks)
                .toList();
    }

    /**
     * Calculate a similarity score from vector search results,
     * combining the vector distance ranking with task attribute matching.
     */
    private double calculateVectorSimilarityScore(Task task, GenerateTaskDescriptionRequest request) {
        double score = 0.7; // base score (task already passed the vector search filter)

        // Bonus for matching task type.
        if (task.getType() != null && task.getType().name().equalsIgnoreCase(request.getType())) {
            score += 0.15;
        }

        // Bonus for matching priority.
        if (request.getPriority() != null && task.getPriority() != null
                && task.getPriority().name().equalsIgnoreCase(request.getPriority())) {
            score += 0.10;
        }

        // Bonus for matching story points.
        if (request.getStoryPoints() != null && task.getStoryPoints() != null
                && request.getStoryPoints().compareTo(task.getStoryPoints()) == 0) {
            score += 0.05;
        }

        return Math.min(score, 1.0);
    }

    private double calculateSimilarity(Task task, GenerateTaskDescriptionRequest request) {
        double score = 0;
        Set<String> requestKeywords = tokenize(request.getTitle());
        Set<String> taskKeywords = tokenize(task.getTitle());

        long commonKeywords = requestKeywords.stream().filter(taskKeywords::contains).count();
        if (!requestKeywords.isEmpty()) {
            score += (double) commonKeywords / requestKeywords.size() * 0.55;
        }
        if (task.getType() != null && task.getType().name().equalsIgnoreCase(request.getType())) {
            score += 0.25;
        }
        if (request.getPriority() != null && task.getPriority() != null
                && task.getPriority().name().equalsIgnoreCase(request.getPriority())) {
            score += 0.15;
        }
        if (request.getStoryPoints() != null && task.getStoryPoints() != null
                && request.getStoryPoints().compareTo(task.getStoryPoints()) == 0) {
            score += 0.05;
        }
        return score;
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return List.of(text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+"))
                .stream()
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .collect(Collectors.toSet());
    }

    private String buildPrompt(Project project,
                               GenerateTaskDescriptionRequest request,
                               List<ScoredTask> scoredTasks) {
        StringBuilder builder = new StringBuilder();
        builder.append("Project: ").append(project.getName()).append("\n")
                .append("Project description: ").append(defaultText(project.getDescription())).append("\n")
                .append("Task title: ").append(request.getTitle()).append("\n")
                .append("Task type: ").append(request.getType()).append("\n")
                .append("Priority: ").append(defaultText(request.getPriority())).append("\n")
                .append("Story points: ").append(formatDecimal(request.getStoryPoints())).append("\n")
                .append("Original estimate: ").append(formatDecimal(request.getOriginalEstimate())).append(" hours\n")
                .append("Requirements: Write a practical task description in Chinese with 2-3 short paragraphs or bullet-style lines. Include goal, key implementation points, and acceptance cues. Do not add markdown headings.\n");

        if (scoredTasks.isEmpty()) {
            builder.append("Historical similar tasks: none\n");
        } else {
            builder.append("Historical similar tasks:\n");
            for (int i = 0; i < scoredTasks.size(); i++) {
                Task task = scoredTasks.get(i).task();
                builder.append(i + 1)
                        .append(". ")
                        .append(task.getTaskKey())
                        .append(" | ")
                        .append(task.getTitle())
                        .append(" | type=")
                        .append(task.getType())
                        .append(" | priority=")
                        .append(task.getPriority())
                        .append(" | storyPoints=")
                        .append(formatDecimal(task.getStoryPoints()))
                        .append(" | description=")
                        .append(defaultText(task.getDescription()))
                        .append("\n");
            }
        }
        return builder.toString();
    }

    private void saveLog(GenerateTaskDescriptionRequest request,
                         Long userId,
                         String description,
                         List<GenerateTaskDescriptionResponse.SimilarTaskDto> similarTasks) {
        AiTaskGenerationLog log = new AiTaskGenerationLog();
        log.setProjectId(request.getProjectId());
        log.setUserId(userId);
        log.setTitle(request.getTitle());

        // Use HashMap to allow null values
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("projectId", request.getProjectId());
        payload.put("sprintId", request.getSprintId());
        payload.put("title", request.getTitle());
        payload.put("type", request.getType());
        payload.put("priority", request.getPriority());
        payload.put("storyPoints", request.getStoryPoints());
        payload.put("originalEstimate", request.getOriginalEstimate());
        payload.put("assigneeId", request.getAssigneeId());

        log.setRequestPayload(writeJson(payload));
        log.setResponseDescription(description);
        log.setSimilarTaskIds(writeJson(similarTasks.stream().map(GenerateTaskDescriptionResponse.SimilarTaskDto::getId).toList()));
        aiTaskGenerationLogRepository.save(log);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("AI_LOG_SERIALIZATION_ERROR", "common.internalError", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String defaultText(String value) {
        return (value == null || value.isBlank()) ? "N/A" : value;
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? "N/A" : value.stripTrailingZeros().toPlainString();
    }

    private double roundSimilarity(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String buildFallbackDescription(GenerateTaskDescriptionRequest request, List<ScoredTask> scoredTasks) {
        StringBuilder builder = new StringBuilder();
        builder.append("Task: ").append(request.getTitle()).append("\n\n");
        builder.append("Type: ").append(request.getType()).append("\n");
        if (request.getPriority() != null) {
            builder.append("Priority: ").append(request.getPriority()).append("\n");
        }
        if (request.getStoryPoints() != null) {
            builder.append("Story points: ").append(formatDecimal(request.getStoryPoints())).append("\n");
        }
        builder.append("\nPlease expand with a detailed description, acceptance criteria, and technical approach based on the task title.");

        if (!scoredTasks.isEmpty()) {
            builder.append("\n\nReference similar tasks:\n");
            for (int i = 0; i < Math.min(3, scoredTasks.size()); i++) {
                Task task = scoredTasks.get(i).task();
                builder.append("- ").append(task.getTaskKey()).append(": ").append(task.getTitle()).append("\n");
            }
        }

        return builder.toString();
    }

    private record ScoredTask(Task task, double similarity) {
    }

    public void submitFeedback(AiGenerationFeedbackRequest request, Long userId) {
        AiTaskGenerationLog log = aiTaskGenerationLogRepository.findById(request.getLogId())
                .orElseThrow(() -> new BusinessException("LOG_NOT_FOUND", "task.ai.logNotFound", HttpStatus.NOT_FOUND));

        if (!log.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "common.forbidden", HttpStatus.FORBIDDEN);
        }

        if (request.getIsAccepted() != null) {
            log.setIsAccepted(request.getIsAccepted());
        }
        if (request.getRating() != null) {
            log.setFeedbackRating(request.getRating());
        }
        if (request.getComment() != null && !request.getComment().isBlank()) {
            log.setFeedbackComment(request.getComment());
        }

        aiTaskGenerationLogRepository.save(log);
    }
}
