import React, { useState } from 'react'
import { Button, Table, Space, Typography, message, Modal, Drawer, Statistic, Card, Row, Col, Tag, Spin } from 'antd'
import { PlusOutlined, ThunderboltOutlined, LineChartOutlined } from '@ant-design/icons'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { projectApi } from '../api/project'
import { similarityApi } from '../api/similarity'
import { sprintApi, type SprintCompletionPrediction } from '../api/sprint'
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
  // 预测抽屉状态
  const [predictionDrawerVisible, setPredictionDrawerVisible] = useState(false)
  const [predictionData, setPredictionData] = useState<SprintCompletionPrediction | null>(null)
  const [predictionLoading, setPredictionLoading] = useState(false)

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

  // 处理查看预测按钮点击
  const handleViewPrediction = async (projectId: number) => {
    setPredictionDrawerVisible(true)
    setPredictionLoading(true)
    setPredictionData(null)

    try {
      // 获取项目的活跃 Sprint
      const sprintsResponse = await sprintApi.getByProject(projectId)
      const activeSprint = sprintsResponse.data.find((sprint) => sprint.status === 'ACTIVE')

      if (!activeSprint) {
        message.warning(t('project.noActiveSprint'))
        setPredictionLoading(false)
        return
      }

      // 获取 Sprint 的完成概率预测
      const predictionResponse = await sprintApi.getCompletionProbability(activeSprint.id)
      setPredictionData(predictionResponse.data)
    } catch (error: any) {
      message.error(t('project.predictionFailed') + ': ' + (error.response?.data?.error || error.message))
    } finally {
      setPredictionLoading(false)
    }
  }

  // 获取风险等级的颜色
  const getRiskLevelColor = (riskLevel: string) => {
    switch (riskLevel) {
      case 'GREEN':
        return 'success'
      case 'YELLOW':
        return 'warning'
      case 'RED':
        return 'error'
      default:
        return 'default'
    }
  }

  // 获取风险等级的文本
  const getRiskLevelText = (riskLevel: string) => {
    switch (riskLevel) {
      case 'GREEN':
        return t('project.riskLevelGreen')
      case 'YELLOW':
        return t('project.riskLevelYellow')
      case 'RED':
        return t('project.riskLevelRed')
      default:
        return riskLevel
    }
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
            icon={<LineChartOutlined />}
            onClick={() => handleViewPrediction(record.id)}
          >
            {t('project.viewPrediction')}
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

      <Drawer
        title={t('project.predictionResult')}
        placement="right"
        width={600}
        onClose={() => setPredictionDrawerVisible(false)}
        open={predictionDrawerVisible}
      >
        {predictionLoading ? (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>
            <Spin size="large" />
            <p style={{ marginTop: 16 }}>{t('project.loadingPrediction')}</p>
          </div>
        ) : predictionData ? (
          <div>
            <Card style={{ marginBottom: 16 }}>
              <Statistic
                title={t('project.completionProbability')}
                value={(predictionData.probability * 100).toFixed(2)}
                suffix="%"
                precision={2}
              />
              <div style={{ marginTop: 16 }}>
                <span style={{ marginRight: 8 }}>{t('project.riskLevel')}:</span>
                <Tag color={getRiskLevelColor(predictionData.riskLevel)}>
                  {getRiskLevelText(predictionData.riskLevel)}
                </Tag>
              </div>
            </Card>

            <Card title={t('project.featureSummary')}>
              <Row gutter={[16, 16]}>
                <Col span={12}>
                  <Statistic
                    title={t('project.daysElapsedRatio')}
                    value={(predictionData.featureSummary.daysElapsedRatio * 100).toFixed(2)}
                    suffix="%"
                  />
                </Col>
                <Col span={12}>
                  <Statistic
                    title={t('project.remainingRatio')}
                    value={(predictionData.featureSummary.remainingRatio * 100).toFixed(2)}
                    suffix="%"
                  />
                </Col>
                <Col span={12}>
                  <Statistic
                    title={t('project.velocityCurrent')}
                    value={predictionData.featureSummary.velocityCurrent.toFixed(2)}
                  />
                </Col>
                <Col span={12}>
                  <Statistic
                    title={t('project.velocityAvg')}
                    value={predictionData.featureSummary.velocityAvg.toFixed(2)}
                  />
                </Col>
                <Col span={12}>
                  <Statistic
                    title={t('project.projectedCompletionRatio')}
                    value={(predictionData.featureSummary.projectedCompletionRatio * 100).toFixed(2)}
                    suffix="%"
                  />
                </Col>
                <Col span={12}>
                  <Statistic
                    title={t('project.blockedStories')}
                    value={predictionData.featureSummary.blockedStories}
                  />
                </Col>
                <Col span={12}>
                  <Statistic
                    title={t('project.attendanceRate')}
                    value={(predictionData.featureSummary.attendanceRate * 100).toFixed(2)}
                    suffix="%"
                  />
                </Col>
              </Row>
            </Card>

            <div style={{ marginTop: 16, color: '#999', fontSize: 12 }}>
              {t('project.predictedAt')}: {new Date(predictionData.predictedAt).toLocaleString()}
            </div>
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
            {t('project.noPredictionData')}
          </div>
        )}
      </Drawer>
    </MainLayout>
  )
}

export default ProjectList
