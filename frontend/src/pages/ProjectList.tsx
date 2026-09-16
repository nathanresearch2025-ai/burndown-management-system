import React, { useState } from 'react'
import { Button, Table, Space, Typography, message, Modal } from 'antd'
import { PlusOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { projectApi } from '../api/project'
import { similarityApi } from '../api/similarity'
import MainLayout from '../components/Layout/MainLayout'
import PermissionGuard from '../components/PermissionGuard'
import CreateProjectModal from '../components/Modals/CreateProjectModal'

const { Title } = Typography

const ProjectList: React.FC = () => {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [createModalVisible, setCreateModalVisible] = useState(false)
  // 记录当前正在生成向量的项目 ID，用于只给该行显示 loading
  const [generatingProjectId, setGeneratingProjectId] = useState<number | null>(null)

  const { data: projects, isLoading } = useQuery({
    queryKey: ['projects'],
    queryFn: async () => {
      const response = await projectApi.getAll()
      return response.data
    },
  })

  // 批量生成向量的 mutation
  const generateEmbeddingsMutation = useMutation({
    mutationFn: async (projectId: number) => {
      return await similarityApi.batchGenerateEmbeddings(projectId, 100)
    },
    onSuccess: (response) => {
      const count = response.data.processedCount
      message.success(t('project.generateEmbeddingsSuccess', { count }))
    },
    onError: (error: any) => {
      message.error(t('project.generateEmbeddingsFailed') + ': ' + (error.response?.data?.error || error.message))
    },
    onSettled: () => {
      setGeneratingProjectId(null)
    },
  })

  // 处理生成向量按钮点击
  const handleGenerateEmbeddings = (projectId: number) => {
    Modal.confirm({
      title: t('project.generateEmbeddings'),
      content: t('project.generateEmbeddingsConfirm'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: () => {
        setGeneratingProjectId(projectId)
        generateEmbeddingsMutation.mutate(projectId)
      },
    })
  }

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
          <Button
            type="link"
            icon={<ThunderboltOutlined />}
            loading={generatingProjectId === record.id}
            disabled={generatingProjectId !== null && generatingProjectId !== record.id}
            onClick={() => handleGenerateEmbeddings(record.id)}
          >
            {t('project.generateEmbeddings')}
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
