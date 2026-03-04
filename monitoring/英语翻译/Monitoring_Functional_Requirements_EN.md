# Backend Monitoring Functional Requirements (Real-time Monitoring for Java Applications)

## 1. Purpose

Introduce a backend runtime monitoring capability for the current system to collect, visualize, and alert on key Java service metrics in real time, including CPU, memory, JVM, and traffic metrics, so that troubleshooting efficiency is improved and future debugging/stability optimization is better supported.

## 2. Background and Problems

Current runtime observability has clear gaps: no unified metric panel, no deep JVM metrics, no API traffic/slow request monitoring, and no alert closed loop.

## 3. Objectives

1. Real-time monitoring of key backend Java runtime metrics.
2. Threshold alerts for abnormal fluctuations (high CPU, high memory, frequent Full GC, slow requests).
3. Visual dashboards with trend analysis by time window.
4. Fast issue localization in dev/test, with smooth extension to production.

## 4. Monitoring Scope

### 4.1 Host and Process Resources
- CPU usage (system/process)
- Memory usage (system memory/process resident memory)
- Disk usage (optional)
- Network traffic (optional)

### 4.2 JVM Metrics
- Heap usage (used/committed/max)
- Non-Heap usage
- GC count and duration (by collector)
- Thread count (total/daemon/peak)
- Class loading count (loaded/unloaded)

### 4.3 Application Traffic and Performance Metrics
- Total request volume (QPS / RPM)
- Per-endpoint request volume (by URI/method)
- Response latency (P50/P90/P95/P99)
- Error rate (4xx/5xx)
- Slow request count (above threshold)

### 4.4 Middleware and Dependencies (Recommended)
- DB connection pool: active/idle/pending connections
- SQL latency distribution (optional)
- Cache hit rate (if Redis is used)

## 5. Functional Requirements

### 5.1 Metrics Collection (P0)
- Automatically collect key metrics from Java applications and hosts
- Configurable collection interval (default: 15s)
- Collection failures must be logged and observable

### 5.2 Real-time Dashboard (P0)
- Provide unified monitoring dashboards (overview + detail pages)
- Support filtering by service/instance/time range
- Charts support near real-time refresh (default: 10s~30s)

Dashboard recommendations:
- System overview: CPU, memory, error rate, request volume
- JVM panel: Heap, GC, threads
- API performance: top slow endpoints and error endpoint ranking

### 5.3 Alerting Mechanism (P0)
- Support static-threshold alerts
- Support sustained trigger strategies (e.g., threshold exceeded for 3 consecutive periods)
- Support alert recovery notifications
- Alert channels: email/WeCom/DingTalk (at least one)

### 5.4 Log-Metric Correlation (P1)
- Quickly jump to relevant time-range logs after an alert
- Support traceId-based lookup (if tracing is integrated)

### 5.5 Historical Trend Analysis (P1)
- Retain historical data and support daily/weekly trend analysis
- Support report export (CSV/images)

## 6. Non-functional Requirements

### 6.1 Performance
- Monitoring overhead on application performance < 5%
- Dashboard query response time: 95% requests < 2s

### 6.2 Availability
- Monitoring system availability target: 99.9%
- Retry support for transient collection failures

### 6.3 Security
- Monitoring pages require authenticated access
- Metrics endpoints require authentication and authorization
- Sensitive information masking (e.g., DB connection strings)

### 6.4 Scalability
- Support custom business metrics
- Support horizontal scaling for multi-instance deployment

## 7. Recommended Technical Solution

Recommended stack:
- Instrumentation: Spring Boot Actuator + Micrometer
- Metric storage: Prometheus
- Visualization: Grafana
- Alerting: Alertmanager

Collection pipeline:
1. Java backend exposes `/actuator/prometheus`
2. Prometheus scrapes metrics periodically
3. Grafana queries Prometheus and renders charts
4. Alertmanager triggers notifications based on rules

## 8. Alert Rule Recommendations

| Alert Item | Condition | Duration | Severity |
|---|---|---|---|
| High CPU | CPU > 80% | 5 min | High |
| High Memory | Memory > 85% | 5 min | High |
| Frequent Full GC | Full GC count > threshold | 10 min | High |
| Rising Error Rate | 5xx ratio > 5% | 3 min | High |
| Slow Response | P95 > 2s | 5 min | Medium |
| Request Surge | QPS > 200% baseline | 3 min | Medium |

## 9. Acceptance Criteria

1. Monitoring page can display real-time CPU/memory/JVM/traffic/error-rate metrics.
2. Metrics refresh latency does not exceed 30 seconds.
3. Threshold alerts can be triggered and notifications can be received.
4. Last 7-day historical trends are available.
5. Monitoring integration does not affect core workflow stability.

## 10. Implementation Plan

### P0 (1~2 weeks)
- Integrate Actuator + Micrometer
- Deploy Prometheus + Grafana
- Deliver baseline dashboard + 4 core alerts

### P1 (2~4 weeks)
- Add deep JVM metrics and slow endpoint analysis
- Add alert grading and recovery notifications
- Add historical trend analysis and export

### P2 (4~6 weeks)
- Log-trace correlation
- Custom business metrics system
- Unified multi-environment monitoring view

## 11. Risks and Mitigation

| Risk | Description | Mitigation |
|---|---|---|
| Storage pressure from too many metrics | High-cardinality labels bloat Prometheus | Standardize labels and control cardinality |
| Alert false positives | Unreasonable thresholds | Add duration and combined conditions |
| Monitoring unavailable | Single point in monitoring components | HA deployment in a later phase |
| Security risk | Metrics may expose sensitive system details | AuthN/AuthZ, network isolation, masking |
