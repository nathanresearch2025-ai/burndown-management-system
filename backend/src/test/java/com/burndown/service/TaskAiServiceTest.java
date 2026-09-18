package com.burndown.service;

import com.burndown.dto.GenerateTaskDescriptionRequest;
import com.burndown.dto.GenerateTaskDescriptionResponse;
import com.burndown.entity.AiTaskGenerationLog;
import com.burndown.entity.Project;
import com.burndown.entity.Task;
import com.burndown.exception.BusinessException;
import com.burndown.repository.AiTaskGenerationLogRepository;
import com.burndown.repository.ProjectRepository;
import com.burndown.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskAiServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AiTaskGenerationLogRepository aiTaskGenerationLogRepository;

    @Mock
    private AiClientService aiClientService;

    @Mock
    private Counter requestCounter;

    @Mock
    private Counter successCounter;

    @Mock
    private Counter failureCounter;

    @Mock
    private Counter fallbackCounter;

    @Mock
    private Timer generationTimer;

    @Mock
    private Timer.Sample timerSample;

    private TaskAiService taskAiService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        taskAiService = new TaskAiService(
                taskRepository,
                projectRepository,
                aiTaskGenerationLogRepository,
                aiClientService,
                null, // EmbeddingService - not needed for tests
                objectMapper,
                requestCounter,
                successCounter,
                failureCounter,
                fallbackCounter,
                generationTimer
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(generationTimer).record(any(Runnable.class));
    }

    @Test
    void testGenerateDescription_Success() {
        // Arrange
        Long userId = 1L;
        GenerateTaskDescriptionRequest request = new GenerateTaskDescriptionRequest();
        request.setProjectId(1L);
        request.setTitle("用户登录优化");
        request.setType("FEATURE");
        request.setPriority("HIGH");
        request.setStoryPoints(BigDecimal.valueOf(5));

        Project project = new Project();
        project.setId(1L);
        project.setName("Test Project");
        project.setDescription("Test Description");

        Task similarTask = new Task();
        similarTask.setId(2L);
        similarTask.setTaskKey("PROJ-123");
        similarTask.setTitle("用户登录性能优化");
        similarTask.setType(Task.TaskType.TASK);
        similarTask.setPriority(Task.TaskPriority.HIGH);
        similarTask.setStoryPoints(BigDecimal.valueOf(5));
        similarTask.setDescription("优化登录性能");
        similarTask.setUpdatedAt(LocalDateTime.now());

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(taskRepository.findByProjectIdOrderByUpdatedAtDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(Arrays.asList(similarTask));
        when(aiClientService.generateTaskDescription(anyString()))
                .thenReturn("优化用户登录流程，提升登录速度和用户体验。");
        when(aiTaskGenerationLogRepository.save(any(AiTaskGenerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        GenerateTaskDescriptionResponse response = taskAiService.generateDescription(request, userId);

        // Assert
        assertNotNull(response);
        assertEquals("优化用户登录流程，提升登录速度和用户体验。", response.getDescription());
        assertEquals("external-llm", response.getGeneratedBy());
        assertNotNull(response.getSimilarTasks());
        assertEquals(1, response.getSimilarTasks().size());
        assertEquals("PROJ-123", response.getSimilarTasks().get(0).getTaskKey());

        verify(requestCounter, times(1)).increment();
        verify(successCounter, times(1)).increment();
        verify(projectRepository, times(1)).findById(1L);
        verify(aiClientService, times(1)).generateTaskDescription(anyString());
        verify(aiTaskGenerationLogRepository, times(1)).save(any(AiTaskGenerationLog.class));
    }

    @Test
    void testGenerateDescription_FallbackWhenAiServiceFails() {
        // Arrange
        Long userId = 1L;
        GenerateTaskDescriptionRequest request = new GenerateTaskDescriptionRequest();
        request.setProjectId(1L);
        request.setTitle("修复支付接口超时");
        request.setType("BUG");
        request.setPriority("CRITICAL");

        Project project = new Project();
        project.setId(1L);
        project.setName("Test Project");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(taskRepository.findByProjectIdOrderByUpdatedAtDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(Arrays.asList());
        when(aiClientService.generateTaskDescription(anyString()))
                .thenThrow(new BusinessException("AI_SERVICE_ERROR", "AI service unavailable", null));
        when(aiTaskGenerationLogRepository.save(any(AiTaskGenerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        GenerateTaskDescriptionResponse response = taskAiService.generateDescription(request, userId);

        // Assert
        assertNotNull(response);
        assertTrue(response.getDescription().contains("修复支付接口超时"));
        assertEquals("fallback-template", response.getGeneratedBy());

        verify(requestCounter, times(1)).increment();
        verify(fallbackCounter, times(1)).increment();
        verify(failureCounter, times(1)).increment();
        verify(successCounter, never()).increment();
    }

    @Test
    void testGenerateDescription_ProjectNotFound() {
        // Arrange
        Long userId = 1L;
        GenerateTaskDescriptionRequest request = new GenerateTaskDescriptionRequest();
        request.setProjectId(999L);
        request.setTitle("Test Task");
        request.setType("TASK");

        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            taskAiService.generateDescription(request, userId);
        });

        verify(requestCounter, times(1)).increment();
        verify(projectRepository, times(1)).findById(999L);
        verify(aiClientService, never()).generateTaskDescription(anyString());
    }

    @Test
    void testGenerateDescription_WithSimilarTasks() {
        // Arrange
        Long userId = 1L;
        GenerateTaskDescriptionRequest request = new GenerateTaskDescriptionRequest();
        request.setProjectId(1L);
        request.setTitle("数据导出功能");
        request.setType("FEATURE");

        Project project = new Project();
        project.setId(1L);
        project.setName("Test Project");

        Task task1 = createTask(1L, "PROJ-1", "数据导入功能", "FEATURE", "HIGH", 3);
        Task task2 = createTask(2L, "PROJ-2", "数据导出Excel", "FEATURE", "MEDIUM", 5);
        Task task3 = createTask(3L, "PROJ-3", "报表导出", "FEATURE", "LOW", 2);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(taskRepository.findByProjectIdOrderByUpdatedAtDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(Arrays.asList(task1, task2, task3));
        when(aiClientService.generateTaskDescription(anyString()))
                .thenReturn("实现数据导出功能，支持多种格式。");
        when(aiTaskGenerationLogRepository.save(any(AiTaskGenerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        GenerateTaskDescriptionResponse response = taskAiService.generateDescription(request, userId);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getSimilarTasks());
        assertTrue(response.getSimilarTasks().size() > 0);
        assertTrue(response.getSimilarTasks().size() <= 5);
    }

    private Task createTask(Long id, String taskKey, String title, String type, String priority, int storyPoints) {
        Task task = new Task();
        task.setId(id);
        task.setTaskKey(taskKey);
        task.setTitle(title);
        task.setType(Task.TaskType.valueOf(type));
        task.setPriority(Task.TaskPriority.valueOf(priority));
        task.setStoryPoints(BigDecimal.valueOf(storyPoints));
        task.setDescription("Test description");
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
