# Backend Monitoring Functional Requirements (Real-time Monitoring for Java Applications)

## 1. Purpose

This document defines a backend runtime monitoring capability for the current system. It focuses on real-time collection, visualization, and alerting of key Java service metrics, covering CPU, memory, JVM, traffic, and related dimensions to improve troubleshooting efficiency and support ongoing debugging and stability optimization.

---

## 2. Background and Problem Statement

Although the current system is functionally mature, observability at runtime is insufficient:

- No unified metrics dashboard for real-time system health visibility
- Lack of deep JVM metrics, making it difficult to detect GC/thread/heap anomalies in time
- Lack of API traffic and slow-request monitoring, causing high performance troubleshooting cost
- No alerting closed loop; issues are often discovered only after user feedback

Therefore, a full monitoring chain is required: **collection -> storage -> visualization -> alerting**.

---

## 3. Objectives

1. Real-time monitoring of key runtime metrics for backend Java services
2. Threshold-based alerting for abnormal fluctuations (high CPU, high memory, frequent Full GC, slow requests)
3. Visual dashboards with trend analysis by time window
4. Fast issue localization in dev/test, with smooth extension to production

---

## 4. Monitoring Scope

## 4.1 Host and Process Resources

- CPU usage (system/process)
- Memory usage (system memory/process resident memory)
- Disk usage (optional)
- Network traffic (optional)

## 4.2 JVM Metrics

- Heap usage (used/committed/max)
- Non-Heap usage
- Young/Old generation usage
- GC count and duration (by collector)
- Thread count (total/daemon/peak)
- Class loading count (loaded/unloaded)
- JVM startup parameters and runtime metadata (for troubleshooting)

## 4.3 Application Traffic and Performance Metrics

- Total request volume (QPS/RPM)
- Per-endpoint request volume (by URI/method)
- Response latency (P50/P90/P95/P99)
- Error rate (4xx/5xx)
- Slow request count (above threshold)
- Online users/active sessions (optional)

## 4.4 Middleware and Dependencies (Recommended)

- Database connection pool: active/idle/pending connections
- SQL execution latency distribution (optional)
- Cache hit rate (if Redis is used)

---

## 5. Target Users

- Backend developers
- Test engineers
- Ops/platform engineers (later phase)
- Project owners (overall health view)

---

## 6. Functional Requirements

## 6.1 Metrics Collection

### Requirement Points
- The system should automatically collect key metrics from Java applications and hosts
- Configurable collection interval (default: 15s)
- Collection failures must be logged and observable

### Priority
- P0

## 6.2 Real-time Monitoring Dashboard

### Requirement Points
- Provide a unified monitoring dashboard (overview + detail pages)
- Support filtering by service/instance/time range
- Support near real-time chart refresh (default: 10s~30s)

### Dashboard Recommendations
- System overview: CPU, memory, error rate, request volume
- JVM panel: heap, GC, threads
- API performance: top slow endpoints and error endpoint ranking

### Priority
- P0

## 6.3 Alerting Mechanism

### Requirement Points
- Support threshold alerts (static thresholds)
- Support continuous trigger strategy (e.g., exceed threshold for 3 consecutive periods)
- Support alert recovery notifications
- Support alert channels: email/WeCom/DingTalk (at least one)

### Priority
- P0

## 6.4 Log-Metric Correlation (Recommended)

### Requirement Points
- After an alert is triggered, quickly jump to logs for the corresponding time range
- Support traceId-based lookup (if tracing is integrated)

### Priority
- P1

## 6.5 Historical Trend Analysis

### Requirement Points
- Retain historical monitoring data; support daily/weekly trend analysis
- Support monitoring report export (CSV/images)

### Priority
- P1

---

## 7. Non-functional Requirements

## 7.1 Performance
- Monitoring overhead on application performance should be < 5%
- Dashboard query response: 95% requests < 2s

## 7.2 Availability
- Monitoring system availability target: 99.9%
- Transient collector-side failures should support retry

## 7.3 Security
- Monitoring pages require authenticated access
- Metrics endpoints require authentication and authorization
- Sensitive information should be masked (e.g., DB connection strings)

## 7.4 Scalability
- Support custom business metrics (e.g., Sprint computation latency)
- Support horizontal scaling for multi-instance deployments

---

## 8. Recommended Technical Solution

## 8.1 Technology Stack

Industry-standard recommendation:

- Instrumentation: `Spring Boot Actuator + Micrometer`
- Metrics storage: `Prometheus`
- Visualization: `Grafana`
- Alerting: `Alertmanager`

## 8.2 Collection Pipeline

1. Java backend exposes metrics via `/actuator/prometheus`
2. Prometheus scrapes metrics periodically
3. Grafana reads from Prometheus and renders dashboards
4. Alertmanager sends notifications based on alert rules

## 8.3 Minimum Viable Version (MVP)

- Integrate JVM/system/HTTP baseline metrics
- Build one overview dashboard
- Configure four key alert rules:
  - CPU > 80% continuously
  - Memory > 85% continuously
  - 5xx error rate > 5%
  - P95 latency > 2s

---

## 9. Alert Rule Recommendations

| Alert Item | Condition | Duration | Severity |
|---|---|---|---|
| High CPU | CPU > 80% | 5 min | High |
| High Memory | Memory > 85% | 5 min | High |
| Frequent Full GC | Full GC count > threshold | 10 min | High |
| Rising Error Rate | 5xx ratio > 5% | 3 min | High |
| Slow Response | P95 > 2s | 5 min | Medium |
| Request Surge | QPS > 200% baseline | 3 min | Medium |

---

## 10. Roles and Permissions

- Admin: view all monitoring data and configure alert rules
- Dev/Test: view monitoring and alerts; cannot modify global strategies (can be adjusted as needed)
- Recommended integration with existing RBAC model to reuse users and roles

---

## 11. Acceptance Criteria

1. Monitoring page displays real-time CPU/memory/JVM/traffic/error-rate metrics
2. Metrics refresh works normally with latency <= 30s
3. Threshold alerts can be triggered and notifications are received
4. Historical trends for the last 7 days are available
5. Monitoring integration does not impact core business flow stability

---

## 12. Implementation Plan

## P0 (1~2 weeks)
- Integrate Actuator + Micrometer
- Deploy Prometheus + Grafana
- Deliver baseline dashboard + 4 core alerts

## P1 (2~4 weeks)
- Add deep JVM metrics and slow-endpoint analysis
- Add alert severity and recovery notifications
- Add historical trend analysis and export

## P2 (4~6 weeks)
- Log-trace correlation
- Custom business metrics system
- Unified cross-environment monitoring view

---

## 13. Risks and Mitigation

| Risk | Description | Mitigation |
|---|---|---|
| Storage pressure from too many metrics | High-cardinality labels bloat Prometheus | Standardize and control label cardinality |
| Frequent false positives | Unreasonable thresholds | Add sustained-duration and multi-condition rules |
| Monitoring system unavailable | Single-point failures in monitoring components | HA deployment for Prometheus/Grafana (later phase) |
| Security risk | Metrics may expose system information | Authentication, network isolation, sensitive-data masking |

---

## 14. Future Enhancements

- Introduce distributed tracing (OpenTelemetry + Tempo/Jaeger)
- Establish SLO/SLI framework (availability, latency, error rate)
- Integrate with release pipeline to observe pre/post-release metric changes
- Build incident review templates to accumulate monitoring and troubleshooting best practices
