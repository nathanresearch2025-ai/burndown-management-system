import React, { useState, useEffect } from 'react';
import { Card, Select, Space, Typography } from 'antd';
import StandupChat from '../../components/StandupChat';
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

const StandupAssistantPage: React.FC = () => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>();
  const [selectedSprintId, setSelectedSprintId] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadProjects();
  }, []);

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
    <div style={{ padding: 24 }}>
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        <Card>
          <Title level={2}>Scrum 站会助手</Title>
          <Space>
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
              style={{ width: 200 }}
              placeholder="选择 Sprint（可选）"
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
        </Card>

        {selectedProjectId && (
          <div style={{ height: 'calc(100vh - 300px)' }}>
            <StandupChat projectId={selectedProjectId} sprintId={selectedSprintId} />
          </div>
        )}
      </Space>
    </div>
  );
};

export default StandupAssistantPage;
