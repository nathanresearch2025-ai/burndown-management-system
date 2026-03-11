package com.burndown.controller;

import com.burndown.dto.SprintCompletionPredictionDto;
import com.burndown.service.SprintPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Sprint 预测控制器
 */
@Slf4j
@RestController
@RequestMapping("/sprints")
@RequiredArgsConstructor
@Tag(name = "Sprint Prediction", description = "Sprint 完成预测 API")
public class SprintPredictionController {

    private final SprintPredictionService sprintPredictionService;

    // Swagger API 文档注解 - 定义接口的基本信息
    @Operation(
            summary = "预测 Sprint 完成概率",  // 接口简要描述
            description = "使用机器学习模型预测指定 Sprint 的完成概率和风险等级"  // 接口详细描述
    )
    // Swagger API 文档注解 - 定义可能的响应状态码和描述
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "预测成功"),      // 成功响应
            @ApiResponse(responseCode = "404", description = "Sprint 不存在"), // Sprint ID 不存在
            @ApiResponse(responseCode = "500", description = "预测服务异常")    // 服务器内部错误
    })
    // Spring MVC 注解 - 定义 GET 请求映射，{id} 为路径变量占位符
    @GetMapping("/{id}/completion-probability")
    // Spring Security 注解 - 权限控制，要求用户具有 SPRINT:VIEW 权限才能访问
    @PreAuthorize("hasAuthority('SPRINT:VIEW')")
    // 控制器方法定义 - 返回类型为 ResponseEntity 包装的预测结果 DTO
    public ResponseEntity<SprintCompletionPredictionDto> getSprintCompletionProbability(
            // Swagger 参数注解 - 描述路径参数的含义和是否必需
            @Parameter(description = "Sprint ID", required = true)
            // Spring MVC 注解 - 将 URL 路径中的 {id} 绑定到方法参数 id
            @PathVariable Long id) {

        // 使用 SLF4J 日志记录请求信息，包含 Sprint ID 用于调试和监控
        log.info("预测 Sprint 完成概率，Sprint ID: {}", id);

        // 调用业务服务层方法执行预测逻辑
        // 1. 从数据库查询 Sprint 信息
        // 2. 计算特征向量（19个特征）
        // 3. 调用 Python 机器学习模型进行预测
        // 4. 返回包含概率、风险等级、特征摘要的结果对象
        SprintCompletionPredictionDto prediction = sprintPredictionService.predictSprintCompletion(id);

        // 记录预测结果日志，包含概率（保留4位小数）和风险等级
        // 用于业务监控和问题排查
        log.info("Sprint {} 预测完成，概率: {:.4f}, 风险等级: {}",
                id, prediction.getProbability(), prediction.getRiskLevel());

        // 返回 HTTP 200 状态码和预测结果 JSON
        // ResponseEntity.ok() 等价于 ResponseEntity.status(HttpStatus.OK).body(prediction)
        return ResponseEntity.ok(prediction);
    }
}