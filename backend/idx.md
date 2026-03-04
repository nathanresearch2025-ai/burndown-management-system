# 数据库索引配置文档

本文档记录了 Burndown Management System 数据库中的所有索引配置。

## 索引统计

- **总索引数**: 26个
- **覆盖表数**: 10个
- **最后更新**: 2026-03-04

---

## 1. 用户表 (users)

| 索引名称 | 字段 | 用途 |
|---------|------|------|
| `idx_users_username` | username | 用户名查询优化 |
| `idx_users_email` | email | 邮箱查询优化 |
| `idx_users_is_active` | is_active | 活跃用户过滤 |

**查询场景**: 登录验证、用户搜索、活跃用户列表

---

## 2. 项目表 (projects)

| 索引名称 | 字段 | 用途 |
|---------|------|------|
| `idx_projects_owner_id` | owner_id | 按所有者查询项目 |
| `idx_projects_status` | status | 按状态过滤项目 |
| `idx_projects_project_key` | project_key | 项目键唯一性查询 |

**查询场景**: 用户项目列表、项目状态筛选、项目键验证

---

## 3. 冲刺表 (sprints)

| 索引名称 | 字段 | 用途 |
|---------|------|------|
| `idx_sprints_project_id` | project_id | 查询项目下的所有冲刺 |
| `idx_sprints_status` | status | 按状态过滤冲刺 |

**查询场景**: 项目冲刺列表、活跃冲刺查询、冲刺状态筛选

---

## 4. 任务表 (tasks)

| 索引名称 | 字段 | 用途 |
|---------|------|------|
| `idx_tasks_project_id` | project_id | 查询项目下的所有任务 |
| `idx_tasks_sprint_id` | sprint_id | 查询冲刺下的所有任务 |
| `idx_tasks_assignee_id` | assignee_id | 查询指派给某用户的任务 |
| `idx_tasks_status` | status | 按状态过滤任务 |
| `idx_tasks_task_key` | task_key | 任务键唯一性查询 |

**查询场景**: 任务看板、用户任务列表、任务状态筛选、任务键验证

---

## 5. 工作日志表 (work_logs)

| 索引名称 | 字段 | 用途 |
|---------|------|------|
| `idx_work_logs_task_id` | task_id | 查询任务的工作日志 |
| `idx_work_logs_user_id` | user_id | 查询用户的工作日志 |
| `idx_work_logs_work_date` | work_date | 按日期范围查询工作日志 |

**查询场景**: 任务工时统计、用户工时报表、日期范围工时查询

---

## 6. 燃尽图数据点表 (burndown_points)

| 索引名称 | 字段 | 用途 |
|---------|------|------|
| `idx_burndown_points_sprint_id` | sprint_id | 查询冲刺的燃尽图数据 |
| `idx_burndown_points_date` | point_date | 按日期查询燃尽图数据 |

**查询场景**: 燃尽图渲染、冲刺进度追踪、历史数据分析

---

## 7. 角色表 (roles)

| 索引名称 | 字段 | 用途 |
|---------|------|------|
| `idx_roles_code` | code | 角色代码查询 |
| `idx_roles_is_active` | is_active | 活跃角色过滤 |

**查询场景**: 角色权限验证、角色列表查询、活跃角色筛选

---

## 8. 权限表 (permissions)

| 索引名称 | 字段 | 用途 |
|---------|------|------|
| `idx_permissions_code` | code | 权限代码查询 |
| `idx_permissions_resource` | resource | 按资源查询权限 |

**查询场景**: 权限验证、资源权限查询、权限列表

---

## 9. 用户角色关联表 (user_roles)

| 索引名称 | 字段 | 用途 |
|---------|------|------|
| `idx_user_roles_user_id` | user_id | 查询用户的所有角色 |
| `idx_user_roles_role_id` | role_id | 查询角色下的所有用户 |

**查询场景**: 用户权限查询、角色成员列表、RBAC验证

**重要性**: 🔥 高频查询索引，用于JWT生成和权限验证

---

## 10. 角色权限关联表 (role_permissions)

| 索引名称 | 字段 | 用途 |
|---------|------|------|
| `idx_role_permissions_role_id` | role_id | 查询角色的所有权限 |
| `idx_role_permissions_permission_id` | permission_id | 查询权限关联的角色 |

**查询场景**: 角色权限查询、权限分配、RBAC验证

**重要性**: 🔥 高频查询索引，用于权限验证

---

## 索引优化建议

### 已优化的查询场景
✅ 外键关联查询（所有外键字段都有索引）
✅ 状态过滤查询（status字段索引）
✅ 日期范围查询（work_date, point_date索引）
✅ 唯一性验证（username, email, project_key, task_key索引）
✅ RBAC权限查询（user_roles, role_permissions索引）

### 潜在优化方向

#### 1. 复合索引优化
考虑为高频组合查询创建复合索引：

```sql
-- 任务表：按项目和状态查询
CREATE INDEX idx_tasks_project_status ON tasks(project_id, status);

-- 任务表：按冲刺和状态查询
CREATE INDEX idx_tasks_sprint_status ON tasks(sprint_id, status);

-- 工作日志：按用户和日期范围查询
CREATE INDEX idx_work_logs_user_date ON work_logs(user_id, work_date);

-- 冲刺表：按项目和状态查询
CREATE INDEX idx_sprints_project_status ON sprints(project_id, status);
```

#### 2. 部分索引优化
针对特定条件的查询创建部分索引：

```sql
-- 只索引活跃用户
CREATE INDEX idx_users_active ON users(username) WHERE is_active = true;

-- 只索引未完成的任务
CREATE INDEX idx_tasks_incomplete ON tasks(sprint_id, assignee_id)
WHERE status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW');
```

#### 3. 覆盖索引优化
为频繁查询的字段组合创建覆盖索引：

```sql
-- 任务列表查询（避免回表）
CREATE INDEX idx_tasks_list_covering ON tasks(project_id, status, assignee_id, priority);
```

---

## 索引维护

### 监控指标
- 索引使用率（pg_stat_user_indexes）
- 索引大小（pg_indexes_size）
- 未使用的索引（pg_stat_user_indexes.idx_scan = 0）
- 索引膨胀率

### 维护命令

```sql
-- 查看索引使用情况
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC;

-- 查看索引大小
SELECT indexname, pg_size_pretty(pg_relation_size(indexrelid))
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY pg_relation_size(indexrelid) DESC;

-- 重建索引（解决索引膨胀）
REINDEX INDEX CONCURRENTLY idx_name;

-- 分析表统计信息
ANALYZE users;
ANALYZE tasks;
```

---

## 性能测试结果

### 权限查询优化（本次修复）
- **问题**: `ClassCastException` 导致所有请求失败
- **修复**: 将 `UserRoleRepository.findPermissionCodesByUserId` 返回类型从 `Set<String>` 改为 `List<String>`
- **影响**: 修复后端500错误，恢复所有API功能

### 压测结果（修复前）
- **基线测试**: 140请求，0%成功率，4.49 RPS
- **标准测试**: 916请求，0%成功率，14.57 RPS
- **峰值测试**: 1142请求，0%成功率，17.39 RPS

**注**: 修复后需重新运行压测验证性能

---

## 相关文档

- 数据库初始化脚本: [/myapp/backend/init.sql](/myapp/backend/init.sql)
- 压测指南: [/myapp/test/pressure/SCENARIO_TEST_GUIDE_V2.md](/myapp/test/pressure/SCENARIO_TEST_GUIDE_V2.md)
- 性能优化文档: [/myapp/docs/performance/](/myapp/docs/performance/)

---

**文档版本**: v1.0
**创建日期**: 2026-03-04
**维护者**: Development Team
