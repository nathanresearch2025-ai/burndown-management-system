import React, { useState } from 'react'
import { Button, Card, Modal, Form, Input, Select, InputNumber, message, Row, Col, Tag, Space, DatePicker, Table, Divider, Alert, Typography } from 'antd'
import { ArrowLeftOutlined, ClockCircleOutlined, HistoryOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import { taskApi, CreateTaskRequest, Task } from '../api/task'
import { workLogApi, LogWorkRequest } from '../api/worklog'
import { sprintApi } from '../api/sprint'
import { getUsers } from '../api/user'
import MainLayout from '../components/Layout/MainLayout'
import dayjs from 'dayjs'

const { Title } = Typography

const TaskBoard: React.FC = () => {
  const { sprintId } = useParams<{ sprintId: string }>()
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [isWorkLogModalOpen, setIsWorkLogModalOpen] = useState(false)
  const [isWorkLogHistoryModalOpen, setIsWorkLogHistoryModalOpen] = useState(false)
  const [editingTask, setEditingTask] = useState<Task | null>(null)
  const [selectedTask, setSelectedTask] = useState<Task | null>(null)
  const [form] = Form.useForm()
  const [workLogForm] = Form.useForm()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: sprint } = useQuery({
    queryKey: ['sprint', sprintId],
    queryFn: async () => {
      const response = await sprintApi.getById(Number(sprintId))
      return response.data
    },
  })

  const { data: tasks } = useQuery({
    queryKey: ['tasks', sprintId],
    queryFn: async () => {
      const response = await taskApi.getBySprint(Number(sprintId))
      return response.data
    },
  })

  const { data: workLogs } = useQuery({
    queryKey: ['worklogs', selectedTask?.id],
    queryFn: async () => {
      if (!selectedTask) return []
      const response = await workLogApi.getByTask(selectedTask.id)
      return response.data
    },
    enabled: !!selectedTask && isWorkLogHistoryModalOpen,
  })

  const { data: users } = useQuery({
    queryKey: ['users'],
    queryFn: getUsers,
  })

  const createMutation = useMutation({
    mutationFn: (data: CreateTaskRequest) => taskApi.create(data),
    onSuccess: () => {
      message.success('任务创建成功')
      setIsModalOpen(false)
      form.resetFields()
      queryClient.invalidateQueries({ queryKey: ['tasks', sprintId] })
    },
    onError: () => {
      message.error('任务创建失败')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: CreateTaskRequest }) => taskApi.update(id, data),
    onSuccess: () => {
      message.success('任务更新成功')
      setIsModalOpen(false)
      setEditingTask(null)
      form.resetFields()
      queryClient.invalidateQueries({ queryKey: ['tasks', sprintId] })
    },
    onError: () => {
      message.error('任务更新失败')
    },
  })

  const updateStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) => taskApi.updateStatus(id, status),
    onSuccess: () => {
      message.success('状态更新成功')
      queryClient.invalidateQueries({ queryKey: ['tasks', sprintId] })
    },
    onError: () => {
      message.error('状态更新失败')
    },
  })

  const logWorkMutation = useMutation({
    mutationFn: (data: LogWorkRequest) => workLogApi.create(data),
    onSuccess: () => {
      message.success('工时记录成功')
      setIsWorkLogModalOpen(false)
      setSelectedTask(null)
      workLogForm.resetFields()
      queryClient.invalidateQueries({ queryKey: ['tasks', sprintId] })
    },
    onError: (error: any) => {
      message.error(error.response?.data?.message || '工时记录失败')
    },
  })

  const handleCreate = () => {
    form.validateFields().then((values) => {
      if (editingTask) {
        // 如果状态改变，先更新任务内容，再更新状态
        const statusChanged = values.status && values.status !== editingTask.status

        updateMutation.mutate(
          {
            id: editingTask.id,
            data: {
              projectId: values.projectId,
              sprintId: Number(sprintId),
              title: values.title,
              description: values.description,
              type: values.type,
              priority: values.priority,
              storyPoints: values.storyPoints,
              originalEstimate: values.originalEstimate,
              assigneeId: values.assigneeId,
            },
          },
          {
            onSuccess: () => {
              if (statusChanged) {
                updateStatusMutation.mutate({ id: editingTask.id, status: values.status })
              }
            },
          }
        )
      } else {
        createMutation.mutate({
          ...values,
          sprintId: Number(sprintId),
        })
      }
    })
  }

  const handleEdit = (task: Task) => {
    setEditingTask(task)
    form.setFieldsValue({
      projectId: task.projectId,
      title: task.title,
      description: task.description,
      type: task.type,
      priority: task.priority,
      status: task.status,
      storyPoints: task.storyPoints,
      originalEstimate: task.originalEstimate,
      assigneeId: task.assigneeId,
    })
    setIsModalOpen(true)
  }

  const handleModalClose = () => {
    setIsModalOpen(false)
    setEditingTask(null)
    form.resetFields()
  }

  const handleLogWork = (task: Task) => {
    setSelectedTask(task)
    workLogForm.setFieldsValue({
      workDate: dayjs(),
      timeSpent: undefined,
    })
    setIsWorkLogModalOpen(true)
  }

  // 计算剩余工时
  const calculateRemaining = () => {
    if (!selectedTask) return 0
    const timeSpent = workLogForm.getFieldValue('timeSpent') || 0
    const originalEstimate = selectedTask.originalEstimate || 0
    const currentTimeSpent = selectedTask.timeSpent || 0
    const newTotalSpent = currentTimeSpent + timeSpent
    return originalEstimate - newTotalSpent
  }

  const handleLogWorkSubmit = () => {
    workLogForm.validateFields().then((values) => {
      if (selectedTask) {
        const calculatedRemaining = calculateRemaining()
        logWorkMutation.mutate({
          taskId: selectedTask.id,
          workDate: values.workDate.format('YYYY-MM-DD'),
          timeSpent: values.timeSpent,
          remainingEstimate: calculatedRemaining,
          comment: values.comment,
        })
      }
    })
  }

  const handleWorkLogModalClose = () => {
    setIsWorkLogModalOpen(false)
    setSelectedTask(null)
    workLogForm.resetFields()
  }

  const handleViewWorkLogs = (task: Task) => {
    setSelectedTask(task)
    setIsWorkLogHistoryModalOpen(true)
  }

  const handleWorkLogHistoryModalClose = () => {
    setIsWorkLogHistoryModalOpen(false)
    setSelectedTask(null)
  }

  const getTasksByStatus = (status: string): Task[] => {
    return tasks?.filter((task) => task.status === status) || []
  }

  const getTypeTag = (type: string) => {
    const typeMap: Record<string, { color: string; text: string }> = {
      STORY: { color: 'green', text: '故事' },
      TASK: { color: 'blue', text: '任务' },
      BUG: { color: 'red', text: '缺陷' },
      EPIC: { color: 'purple', text: '史诗' },
    }
    const config = typeMap[type] || { color: 'default', text: type }
    return <Tag color={config.color}>{config.text}</Tag>
  }

  const getPriorityTag = (priority: string) => {
    const priorityMap: Record<string, { color: string; text: string }> = {
      CRITICAL: { color: 'red', text: '紧急' },
      HIGH: { color: 'orange', text: '高' },
      MEDIUM: { color: 'blue', text: '中' },
      LOW: { color: 'default', text: '低' },
    }
    const config = priorityMap[priority] || { color: 'default', text: priority }
    return <Tag color={config.color}>{config.text}</Tag>
  }

  const renderColumn = (status: string, title: string) => {
    const columnTasks = getTasksByStatus(status)

    return (
      <Col span={6}>
        <Card
          title={`${title} (${columnTasks.length})`}
          style={{ minHeight: '500px', background: '#f5f5f5' }}
          bodyStyle={{ padding: '8px' }}
        >
          <div style={{ minHeight: '400px' }}>
            {columnTasks.map((task) => (
              <Card
                key={task.id}
                size="small"
                style={{ marginBottom: 8 }}
                hoverable
              >
                <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Space>
                    {getTypeTag(task.type)}
                    {getPriorityTag(task.priority)}
                  </Space>
                  <Space>
                    <Button
                      type="link"
                      size="small"
                      icon={<ClockCircleOutlined />}
                      onClick={() => handleLogWork(task)}
                      style={{ padding: 0 }}
                    >
                      记录工时
                    </Button>
                    <Button
                      type="link"
                      size="small"
                      icon={<HistoryOutlined />}
                      onClick={() => handleViewWorkLogs(task)}
                      style={{ padding: 0 }}
                    >
                      工时历史
                    </Button>
                    <Button
                      type="link"
                      size="small"
                      onClick={() => handleEdit(task)}
                      style={{ padding: 0 }}
                    >
                      编辑
                    </Button>
                  </Space>
                </div>
                <div style={{ fontWeight: 'bold', marginBottom: 4 }}>{task.taskKey}</div>
                <div style={{ marginBottom: 8 }}>{task.title}</div>
                <Space>
                  {task.storyPoints && (
                    <Tag color="cyan">{task.storyPoints} 点</Tag>
                  )}
                  {task.timeSpent !== undefined && (
                    <Tag color="orange">已用: {task.timeSpent}h</Tag>
                  )}
                  {task.remainingEstimate !== undefined && (
                    <Tag color="blue">剩余: {task.remainingEstimate}h</Tag>
                  )}
                </Space>
              </Card>
            ))}
          </div>
        </Card>
      </Col>
    )
  }

  return (
    <MainLayout>
      <div style={{ background: 'white', padding: 24, borderRadius: 8 }}>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
              返回
            </Button>
            <Title level={2} style={{ margin: 0 }}>任务看板</Title>
          </Space>
        </div>

        <Row gutter={16}>
          {renderColumn('TODO', '待办')}
          {renderColumn('IN_PROGRESS', '进行中')}
          {renderColumn('IN_REVIEW', '审核中')}
          {renderColumn('DONE', '已完成')}
        </Row>

        <Modal
          title={editingTask ? '编辑任务' : '创建任务'}
          open={isModalOpen}
          onOk={handleCreate}
          onCancel={handleModalClose}
          confirmLoading={createMutation.isPending || updateMutation.isPending}
          width={600}
        >
          <Form form={form} layout="vertical">
            <Form.Item name="projectId" label="项目ID" rules={[{ required: true, message: '请输入项目ID' }]}>
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="title" label="任务标题" rules={[{ required: true, message: '请输入任务标题' }]}>
              <Input />
            </Form.Item>
            <Form.Item name="description" label="任务描述">
              <Input.TextArea rows={4} />
            </Form.Item>
            <Form.Item name="type" label="任务类型" rules={[{ required: true, message: '请选择任务类型' }]}>
              <Select>
                <Select.Option value="STORY">故事</Select.Option>
                <Select.Option value="TASK">任务</Select.Option>
                <Select.Option value="BUG">缺陷</Select.Option>
                <Select.Option value="EPIC">史诗</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item name="priority" label="优先级" initialValue="MEDIUM">
              <Select>
                <Select.Option value="CRITICAL">紧急</Select.Option>
                <Select.Option value="HIGH">高</Select.Option>
                <Select.Option value="MEDIUM">中</Select.Option>
                <Select.Option value="LOW">低</Select.Option>
              </Select>
            </Form.Item>
            {editingTask && (
              <Form.Item name="status" label="任务状态" rules={[{ required: true, message: '请选择任务状态' }]}>
                <Select>
                  <Select.Option value="TODO">待办</Select.Option>
                  <Select.Option value="IN_PROGRESS">进行中</Select.Option>
                  <Select.Option value="IN_REVIEW">审核中</Select.Option>
                  <Select.Option value="DONE">已完成</Select.Option>
                  <Select.Option value="BLOCKED">已阻塞</Select.Option>
                </Select>
              </Form.Item>
            )}
            <Form.Item name="storyPoints" label="故事点">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="originalEstimate" label="预估工时（小时）">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="assigneeId" label="指派给">
              <Select
                placeholder="选择指派人员"
                allowClear
                showSearch
                optionFilterProp="children"
                filterOption={(input, option) =>
                  String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
                }
              >
                {users?.map((user: any) => (
                  <Select.Option key={user.id} value={user.id} label={user.username}>
                    {user.username} {user.fullName ? `(${user.fullName})` : ''}
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title="记录工时"
          open={isWorkLogModalOpen}
          onOk={handleLogWorkSubmit}
          onCancel={handleWorkLogModalClose}
          confirmLoading={logWorkMutation.isPending}
          width={500}
        >
          {sprint && (
            <Alert
              message={`Sprint 开始日期: ${dayjs(sprint.startDate).format('YYYY-MM-DD')}`}
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
            />
          )}
          {selectedTask && (
            <Alert
              message={
                <div>
                  <div>预估工时: {selectedTask.originalEstimate || 0}h</div>
                  <div>已用工时: {selectedTask.timeSpent || 0}h</div>
                  <div>当前剩余: {selectedTask.remainingEstimate || 0}h</div>
                </div>
              }
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
            />
          )}
          <Form form={workLogForm} layout="vertical">
            <Form.Item name="workDate" label="工作日期" rules={[{ required: true, message: '请选择工作日期' }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="timeSpent"
              label="花费工时（小时）"
              rules={[
                { required: true, message: '请输入花费工时' },
                { type: 'number', min: 0.1, message: '工时必须大于0' },
              ]}
            >
              <InputNumber
                min={0.1}
                step={0.5}
                style={{ width: '100%' }}
                placeholder="例如: 2.5"
              />
            </Form.Item>
            {selectedTask && workLogForm.getFieldValue('timeSpent') && (
              <Alert
                message={`记录后剩余工时将为: ${calculateRemaining()}h ${calculateRemaining() < 0 ? '(超时)' : ''}`}
                type={calculateRemaining() < 0 ? 'error' : 'success'}
                showIcon
                style={{ marginBottom: 16 }}
              />
            )}
            <Form.Item name="comment" label="工作说明">
              <Input.TextArea rows={3} placeholder="描述本次工作内容..." />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title={`工时历史 - ${selectedTask?.taskKey}`}
          open={isWorkLogHistoryModalOpen}
          onCancel={handleWorkLogHistoryModalClose}
          footer={[
            <Button key="close" onClick={handleWorkLogHistoryModalClose}>
              关闭
            </Button>,
          ]}
          width={800}
        >
          <Table
            dataSource={workLogs}
            rowKey="id"
            pagination={false}
            columns={[
              {
                title: '工作日期',
                dataIndex: 'workDate',
                key: 'workDate',
                render: (date: string) => dayjs(date).format('YYYY-MM-DD'),
              },
              {
                title: '花费工时',
                dataIndex: 'timeSpent',
                key: 'timeSpent',
                render: (time: number) => `${time}h`,
              },
              {
                title: '剩余工时',
                dataIndex: 'remainingEstimate',
                key: 'remainingEstimate',
                render: (time: number) => `${time}h`,
              },
              {
                title: '工作说明',
                dataIndex: 'comment',
                key: 'comment',
                ellipsis: true,
              },
              {
                title: '记录时间',
                dataIndex: 'createdAt',
                key: 'createdAt',
                render: (date: string) => dayjs(date).format('YYYY-MM-DD HH:mm'),
              },
            ]}
          />
          {selectedTask && (
            <>
              <Divider />
              <div style={{ display: 'flex', justifyContent: 'space-around' }}>
                <div>
                  <strong>预估工时：</strong>
                  {selectedTask.originalEstimate ? `${selectedTask.originalEstimate}h` : '未设置'}
                </div>
                <div>
                  <strong>已用工时：</strong>
                  {selectedTask.timeSpent ? `${selectedTask.timeSpent}h` : '0h'}
                </div>
                <div>
                  <strong>剩余工时：</strong>
                  {selectedTask.remainingEstimate ? `${selectedTask.remainingEstimate}h` : '未设置'}
                </div>
              </div>
            </>
          )}
        </Modal>
      </div>
    </MainLayout>
  )
}

export default TaskBoard
