# Backend Monitoring System Sequence Diagram Design

This document provides the core sequence flows for the monitoring solution:
1. Metrics collection and visualization flow
2. Alert triggering and notification flow
3. Exception and degradation handling flow

---

## 1. Metrics Collection and Visualization Sequence Diagram

```mermaid
sequenceDiagram
  autonumber
  participant APP as Java Backend
  participant PRO as Prometheus
  participant GRA as Grafana
  participant USER as Dev/Test/Admin

  PRO->>APP: Periodically scrape /actuator/prometheus (15s)
  APP-->>PRO: Return JVM/HTTP/Process/DB metrics
  PRO->>PRO: Write into TSDB

  USER->>GRA: Open monitoring dashboard
  GRA->>PRO: PromQL query (time window + instance dimension)
  PRO-->>GRA: Return time-series data
  GRA-->>USER: Render real-time charts (CPU/Memory/JVM/Traffic)

  loop Auto Refresh
    GRA->>PRO: Periodic query (10s~30s)
    PRO-->>GRA: Incremental data
    GRA-->>USER: Refresh charts
  end
```

---

## 2. Alert Triggering and Notification Sequence Diagram

```mermaid
sequenceDiagram
  autonumber
  participant PRO as Prometheus
  participant AR as Alert Rule Engine
  participant AM as Alertmanager
  participant CH as Notification Channels
  participant ONCALL as On-call Engineer
  participant GRA as Grafana

  PRO->>AR: Execute rule evaluation periodically
  AR->>AR: Evaluate threshold and duration(for)

  alt Alert Triggered
    AR-->>AM: Send alert event (firing)
    AM->>AM: Group/Inhibit/Route
    AM-->>CH: Send alert notification
    CH-->>ONCALL: Push alert message

    ONCALL->>GRA: Open alert dashboard for investigation
    GRA->>PRO: Query related metric trends
    PRO-->>GRA: Return anomaly-period data
    GRA-->>ONCALL: Display root-cause clues
  else Alert Recovered
    AR-->>AM: Send recovery event (resolved)
    AM-->>CH: Send recovery notification
    CH-->>ONCALL: Recovery acknowledged
  end
```

---

## 3. Exception and Degradation Handling Sequence Diagram

```mermaid
sequenceDiagram
  autonumber
  participant APP as Java Backend
  participant PRO as Prometheus
  participant GRA as Grafana
  participant USER as User

  alt Application metrics endpoint failure
    PRO->>APP: Scrape /actuator/prometheus
    APP--x PRO: Timeout/5xx
    PRO->>PRO: Mark target down
    PRO-->>GRA: Data missing/instance offline status
    GRA-->>USER: Show instance anomaly indicator
  else Prometheus temporarily unavailable
    USER->>GRA: Query monitoring charts
    GRA->>PRO: Query request
    PRO--x GRA: Unreachable
    GRA-->>USER: Show data-source-unavailable degradation message
  else Single-metric jitter
    PRO->>PRO: Rule evaluation
    PRO->>PRO: Do not trigger alert (for-window not met)
    PRO-->>GRA: Display normal fluctuation curve
    GRA-->>USER: Suggest continuous observation
  end
```

---

## 4. Sequence Design Notes

1. **Pull-based collection model**: Prometheus actively scrapes metrics, reducing application-side push complexity.
2. **Rule-evaluate-before-notify**: Alert decisions are centralized in rule evaluation, reducing false positives.
3. **Alert noise reduction**: Use `for`, grouping, inhibition, and silence to prevent notification storms.
4. **Visualization-troubleshooting closed loop**: After alerts, users can quickly drill down in Grafana.
5. **Degradation visibility**: Collection failures and data-source outages must be visible on dashboards to avoid silent failures.
