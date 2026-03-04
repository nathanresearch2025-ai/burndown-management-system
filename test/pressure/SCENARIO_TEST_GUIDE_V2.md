# 场景化压力测试 - 完整使用指南

## 功能特性

✅ **三种预定义场景**: baseline、standard、peak
✅ **自动版本管理**: 每次运行版本号自动递增
✅ **多格式报告**: HTML、Markdown、JSON
✅ **5张对比图表**: RPS、响应时间、成功率、P95、请求数
✅ **详细性能指标**: P50、P95、P99响应时间百分位数

## 快速开始

```bash
cd /myapp/test/pressure
python3 scenario_pressure_test.py
```

## 输出目录结构

```
/myapp/test/pressure/
├── 20260302-183725-v1/          # 日期-时分秒-版本号
│   ├── 1.png                    # RPS对比图
│   ├── 2.png                    # 平均响应时间对比图
│   ├── 3.png                    # 成功率对比图
│   ├── 4.png                    # P95响应时间对比图
│   ├── 5.png                    # 总请求数对比图
│   ├── scenario_comparison_report.html  # HTML报告（推荐）
│   ├── scenario_comparison_report.md    # Markdown报告
│   └── results.json             # 原始JSON数据
├── 20260302-184201-v2/          # 第二次运行，版本号递增
└── ...
```

## 版本管理规则

- **格式**: `YYYYMMDD-HHMMSS-vN`
- **版本递增**: 每次运行自动查找当天最大版本号并+1
- **每日重置**: 每天从v1开始
- **示例**:
  - 第1次运行: `20260302-183725-v1`
  - 第2次运行: `20260302-184201-v2`
  - 第3次运行: `20260302-190530-v3`
  - 次日第1次: `20260303-090000-v1`

## 三种测试场景

### 1. Baseline（基线测试）
```
并发用户: 5
测试时长: 30秒
用途: 建立性能基线，低负载场景
```

### 2. Standard（标准测试）
```
并发用户: 20
测试时长: 60秒
用途: 模拟正常业务负载
```

### 3. Peak（峰值测试）
```
并发用户: 50
测试时长: 60秒
用途: 模拟高峰期负载，压力测试
```

## 使用方法

### 运行所有场景（默认）

```bash
python3 scenario_pressure_test.py
```

### 运行单个场景

```bash
# 只运行基线测试
python3 scenario_pressure_test.py --scenarios baseline

# 只运行标准测试
python3 scenario_pressure_test.py --scenarios standard

# 只运行峰值测试
python3 scenario_pressure_test.py --scenarios peak
```

### 运行多个场景

```bash
# 运行基线和峰值测试
python3 scenario_pressure_test.py --scenarios baseline peak

# 运行标准和峰值测试
python3 scenario_pressure_test.py --scenarios standard peak
```

## 查看报告

### 方式1: 浏览器查看HTML报告（推荐）

```bash
# 找到最新的测试目录
ls -lt /myapp/test/pressure/20260302-* | head -1

# 在浏览器中打开HTML文件
# 例如: /myapp/test/pressure/20260302-184808-v2/scenario_comparison_report.html
```

HTML报告特点：
- 🎨 精美的可视化界面
- 📊 内嵌图表，无需单独查看
- 📱 响应式设计，支持移动端
- 🎯 一键查看所有数据

### 方式2: 查看Markdown报告

```bash
cat /myapp/test/pressure/20260302-184808-v2/scenario_comparison_report.md
```

### 方式3: 查看图表

```bash
# 查看所有图表
ls /myapp/test/pressure/20260302-184808-v2/*.png
```

### 方式4: 查看JSON原始数据

```bash
cat /myapp/test/pressure/20260302-184808-v2/results.json | jq
```

## 报告内容说明

### 场景概览
- 三种场景的核心指标对比表
- 彩色卡片展示各场景关键数据

### 核心指标对比
| 指标 | 说明 |
|------|------|
| 并发用户 | 同时发起请求的用户数 |
| 测试时长 | 测试持续时间（秒） |
| 总请求数 | 测试期间发送的总请求数 |
| 成功率 | 成功请求占比 |
| RPS | 每秒请求数（吞吐量） |

### 各场景详细数据
- 整体指标（总请求数、成功率、RPS等）
- 各接口性能（登录、项目列表、任务列表）
- 响应时间百分位数（P50、P95、P99）

### 可视化对比分析
- 5张图表全面展示性能对比
- 直观了解不同负载下的系统表现

### 性能评估
- 负载能力分析
- 响应时间变化趋势
- 性能瓶颈识别

## 性能指标解读

### RPS (Requests Per Second)
- **含义**: 每秒处理的请求数
- **越高越好**: 表示系统吞吐量大
- **示例**: 27.12 → 45.92 (提升69%)

### 响应时间
- **平均响应时间**: 所有请求的平均耗时
- **P50 (中位数)**: 50%的请求响应时间低于此值
- **P95**: 95%的请求响应时间低于此值
- **P99**: 99%的请求响应时间低于此值

### 成功率
- **理想值**: 100%
- **可接受**: > 99%
- **需优化**: < 99%

## 测试结果示例

```
场景化API压力测试
============================================================
目标服务器: http://159.75.202.106:30080/api/v1
测试用户: testuser1
输出目录: /myapp/test/pressure/20260302-184808-v2
版本号: v2

基线测试 完成!
总请求数: 852
成功: 852, 失败: 0
成功率: 100.00%
RPS: 27.81

标准测试 完成!
总请求数: 2874
成功: 2874, 失败: 0
成功率: 100.00%
RPS: 45.44

峰值测试 完成!
总请求数: 3132
成功: 3132, 失败: 0
成功率: 100.00%
RPS: 45.03

✓ 图表已生成
✓ 报告已生成
✓ HTML报告已生成
✓ JSON数据已保存

快速查看报告:
  HTML: /myapp/test/pressure/20260302-184808-v2/scenario_comparison_report.html
  Markdown: /myapp/test/pressure/20260302-184808-v2/scenario_comparison_report.md
```

## 依赖要求

```bash
pip3 install matplotlib requests
```

## 自定义场景

编辑脚本中的 `SCENARIOS` 字典：

```python
SCENARIOS = {
    'baseline': {
        'name': '基线测试',
        'users': 5,        # 修改并发用户数
        'duration': 30,    # 修改测试时长（秒）
        'description': '模拟低负载场景，5个并发用户'
    },
    'standard': {
        'name': '标准测试',
        'users': 20,
        'duration': 60,
        'description': '模拟正常负载场景，20个并发用户'
    },
    'peak': {
        'name': '峰值测试',
        'users': 50,
        'duration': 60,
        'description': '模拟高负载场景，50个并发用户'
    }
}
```

## 故障排查

### 问题1: matplotlib导入失败
```bash
pip3 install matplotlib
```

### 问题2: 无法连接到服务器
1. 检查 `/myapp/test/all-interface-test/config.json` 中的 baseUrl
2. 确认服务器是否正常运行
3. 检查网络连接

### 问题3: 测试失败率高（所有登录请求失败）

**症状**: 测试显示 0% 成功率，所有登录请求返回 HTTP 500 错误

**原因**: 这是后端服务器问题，不是测试脚本问题

**排查步骤**:

1. **检查后端日志**（最重要）
```bash
# 查看详细错误
kubectl logs -l app=burndown-backend --tail=200 | grep -A 10 "Exception"

# 实时监控
kubectl logs -f -l app=burndown-backend
```

2. **验证服务状态**
```bash
# 检查 Pod 状态
kubectl get pods

# 测试登录接口
curl -X POST http://159.75.202.106:30080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser1","password":"Test123456"}'
```

3. **检查数据库连接**
```bash
# 进入数据库
kubectl exec -it <postgres-pod> -- psql -U postgres -d burndown_db

# 验证用户存在
SELECT username, email FROM users WHERE username='testuser1';
```

4. **检查 Redis 状态**（如果使用）
```bash
kubectl get pods | grep redis
kubectl exec -it <redis-pod> -- redis-cli ping
```

**快速修复方案**:

```bash
# 方案1: 重启后端服务
kubectl delete pod -l app=burndown-backend

# 方案2: 重新部署
./deploy.sh

# 方案3: 重新初始化数据库
kubectl exec -it <postgres-pod> -- psql -U postgres -d burndown_db -f /docker-entrypoint-initdb.d/init.sql
```

**详细分析**: 参考 `/myapp/test/pressure/TEST_FAILURE_ANALYSIS.md`

### 问题4: 部分请求失败（成功率 50-90%）

**可能原因**:
1. 服务器资源不足（CPU/内存）
2. 数据库连接池耗尽
3. 网络超时

**解决方案**:
1. 降低并发用户数
2. 增加测试时长，减少突发压力
3. 检查服务器资源使用情况
```bash
# 查看 Pod 资源使用
kubectl top pods

# 查看节点资源
kubectl top nodes
```

### 问题5: 中文字体显示为方框
- 这是matplotlib的字体问题，不影响数据准确性
- 图表中的数字和英文正常显示
- HTML报告中的中文完全正常

## 最佳实践

1. **渐进式测试**: 先运行baseline，再运行standard，最后运行peak
2. **监控服务器**: 测试时同时监控服务器CPU、内存、数据库连接
3. **多次测试**: 每个场景至少运行3次，取平均值
4. **对比分析**: 定期运行测试，对比不同版本的性能变化
5. **保存报告**: 重要的测试报告建议备份到其他目录

## 与其他测试工具对比

| 工具 | 场景化 | 图表 | HTML报告 | 版本管理 | 易用性 |
|------|--------|------|----------|---------|--------|
| scenario_pressure_test.py | ✅ | ✅ | ✅ | ✅ | ⭐⭐⭐⭐⭐ |
| simple_pressure_test.py | ❌ | ❌ | ❌ | ✅ | ⭐⭐⭐⭐ |
| Locust (pressure_test.py) | ❌ | ✅ | ✅ | ❌ | ⭐⭐⭐ |

## 更新日志

### 2026-03-02 v2
- ✅ 添加HTML报告生成
- ✅ 实现自动版本管理（日期-时分秒-版本号）
- ✅ 优化版本递增逻辑（按日期匹配）
- ✅ 改进报告输出提示

### 2026-03-02 v1
- ✅ 创建场景化压力测试脚本
- ✅ 支持 baseline、standard、peak 三种场景
- ✅ 自动生成5张对比图表
- ✅ 生成详细的Markdown对比报告
- ✅ 支持选择性运行场景
