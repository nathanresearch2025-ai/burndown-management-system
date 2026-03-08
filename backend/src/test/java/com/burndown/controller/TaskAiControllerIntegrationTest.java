package com.burndown.controller;

import com.burndown.dto.GenerateTaskDescriptionRequest;
import com.burndown.dto.GenerateTaskDescriptionResponse;
import com.burndown.service.RateLimitService;
import com.burndown.service.TaskAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskAiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskAiService taskAiService;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    @WithMockUser(username = "testuser", authorities = {"ROLE_USER"})
    void testGenerateDescription_Success() throws Exception {
        // Arrange
        GenerateTaskDescriptionRequest request = new GenerateTaskDescriptionRequest();
        request.setProjectId(1L);
        request.setTitle("用户登录优化");
        request.setType("FEATURE");
        request.setPriority("HIGH");
        request.setStoryPoints(BigDecimal.valueOf(5));

        GenerateTaskDescriptionResponse.SimilarTaskDto similarTask =
            GenerateTaskDescriptionResponse.SimilarTaskDto.builder()
                .id(2L)
                .taskKey("PROJ-123")
                .title("用户登录性能优化")
                .similarity(0.85)
                .build();

        GenerateTaskDescriptionResponse response = GenerateTaskDescriptionResponse.builder()
                .description("优化用户登录流程，提升登录速度和用户体验。")
                .similarTasks(Arrays.asList(similarTask))
                .generatedBy("external-llm")
                .generatedAt(LocalDateTime.now())
                .build();

        when(rateLimitService.checkRateLimit(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(true);
        when(taskAiService.generateDescription(any(GenerateTaskDescriptionRequest.class), anyLong()))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/tasks/ai/generate-description")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("优化用户登录流程，提升登录速度和用户体验。"))
                .andExpect(jsonPath("$.generatedBy").value("external-llm"))
                .andExpect(jsonPath("$.similarTasks").isArray())
                .andExpect(jsonPath("$.similarTasks[0].taskKey").value("PROJ-123"));
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"ROLE_USER"})
    void testGenerateDescription_RateLimitExceeded() throws Exception {
        // Arrange
        GenerateTaskDescriptionRequest request = new GenerateTaskDescriptionRequest();
        request.setProjectId(1L);
        request.setTitle("测试任务");
        request.setType("TASK");

        when(rateLimitService.checkRateLimit(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(false);
        when(rateLimitService.getResetTime(anyString()))
                .thenReturn(300L);

        // Act & Assert
        mockMvc.perform(post("/tasks/ai/generate-description")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"ROLE_USER"})
    void testGenerateDescription_InvalidRequest() throws Exception {
        // Arrange - missing required fields
        GenerateTaskDescriptionRequest request = new GenerateTaskDescriptionRequest();
        request.setProjectId(1L);
        // Missing title and type

        when(rateLimitService.checkRateLimit(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/tasks/ai/generate-description")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGenerateDescription_Unauthorized() throws Exception {
        // Arrange
        GenerateTaskDescriptionRequest request = new GenerateTaskDescriptionRequest();
        request.setProjectId(1L);
        request.setTitle("测试任务");
        request.setType("TASK");

        // Act & Assert - no authentication
        mockMvc.perform(post("/tasks/ai/generate-description")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
