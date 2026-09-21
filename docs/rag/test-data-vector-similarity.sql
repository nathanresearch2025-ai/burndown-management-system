-- Insert sample tasks with embeddings for vector similarity testing
-- These tasks are designed to test similarity search functionality

-- User Login related tasks (similar group 1)
INSERT INTO tasks (project_id, sprint_id, task_key, title, description, type, status, priority, story_points, assignee_id, reporter_id, created_at, updated_at)
VALUES
(1, 1, 'PROJ-101', '实现用户登录功能', '开发用户登录页面，包括用户名密码验证、记住我功能、错误提示', 'FEATURE', 'TODO', 'HIGH', 5, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'PROJ-102', '优化登录界面UI', '改进登录页面的用户体验，添加动画效果和响应式设计', 'TASK', 'TODO', 'MEDIUM', 3, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'PROJ-103', '修复登录超时问题', '解决用户登录后会话超时的bug，增加token刷新机制', 'BUG', 'IN_PROGRESS', 'HIGH', 3, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'PROJ-104', '添加第三方登录支持', '集成微信、支付宝等第三方登录方式', 'FEATURE', 'TODO', 'MEDIUM', 8, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Data Export related tasks (similar group 2)
INSERT INTO tasks (project_id, sprint_id, task_key, title, description, type, status, priority, story_points, assignee_id, reporter_id, created_at, updated_at)
VALUES
(1, 1, 'PROJ-105', '实现数据导出功能', '开发Excel和CSV格式的数据导出功能，支持自定义字段选择', 'FEATURE', 'TODO', 'MEDIUM', 5, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'PROJ-106', '优化导出性能', '提升大数据量导出的性能，使用异步处理和分页导出', 'TASK', 'TODO', 'HIGH', 5, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'PROJ-107', '修复导出乱码问题', '解决中文导出时出现乱码的问题，统一使用UTF-8编码', 'BUG', 'DONE', 'HIGH', 2, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- API Integration related tasks (similar group 3)
INSERT INTO tasks (project_id, sprint_id, task_key, title, description, type, status, priority, story_points, assignee_id, reporter_id, created_at, updated_at)
VALUES
(1, 1, 'PROJ-108', '集成支付API', '对接支付宝和微信支付API，实现在线支付功能', 'FEATURE', 'IN_PROGRESS', 'CRITICAL', 8, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'PROJ-109', '集成短信API', '接入阿里云短信服务，实现验证码发送功能', 'FEATURE', 'TODO', 'HIGH', 5, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'PROJ-110', '优化API调用性能', '减少API调用次数，添加缓存机制，提升响应速度', 'TASK', 'TODO', 'MEDIUM', 3, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Database related tasks (similar group 4)
INSERT INTO tasks (project_id, sprint_id, task_key, title, description, type, status, priority, story_points, assignee_id, reporter_id, created_at, updated_at)
VALUES
(1, 1, 'PROJ-111', '数据库性能优化', '优化慢查询，添加索引，提升数据库查询性能', 'TASK', 'TODO', 'HIGH', 5, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'PROJ-112', '实现数据库备份', '配置自动备份策略，确保数据安全', 'TASK', 'TODO', 'CRITICAL', 3, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'PROJ-113', '修复数据库连接池问题', '解决高并发下连接池耗尽的问题', 'BUG', 'IN_PROGRESS', 'CRITICAL', 5, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- UI/UX related tasks (similar group 5)
INSERT INTO tasks (project_id, sprint_id, task_key, title, description, type, status, priority, story_points, assignee_id, reporter_id, created_at, updated_at)
VALUES
(1, 1, 'PROJ-114', '重构前端组件库', '使用Ant Design重构现有组件，提升UI一致性', 'TASK', 'TODO', 'MEDIUM', 8, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'PROJ-115', '优化移动端适配', '改进移动端响应式布局，提升移动端用户体验', 'TASK', 'TODO', 'MEDIUM', 5, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'PROJ-116', '修复页面加载慢问题', '优化资源加载，减少首屏加载时间', 'BUG', 'TODO', 'HIGH', 3, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
