# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Burndown Management System - A full-stack Scrum project management application with burndown chart visualization, built with Spring Boot 3.2 (Java 21) backend and React 18 (TypeScript) frontend.

**Tech Stack:**
- Backend: Spring Boot 3.2, Java 21, PostgreSQL 16, Redis 7, Spring Security + JWT
- Frontend: React 18, TypeScript 5, Ant Design 5, Vite 5, Zustand, React Query, ECharts
- Deployment: Kubernetes, Docker
- Monitoring: Prometheus, Grafana, Alertmanager

## Development Commands

### Backend (Spring Boot)

```bash
cd backend

# Run locally (requires PostgreSQL and Redis running)
mvn spring-boot:run

# Build JAR
mvn clean package

# Run tests
mvn test

# Skip tests during build
mvn clean package -DskipTests
```

Backend runs on `http://localhost:8080` with context path `/api/v1`
- API docs: `http://localhost:8080/api/v1/swagger-ui.html`
- Metrics: `http://localhost:8080/api/v1/actuator/prometheus`

### Frontend (React + Vite)

```bash
cd frontend

# Install dependencies
npm install

# Run dev server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

Frontend dev server runs on `http://localhost:5173`

### Database Setup

```bash
# Create database
psql -U postgres -c "CREATE DATABASE burndown_db;"

# Initialize schema and data
psql -U postgres -d burndown_db -f backend/init.sql
```

### Deployment

```bash
# Full deployment (rebuild images without cache)
./deploy.sh

# Quick deployment (use Docker cache)
./deploy.sh --cache

# Deploy monitoring only
./deploy.sh --monitoring-only
```

The deploy script:
1. Builds Docker images for backend and frontend
2. Deploys PostgreSQL, backend, and frontend to Kubernetes
3. Initializes database with schema and sample data
4. Optionally deploys Prometheus, Grafana, and Alertmanager

Access deployed services:
- Frontend: `http://<node-ip>:30173`
- Backend API: `http://<node-ip>:30080`
- PostgreSQL: `<node-ip>:30432`
- Prometheus: `http://<node-ip>:30090`
- Grafana: `http://<node-ip>:30300` (admin/admin123)
- Alertmanager: `http://<node-ip>:30093`

## Architecture

### Backend Structure

```
backend/src/main/java/com/burndown/
├── config/          # Security, JWT, I18n configuration
├── controller/      # REST API endpoints
├── dto/            # Request/response DTOs
├── entity/         # JPA entities (User, Project, Sprint, Task, WorkLog, etc.)
├── exception/      # Custom exceptions and handlers
├── filter/         # JWT authentication filter
├── repository/     # Spring Data JPA repositories
└── service/        # Business logic layer
```

**Key entities:**
- `User` - User accounts with RBAC (roles/permissions)
- `Project` - Scrum projects with owner and settings
- `Sprint` - Time-boxed iterations with capacity tracking
- `Task` - Work items with status, priority, story points
- `WorkLog` - Time tracking entries
- `BurndownPoint` - Daily burndown chart data points

**Authentication:** JWT-based with Spring Security. Token expiration configured in `application.yml` (default 7 days).

**Database:** PostgreSQL with JPA/Hibernate. Schema managed via `init.sql` (ddl-auto: none). HikariCP connection pool (max 20, min 5).

**Monitoring:** Spring Boot Actuator exposes Prometheus metrics at `/api/v1/actuator/prometheus`. Includes JVM, HTTP, database connection pool metrics.

### Frontend Structure

```
frontend/src/
├── api/            # Axios API client and endpoint definitions
├── components/     # Reusable React components
│   ├── Layout/     # App layout components
│   └── Modals/     # Modal dialogs
├── hooks/          # Custom React hooks
├── i18n/           # Internationalization (i18next)
│   └── locales/    # Translation files (en-US, zh-CN)
├── pages/          # Page components (Login, Dashboard, TaskBoard, etc.)
├── store/          # Zustand state management
├── App.tsx         # Root component with routing
└── main.tsx        # Application entry point
```

**State Management:** Zustand for global state, React Query for server state caching.

**Routing:** React Router v6 with protected routes requiring authentication.

**UI Components:** Ant Design 5 component library with custom theming.

**Charts:** ECharts for burndown chart visualization.

**Drag & Drop:** @hello-pangea/dnd for task board kanban functionality.

### Key Workflows

**Burndown Chart Calculation:**
1. `BurndownController` calculates daily remaining work
2. Aggregates story points from incomplete tasks
3. Stores snapshots in `burndown_points` table
4. Frontend fetches and visualizes with ECharts

**Task Status Flow:**
TODO → IN_PROGRESS → IN_REVIEW → DONE

**Sprint Lifecycle:**
PLANNED → ACTIVE → COMPLETED

## Configuration Files

- `backend/src/main/resources/application.yml` - Main Spring Boot config (datasource, Redis, JWT, Actuator)
- `backend/src/main/resources/application-docker.yml` - Docker-specific overrides
- `backend/init.sql` - Complete database schema and sample data
- `frontend/vite.config.ts` - Vite build configuration
- `k8s/*.yaml` - Kubernetes deployment manifests
- `monitoring/k8s-monitoring-all.yaml` - Complete monitoring stack

## Important Notes

- **CORS:** Backend configured for specific origins in `SecurityConfig.java`. Update `setAllowedOrigins()` for new frontend URLs.
- **JWT Secret:** Change `jwt.secret` in production (min 256 bits).
- **Database Persistence:** K8s deployments use `emptyDir` - data lost on pod restart. Use PersistentVolume for production.
- **Host Network:** K8s deployments use `hostNetwork: true` - watch for port conflicts.
- **Docker Cache:** Always use `./deploy.sh` (no cache) after code changes to ensure fresh builds.
- **Monitoring Alerts:** Pre-configured for high CPU (>80%), memory (>85%), error rate (>5%), slow responses (>2s).

## Troubleshooting

**Backend won't start:**
```bash
# Check PostgreSQL connection
kubectl exec -it <postgres-pod> -- psql -U postgres -d burndown_db

# View backend logs
kubectl logs -l app=burndown-backend

# Check if database is initialized
kubectl exec -it <postgres-pod> -- psql -U postgres -d burndown_db -c "\dt"
```

**Frontend API calls fail:**
```bash
# Check backend health
curl http://localhost:8080/api/v1/actuator/health

# Verify CORS configuration in SecurityConfig.java
# Check network connectivity between frontend and backend
```

**Deployment issues:**
```bash
# View all pods
kubectl get pods

# Describe pod for events
kubectl describe pod <pod-name>

# Check if images built correctly
docker images | grep burndown

# Force rebuild without cache
./deploy.sh
```

# 压测运行文件的路径
/myapp/test/pressure/scenario_pressure_test.py

# 压测汇总报告路径
/myapp/test/pressure/summary_report.html

# 后端代码目录
/myapp/backend/

# 优化文件相关内容目录
/myapp/docs/performance/

# 全接口测试脚本
/myapp/test/all-interface-test/api_test.py

# 部署shell文件
/myapp/deploy.sh



