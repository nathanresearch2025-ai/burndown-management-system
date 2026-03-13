package com.burndown.aiagent.langchain.dto; // LangChain 站会响应 DTO 包名

import lombok.AllArgsConstructor; // Lombok：全参构造器
import lombok.Builder; // Lombok：Builder 模式
import lombok.Data; // Lombok：Getter/Setter/toString
import lombok.NoArgsConstructor; // Lombok：无参构造器

import java.util.List; // List 类型

@Data // 生成 Getter/Setter 等
@Builder // 生成 Builder
@NoArgsConstructor // 生成无参构造器
@AllArgsConstructor // 生成全参构造器
public class LangchainStandupResponse { // LangChain 响应体
    private String answer; // 最终回答
    private List<String> toolsUsed; // 使用过的工具
    private List<String> evidence; // 证据列表
    private String riskLevel; // 风险等级
}
