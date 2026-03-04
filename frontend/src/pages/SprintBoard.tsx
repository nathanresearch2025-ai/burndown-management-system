import React from 'react'
import { Button, Table, Space, Tag, message, Typography } from 'antd'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import { sprintApi } from '../api/sprint'
import MainLayout from '../components/Layout/MainLayout'

const { Title } = Typography

const SprintBoard: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: sprints, isLoading } = useQuery({
    queryKey: ['sprints', projectId],
    queryFn: async () => {
      const response = await sprintApi.getByProject(Number(projectId))
      return response.data
    },
  })

  const startMutation = useMutation({
    mutationFn: (id: number) => sprintApi.start(id),
    onSuccess: () => {
      message.success('Sprint已启动')
      queryClient.invalidateQueries({ queryKey: ['sprints', projectId] })
    },
  })

  const completeMutation = useMutation({
    mutationFn: (id: number) => sprintApi.complete(id),
    onSuccess: () => {
      message.success('Sprint已完成')
      queryClient.invalidateQueries({ queryKey: ['sprints', projectId] })
    },
  })

  const getStatusTag = (status: string) => {
    const statusMap: Record<string, { color: string; text: string }> = {
      PLANNED: { color: 'default', text: '计划中' },
      ACTIVE: { color: 'processing', text: '进行中' },
      COMPLETED: { color: 'success', text: '已完成' },
      CANCELLED: { color: 'error', text: '已取消' },
    }
    const config = statusMap[status] || { color: 'default', text: status }
    return <Tag color={config.color}>{config.text}</Tag>
  }

  const columns = [
    { title: 'Sprint名称', dataIndex: 'name', key: 'name' },
    { title: '目标', dataIndex: 'goal', key: 'goal' },
    { title: '开始日期', dataIndex: 'startDate', key: 'startDate' },
    { title: '结束日期', dataIndex: 'endDate', key: 'endDate' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => getStatusTag(status),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: any) => (
        <Space>
          {record.status === 'PLANNED' && (
            <Button type="link" onClick={() => startMutation.mutate(record.id)}>
              启动
            </Button>
          )}
          {record.status === 'ACTIVE' && (
            <>
              <Button type="link" onClick={() => navigate(`/tasks/${record.id}`)}>
                管理任务
              </Button>
              <Button type="link" onClick={() => completeMutation.mutate(record.id)}>
                完成
              </Button>
              <Button type="link" onClick={() => navigate(`/burndown/${record.id}`)}>
                查看燃尽图
              </Button>
            </>
          )}
          {record.status === 'COMPLETED' && (
            <>
              <Button type="link" onClick={() => navigate(`/tasks/${record.id}`)}>
                查看任务
              </Button>
              <Button type="link" onClick={() => navigate(`/burndown/${record.id}`)}>
                查看燃尽图
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ]

  return (
    <MainLayout>
      <div style={{ background: 'white', padding: 24, borderRadius: 8 }}>
        <Title level={2}>Sprint列表</Title>
        <Table columns={columns} dataSource={sprints} loading={isLoading} rowKey="id" />
      </div>
    </MainLayout>
  )
}

export default SprintBoard
