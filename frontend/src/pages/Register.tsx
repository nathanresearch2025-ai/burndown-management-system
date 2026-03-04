import React from 'react'
import { Form, Input, Button, Card, message, Select } from 'antd'
import { UserOutlined, LockOutlined, MailOutlined, TeamOutlined } from '@ant-design/icons'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { authApi, RegisterRequest } from '../api/auth'
import { roleApi } from '../api/role'
import { useAuthStore } from '../store/authStore'

const Register: React.FC = () => {
  const navigate = useNavigate()
  const setAuth = useAuthStore((state) => state.setAuth)
  const { t } = useTranslation()
  const [form] = Form.useForm()

  // 获取可用角色列表
  const { data: rolesData, isLoading: rolesLoading } = useQuery({
    queryKey: ['availableRoles'],
    queryFn: () => roleApi.getAvailableRoles(),
  })

  const registerMutation = useMutation({
    mutationFn: (data: RegisterRequest) => authApi.register(data),
    onSuccess: (response) => {
      setAuth(response.data.user, response.data.token)
      message.success(t('auth.register.success'))
      navigate('/dashboard')
    },
    onError: (error: any) => {
      // 显示后端返回的具体错误信息
      const errorMessage = error.response?.data?.message || t('auth.register.failed')
      message.error(errorMessage)
    },
  })

  const onFinish = (values: RegisterRequest) => {
    registerMutation.mutate(values)
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: '#f0f2f5' }}>
      <Card title={t('auth.register.title')} style={{ width: 400 }}>
        <Form form={form} name="register" onFinish={onFinish} autoComplete="off">
          <Form.Item name="username" rules={[{ required: true, message: t('auth.register.usernameRequired') }]}>
            <Input prefix={<UserOutlined />} placeholder={t('auth.register.username')} />
          </Form.Item>

          <Form.Item name="email" rules={[{ required: true, message: t('auth.register.emailRequired') }, { type: 'email', message: t('auth.register.emailInvalid') }]}>
            <Input prefix={<MailOutlined />} placeholder={t('auth.register.email')} />
          </Form.Item>

          <Form.Item name="password" rules={[{ required: true, message: t('auth.register.passwordRequired') }, { min: 6, message: t('auth.register.passwordMinLength') }]}>
            <Input.Password prefix={<LockOutlined />} placeholder={t('auth.register.password')} />
          </Form.Item>

          <Form.Item name="fullName">
            <Input placeholder={t('auth.register.fullName')} />
          </Form.Item>

          <Form.Item name="roleId" rules={[{ required: true, message: t('auth.register.roleRequired') }]}>
            <Select
              placeholder={t('auth.register.selectRole')}
              loading={rolesLoading}
              suffixIcon={<TeamOutlined />}
            >
              {rolesData?.data.map((role) => (
                <Select.Option key={role.id} value={role.id}>
                  {role.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={registerMutation.isPending}>
              {t('auth.register.registerButton')}
            </Button>
          </Form.Item>

          <div style={{ textAlign: 'center' }}>
            {t('auth.register.hasAccount')} <Link to="/login">{t('auth.register.login')}</Link>
          </div>
        </Form>
      </Card>
    </div>
  )
}

export default Register
