import { Modal, Form, Input, InputNumber, Select, message } from 'antd';
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query';
import { createTask } from '../../api/task';
import { getProjects } from '../../api/project';
import { getSprints } from '../../api/sprint';
import { getUsers } from '../../api/user';
import { useState, useEffect } from 'react';

interface CreateTaskModalProps {
  visible: boolean;
  onClose: () => void;
  sprintId?: number;
  projectId?: number;
}

const CreateTaskModal: React.FC<CreateTaskModalProps> = ({
  visible,
  onClose,
  sprintId,
  projectId,
}) => {
  const [form] = Form.useForm();
  const queryClient = useQueryClient();
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>(projectId);
  const [selectedSprintId, setSelectedSprintId] = useState<number | undefined>(sprintId);

  const { data: projects } = useQuery({
    queryKey: ['projects'],
    queryFn: getProjects,
  });

  const { data: sprints } = useQuery({
    queryKey: ['sprints', selectedProjectId],
    queryFn: () => getSprints(selectedProjectId!),
    enabled: !!selectedProjectId,
  });

  const { data: users } = useQuery({
    queryKey: ['users'],
    queryFn: getUsers,
  });

  useEffect(() => {
    if (projectId) {
      setSelectedProjectId(projectId);
      form.setFieldValue('projectId', projectId);
    }
    if (sprintId) {
      setSelectedSprintId(sprintId);
    }
  }, [projectId, sprintId, form]);

  const createMutation = useMutation({
    mutationFn: (data: any) => createTask(selectedSprintId!, data),
    onSuccess: () => {
      message.success('任务创建成功');
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      form.resetFields();
      onClose();
    },
    onError: () => {
      message.error('任务创建失败');
    },
  });

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();

      if (!selectedSprintId) {
        message.warning('请先选择Sprint');
        return;
      }

      createMutation.mutate(values);
    } catch (error) {
      console.error('表单验证失败:', error);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    onClose();
  };

  return (
    <Modal
      title="创建任务"
      open={visible}
      onOk={handleCreate}
      onCancel={handleCancel}
      confirmLoading={createMutation.isPending}
      destroyOnClose
      width={600}
    >
      <Form form={form} layout="vertical">
        {!projectId && (
          <Form.Item
            name="projectId"
            label="所属项目"
            rules={[{ required: true, message: '请选择项目' }]}
          >
            <Select
              placeholder="选择项目"
              onChange={(value) => {
                setSelectedProjectId(value);
                setSelectedSprintId(undefined);
                form.setFieldValue('sprintId', undefined);
              }}
            >
              {projects?.map((project: any) => (
                <Select.Option key={project.id} value={project.id}>
                  {project.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        )}
        {!sprintId && (
          <Form.Item
            name="sprintId"
            label="所属Sprint"
            rules={[{ required: true, message: '请选择Sprint' }]}
          >
            <Select
              placeholder={selectedProjectId ? '选择Sprint' : '请先选择项目'}
              disabled={!selectedProjectId}
              onChange={(value) => setSelectedSprintId(value)}
            >
              {sprints?.map((sprint: any) => (
                <Select.Option key={sprint.id} value={sprint.id}>
                  {sprint.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        )}
        <Form.Item
          name="title"
          label="任务标题"
          rules={[{ required: true, message: '请输入任务标题' }]}
        >
          <Input placeholder="输入任务标题" />
        </Form.Item>
        <Form.Item name="description" label="任务描述">
          <Input.TextArea rows={4} placeholder="输入任务描述（可选）" />
        </Form.Item>
        <Form.Item
          name="type"
          label="任务类型"
          rules={[{ required: true, message: '请选择任务类型' }]}
          initialValue="TASK"
        >
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
        <Form.Item name="storyPoints" label="故事点">
          <InputNumber min={0} style={{ width: '100%' }} placeholder="输入故事点" />
        </Form.Item>
        <Form.Item name="originalEstimate" label="预估工时（小时）">
          <InputNumber min={0} style={{ width: '100%' }} placeholder="输入预估工时" />
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
  );
};

export default CreateTaskModal;
