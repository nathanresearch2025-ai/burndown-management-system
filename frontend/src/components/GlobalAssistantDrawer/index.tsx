import React, { useState, useEffect } from 'react';
import { Drawer, Select, Space, Typography, Button } from 'antd';
import { MessageOutlined, CloseOutlined } from '@ant-design/icons';
import StandupChat from '../StandupChat';
import { projectApi } from '../../api/project';
import { sprintApi } from '../../api/sprint';

const { Title } = Typography;
const { Option } = Select;

interface Project {
  id: number;
  name: string;
}

interface Sprint {
  id: number;
  name: string;
}

const GlobalAssistantDrawer: React.FC = () => {
  const [visible, setVisible] = useState(false);
  const [projects, setProjects] = useState<Project[]>([]);
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>();
  const [selectedSprintId, setSelectedSprintId] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (visible) {
      loadProjects();
    }
  }, [visible]);

  useEffect(() => {
    if (selectedProjectId) {
      loadSprints(selectedProjectId);
    } else {
      setSprints([]);
      setSelectedSprintId(undefined);
    }
  }, [selectedProjectId]);

  const loadProjects = async () => {
    try {
      setLoading(true);
      const response = await projectApi.getAll();
      setProjects(response.data);
    } catch (error) {
      console.error('Failed to load projects:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadSprints = async (projectId: number) => {
    try {
      setLoading(true);
      const response = await sprintApi.getByProject(projectId);
      setSprints(response.data);
    } catch (error) {
      console.error('Failed to load sprints:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      {/* 侧边触发按钮 */}
      <div
        style={{
          position: 'fixed',
          right: visible ? 600 : 0,
          top: '50%',
          transform: 'translateY(-50%)',
          zIndex: 999,
          transition: 'right 0.3s ease',
        }}
      >
        <Button
          type="primary"
          icon={visible ? <CloseOutlined /> : <MessageOutlined />}
          onClick={() => setVisible(!visible)}
          style={{
            height: 80,
            width: 40,
            borderRadius: '8px 0 0 8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '-2px 2px 8px rgba(0,0,0,0.15)',
          }}
        />
      </div>

      {/* 侧拉抽屉 */}
      <Drawer
        title={
          <Space direction="vertical" style={{ width: '100%' }} size="small">
            <Title level={4} style={{ margin: 0 }}>Scrum 站会助手</Title>
            <Space style={{ width: '100%' }}>
              <Select
                style={{ width: 200 }}
                placeholder="选择项目"
                value={selectedProjectId}
                onChange={setSelectedProjectId}
                loading={loading}
              >
                {projects.map((project) => (
                  <Option key={project.id} value={project.id}>
                    {project.name}
                  </Option>
                ))}
              </Select>
              <Select
                style={{ width: 180 }}
                placeholder="选择 Sprint"
                value={selectedSprintId}
                onChange={setSelectedSprintId}
                loading={loading}
                disabled={!selectedProjectId}
                allowClear
              >
                {sprints.map((sprint) => (
                  <Option key={sprint.id} value={sprint.id}>
                    {sprint.name}
                  </Option>
                ))}
              </Select>
            </Space>
          </Space>
        }
        placement="right"
        onClose={() => setVisible(false)}
        open={visible}
        width={600}
        bodyStyle={{ padding: 0, height: 'calc(100vh - 140px)' }}
        maskClosable={true}
      >
        {selectedProjectId ? (
          <div style={{ height: '100%', padding: '16px' }}>
            <StandupChat projectId={selectedProjectId} sprintId={selectedSprintId} />
          </div>
        ) : (
          <div style={{
            padding: 24,
            textAlign: 'center',
            color: '#999',
            paddingTop: 100
          }}>
            <MessageOutlined style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }} />
            <p>请先选择一个项目开始对话</p>
          </div>
        )}
      </Drawer>
    </>
  );
};

export default GlobalAssistantDrawer;
