import { Modal, Form, Input, InputNumber, Select, message, Button, Space, Typography } from 'antd';
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query';
import { createTask, taskApi, SimilarTaskReference } from '../../api/task';
import { getProjects } from '../../api/project';
import { getSprints } from '../../api/sprint';
import { getUsers } from '../../api/user';
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';

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
  const { t } = useTranslation();
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>(projectId);
  const [selectedSprintId, setSelectedSprintId] = useState<number | undefined>(sprintId);
  const [similarTasks, setSimilarTasks] = useState<SimilarTaskReference[]>([]);

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
      message.success(t('task.createSuccess'));
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      form.resetFields();
      setSimilarTasks([]);
      onClose();
    },
    onError: () => {
      message.error(t('task.createFailed'));
    },
  });

  const generateDescriptionMutation = useMutation({
    mutationFn: (data: Parameters<typeof taskApi.generateDescription>[0]) => taskApi.generateDescription(data),
    onSuccess: (response) => {
      form.setFieldValue('description', response.data.description);
      setSimilarTasks(response.data.similarTasks || []);
      message.success(t('task.ai.generateSuccess'));
    },
    onError: (error: any) => {
      message.error(error?.response?.data?.message || t('task.ai.generateFailed'));
    },
  });

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();

      if (!selectedSprintId) {
        message.warning(t('task.ai.sprintRequired'));
        return;
      }

      createMutation.mutate(values);
    } catch (error) {
      console.error('表单验证失败:', error);
    }
  };

  const handleGenerateDescription = async () => {
    const values = form.getFieldsValue();
    const currentProjectId = values.projectId ?? selectedProjectId ?? projectId;

    if (!currentProjectId || !values.title || !values.type) {
      message.warning(t('task.ai.missingRequiredFields'));
      return;
    }

    generateDescriptionMutation.mutate({
      projectId: currentProjectId,
      sprintId: values.sprintId ?? selectedSprintId,
      title: values.title,
      type: values.type,
      priority: values.priority,
      storyPoints: values.storyPoints,
      originalEstimate: values.originalEstimate,
      assigneeId: values.assigneeId,
    });
  };

  const handleCancel = () => {
    form.resetFields();
    setSimilarTasks([]);
    onClose();
  };

  return (
    <Modal
      title={t('task.create')}
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
            label={t('task.project')}
            rules={[{ required: true, message: t('task.projectRequired') }]}
          >
            <Select
              placeholder={t('task.projectPlaceholder')}
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
            label={t('task.sprint')}
            rules={[{ required: true, message: t('task.sprintRequired') }]}
          >
            <Select
              placeholder={selectedProjectId ? t('task.sprintPlaceholder') : t('task.sprintSelectProjectFirst')}
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
          label={t('task.titleLabel')}
          rules={[{ required: true, message: t('task.titleRequired') }]}
        >
          <Input placeholder={t('task.titlePlaceholder')} />
        </Form.Item>
        <Form.Item name="description" label={t('task.description')}>
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            <Button
              onClick={handleGenerateDescription}
              loading={generateDescriptionMutation.isPending}
            >
              {t('task.ai.generateButton')}
            </Button>
            <Input.TextArea rows={4} placeholder={t('task.descriptionPlaceholder')} />
            {similarTasks.length > 0 && (
              <Typography.Text type="secondary">
                {t('task.ai.similarTasksHint', {
                  tasks: similarTasks.map((task) => `${task.taskKey} ${task.title}`).join('；'),
                })}
              </Typography.Text>
            )}
          </Space>
        </Form.Item>
        <Form.Item
          name="type"
          label={t('task.type')}
          rules={[{ required: true, message: t('task.typeRequired') }]}
          initialValue="TASK"
        >
          <Select>
            <Select.Option value="STORY">{t('task.story')}</Select.Option>
            <Select.Option value="TASK">{t('task.taskType')}</Select.Option>
            <Select.Option value="BUG">{t('task.bug')}</Select.Option>
            <Select.Option value="EPIC">{t('task.epic')}</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item name="priority" label={t('task.priority')} initialValue="MEDIUM">
          <Select>
            <Select.Option value="CRITICAL">{t('task.highest')}</Select.Option>
            <Select.Option value="HIGH">{t('task.high')}</Select.Option>
            <Select.Option value="MEDIUM">{t('task.medium')}</Select.Option>
            <Select.Option value="LOW">{t('task.low')}</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item name="storyPoints" label={t('task.storyPoints')}>
          <InputNumber min={0} style={{ width: '100%' }} placeholder={t('task.storyPointsPlaceholder')} />
        </Form.Item>
        <Form.Item name="originalEstimate" label={t('task.estimatedHours')}>
          <InputNumber min={0} style={{ width: '100%' }} placeholder={t('task.estimatedHoursPlaceholder')} />
        </Form.Item>
        <Form.Item name="assigneeId" label={t('task.assignee')}>
          <Select
            placeholder={t('task.assigneePlaceholder')}
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
