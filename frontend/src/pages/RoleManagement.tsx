import React from 'react';
import { Table, Tag, Space, Button, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import MainLayout from '../components/Layout/MainLayout';
import PermissionGuard from '../components/PermissionGuard';
import api from '../api/axios';

const { Title } = Typography;

interface Role {
  id: number;
  name: string;
  code: string;
  description: string;
  isSystem: boolean;
  isActive: boolean;
  permissions: Array<{
    id: number;
    name: string;
    code: string;
  }>;
}

const RoleManagement: React.FC = () => {
  const { t } = useTranslation();

  const { data: roles, isLoading } = useQuery({
    queryKey: ['roles'],
    queryFn: async () => {
      const response = await api.get('/roles');
      return response.data as Role[];
    },
  });

  const columns = [
    {
      title: t('role.name'),
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: t('role.code'),
      dataIndex: 'code',
      key: 'code',
      render: (code: string) => <Tag color="blue">{code}</Tag>,
    },
    {
      title: t('role.description'),
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: t('role.type'),
      key: 'isSystem',
      render: (_: any, record: Role) => (
        <Tag color={record.isSystem ? 'gold' : 'green'}>
          {record.isSystem ? t('role.system') : t('role.custom')}
        </Tag>
      ),
    },
    {
      title: t('common.status'),
      key: 'isActive',
      render: (_: any, record: Role) => (
        <Tag color={record.isActive ? 'success' : 'default'}>
          {record.isActive ? t('role.active') : t('role.inactive')}
        </Tag>
      ),
    },
    {
      title: t('role.permissions'),
      key: 'permissions',
      render: (_: any, record: Role) => (
        <span>{record.permissions?.length || 0} {t('role.permissionsCount')}</span>
      ),
    },
    {
      title: t('common.actions'),
      key: 'action',
      render: (_: any, record: Role) => (
        <Space>
          <PermissionGuard permission="ROLE:MANAGE">
            <Button type="link" size="small">
              {t('common.edit')}
            </Button>
          </PermissionGuard>
          <PermissionGuard permission="ROLE:MANAGE">
            {!record.isSystem && (
              <Button type="link" danger size="small">
                {t('common.delete')}
              </Button>
            )}
          </PermissionGuard>
        </Space>
      ),
    },
  ];

  return (
    <MainLayout>
      <div style={{ background: 'white', padding: 24, borderRadius: 8 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Title level={2} style={{ margin: 0 }}>{t('role.management')}</Title>
          <PermissionGuard permission="ROLE:MANAGE">
            <Button type="primary">
              {t('role.create')}
            </Button>
          </PermissionGuard>
        </div>
        <Table
          columns={columns}
          dataSource={roles}
          loading={isLoading}
          rowKey="id"
          pagination={{ pageSize: 10 }}
        />
      </div>
    </MainLayout>
  );
};

export default RoleManagement;
