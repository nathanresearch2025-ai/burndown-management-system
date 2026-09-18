# JVM 运行监控与优化报告

**生成时间**: 2026-03-24
**环境**: Kubernetes (K3s) / OpenCloudOS 9.4
**JVM**: TencentKonaJDK 21.0.10-ga (OpenJDK 21)
**应用**: burndown-management v1.0.0 (Spring Boot 3.2)
**采集方式**: Spring Boot Actuator `/api/v1/actuator/metrics`

---

## 一、JVM 实时指标快照

> 采集时应用已运行约 46 秒（冷启动后）

### 1.1 内存使用情况

| 区域 | 已使用 | 已提交 | 最大值 | 使用率 |
|------|--------|--------|--------|--------|
| **堆内存 (Heap)** | 130 MB | — | 512 MB | **25.4%** |
| **非堆 (NonHeap)** | 147 MB | — | 无上限 | — |
| **总内存** | 265 MB | 404 MB | 1,778 MB | **14.9%** |

**堆内存分区详情**（G1GC）：

| 分区 | 说明 |
|------|------|
| G1 Eden Space | 新生代 Eden 区，存放新对象 |
| G1 Survivor Space | 新生代存活区 |
| G1 Old Gen | 老年代，存放长期存活对象，GC live data = 0（冷启动） |

**非堆内存分区详情**：

| 分区 | 说明 |
|------|------|
| Metaspace | 类元数据，当前加载 21,845 个类 |
| CodeHeap 'profiled nmethods' | JIT 已分析方法代码缓存 |
| CodeHeap 'non-profiled nmethods' | JIT 未分析方法代码缓存 |
| CodeHeap 'non-nmethods' | JVM 内部代码 |
| Compressed Class Space | 压缩类指针空间 |

---

### 1.2 垃圾回收（GC）情况

| 指标 | 数值 | 评估 |
|------|------|------|
| GC 类型 | G1GC（G1 Young + G1 Concurrent） | 适合低延迟场景 ✅ |
| Young GC 次数 | 1 次 | 正常（冷启动触发）|
| Young GC 总耗时 | 20 ms | 优秀 ✅ |
| Young GC 最大单次 | 20 ms | 远低于 200ms 目标 ✅ |
| GC 触发原因 | Metadata GC Threshold | Metaspace 扩张触发 |
| Concurrent GC 阶段 | 2 次，共 21 ms | G1 并发标记正常 ✅ |
| 内存分配速率 | ~20 MB（启动期） | 正常 ✅ |
| Old Gen 晋升量 | 0 bytes | 无对象晋升（冷启动）|
| Old Gen Live Size | 0 bytes | 老年代尚未填充 |

---

### 1.3 线程状态

| 状态 | 数量 | 说明 |
|------|------|------|
| **总线程数** | 32 | — |
| RUNNABLE | 11 | 正在执行或可被调度 |
| WAITING | 13 | 等待唤醒（如锁、Queue） |
| TIMED_WAITING | 8 | 限时等待（Sleep、定时任务等）|
| BLOCKED | **0** | 无线程阻塞 ✅ |

> 32 个线程中：11 可运行、0 阻塞，线程健康状况良好。

---

### 1.4 CPU 与系统负载

| 指标 | 数值 | 评估 |
|------|------|------|
| JVM 进程 CPU 使用率 | **1.23%** | 极低，冷启动后趋于稳定 ✅ |
| 系统总 CPU 使用率 | **13.05%** | 正常水平 ✅ |
| 系统 1 分钟负载均值 | **0.86** | 健康（< CPU 核数）✅ |

---

### 1.5 JIT 编译

| 指标 | 数值 | 说明 |
|------|------|------|
| 编译器 | HotSpot 64-Bit Tiered Compilers | 分层编译（C1+C2）|
| 累计编译时间 | **25,546 ms**（25.5 秒） | 冷启动 JIT 编译开销 |
| 已加载类数量 | **21,845** | Spring Boot 应用正常范围 |

> JIT 编译时间 25.5s 说明启动后存在较长的 JIT 热身期，首批请求响应较慢。

---

### 1.6 数据库连接池（HikariCP）

| 指标 | 数值 | 评估 |
|------|------|------|
| 总连接数 | 20 | 启动时建立 |
| 活跃连接 | 0 | 无请求时正常 |
| 空闲连接 | 20 | 全部空闲 |
| 最大连接数 | 50 | 配置值 |
| 等待线程 | 0 | 无等待 ✅ |
| 连接超时次数 | 0 | 无超时 ✅ |

> 最大连接数 50 对于当前负载偏高，空闲连接占用数据库资源。

---

## 二、问题诊断

### 2.1 当前发现的问题

| 编号 | 级别 | 问题描述 | 数据支撑 |
|------|------|----------|----------|
| P1 | 🟡 中 | Heap Xmx 仅 512MB，压测高并发下可能不足 | 当前 25% 使用，但负载极低 |
| P2 | 🟡 中 | JIT 冷启动热身期长（25.5s 编译）| 首批请求响应偏慢 |
| P3 | 🟡 中 | HikariCP 初始/最大连接数偏高 | 20 空闲连接浪费 DB 资源 |
| P4 | 🟢 低 | GC 触发原因为 Metadata GC Threshold | Metaspace 初始值未设置上限 |
| P5 | 🟢 低 | MaxGCPauseMillis=200ms 目标偏宽松 | 可降至 100ms 提升响应 |
| P6 | 🟢 低 | 缺少 JVM 监控参数（GC 日志输出）| 生产环境排查困难 |

---

## 三、JVM 优化措施

### 3.1 内存配置优化

**现状**：`-Xms256m -Xmx512m`

**问题**：
- Xms（256MB）远低于 Xmx（512MB），JVM 启动后需动态扩容堆，产生额外 GC 压力
- Xmx（512MB）在高并发场景下可能不足（Spring Boot + 连接池 + 缓存）
- 未设置 MetaspaceSize

**优化建议**：

```bash
# K8s Deployment JVM 参数（backend-hostpath.yaml）
-Xms512m                          # 初始堆与最大堆一致，避免动态扩容
-Xmx512m                          # 视节点内存调整，建议容器内存限制的 60-70%
-XX:MetaspaceSize=128m            # 初始 Metaspace，减少动态扩容 GC
-XX:MaxMetaspaceSize=256m         # 限制 Metaspace 上限，防止内存泄漏
-XX:+UseG1GC                      # 保持 G1GC
-XX:MaxGCPauseMillis=100          # 降低 GC 停顿目标（200ms → 100ms）
-XX:+UseStringDeduplication       # 保持字符串去重
-XX:+HeapDumpOnOutOfMemoryError   # OOM 时自动 Dump
-XX:HeapDumpPath=/tmp/heapdump.hprof
```

**预期效果**：
- 消除启动后堆扩容 GC，减少冷启动 GC 次数
- MetaspaceSize 预留足够空间，避免 `Metadata GC Threshold` 触发

---

### 3.2 GC 策略优化

**现状**：G1GC，MaxGCPauseMillis=200ms（默认）

**优化建议**：

```bash
-XX:MaxGCPauseMillis=100          # 停顿目标降低至 100ms
-XX:G1HeapRegionSize=8m           # Region 大小（堆<2GB 时建议 4-8MB）
-XX:G1NewSizePercent=20           # 新生代最小比例 20%
-XX:G1MaxNewSizePercent=40        # 新生代最大比例 40%
-XX:G1MixedGCLiveThresholdPercent=85  # Mixed GC 触发阈值
-XX:InitiatingHeapOccupancyPercent=45 # 并发标记触发堆占用比例（默认 45%）
```

**预期效果**：
- Young GC 停顿从 20ms 进一步降低至 10ms 以内
- 减少 Full GC 风险

---

### 3.3 JIT 编译优化（缩短冷启动热身期）

**现状**：冷启动 JIT 编译耗时 25.5 秒

**优化建议**：

```bash
-XX:+TieredCompilation              # 分层编译（默认开启，显式声明）
-XX:ReservedCodeCacheSize=256m      # 增大 Code Cache 防止 JIT 降级
-XX:+UseCodeCacheFlushing           # Code Cache 满时自动清理
```

**应用层优化**：在 Spring Boot 配置中启用 AOT（Ahead-Of-Time）预编译：

```yaml
# application.yml
spring:
  aot:
    enabled: true
```

**预期效果**：
- 减少 JIT 热身期对首批请求的影响
- 提升 Code Cache 容量，避免 JIT 反优化

---

### 3.4 HikariCP 连接池优化

**现状**：最大连接 50，初始建立 20，全部空闲

**问题**：连接数过多，数据库端维护代价高

**优化建议**：

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20         # 从 50 降至 20（与 CPU 核数 * 2 对齐）
      minimum-idle: 5               # 空闲连接从 20 降至 5
      connection-timeout: 30000     # 30s 连接超时
      idle-timeout: 600000          # 10 分钟空闲超时
      max-lifetime: 1800000         # 30 分钟连接最大生命周期
      keepalive-time: 60000         # 60s 保活检测
      pool-name: BurndownHikariPool
      leak-detection-threshold: 60000  # 60s 连接泄漏检测
```

**预期效果**：
- 减少 DB 端无效连接，释放数据库资源
- 降低连接池内存占用约 30%

---

### 3.5 GC 日志与监控（生产必备）

**现状**：无 GC 日志输出，生产排查困难

**优化建议**：

```bash
-Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags:filecount=5,filesize=20m
-Xlog:gc+heap=debug
-Xlog:safepoint
```

**预期效果**：
- 完整 GC 日志便于分析停顿原因
- 文件滚动避免磁盘占满

---

### 3.6 线程池优化（Tomcat）

**现状**：Tomcat 线程池使用默认配置

**优化建议**：

```yaml
# application.yml
server:
  tomcat:
    threads:
      max: 200                  # 最大工作线程（默认 200，可视并发调整）
      min-spare: 10             # 最小空闲线程（默认 10）
    accept-count: 100           # 等待队列长度
    connection-timeout: 20000   # 连接超时 20s
    max-connections: 8192       # 最大连接数
```

---

## 四、优化后目标预期

| 指标 | 当前值 | 优化目标 | 改善幅度 |
|------|--------|----------|----------|
| 堆内存使用率（空载） | 25.4% | 40-50%（Xms=Xmx 后） | 更稳定 |
| Young GC 停顿 | 20ms | < 10ms | ↓50% |
| GC 触发原因 | Metadata GC Threshold | Heap Occupation | 更可预期 |
| 冷启动时间 | 17.4s | 12-15s | ↓15-30% |
| 连接池空闲连接 | 20 | 5 | ↓75% |
| BLOCKED 线程 | 0 | 0 | 保持 ✅ |

---

## 五、完整优化 JVM 启动参数

下方为建议的完整 JVM 参数，可直接更新 `k8s/backend-hostpath.yaml`：

```yaml
args:
- -Xms512m
- -Xmx512m
- -XX:MetaspaceSize=128m
- -XX:MaxMetaspaceSize=256m
- -XX:+UseG1GC
- -XX:MaxGCPauseMillis=100
- -XX:G1HeapRegionSize=8m
- -XX:G1NewSizePercent=20
- -XX:G1MaxNewSizePercent=40
- -XX:+UseStringDeduplication
- -XX:+TieredCompilation
- -XX:ReservedCodeCacheSize=256m
- -XX:+UseCodeCacheFlushing
- -XX:+HeapDumpOnOutOfMemoryError
- -XX:HeapDumpPath=/tmp/heapdump.hprof
- -Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags:filecount=5,filesize=20m
- -Djava.security.egd=file:/dev/./urandom
- -jar
- /app/burndown-management-1.0.0.jar
- --spring.profiles.active=docker
```

---

## 六、后续监控建议

### 6.1 持续监控指标（通过 Prometheus + Grafana）

```promql
# 堆内存使用率
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# GC 停顿时间（Young GC 频率）
rate(jvm_gc_pause_seconds_sum[5m])

# GC 停顿次数
rate(jvm_gc_pause_seconds_count[5m])

# 线程数
jvm_threads_live_threads

# CPU 使用率
process_cpu_usage

# HikariCP 活跃连接
hikaricp_connections_active
```

### 6.2 告警阈值建议

| 指标 | 告警阈值 | 级别 |
|------|----------|------|
| 堆内存使用率 | > 80% | 🔴 严重 |
| Young GC 停顿 | > 200ms | 🟡 警告 |
| Full GC 频率 | > 1次/小时 | 🔴 严重 |
| BLOCKED 线程数 | > 5 | 🟡 警告 |
| HikariCP 等待线程 | > 0 持续 30s | 🟡 警告 |
| 连接池超时次数 | > 0 | 🔴 严重 |

### 6.3 定期压测验证

```bash
# 优化后运行压力测试验证效果
cd /myapp/test/pressure
python3 scenario_pressure_test.py

# 对比报告
open summary_report.html
```

---

## 七、参考资料

- [G1GC 官方文档](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html)
- [HikariCP 配置说明](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
- [JVM 统一日志框架](https://openjdk.org/jeps/158)

---

**文档版本**: 1.0
**作者**: Claude Code 自动生成（基于实时 Actuator 数据）
**下次复查**: 压测后更新（建议压测 5/20/50 并发场景）
