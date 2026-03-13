package com.burndown.aiagent.standup.service;

import com.burndown.aiagent.standup.config.StandupAgentMetrics;
import com.burndown.aiagent.standup.dto.StandupQueryRequest;
import com.burndown.aiagent.standup.dto.StandupQueryResponse;
import com.burndown.aiagent.standup.entity.AgentChatMessage;
import com.burndown.aiagent.standup.entity.AgentChatSession;
import com.burndown.aiagent.standup.prompt.StandupPromptTemplate;
import com.burndown.aiagent.standup.repository.AgentChatMessageRepository;
import com.burndown.aiagent.standup.repository.AgentChatSessionRepository;
import com.burndown.aiagent.standup.tool.StandupBurndownTools;
import com.burndown.aiagent.standup.tool.StandupRiskTools;
import com.burndown.aiagent.standup.tool.StandupTaskTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Standup Agent 核心服务类
 *
 * 功能说明：
 * 1. 处理 Scrum 站会相关的智能问答请求
 * 2. 集成 Spring AI 框架，调用大语言模型（LLM）
 * 3. 管理会话上下文，支持多轮对话
 * 4. 提供工具调用能力（Function Calling），让 AI 可以查询任务、燃尽图、风险评估等数据
 * 5. 记录对话历史和性能指标
 *
 * 工作流程：
 * 用户提问 -> 创建/获取会话 -> 构建提示词 -> 调用 LLM（带工具） -> 解析响应 -> 保存记录 -> 返回结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandupAgentService {

    // Spring AI 的聊天客户端构建器，用于与 LLM 交互
    private final ChatClient.Builder chatClientBuilder;

    // 会话仓库，管理用户的对话会话
    private final AgentChatSessionRepository sessionRepository;

    // 消息仓库，存储每一轮的问答记录
    private final AgentChatMessageRepository messageRepository;

    // JSON 序列化工具
    private final ObjectMapper objectMapper;

    // 任务工具：提供查询任务相关数据的能力
    private final StandupTaskTools taskTools;

    // 燃尽图工具：提供查询 Sprint 燃尽图数据的能力
    private final StandupBurndownTools burndownTools;

    // 风险评估工具：提供评估项目风险的能力
    private final StandupRiskTools riskTools;

    // 性能监控指标收集器
    private final StandupAgentMetrics metrics;

    /**
     * 处理站会问答请求的核心方法
     *
     * @param request 用户的问答请求，包含问题、项目ID、Sprint ID等信息
     * @param userId 当前用户ID
     * @param traceId 请求追踪ID，用于日志关联和问题排查
     * @return 站会问答响应，包含 AI 生成的回答、摘要、使用的工具等信息
     *
     * 执行流程：
     * 1. 记录请求开始时间，增加请求计数指标
     * 2. 创建或获取用户的对话会话（支持上下文记忆）
     * 3. 构建用户提示词（将请求参数填充到模板中）
     * 4. 调用 LLM，并注册可用的工具函数（Function Calling）
     * 5. 解析 LLM 的响应
     * 6. 保存对话记录到数据库
     * 7. 返回响应结果
     */
    @Transactional
    public StandupQueryResponse query(StandupQueryRequest request, Long userId, String traceId) {
        long startTime = System.currentTimeMillis();
        // 增加请求总数指标
        metrics.getRequestsTotal().increment();

        try {
            // 步骤1：创建或获取会话
            // 会话Key格式：standup_{userId}_{projectId}_{随机8位UUID}
            String sessionKey = generateSessionKey(userId, request.getProjectId());
            AgentChatSession session = getOrCreateSession(sessionKey, userId, request.getProjectId());

            // 步骤2：构建用户提示词
            // 将请求中的问题、项目ID、Sprint ID、时区等信息填充到提示词模板中
            String userPrompt = buildUserPrompt(request);

            // 步骤3：调用 LLM（大语言模型）
            ChatClient chatClient = chatClientBuilder.build();

            // 使用 Spring AI 的 Function Calling 机制
            // LLM 可以根据用户问题，自动决定是否调用这些工具函数来获取数据
            // 注册的工具函数：
            // - getInProgressTasks: 获取进行中的任务
            // - getSprintBurndown: 获取 Sprint 燃尽图数据
            // - evaluateBurndownRisk: 评估燃尽图风险
            ChatResponse response = metrics.getDurationTimer().record(() ->
                    chatClient.prompt()
                            .system(StandupPromptTemplate.SYSTEM_PROMPT)  // 系统提示词：定义 AI 的角色和行为
                            .user(userPrompt)  // 用户提示词：用户的具体问题
                            .functions("getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk")  // 注册工具函数
                            .call()
                            .chatResponse()
            );

            // 步骤4：提取 LLM 的回答内容
            String answer = response.getResult().getOutput().getContent();

            // 步骤5：解析响应，构建结构化的返回对象
            StandupQueryResponse queryResponse = parseResponse(answer);

            // 步骤6：保存对话记录到数据库
            long latency = System.currentTimeMillis() - startTime;
            saveMessage(session.getId(), request.getQuestion(), answer, queryResponse, traceId, (int) latency);

            return queryResponse;

        } catch (Exception e) {
            log.error("Error processing standup query: {}", e.getMessage(), e);
            // 增加失败计数指标
            metrics.getFallbackTotal().increment();
            throw new RuntimeException("Failed to process query: " + e.getMessage(), e);
        }
    }

    /**
     * 生成会话唯一标识Key
     *
     * 格式：standup_{userId}_{projectId}_{8位随机UUID}
     * 用途：标识一个用户在特定项目下的对话会话
     */
    private String generateSessionKey(Long userId, Long projectId) {
        return String.format("standup_%d_%d_%s", userId, projectId, UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 获取或创建对话会话
     *
     * 如果会话已存在，则复用（支持上下文记忆）
     * 如果会话不存在，则创建新会话并保存到数据库
     */
    private AgentChatSession getOrCreateSession(String sessionKey, Long userId, Long projectId) {
        return sessionRepository.findBySessionKey(sessionKey)
                .orElseGet(() -> {
                    AgentChatSession session = AgentChatSession.builder()
                            .sessionKey(sessionKey)
                            .userId(userId)
                            .projectId(projectId)
                            .build();
                    return sessionRepository.save(session);
                });
    }

    /**
     * 构建用户提示词
     *
     * 将请求参数填充到提示词模板中：
     * - {question}: 用户的问题
     * - {projectId}: 项目ID
     * - {sprintId}: Sprint ID（可选）
     * - {timezone}: 时区信息
     */
    private String buildUserPrompt(StandupQueryRequest request) {
        return StandupPromptTemplate.USER_PROMPT_TEMPLATE
                .replace("{question}", request.getQuestion())
                .replace("{projectId}", String.valueOf(request.getProjectId()))
                .replace("{sprintId}", request.getSprintId() != null ? String.valueOf(request.getSprintId()) : "N/A")
                .replace("{timezone}", request.getTimezone());
    }

    /**
     * 解析 LLM 的响应
     *
     * 当前实现：简单解析，直接返回 AI 的回答文本
     * TODO：生产环境中可以实现更复杂的解析逻辑，例如：
     * - 提取结构化信息（任务列表、风险等级等）
     * - 识别 AI 使用了哪些工具
     * - 提取关键证据和数据来源
     */
    private StandupQueryResponse parseResponse(String answer) {
        // Simple parsing - in production, you might want more sophisticated parsing
        return StandupQueryResponse.builder()
                .answer(answer)
                .summary(StandupQueryResponse.StandupSummary.builder().build())
                .toolsUsed(new ArrayList<>())
                .evidence(new ArrayList<>())
                .build();
    }

    /**
     * 保存对话消息到数据库
     *
     * 记录内容包括：
     * - 用户问题和 AI 回答
     * - 使用的工具列表（JSON格式）
     * - 风险等级
     * - 请求追踪ID
     * - 响应延迟（毫秒）
     *
     * 用途：
     * - 对话历史查询
     * - 性能分析
     * - 问题排查
     * - 用户行为分析
     */
    private void saveMessage(Long sessionId, String question, String answer,
                           StandupQueryResponse response, String traceId, int latencyMs) {
        try {
            String toolsUsedJson = objectMapper.writeValueAsString(response.getToolsUsed());

            AgentChatMessage message = AgentChatMessage.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .question(question)
                    .answer(answer)
                    .toolsUsed(toolsUsedJson)
                    .riskLevel(response.getSummary() != null ? response.getSummary().getRiskLevel() : null)
                    .traceId(traceId)
                    .latencyMs(latencyMs)
                    .build();

            messageRepository.save(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to save message: {}", e.getMessage(), e);
        }
    }
}
