# 监控系统使用说明

## 概述

本系统集成了 Prometheus + Grafana + Alertmanager 监控方案，用于实时监控 Java 后端服务的运行状态。

## 部署监控组件

### 方式一：完整部署（应用 + 监控）

```bash
./deploy.sh
```

### 方式二：仅部署监控组件

```bash
./deploy.sh --monitoring-only
```

### 方式三：手动部署

```bash
kubectl apply -f monitoring/k8s-monitoring-all.yaml
```

## 访问监控平台

部署完成后，可以通过以下地址访问监控平台：

### Prometheus
- **地址**: http://159.75.202.106:30090
- **用途**: 指标查询、告警规则管理
- **说明**: 无需登录

### Grafana
- **地址**: http://159.75.202.106:30300
- **用户名**: admin
- **密码**: admin123
- **用途**: 可视化监控看板

### Alertmanager
- **地址**: http://159.75.202.106:30093
- **用途**: 告警管理、静默配置
- **说明**: 无需登录

## 查看监控指标

### 1. 在 Prometheus 中查询指标

访问 Prometheus Web UI，在查询框中输入 PromQL 表达式：

#### 常用指标查询

**CPU 使用率**
```promql
process_cpu_usage{job="burndown-backend"}
```

**堆内存使用率**
```promql
jvm_memory_used_bytes{area="heap",job="burndown-backend"} / jvm_memory_max_bytes{area="heap",job="burndown-backend"}
```

**请求总量（QPS）**
```promql
rate(http_server_requests_seconds_count{job="burndown-backend"}[1m])
```

**错误率**
```promql
sum(rate(http_server_requests_seconds_count{status=~"5..",job="burndown-backend"}[5m])) / sum(rate(http_server_requests_seconds_count{job="burndown-backend"}[5m]))
```

**P95 响应时间**
```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job="burndown-backend"}[5m])) by (le))
```

**GC 次数**
```promql
rate(jvm_gc_pause_seconds_count{job="burndown-backend"}[5m])
```

**活跃线程数**
```promql
jvm_threads_live_threads{job="burndown-backend"}
```

**数据库连接池使用率**
```promql
hikaricp_connections_active{job="burndown-backend"} / hikaricp_connections_max{job="burndown-backend"}
```

### 2. 在 Grafana 中创建看板

#### 首次登录配置

1. 访问 http://159.75.202.106:30300
2. 使用用户名 `admin` 和密码 `admin123` 登录
3. 数据源已自动配置（Prometheus）

#### 创建监控看板

**方式一：导入官方模板**

1. 点击左侧菜单 "Dashboards" → "Import"
2. 输入以下模板 ID：
   - **JVM (Micrometer)**: 4701
   - **Spring Boot Statistics**: 6756
   - **Spring Boot 2.1 System Monitor**: 11378
3. 选择 Prometheus 数据源
4. 点击 "Import"

**方式二：手动创建看板**

1. 点击 "+" → "Dashboard" → "Add visualization"
2. 选择 Prometheus 数据源
3. 在查询框中输入 PromQL 表达式
4. 选择可视化类型（时间序列、仪表盘、统计等）
5. 保存看板

#### 推荐看板布局

**系统总览看板**
- CPU 使用率（时间序列图）
- 内存使用率（时间序列图）
- 请求量 QPS（时间序列图）
- 错误率（时间序列图）
- P95 响应时间（时间序列图）
- 活跃实例数（统计面板）

**JVM 专项看板**
- 堆内存使用趋势
- 各代内存使用情况
- GC 次数与耗时
- 线程数变化
- 类加载数量

**接口性能看板**
- TOP 10 慢接口
- TOP 10 高频接口
- 各接口错误率
- 各接口响应时间分布

## 告警配置

### 查看告警规则

访问 Prometheus → Status → Rules，可以看到已配置的告警规则：

- **HighCPUUsage**: CPU 使用率 > 80%，持续 5 分钟
- **HighMemoryUsage**: 堆内存使用率 > 85%，持续 5 分钟
- **HighErrorRate**: 5xx 错误率 > 5%，持续 3 分钟
- **ServiceDown**: 服务不可用，持续 1 分钟
- **SlowResponseTime**: P95 响应时间 > 2s，持续 5 分钟
- **HighThreadCount**: 线程数 > 500，持续 5 分钟
- **HighDatabaseConnectionUsage**: 数据库连接池使用率 > 80%，持续 5 分钟

### 查看当前告警

访问 Prometheus → Alerts，可以看到：
- **Inactive**: 未触发的告警
- **Pending**: 正在评估的告警（未达到持续时间）
- **Firing**: 已触发的告警

### 配置告警通知

当前 Alertmanager 配置了 webhook 通知。如需配置邮件、企业微信、钉钉等通知渠道：

1. 编辑 `monitoring/k8s-monitoring-all.yaml` 中的 Alertmanager ConfigMap
2. 修改 `receivers` 配置，添加通知渠道
3. 重新部署：`kubectl apply -f monitoring/k8s-monitoring-all.yaml`

**邮件通知示例**：
```yaml
receivers:
- name: 'email-receiver'
  email_configs:
  - to: 'your-email@example.com'
    from: 'alertmanager@example.com'
    smarthost: 'smtp.example.com:587'
    auth_username: 'alertmanager@example.com'
    auth_password: 'your-password'
```

**企业微信通知示例**：
```yaml
receivers:
- name: 'wechat-receiver'
  wechat_configs:
  - corp_id: 'your-corp-id'
    to_user: '@all'
    agent_id: 'your-agent-id'
    api_secret: 'your-api-secret'
```

### 告警静默

如需临时关闭某些告警（如维护期间）：

1. 访问 Alertmanager Web UI
2. 点击 "Silences" → "New Silence"
3. 配置静默规则（匹配标签、持续时间等）
4. 点击 "Create"

## 监控指标说明

### 系统指标

| 指标名称 | 说明 | 单位 |
|---------|------|------|
| process_cpu_usage | 进程 CPU 使用率 | 0-1 |
| system_cpu_usage | 系统 CPU 使用率 | 0-1 |
| process_uptime_seconds | 进程运行时间 | 秒 |

### JVM 内存指标

| 指标名称 | 说明 | 单位 |
|---------|------|------|
| jvm_memory_used_bytes | 已使用内存 | 字节 |
| jvm_memory_max_bytes | 最大内存 | 字节 |
| jvm_memory_committed_bytes | 已提交内存 | 字节 |

### GC 指标

| 指标名称 | 说明 | 单位 |
|---------|------|------|
| jvm_gc_pause_seconds_count | GC 次数 | 次 |
| jvm_gc_pause_seconds_sum | GC 总耗时 | 秒 |
| jvm_gc_memory_allocated_bytes_total | GC 分配的内存总量 | 字节 |

### HTTP 指标

| 指标名称 | 说明 | 单位 |
|---------|------|------|
| http_server_requests_seconds_count | 请求总数 | 次 |
| http_server_requests_seconds_sum | 请求总耗时 | 秒 |
| http_server_requests_seconds_bucket | 请求耗时分布 | 秒 |

### 线程指标

| 指标名称 | 说明 | 单位 |
|---------|------|------|
| jvm_threads_live_threads | 活跃线程数 | 个 |
| jvm_threads_daemon_threads | 守护线程数 | 个 |
| jvm_threads_peak_threads | 峰值线程数 | 个 |

### 数据库连接池指标

| 指标名称 | 说明 | 单位 |
|---------|------|------|
| hikaricp_connections_active | 活跃连接数 | 个 |
| hikaricp_connections_idle | 空闲连接数 | 个 |
| hikaricp_connections_max | 最大连接数 | 个 |
| hikaricp_connections_pending | 等待连接数 | 个 |

## 故障排查

### 监控组件无法访问

```bash
# 检查 Pod 状态
kubectl get pods -l app=prometheus
kubectl get pods -l app=grafana
kubectl get pods -l app=alertmanager

# 查看 Pod 日志
kubectl logs -l app=prometheus
kubectl logs -l app=grafana
kubectl logs -l app=alertmanager

# 检查 Service
kubectl get svc prometheus
kubectl get svc grafana
kubectl get svc alertmanager
```

### 后端指标无法采集

```bash
# 检查后端 Pod 是否运行
kubectl get pods -l app=burndown-backend

# 检查后端指标端点是否可访问
kubectl exec -it <backend-pod-name> -- curl http://localhost:8080/api/v1/actuator/prometheus

# 查看 Prometheus 采集目标状态
# 访问 Prometheus Web UI → Status → Targets
```

### Grafana 无法连接 Prometheus

1. 访问 Grafana → Configuration → Data Sources
2. 检查 Prometheus 数据源配置
3. 点击 "Test" 按钮测试连接
4. 如果失败，检查 Prometheus Service 是否正常

### 告警未触发

1. 访问 Prometheus → Alerts，检查告警规则状态
2. 检查告警规则表达式是否正确
3. 查看 Alertmanager 日志：`kubectl logs -l app=alertmanager`

## 性能优化建议

### Prometheus 存储优化

- 默认保留 15 天数据，可根据需求调整
- 避免高基数标签（如 userId、traceId）
- 使用 Recording Rules 预聚合高频查询

### Grafana 查询优化

- 使用合适的时间范围
- 避免过于复杂的 PromQL 查询
- 使用变量简化看板配置

## 扩展功能

### 添加自定义业务指标

在 Java 代码中使用 Micrometer 添加自定义指标：

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

@Service
public class MyService {
    private final Counter myCounter;

    public MyService(MeterRegistry registry) {
        this.myCounter = Counter.builder("my_custom_metric")
            .description("My custom metric description")
            .tag("type", "business")
            .register(registry);
    }

    public void doSomething() {
        myCounter.increment();
        // 业务逻辑
    }
}
```

### 配置新的告警规则

编辑 `monitoring/alert_rules.yml`，添加新规则：

```yaml
- alert: MyCustomAlert
  expr: my_custom_metric > 100
  for: 5m
  labels:
    severity: medium
  annotations:
    summary: "自定义告警"
    description: "指标值为 {{ $value }}"
```

重新部署：
```bash
kubectl apply -f monitoring/k8s-monitoring-all.yaml
```

## 常见问题

**Q: 如何修改 Grafana 管理员密码？**

A: 编辑 `monitoring/k8s-grafana.yaml`，修改 `GF_SECURITY_ADMIN_PASSWORD` 环境变量，然后重新部署。

**Q: 监控数据保留多久？**

A: 默认保留 15 天，可在 Prometheus Deployment 中修改 `--storage.tsdb.retention.time` 参数。

**Q: 如何增加 Prometheus 存储空间？**

A: 当前使用 emptyDir，重启后数据会丢失。生产环境建议使用 PersistentVolume。

**Q: 如何配置多环境监控？**

A: 在 Prometheus 配置中添加不同的 job，使用不同的标签区分环境。

## 参考资料

- [Prometheus 官方文档](https://prometheus.io/docs/)
- [Grafana 官方文档](https://grafana.com/docs/)
- [Micrometer 官方文档](https://micrometer.io/docs/)
- [Spring Boot Actuator 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
