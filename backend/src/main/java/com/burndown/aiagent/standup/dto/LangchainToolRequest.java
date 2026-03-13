package com.burndown.aiagent.standup.dto; // LangChain 工具请求 DTO 包名

import lombok.AllArgsConstructor; // Lombok：全参构造器
import lombok.Builder; // Lombok：Builder 模式
import lombok.Data; // Lombok：Getter/Setter/toString
import lombok.NoArgsConstructor; // Lombok：无参构造器

@Data // 生成 Getter/Setter 等
@Builder // 生成 Builder
@NoArgsConstructor // 生成无参构造器
@AllArgsConstructor // 生成全参构造器
public class LangchainToolRequest { // LangChain 工具请求体
    private Long projectId; // 项目 ID
    private Long sprintId; // Sprint ID
    private Long userId; // 用户 ID
}
