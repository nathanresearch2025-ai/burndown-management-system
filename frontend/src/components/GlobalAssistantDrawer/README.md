# 全局站会助手侧拉框

## 功能说明

在整个应用的所有页面（使用 MainLayout 的页面）的右侧添加了一个全局可访问的站会助手对话框。

## 使用方式

1. **触发按钮**：在任何页面的右侧中间位置，有一个固定的蓝色按钮
   - 点击可以展开/收起侧拉框
   - 按钮会随着抽屉的打开/关闭而移动位置

2. **选择项目和 Sprint**：
   - 打开抽屉后，在顶部可以选择项目
   - 选择项目后，可以进一步选择该项目下的 Sprint（可选）

3. **开始对话**：
   - 选择项目后，StandupChat 组件会自动加载
   - 可以开始与站会助手进行对话

## 实现细节

- **组件位置**：`/src/components/GlobalAssistantDrawer/index.tsx`
- **集成位置**：在 `MainLayout.tsx` 中全局引入
- **状态管理**：使用本地 state 管理抽屉的显示/隐藏状态
- **样式特点**：
  - 固定位置的触发按钮（右侧中间）
  - 600px 宽度的抽屉
  - 平滑的过渡动画
  - 响应式设计

## 影响范围

所有使用 MainLayout 的页面都会自动获得这个全局助手功能：
- Dashboard
- ProjectList
- SprintBoard
- TaskBoard
- BurndownChart
- RoleManagement

## 未来改进

- 可以添加快捷键支持（如 Ctrl+K 或 Cmd+K）
- 可以记住用户上次选择的项目和 Sprint
- 可以添加更多的对话历史记录功能
