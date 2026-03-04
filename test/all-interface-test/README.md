# API测试工具集

本目录包含完整的API测试工具，包括功能测试和压力测试。

## 📁 目录结构

```
/myapp/test/
├── api_test.py              # 全接口功能测试脚本
├── config.json              # 测试配置文件
├── TEST_SUMMARY.md          # 最新测试结果总结
├── VERSION_MANAGEMENT.md    # 版本管理说明
├── README.md                # 本文件
├── 20260302-v1/             # 功能测试报告（自动版本管理）
├── 20260302-v2/             # 每次运行自动递增版本号
├── 20260302-v3/             # 每天重置为v1
│   ├── test_report.json
│   ├── test_report.html
│   └── test_report.md
└── pressure/                # 压力测试工具目录
    ├── simple_pressure_test.py
    ├── pressure_test.py
    ├── README.md
    ├── QUICKSTART.md
    ├── API_REFERENCE.md
    ├── 20260302-v1/         # 压力测试报告（自动版本管理）
    └── 20260302-v2/
```

## 🔄 自动版本管理

测试脚本已实现自动版本管理：

- ✅ **自动递增**: 每次运行版本号自动+1（v1 → v2 → v3）
- ✅ **按日重置**: 新的一天版本号从v1重新开始
- ✅ **独立目录**: 每次测试生成独立报告目录，不覆盖历史数据
- ✅ **版本同步**: 自动更新config.json中的版本号

详细说明请查看 [VERSION_MANAGEMENT.md](VERSION_MANAGEMENT.md)

## 🎯 快速开始

### 1. 功能测试（全接口测试）

测试所有API接口的功能是否正常：

```bash
# 运行全接口测试
python3 /myapp/test/api_test.py

# 查看测试报告
cat /myapp/test/20260302-1.0.0/test_report.md
```

**最新测试结果**: 92.86% 通过率 (26/28)

### 2. 压力测试

测试API接口的性能和并发能力：

```bash
# 快速压测（5用户，30秒）
python3 /myapp/test/pressure/simple_pressure_test.py -u 5 -t 30

# 标准压测（10用户，60秒）
python3 /myapp/test/pressure/simple_pressure_test.py

# 高压测试（50用户，5分钟）
python3 /myapp/test/pressure/simple_pressure_test.py -u 50 -t 300
```

## 📊 测试覆盖

### 功能测试覆盖的模块

| 模块 | 接口数 | 通过率 | 状态 |
|------|--------|--------|------|
| 认证模块 | 2 | 100% | ✅ |
| 角色模块 | 2 | 100% | ✅ |
| 用户模块 | 4 | 100% | ✅ |
| 项目模块 | 4 | 75% | ⚠️ |
| 冲刺模块 | 4 | 100% | ✅ |
| 任务模块 | 6 | 100% | ✅ |
| 工作日志模块 | 3 | 66.67% | ⚠️ |
| 燃尽图模块 | 2 | 100% | ✅ |
| 权限模块 | 1 | 100% | ✅ |

**总计**: 28个接口，26个通过

### 压力测试覆盖的接口

| 接口 | 权重 | 平均响应时间 |
|------|------|-------------|
| 登录接口 | 20% | ~170ms |
| 项目列表接口 | 30% | ~17ms |
| 任务列表接口 | 50% | ~15ms |

## 📖 详细文档

### 功能测试文档

- [TEST_SUMMARY.md](TEST_SUMMARY.md) - 最新测试结果总结
- [config.json](config.json) - 测试配置文件

### 压力测试文档

- [pressure/README.md](pressure/README.md) - 完整使用说明
- [pressure/QUICKSTART.md](pressure/QUICKSTART.md) - 快速开始指南
- [pressure/API_REFERENCE.md](pressure/API_REFERENCE.md) - 接口详细文档

## 🔧 配置说明

### 修改测试服务器

编辑 `config.json`:

```json
{
  "baseUrl": "http://159.75.202.106:30080/api/v1",
  "version": "1.0.0",
  "testData": {
    "users": [...],
    "projects": [...],
    "sprints": [...],
    "tasks": [...]
  }
}
```

### 修改测试数据

在 `config.json` 的 `testData` 部分修改测试用的用户、项目、冲刺、任务数据。

## 📈 测试报告

### 功能测试报告

每次运行功能测试后，会在 `/myapp/test/日期-版本/` 目录生成：

- `test_report.json` - JSON格式，适合程序化处理
- `test_report.html` - HTML格式，可在浏览器查看
- `test_report.md` - Markdown格式，适合文档查看

### 压力测试报告

每次运行压力测试后，会在 `/myapp/test/pressure/日期-版本/` 目录生成：

- `pressure_report.json` - JSON格式
- `pressure_report.md` - Markdown格式

## ⚠️ 已知问题

### 1. 项目创建接口 (POST /projects)
- **状态**: 返回500错误
- **优先级**: 高
- **建议**: 检查后端日志和权限配置

### 2. 工作日志创建接口 (POST /worklogs)
- **状态**: 缺少必填字段
- **优先级**: 中
- **缺失字段**: workDate, timeSpent, remainingEstimate

## 🚀 持续集成

可以将测试脚本集成到CI/CD流程中：

```bash
# 在CI/CD中运行
python3 /myapp/test/api_test.py
if [ $? -eq 0 ]; then
    echo "测试通过"
else
    echo "测试失败"
    exit 1
fi
```

## 📞 联系方式

如有问题或建议，请联系开发团队。

---

**最后更新**: 2026-03-02
**测试版本**: 1.0.0
**服务器**: http://159.75.202.106:30080/api/v1
