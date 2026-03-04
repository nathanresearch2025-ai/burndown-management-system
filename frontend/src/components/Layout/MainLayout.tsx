import { Layout, Menu, Button, Space, Typography } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../store/authStore';
import { usePermission } from '../../hooks/usePermission';
import QuickAccessMenu from './QuickAccessMenu';
import LanguageSwitcher from '../LanguageSwitcher';
import { LogoutOutlined } from '@ant-design/icons';

const { Header, Content } = Layout;
const { Text } = Typography;

interface MainLayoutProps {
  children: React.ReactNode;
}

const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuthStore();
  const { t } = useTranslation();
  const canManageRoles = usePermission('ROLE:MANAGE');

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const menuItems = [
    {
      key: '/dashboard',
      label: t('menu.dashboard'),
    },
    {
      key: '/projects',
      label: t('menu.projects'),
    },
  ];

  // 只有有权限的用户才能看到角色管理菜单
  if (canManageRoles) {
    menuItems.push({
      key: '/roles',
      label: t('menu.roles'),
    });
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{
        background: '#fff',
        padding: '0 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
        height: '64px',
        overflow: 'visible'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flex: '1 1 auto', minWidth: 0 }}>
          <Text strong style={{ fontSize: '18px', color: '#1890ff', whiteSpace: 'nowrap', flexShrink: 0 }}>
            {t('common.systemName')}
          </Text>
          <Menu
            mode="horizontal"
            selectedKeys={[location.pathname]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
            style={{ border: 'none', flex: '1 1 auto', minWidth: 0 }}
          />
        </div>

        <Space size="middle" style={{ flex: '0 0 auto', flexShrink: 0 }}>
          <QuickAccessMenu />
          <LanguageSwitcher />
          <Text style={{ whiteSpace: 'nowrap' }}>{t('dashboard.welcome')}, {user?.username || t('common.name')}</Text>
          <Button
            type="text"
            icon={<LogoutOutlined />}
            onClick={handleLogout}
            style={{ whiteSpace: 'nowrap' }}
          >
            {t('auth.logout')}
          </Button>
        </Space>
      </Header>

      <Content style={{ padding: '24px', background: '#f0f2f5' }}>
        {children}
      </Content>
    </Layout>
  );
};

export default MainLayout;
