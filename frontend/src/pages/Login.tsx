import React from 'react'
import { Form, Input, Button, Card, message } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { authApi, LoginRequest } from '../api/auth'
import { useAuthStore } from '../store/authStore'

const Login: React.FC = () => {
  const navigate = useNavigate()
  const setAuth = useAuthStore((state) => state.setAuth)
  const { t } = useTranslation()

  const loginMutation = useMutation({
    mutationFn: (data: LoginRequest) => authApi.login(data),
    onSuccess: (response) => {
      setAuth(response.data.user, response.data.token)
      message.success(t('auth.login.success'))
      navigate('/dashboard')
    },
    onError: (error: any) => {
      // 显示后端返回的具体错误信息
      const errorMessage = error.response?.data?.message || t('auth.login.failed')
      message.error(errorMessage)
    },
  })

  const onFinish = (values: LoginRequest) => {
    loginMutation.mutate(values)
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: '#f0f2f5' }}>
      <Card title={t('auth.login.title')} style={{ width: 400 }}>
        <Form name="login" onFinish={onFinish} autoComplete="off">
          <Form.Item name="username" rules={[{ required: true, message: t('auth.login.usernameRequired') }]}>
            <Input prefix={<UserOutlined />} placeholder={t('auth.login.username')} />
          </Form.Item>

          <Form.Item name="password" rules={[{ required: true, message: t('auth.login.passwordRequired') }]}>
            <Input.Password prefix={<LockOutlined />} placeholder={t('auth.login.password')} />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={loginMutation.isPending}>
              {t('auth.login.loginButton')}
            </Button>
          </Form.Item>

          <div style={{ textAlign: 'center' }}>
            {t('auth.login.noAccount')} <Link to="/register">{t('auth.login.register')}</Link>
          </div>
        </Form>
      </Card>
    </div>
  )
}

export default Login
