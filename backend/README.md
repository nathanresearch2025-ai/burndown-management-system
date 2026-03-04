# Burndown Management System - Backend

Spring Boot 3.2 + Java 21 后端服务

## 技术栈
- Spring Boot 3.2
- Java 21
- PostgreSQL 16
- Redis 7
- Spring Security + JWT
- Spring Data JPA

## 快速开始

### 前置要求
- JDK 21
- Maven 3.8+
- PostgreSQL 16
- Redis 7

### 数据库配置
1. 创建数据库：
```sql
CREATE DATABASE burndown_db;
```

2. 修改 `application.yml` 中的数据库连接信息

### 运行
```bash
cd backend
mvn spring-boot:run
```

服务将在 http://localhost:8080 启动

## API文档
启动后访问: http://localhost:8080/api/v1/swagger-ui.html

## 主要功能
- 用户认证（JWT）
- 项目管理
- Sprint管理
- 任务管理
- 工时记录
- 燃尽图计算
