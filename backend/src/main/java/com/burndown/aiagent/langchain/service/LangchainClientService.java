package com.burndown.aiagent.langchain.service; // LangChain 客户端服务包名

import com.burndown.aiagent.langchain.dto.LangchainStandupRequest; // LangChain 请求 DTO
import com.burndown.aiagent.langchain.dto.LangchainStandupResponse; // LangChain 响应 DTO
import com.burndown.exception.BusinessException; // 业务异常
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 序列化
import lombok.extern.slf4j.Slf4j; // 日志注解
import org.springframework.beans.factory.annotation.Value; // 读取配置
import org.springframework.http.HttpStatus; // HTTP 状态码
import org.springframework.http.MediaType; // 请求类型
import org.springframework.stereotype.Service; // Spring Service

import java.io.IOException; // IO 异常
import java.net.URI; // URI
import java.net.http.HttpClient; // Java HTTP 客户端
import java.net.http.HttpRequest; // HTTP 请求
import java.net.http.HttpResponse; // HTTP 响应
import java.time.Duration; // 超时配置

@Slf4j // 注入日志对象
@Service // Spring Bean
public class LangchainClientService { // LangChain 调用服务

    private final ObjectMapper objectMapper; // JSON 处理
    private final HttpClient httpClient; // HTTP 客户端

    @Value("${langchain.enabled:false}") // 是否启用 LangChain
    private boolean enabled;

    @Value("${langchain.base-url:}") // LangChain 服务地址
    private String baseUrl;

    @Value("${langchain.timeout:30s}") // LangChain 调用超时
    private Duration timeout;

    public LangchainClientService(ObjectMapper objectMapper) { // 构造器注入
        this.objectMapper = objectMapper; // 保存 JSON 工具
        this.httpClient = HttpClient.newBuilder().build(); // 初始化 HTTP 客户端
    }

    public LangchainStandupResponse queryStandup(LangchainStandupRequest request) { // 调用 LangChain 站会接口
        if (!enabled) { // 检查是否启用
            throw new BusinessException("LANGCHAIN_DISABLED", "langchain.disabled", HttpStatus.BAD_REQUEST); // 未启用则报错
        }
        if (baseUrl == null || baseUrl.isBlank()) { // 检查服务地址
            throw new BusinessException("LANGCHAIN_CONFIG_MISSING", "langchain.configMissing", HttpStatus.BAD_REQUEST); // 配置缺失
        }

        try { // 捕获 IO 与中断异常
            String url = baseUrl + "/agent/standup/query"; // 拼接 LangChain 接口地址

            HttpRequest httpRequest = HttpRequest.newBuilder() // 构建 HTTP 请求
                    .uri(URI.create(url)) // 设置请求地址
                    .timeout(timeout) // 设置超时
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE) // 设置 JSON
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request))) // 序列化请求体
                    .build(); // 构建完成

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString()); // 发送请求并获取响应

            if (response.statusCode() >= 400) { // 检查错误状态码
                log.error("LangChain service error: {}", response.body()); // 记录错误
                throw new BusinessException("LANGCHAIN_SERVICE_ERROR", "langchain.serviceUnavailable", HttpStatus.BAD_GATEWAY); // 抛出异常
            }

            return objectMapper.readValue(response.body(), LangchainStandupResponse.class); // 解析响应体
        } catch (IOException | InterruptedException ex) { // 捕获异常
            if (ex instanceof InterruptedException) { // 中断处理
                Thread.currentThread().interrupt(); // 恢复中断标志
            }
            throw new BusinessException("LANGCHAIN_SERVICE_ERROR", "langchain.serviceUnavailable", HttpStatus.BAD_GATEWAY); // 统一异常
        }
    }
}
