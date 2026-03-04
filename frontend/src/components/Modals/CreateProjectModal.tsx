import { Modal, Form, Input, Select, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createProject } from '../../api/project';

interface CreateProjectModalProps {
  visible: boolean;
  onClose: () => void;
}

const CreateProjectModal: React.FC<CreateProjectModalProps> = ({ visible, onClose }) => {
  const [form] = Form.useForm();
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: createProject,
    onSuccess: () => {
      message.success('项目创建成功');
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      form.resetFields();
      onClose();
    },
    onError: () => {
      message.error('项目创建失败');
    },
  });

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
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
      title="创建项目"
      open={visible}
      onOk={handleCreate}
      onCancel={handleCancel}
      confirmLoading={createMutation.isPending}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="name"
          label="项目名称"
          rules={[{ required: true, message: '请输入项目名称' }]}
        >
          <Input placeholder="输入项目名称" />
        </Form.Item>
        <Form.Item
          name="projectKey"
          label="项目Key"
          rules={[{ required: true, message: '请输入项目Key' }]}
        >
          <Input placeholder="例如: PROJ" />
        </Form.Item>
        <Form.Item
          name="type"
          label="项目类型"
          rules={[{ required: true, message: '请选择项目类型' }]}
        >
          <Select placeholder="选择项目类型">
            <Select.Option value="SCRUM">Scrum</Select.Option>
            <Select.Option value="KANBAN">Kanban</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={4} placeholder="输入项目描述（可选）" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default CreateProjectModal;
