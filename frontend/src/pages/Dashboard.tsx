import React from 'react'
import { Button, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import MainLayout from '../components/Layout/MainLayout'

const { Title } = Typography

const Dashboard: React.FC = () => {
  const navigate = useNavigate()
  const { t } = useTranslation()

  return (
    <MainLayout>
      <div style={{ background: 'white', padding: 24, minHeight: 500, borderRadius: 8 }}>
        <Title level={2}>{t('dashboard.welcome')}</Title>
        <p>{t('dashboard.description')}</p>
        <Button type="primary" onClick={() => navigate('/projects')}>
          {t('dashboard.viewProjects')}
        </Button>
      </div>
    </MainLayout>
  )
}

export default Dashboard
