import React, { useState } from 'react'
import { Button, Table, Space, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { projectApi } from '../api/project'
import MainLayout from '../components/Layout/MainLayout'
import PermissionGuard from '../components/PermissionGuard'
import CreateProjectModal from '../components/Modals/CreateProjectModal'

const { Title } = Typography

const ProjectList: React.FC = () => {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [createModalVisible, setCreateModalVisible] = useState(false)

  const { data: projects, isLoading } = useQuery({
    queryKey: ['projects'],
    queryFn: async () => {
      const response = await projectApi.getAll()
      return response.data
    },
  })

  const columns = [
    { title: t('project.name'), dataIndex: 'name', key: 'name' },
    { title: t('project.key'), dataIndex: 'projectKey', key: 'projectKey' },
    { title: t('project.type'), dataIndex: 'type', key: 'type' },
    { title: t('project.description'), dataIndex: 'description', key: 'description' },
    {
      title: t('common.actions'),
      key: 'action',
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" onClick={() => navigate(`/sprints/${record.id}`)}>
            {t('project.viewSprints')}
          </Button>
          <PermissionGuard permission="PROJECT:DELETE">
            <Button type="link" danger>
              {t('common.delete')}
            </Button>
          </PermissionGuard>
        </Space>
      ),
    },
  ]

  return (
    <MainLayout>
      <div style={{ background: 'white', padding: 24, borderRadius: 8 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Title level={2} style={{ margin: 0 }}>{t('project.list')}</Title>
          <PermissionGuard permission="PROJECT:CREATE">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalVisible(true)}>
              {t('project.create')}
            </Button>
          </PermissionGuard>
        </div>
        <Table columns={columns} dataSource={projects} loading={isLoading} rowKey="id" />
      </div>

      <CreateProjectModal
        visible={createModalVisible}
        onClose={() => setCreateModalVisible(false)}
      />
    </MainLayout>
  )
}

export default ProjectList
