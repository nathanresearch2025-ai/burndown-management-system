# GitHub SSH 配置指南

## 🔑 你的 SSH 公钥

```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAINxRrm3wEPar03c7JeWS0x34bSD56+EQelI9CCbU7qU6 nathan.research.2025@gmail.com
```

**⚠️ 请复制上面的整行内容（包括 `ssh-ed25519` 开头到邮箱结尾）**

---

## 📋 添加 SSH 公钥到 GitHub

### 步骤 1：访问 GitHub SSH 设置页面

打开浏览器，访问：
```
https://github.com/settings/keys
```

或者：
1. 登录 GitHub
2. 点击右上角头像 → **Settings**
3. 左侧菜单点击 **SSH and GPG keys**

### 步骤 2：添加新的 SSH Key

1. 点击 **"New SSH key"** 按钮（绿色按钮）

2. 填写信息：
   - **Title**: `Burndown Dev Machine` （或任意你喜欢的名称）
   - **Key type**: 选择 `Authentication Key`
   - **Key**: 粘贴上面的公钥（完整的一行）

3. 点击 **"Add SSH key"** 按钮

4. 可能需要输入 GitHub 密码确认

---

## ✅ 验证 SSH 连接

添加公钥后，在命令行中测试连接：

```bash
ssh -T git@github.com
```

**期望输出**：
```
Hi nathanresearch2025-ai! You've successfully authenticated, but GitHub does not provide shell access.
```

如果看到这个消息，说明 SSH 配置成功！

---

## 🔧 修改远程仓库 URL 为 SSH

### 方式 1：使用命令行（推荐）

```bash
# 进入项目目录
cd D:\java\claude\projects\2

# 查看当前远程 URL
git remote -v

# 修改为 SSH URL
git remote set-url origin git@github.com:nathanresearch2025-ai/burndown-management-system.git

# 验证修改
git remote -v
```

### 方式 2：使用 IDEA

1. 打开 IDEA
2. 点击 **VCS → Git → Remotes**
3. 选择 `origin`，点击编辑图标
4. 将 URL 修改为：
   ```
   git@github.com:nathanresearch2025-ai/burndown-management-system.git
   ```
5. 点击 **OK**

---

## 🚀 推送代码到远程

修改为 SSH URL 后，推送代码：

### 在命令行中：
```bash
git push origin ai-agent-9-March
```

### 在 IDEA 中：
1. 按 `Ctrl + Shift + K` 打开 Push 窗口
2. 点击 **"Push"** 按钮
3. **不需要输入用户名和密码！** SSH 会自动认证

---

## 🎯 完整操作流程

### 1️⃣ 添加 SSH 公钥到 GitHub
- 访问：https://github.com/settings/keys
- 点击 "New SSH key"
- 粘贴公钥（见上方）
- 保存

### 2️⃣ 测试 SSH 连接
```bash
ssh -T git@github.com
```

### 3️⃣ 修改远程 URL
```bash
cd D:\java\claude\projects\2
git remote set-url origin git@github.com:nathanresearch2025-ai/burndown-management-system.git
```

### 4️⃣ 推送代码
```bash
git push origin ai-agent-9-March
```

---

## 🔍 常见问题

### Q1: ssh -T 提示 "Permission denied (publickey)"

**原因**：SSH 公钥未正确添加到 GitHub

**解决**：
1. 确认公钥已添加到 https://github.com/settings/keys
2. 确认复制了完整的公钥内容
3. 等待几分钟后重试

### Q2: 推送时提示 "Could not read from remote repository"

**原因**：SSH 密钥权限问题或未启动 SSH Agent

**解决**：
```bash
# 启动 SSH Agent
eval "$(ssh-agent -s)"

# 添加私钥
ssh-add ~/.ssh/id_ed25519

# 重新推送
git push origin ai-agent-9-March
```

### Q3: 首次连接提示 "authenticity of host can't be established"

**提示信息**：
```
The authenticity of host 'github.com (xxx.xxx.xxx.xxx)' can't be established.
ED25519 key fingerprint is SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU.
Are you sure you want to continue connecting (yes/no/[fingerprint])?
```

**解决**：输入 `yes` 并回车

---

## 📌 SSH vs HTTPS 对比

| 特性 | SSH | HTTPS |
|------|-----|-------|
| 认证方式 | SSH 密钥 | Token/密码 |
| 安全性 | 高 | 中 |
| 便捷性 | 无需每次输入密码 | 需要输入 Token |
| 配置难度 | 中等 | 简单 |
| 推荐场景 | 日常开发 | CI/CD、临时访问 |

**推荐使用 SSH**，配置一次后永久有效，无需管理 Token。

---

## ✅ 验证配置成功

推送成功后，访问：
```
https://github.com/nathanresearch2025-ai/burndown-management-system/tree/ai-agent-9-March
```

应该能看到最新的提交记录。

---

**配置时间**：2026-03-10
**账号**：nathanresearch2025-ai
**邮箱**：nathan.research.2025@gmail.com
**SSH Key Type**：ED25519
