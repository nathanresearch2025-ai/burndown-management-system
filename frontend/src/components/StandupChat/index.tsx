import React, { useState } from 'react';
import { Card, Input, Button, List, Typography, Tag, Space, Spin, Alert } from 'antd';
import { SendOutlined, RobotOutlined, UserOutlined } from '@ant-design/icons';
import { standupAgentApi, StandupQueryRequest, StandupQueryResponse } from '../../api/standupAgent';

const { TextArea } = Input;
const { Text, Paragraph } = Typography;

interface Message {
  role: 'user' | 'assistant';
  content: string;
  response?: StandupQueryResponse;
  timestamp: Date;
}

interface StandupChatProps {
  projectId: number;
  sprintId?: number;
}

const StandupChat: React.FC<StandupChatProps> = ({ projectId, sprintId }) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSend = async () => {
    if (!input.trim()) return;

    const userMessage: Message = {
      role: 'user',
      content: input,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setLoading(true);
    setError(null);

    try {
      const request: StandupQueryRequest = {
        question: input,
        projectId,
        sprintId,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      };

      const response = await standupAgentApi.query(request);

      if (response.data.code === 'OK') {
        const assistantMessage: Message = {
          role: 'assistant',
          content: response.data.data.answer,
          response: response.data.data,
          timestamp: new Date(),
        };
        setMessages((prev) => [...prev, assistantMessage]);
      } else {
        setError(response.data.message);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || '请求失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const getRiskLevelColor = (level?: string) => {
    switch (level) {
      case 'LOW':
        return 'success';
      case 'MEDIUM':
        return 'warning';
      case 'HIGH':
        return 'error';
      default:
        return 'default';
    }
  };

  return (
    <Card title="Scrum 站会助手" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto', marginBottom: 16 }}>
        <List
          dataSource={messages}
          renderItem={(message) => (
            <List.Item style={{ border: 'none', padding: '12px 0' }}>
              <Space direction="vertical" style={{ width: '100%' }}>
                <Space>
                  {message.role === 'user' ? (
                    <UserOutlined style={{ fontSize: 20 }} />
                  ) : (
                    <RobotOutlined style={{ fontSize: 20, color: '#1890ff' }} />
                  )}
                  <Text strong>{message.role === 'user' ? '你' : 'AI 助手'}</Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {message.timestamp.toLocaleTimeString()}
                  </Text>
                </Space>
                <Paragraph style={{ marginLeft: 28, marginBottom: 0 }}>
                  {message.content}
                </Paragraph>
                {message.response && (
                  <Space direction="vertical" style={{ marginLeft: 28, width: '100%' }}>
                    {message.response.summary?.riskLevel && (
                      <Tag color={getRiskLevelColor(message.response.summary.riskLevel)}>
                        风险等级: {message.response.summary.riskLevel}
                      </Tag>
                    )}
                    {message.response.toolsUsed && message.response.toolsUsed.length > 0 && (
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        使用工具: {message.response.toolsUsed.join(', ')}
                      </Text>
                    )}
                  </Space>
                )}
              </Space>
            </List.Item>
          )}
        />
        {loading && (
          <div style={{ textAlign: 'center', padding: 20 }}>
            <Spin tip="AI 正在思考..." />
          </div>
        )}
      </div>

      {error && (
        <Alert
          message="错误"
          description={error}
          type="error"
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      <Space.Compact style={{ width: '100%' }}>
        <TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          placeholder="输入你的问题，例如：我今天有哪些进行中的任务？"
          autoSize={{ minRows: 2, maxRows: 4 }}
          disabled={loading}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          loading={loading}
          disabled={!input.trim()}
        >
          发送
        </Button>
      </Space.Compact>
    </Card>
  );
};

export default StandupChat;
