package com.burndown.aiagent.langchain.dto; // LangChain 站会请求 DTO 包名

import lombok.AllArgsConstructor; // Lombok：全参构造器
import lombok.Builder; // Lombok：Builder 模式
import lombok.Data; // Lombok：Getter/Setter/toString
import lombok.NoArgsConstructor; // Lombok：无参构造器

@Data // 生成 Getter/Setter 等
@Builder // 生成 Builder
@NoArgsConstructor // 生成无参构造器
@AllArgsConstructor // 生成全参构造器
public class LangchainStandupRequest { // LangChain 请求体
    private String question; // 用户问题
    private Long projectId; // 项目 ID
    private Long sprintId; // Sprint ID
    private Long userId; // 用户 ID
    private String timezone; // 时区信息
    private String traceId; // 链路追踪 ID
}
