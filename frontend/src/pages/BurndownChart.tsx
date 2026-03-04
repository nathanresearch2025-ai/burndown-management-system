import React from 'react'
import { Card, Spin, Button, Empty, Space, message } from 'antd'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import { burndownApi } from '../api/burndown'
import MainLayout from '../components/Layout/MainLayout'

const BurndownChart: React.FC = () => {
  const { sprintId } = useParams<{ sprintId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: burndownData, isLoading } = useQuery({
    queryKey: ['burndown', sprintId],
    queryFn: async () => {
      const response = await burndownApi.getData(Number(sprintId))
      return response.data
    },
  })

  const calculateMutation = useMutation({
    mutationFn: () => burndownApi.calculate(Number(sprintId)),
    onSuccess: () => {
      message.success('燃尽图计算成功')
      queryClient.invalidateQueries({ queryKey: ['burndown', sprintId] })
    },
    onError: () => {
      message.error('燃尽图计算失败')
    },
  })

  const getChartOption = () => {
    if (!burndownData || burndownData.length === 0) {
      return {}
    }

    const dates = burndownData.map((point) => point.pointDate)
    const idealLine = burndownData.map((point) => point.idealRemaining)
    const actualLine = burndownData.map((point) => point.actualRemaining)

    return {
      title: {
        text: 'Sprint燃尽图',
        left: 'center',
      },
      tooltip: {
        trigger: 'axis',
      },
      legend: {
        data: ['理想燃尽线', '实际燃尽线'],
        top: 30,
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true,
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: dates,
        name: '日期',
      },
      yAxis: {
        type: 'value',
        name: '剩余工时',
      },
      series: [
        {
          name: '理想燃尽线',
          type: 'line',
          data: idealLine,
          smooth: true,
          lineStyle: {
            color: '#91cc75',
            type: 'dashed',
          },
        },
        {
          name: '实际燃尽线',
          type: 'line',
          data: actualLine,
          smooth: true,
          lineStyle: {
            color: '#5470c6',
          },
          areaStyle: {
            color: 'rgba(84, 112, 198, 0.2)',
          },
        },
      ],
    }
  }

  return (
    <MainLayout>
      <Card
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Space>
              <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
                返回
              </Button>
                <span>Sprint 燃尽图</span>
              </Space>
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                onClick={() => calculateMutation.mutate()}
                loading={calculateMutation.isPending}
              >
                计算燃尽图
              </Button>
            </div>
          }
        >
          {isLoading ? (
            <div style={{ textAlign: 'center', padding: '50px' }}>
              <Spin size="large" />
            </div>
          ) : !burndownData || burndownData.length === 0 ? (
            <Empty
              description="暂无燃尽图数据，请先点击「计算燃尽图」按钮生成数据"
              style={{ padding: '50px' }}
            />
          ) : (
            <ReactECharts option={getChartOption()} style={{ height: '500px' }} />
          )}
        </Card>
    </MainLayout>
  )
}

export default BurndownChart
