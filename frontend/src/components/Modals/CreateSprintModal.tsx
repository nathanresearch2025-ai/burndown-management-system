import { Modal, Form, Input, DatePicker, Select, message } from 'antd';
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query';
import { createSprint } from '../../api/sprint';
import { getProjects } from '../../api/project';
import { useState, useEffect } from 'react';

interface CreateSprintModalProps {
  visible: boolean;
  onClose: () => void;
  projectId?: number;
}

const CreateSprintModal: React.FC<CreateSprintModalProps> = ({ visible, onClose, projectId }) => {
  const [form] = Form.useForm();
  const queryClient = useQueryClient();
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>(projectId);

  const { data: projects } = useQuery({
    queryKey: ['projects'],
    queryFn: getProjects,
  });

  useEffect(() => {
    if (projectId) {
      setSelectedProjectId(projectId);
      form.setFieldValue('projectId', projectId);
    }
  }, [projectId, form]);

  const createMutation = useMutation({
    mutationFn: (data: any) => createSprint(selectedProjectId!, data),
    onSuccess: () => {
      message.success('Sprint创建成功');
      queryClient.invalidateQueries({ queryKey: ['sprints'] });
      form.resetFields();
      onClose();
    },
    onError: () => {
      message.error('Sprint创建失败');
    },
  });

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();

      if (!selectedProjectId) {
        message.warning('请先选择项目');
        return;
      }

      const data = {
        ...values,
        startDate: values.startDate.format('YYYY-MM-DD'),
        endDate: values.endDate.format('YYYY-MM-DD'),
      };

      createMutation.mutate(data);
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
      title="创建Sprint"
      open={visible}
      onOk={handleCreate}
      onCancel={handleCancel}
      confirmLoading={createMutation.isPending}
      destroyOnClose
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
              onChange={(value) => setSelectedProjectId(value)}
            >
              {projects?.map((project: any) => (
                <Select.Option key={project.id} value={project.id}>
                  {project.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        )}
        <Form.Item
          name="name"
          label="Sprint名称"
          rules={[{ required: true, message: '请输入Sprint名称' }]}
        >
          <Input placeholder="输入Sprint名称" />
        </Form.Item>
        <Form.Item name="goal" label="Sprint目标">
          <Input.TextArea rows={3} placeholder="输入Sprint目标（可选）" />
        </Form.Item>
        <Form.Item
          name="startDate"
          label="开始日期"
          rules={[{ required: true, message: '请选择开始日期' }]}
        >
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="endDate"
          label="结束日期"
          rules={[{ required: true, message: '请选择结束日期' }]}
        >
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default CreateSprintModal;
