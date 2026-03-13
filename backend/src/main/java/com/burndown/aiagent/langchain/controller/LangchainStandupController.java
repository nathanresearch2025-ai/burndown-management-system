package com.burndown.aiagent.langchain.controller; // LangChain 站会控制器包名

import com.burndown.aiagent.langchain.dto.LangchainStandupRequest; // LangChain 请求 DTO
import com.burndown.aiagent.langchain.dto.LangchainStandupResponse; // LangChain 响应 DTO
import com.burndown.aiagent.langchain.service.LangchainClientService; // LangChain 服务调用
import com.burndown.aiagent.standup.dto.ApiResponse; // 通用 API 响应
import io.swagger.v3.oas.annotations.Operation; // Swagger 注解
import io.swagger.v3.oas.annotations.tags.Tag; // Swagger 标签
import jakarta.validation.Valid; // 参数校验
import lombok.RequiredArgsConstructor; // 构造器注入
import lombok.extern.slf4j.Slf4j; // 日志注解
import org.springframework.security.core.Authentication; // 身份认证
import org.springframework.web.bind.annotation.*; // Web 注解

import java.util.UUID; // 生成 traceId

@Slf4j // 注入日志对象
@RestController // 标记为 REST Controller
@RequestMapping("/agent/langchain") // 控制器路由前缀
@RequiredArgsConstructor // Lombok 生成构造器
@Tag(name = "LangChain Standup Agent", description = "LangChain Standup Agent API") // Swagger 描述
public class LangchainStandupController { // LangChain 站会控制器

    private final LangchainClientService langchainClientService; // LangChain 调用服务

    @PostMapping("/standup/query") // 站会查询接口
    @Operation(summary = "LangChain 站会问答", description = "通过 LangChain 服务处理站会问题") // Swagger 描述
    public ApiResponse<LangchainStandupResponse> query( // 查询入口
            @Valid @RequestBody LangchainStandupRequest request, // 请求体
            Authentication authentication) { // 当前用户认证信息

        String traceId = UUID.randomUUID().toString().substring(0, 16); // 生成 traceId
        request.setTraceId(traceId); // 写入请求

        if (authentication == null || authentication.getPrincipal() == null) { // 认证为空时写入默认用户
            request.setUserId(1L); // 设置默认用户 ID
        }

        LangchainStandupResponse response = langchainClientService.queryStandup(request); // 调用 LangChain 服务
        return ApiResponse.success(response, traceId); // 返回成功结果
    }
}
