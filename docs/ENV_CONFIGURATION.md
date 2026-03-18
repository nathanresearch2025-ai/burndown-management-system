# 环境变量配置指南

## 概述

本项目使用 `.env` 文件管理敏感信息，避免将密钥提交到 Git 仓库。

## 快速开始

### 1. 复制示例文件

```bash
cd backend
cp .env.example .env
```

### 2. 编辑 `.env` 文件

打开 `backend/.env` 文件，填入你的真实密钥：

```properties
# Hugging Face Token for DJL model download
HF_TOKEN=your_actual_huggingface_token

# Embedding API Configuration
EMBEDDING_BASE_URL=https://api.deepseek.com
EMBEDDING_API_KEY=your_actual_api_key
```

### 3. 获取密钥

#### Hugging Face Token
1. 访问 https://huggingface.co/settings/tokens
2. 创建新 token（Read 权限即可）
3. 复制 token 到 `.env` 文件的 `HF_TOKEN`

#### DeepSeek API Key
1. 访问 https://platform.deepseek.com/api_keys
2. 创建新 API key
3. 复制 key 到 `.env` 文件的 `EMBEDDING_API_KEY`

## 工作原理

1. **`.env` 文件**：存储实际的敏感信息（已在 `.gitignore` 中，不会提交到 Git）
2. **`.env.example` 文件**：提供配置模板（会提交到 Git，供团队参考）
3. **`application.yml`**：使用 `${ENV_VAR:default}` 语法引用环境变量
4. **`DotenvConfig.java`**：启动时自动加载 `.env` 文件

## 生产环境

生产环境不使用 `.env` 文件，而是直接设置系统环境变量：

### Linux/Mac
```bash
export HF_TOKEN=your_token
export EMBEDDING_API_KEY=your_key
```

### Windows
```cmd
set HF_TOKEN=your_token
set EMBEDDING_API_KEY=your_key
```

### Docker
```yaml
environment:
  - HF_TOKEN=${HF_TOKEN}
  - EMBEDDING_API_KEY=${EMBEDDING_API_KEY}
```

### Kubernetes
```yaml
env:
  - name: HF_TOKEN
    valueFrom:
      secretKeyRef:
        name: app-secrets
        key: hf-token
```

## 安全提醒

⚠️ **永远不要将 `.env` 文件提交到 Git！**

- `.env` 已在 `.gitignore` 中
- 只提交 `.env.example` 作为模板
- 定期轮换密钥
- 不要在代码中硬编码密钥

## 故障排除

### 问题：应用启动时提示找不到环境变量

**解决方案：**
1. 确认 `backend/.env` 文件存在
2. 检查 `.env` 文件格式（无空格，无引号）
3. 查看启动日志中的 "Loaded environment variable" 消息

### 问题：DJL 下载模型失败

**解决方案：**
1. 确认 `HF_TOKEN` 已正确设置
2. 检查 token 权限（需要 Read 权限）
3. 尝试手动访问 https://huggingface.co 验证 token

## 相关文件

- `backend/.env` - 本地环境变量（不提交）
- `backend/.env.example` - 配置模板（提交）
- `backend/src/main/resources/application.yml` - Spring 配置
- `backend/src/main/java/com/burndown/config/DotenvConfig.java` - 环境变量加载器
