import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import enUS from 'antd/locale/en_US'
import { useTranslation } from 'react-i18next'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import ProjectList from './pages/ProjectList'
import SprintBoard from './pages/SprintBoard'
import TaskBoard from './pages/TaskBoard'
import BurndownChart from './pages/BurndownChart'
import RoleManagement from './pages/RoleManagement'
import { useAuthStore } from './store/authStore'

const PrivateRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = useAuthStore((state) => state.token)
  return token ? <>{children}</> : <Navigate to="/login" />
}

const App: React.FC = () => {
  const { i18n } = useTranslation()
  const antdLocale = i18n.language === 'en-US' ? enUS : zhCN

  return (
    <ConfigProvider locale={antdLocale}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route
          path="/dashboard"
          element={
            <PrivateRoute>
              <Dashboard />
            </PrivateRoute>
          }
        />
        <Route
          path="/projects"
          element={
            <PrivateRoute>
              <ProjectList />
            </PrivateRoute>
          }
        />
        <Route
          path="/sprints/:projectId"
          element={
            <PrivateRoute>
              <SprintBoard />
            </PrivateRoute>
          }
        />
        <Route
          path="/tasks/:sprintId"
          element={
            <PrivateRoute>
              <TaskBoard />
            </PrivateRoute>
          }
        />
        <Route
          path="/burndown/:sprintId"
          element={
            <PrivateRoute>
              <BurndownChart />
            </PrivateRoute>
          }
        />
        <Route
          path="/roles"
          element={
            <PrivateRoute>
              <RoleManagement />
            </PrivateRoute>
          }
        />
        <Route path="/" element={<Navigate to="/dashboard" />} />
      </Routes>
    </ConfigProvider>
  )
}

export default App
