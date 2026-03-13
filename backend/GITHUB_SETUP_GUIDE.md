# GitHub 账号配置指南 - nathanresearch2025-ai

## ✅ 已完成的配置

1. **Git 用户信息已配置**：
   - 用户名：`nathanresearch2025-ai`
   - 邮箱：`nathanresearch2025-ai@users.noreply.github.com`
   - 凭据管理器：Windows Credential Manager

2. **远程仓库配置正确**：
   - URL: `https://github.com/nathanresearch2025-ai/burndown-management-system.git`

## ⚠️ 当前问题

Git 仍在使用旧账号 `useforlogingithub` 的凭据，需要清除。

## 🔧 解决方案

### 方式 1：清除 Windows 凭据管理器（推荐）

#### 步骤 1：打开凭据管理器
1. 按 `Win + R` 打开运行对话框
2. 输入：`control /name Microsoft.CredentialManager`
3. 点击"确定"

#### 步骤 2：删除 GitHub 相关凭据
1. 点击 **"Windows 凭据"** 标签
2. 找到所有包含 `github.com` 或 `git:https://github.com` 的凭据
3. 点击展开，然后点击 **"删除"**
4. 确认删除

#### 步骤 3：重新推送代码
在命令行或 IDEA 中执行：
```bash
git push origin ai-agent-9-March
```

系统会提示输入凭据：
- **Username**: `nathanresearch2025-ai`
- **Password**: 你的 GitHub Personal Access Token（见下方如何生成）

---

### 方式 2：使用命令行清除凭据

打开 PowerShell 或 CMD，执行：

```powershell
# 查看所有 GitHub 凭据
cmdkey /list | findstr github

# 删除 GitHub 凭据（替换为实际显示的凭据名称）
cmdkey /delete:git:https://github.com
cmdkey /delete:LegacyGeneric:target=git:https://github.com
```

---

## 🔑 生成 GitHub Personal Access Token

### 步骤 1：访问 GitHub Token 设置页面
打开浏览器，访问：
```
https://github.com/settings/tokens
```

### 步骤 2：生成新 Token
1. 点击 **"Generate new token"** 按钮
2. 选择 **"Generate new token (classic)"**
3. 填写信息：
   - **Note**: `Burndown Management System - Dev`
   - **Expiration**: 选择过期时间（建议 90 days）
   - **Select scopes**: 勾选 **`repo`**（完整仓库访问权限）
4. 滚动到底部，点击 **"Generate token"**

### 步骤 3：复制 Token
⚠️ **重要**：Token 只显示一次，请立即复制并保存！

格式类似：`ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

---

## 🚀 在 IDEA 中配置

### 方式 A：使用 IDEA 内置 GitHub 集成

1. 打开 IDEA
2. 点击 **File → Settings** (或按 `Ctrl + Alt + S`)
3. 导航到 **Version Control → GitHub**
4. 点击 **"+"** 添加账号
5. 选择 **"Log In with Token"**
6. 粘贴你的 Personal Access Token
7. 点击 **"Add Account"**

### 方式 B：在推送时输入凭据

1. 在 IDEA 中点击 **VCS → Git → Push** (或按 `Ctrl + Shift + K`)
2. 点击 **"Push"** 按钮
3. 如果提示输入凭据：
   - **Username**: `nathanresearch2025-ai`
   - **Password**: 粘贴你的 Personal Access Token
4. 勾选 **"Remember"** 保存凭据

---

## 📝 推送代码

清除旧凭据并配置新 Token 后，执行以下命令：

### 在命令行中：
```bash
cd D:\java\claude\projects\2

# 推送到远程分支
git push origin ai-agent-9-March
```

### 在 IDEA 中：
1. 按 `Ctrl + K` 打开 Commit 窗口（已提交，可跳过）
2. 按 `Ctrl + Shift + K` 打开 Push 窗口
3. 确认分支为 `ai-agent-9-March`
4. 点击 **"Push"** 按钮

---

## ✅ 验证推送成功

推送成功后，访问：
```
https://github.com/nathanresearch2025-ai/burndown-management-system/tree/ai-agent-9-March
```

应该能看到最新的提交记录。

---

## 🔍 常见问题

### Q1: 提示 "Permission denied"
**原因**：使用了错误的账号或 Token 无效

**解决**：
1. 确认 Token 有 `repo` 权限
2. 确认 Token 未过期
3. 重新生成 Token 并清除旧凭据

### Q2: IDEA 不提示输入凭据
**原因**：IDEA 使用了缓存的旧凭据

**解决**：
1. File → Settings → Appearance & Behavior → System Settings → Passwords
2. 点击 **"Clear"** 清除密码缓存
3. 重新推送

### Q3: 推送时提示 "fatal: Authentication failed"
**原因**：Token 输入错误或已过期

**解决**：
1. 重新生成 Token
2. 确保复制完整的 Token（包括 `ghp_` 前缀）
3. 清除凭据后重新输入

---

## 📌 下一步操作

1. ✅ **清除 Windows 凭据管理器中的旧 GitHub 凭据**
2. ✅ **生成新的 Personal Access Token**
3. ✅ **在 IDEA 或命令行中推送代码**
4. ✅ **验证推送成功**

---

**配置完成时间**：2026-03-10
**账号**：nathanresearch2025-ai
**仓库**：burndown-management-system
**分支**：ai-agent-9-March
