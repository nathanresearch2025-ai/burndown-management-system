import React, { useEffect } from 'react'
import { Modal, Form, Input, Select, message } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { roleApi, CreateRoleRequest, UpdateRoleRequest, RoleResponse } from '../api/role'
import { permissionApi } from '../api/permission'

interface RoleModalProps {
  visible: boolean
  onCancel: () => void
  role?: RoleResponse
}

const RoleModal: React.FC<RoleModalProps> = ({ visible, onCancel, role }) => {
  const { t } = useTranslation()
  const [form] = Form.useForm()
  const queryClient = useQueryClient()
  const isEdit = !!role

  // 获取所有权限
  const { data: permissionsData } = useQuery({
    queryKey: ['permissions'],
    queryFn: () => permissionApi.getAllPermissions(),
    enabled: visible,
  })

  // 创建角色
  const createMutation = useMutation({
    mutationFn: (data: CreateRoleRequest) => roleApi.createRole(data),
    onSuccess: () => {
      message.success(t('success.created'))
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      onCancel()
      form.resetFields()
    },
    onError: (error: any) => {
      message.error(error.response?.data?.message || t('error.unknown'))
    },
  })

  // 更新角色
  const updateMutation = useMutation({
    mutationFn: (data: UpdateRoleRequest) => roleApi.updateRole(role!.id, data),
    onSuccess: () => {
      message.success(t('success.updated'))
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      onCancel()
      form.resetFields()
    },
    onError: (error: any) => {
      message.error(error.response?.data?.message || t('error.unknown'))
    },
  })

  useEffect(() => {
    if (visible && role) {
      form.setFieldsValue({
        name: role.name,
        description: role.description,
        permissionIds: role.permissions?.map(p => p.id) || [],
      })
    } else if (!visible) {
      form.resetFields()
    }
  }, [visible, role, form])

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      if (isEdit) {
        updateMutation.mutate(values)
      } else {
        createMutation.mutate(values)
      }
    } catch (error) {
      console.error('Form validation failed:', error)
    }
  }

  return (
    <Modal
      title={isEdit ? t('role.edit') : t('role.create')}
      open={visible}
      onOk={handleSubmit}
      onCancel={onCancel}
      confirmLoading={createMutation.isPending || updateMutation.isPending}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="name"
          label={t('role.name')}
          rules={[{ required: true, message: t('validation.required') }]}
        >
          <Input placeholder={t('role.namePlaceholder')} />
        </Form.Item>
        <Form.Item name="description" label={t('role.description')}>
          <Input.TextArea rows={3} placeholder={t('role.descriptionPlaceholder')} />
        </Form.Item>
        <Form.Item name="permissionIds" label={t('role.permissions')}>
          <Select
            mode="multiple"
            placeholder={t('role.selectPermissions')}
            options={permissionsData?.data?.map((p: any) => ({
              label: p.name,
              value: p.id,
            }))}
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}

export default RoleModal
