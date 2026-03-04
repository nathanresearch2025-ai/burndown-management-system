# 燃尽图计算性能测试 - Selenium端到端测试

## 功能说明

这个测试脚本使用Selenium WebDriver模拟真实用户操作，测量从前端发起燃尽图计算到完成的整体时间。

## 测试流程

1. 启动Chrome浏览器（无头模式）
2. 登录系统（默认用户：admin）
3. 导航到冲刺详情页
4. 多次点击"计算燃尽图"按钮
5. 测量每次计算的完整耗时
6. 生成详细的测试报告

## 环境要求

```bash
# 安装依赖
pip3 install selenium

# 安装Chrome和ChromeDriver
# Ubuntu/Debian:
sudo apt-get update
sudo apt-get install -y chromium-browser chromium-chromedriver

# 或者使用webdriver-manager自动管理驱动
pip3 install webdriver-manager
```

## 使用方法

### 基本用法

```bash
cd /myapp/test/selenium-calculate
python3 burndown_calculate_test.py
```

### 自定义参数

```bash
# 指定前端URL
python3 burndown_calculate_test.py --url http://159.75.202.106:30173

# 指定冲刺ID
python3 burndown_calculate_test.py --sprint-id 1

# 指定测试次数
python3 burndown_calculate_test.py --count 10

# 组合使用
python3 burndown_calculate_test.py --url http://localhost:5173 --sprint-id 2 --count 5
```

## 输出报告

测试完成后会生成两种格式的报告：

### 1. JSON报告
```
burndown_test_20260304-163500.json
```

包含完整的测试数据，可用于进一步分析。

### 2. Markdown报告
```
burndown_test_20260304-163500.md
```

人类可读的测试报告，包含：
- 测试信息（URL、时间、次数）
- 测试结果摘要（成功率、平均耗时、P95等）
- 详细测试结果表格

## 报告示例

```markdown
# 燃尽图计算性能测试报告

## 测试信息

- **测试URL**: http://159.75.202.106:30173
- **测试时间**: 2026-03-04 16:35:00
- **测试次数**: 5

## 测试结果摘要

| 指标 | 数值 |
|------|------|
| 总测试数 | 5 |
| 成功次数 | 5 |
| 失败次数 | 0 |
| 成功率 | 100.00% |
| 平均耗时 | 2345.67ms |
| 最小耗时 | 2123.45ms |
| 最大耗时 | 2567.89ms |
| P95耗时 | 2550.00ms |
```

## 注意事项

1. **前端元素选择器**：脚本中的按钮选择器可能需要根据实际前端实现调整
2. **等待时间**：如果计算时间很长，可能需要增加超时时间（默认30秒）
3. **无头模式**：默认使用无头模式，如需查看浏览器操作，可修改代码中的`--headless`参数
4. **并发测试**：当前是串行测试，如需并发测试需要修改脚本

## 前端元素适配

如果测试失败，可能需要调整以下选择器：

```python
# 登录页面
username_input = self.driver.find_element(By.ID, "username")
password_input = self.driver.find_element(By.ID, "password")
login_button = self.driver.find_element(By.CSS_SELECTOR, "button[type='submit']")

# 计算按钮（根据实际前端调整）
calculate_button = self.driver.find_element(
    By.XPATH,
    "//button[contains(text(), '计算燃尽图') or contains(text(), 'Calculate')]"
)

# 成功提示（根据实际前端调整）
success_message = self.driver.find_element(By.CLASS_NAME, "ant-message-success")
```

## 对比优化前后

### 优化前测试
```bash
# 在优化前运行测试
python3 burndown_calculate_test.py --count 10
# 保存报告：burndown_test_before_optimization.json
```

### 优化后测试
```bash
# 在引入MQ异步化后运行测试
python3 burndown_calculate_test.py --count 10
# 保存报告：burndown_test_after_optimization.json
```

### 对比分析
```python
# 可以编写脚本对比两次测试结果
import json

with open('burndown_test_before_optimization.json') as f:
    before = json.load(f)

with open('burndown_test_after_optimization.json') as f:
    after = json.load(f)

before_avg = before['summary']['avg_duration_ms']
after_avg = after['summary']['avg_duration_ms']

improvement = (before_avg - after_avg) / before_avg * 100
print(f"性能提升: {improvement:.2f}%")
```

## 故障排查

### 问题1：找不到ChromeDriver
```bash
# 安装ChromeDriver
sudo apt-get install chromium-chromedriver

# 或使用webdriver-manager
pip3 install webdriver-manager
# 修改代码使用webdriver-manager
```

### 问题2：找不到元素
- 检查前端是否正常运行
- 使用浏览器开发者工具确认元素选择器
- 增加等待时间

### 问题3：登录失败
- 检查用户名密码是否正确
- 检查后端API是否正常
- 查看浏览器截图（可在代码中添加截图功能）

## 扩展功能

可以根据需要添加以下功能：

1. **截图功能**：失败时自动截图
2. **视频录制**：记录整个测试过程
3. **并发测试**：多个浏览器实例同时测试
4. **性能监控**：监控CPU、内存使用情况
5. **网络监控**：记录网络请求时间

## 许可证

MIT
