import { useState, useEffect } from 'react';
import { Dropdown, Button } from 'antd';
import type { MenuProps } from 'antd';
import {
  PlusOutlined,
  ProjectOutlined,
  ThunderboltOutlined,
  CheckSquareOutlined,
  UnorderedListOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { usePermission } from '../../hooks/usePermission';
import CreateProjectModal from '../Modals/CreateProjectModal';
import CreateSprintModal from '../Modals/CreateSprintModal';
import CreateTaskModal from '../Modals/CreateTaskModal';

const QuickAccessMenu: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [createProjectVisible, setCreateProjectVisible] = useState(false);
  const [createSprintVisible, setCreateSprintVisible] = useState(false);
  const [createTaskVisible, setCreateTaskVisible] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);

  // 权限检查
  const canCreateProject = usePermission('PROJECT:CREATE');
  const canCreateSprint = usePermission('SPRINT:CREATE');
  const canCreateTask = usePermission('TASK:CREATE');

  // 键盘快捷键：Ctrl + /
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.key === '/') {
        e.preventDefault();
        setDropdownOpen(prev => !prev);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // 构建创建菜单项（根据权限过滤）
  const createMenuItems = [];
  if (canCreateProject) {
    createMenuItems.push({
      key: 'create-project',
      label: t('quickAccess.createProject'),
      icon: <ProjectOutlined />,
      onClick: () => {
        setCreateProjectVisible(true);
        setDropdownOpen(false);
      },
    });
  }
  if (canCreateSprint) {
    createMenuItems.push({
      key: 'create-sprint',
      label: t('quickAccess.createSprint'),
      icon: <ThunderboltOutlined />,
      onClick: () => {
        setCreateSprintVisible(true);
        setDropdownOpen(false);
      },
    });
  }
  if (canCreateTask) {
    createMenuItems.push({
      key: 'create-task',
      label: t('quickAccess.createTask'),
      icon: <CheckSquareOutlined />,
      onClick: () => {
        setCreateTaskVisible(true);
        setDropdownOpen(false);
      },
    });
  }

  const menuItems: MenuProps['items'] = [];

  // 只有有创建权限时才显示创建分组
  if (createMenuItems.length > 0) {
    menuItems.push({
      key: 'create-group',
      type: 'group',
      label: t('quickAccess.createGroup'),
      children: createMenuItems,
    });
    menuItems.push({ type: 'divider' });
  }

  // 导航分组
  menuItems.push({
    key: 'navigation-group',
    type: 'group',
    label: t('quickAccess.navigationGroup'),
    children: [
      {
        key: 'my-tasks',
        label: t('quickAccess.myTasks'),
        icon: <UnorderedListOutlined />,
        onClick: () => {
          navigate('/dashboard');
          setDropdownOpen(false);
        },
      },
      {
        key: 'all-projects',
        label: t('quickAccess.allProjects'),
        icon: <AppstoreOutlined />,
        onClick: () => {
          navigate('/projects');
          setDropdownOpen(false);
        },
      },
      {
        key: 'reports',
        label: t('quickAccess.reports'),
        icon: <BarChartOutlined />,
        onClick: () => {
          navigate('/dashboard');
          setDropdownOpen(false);
        },
      },
    ],
  });

  menuItems.push({ type: 'divider' });

  // 其他分组
  menuItems.push({
    key: 'settings-group',
    type: 'group',
    label: t('quickAccess.othersGroup'),
    children: [
      {
        key: 'help',
        label: t('quickAccess.help'),
        icon: <QuestionCircleOutlined />,
        onClick: () => {
          window.open('https://docs.example.com', '_blank');
          setDropdownOpen(false);
        },
      },
    ],
  });

  return (
    <>
      <Dropdown
        menu={{ items: menuItems }}
        trigger={['click']}
        open={dropdownOpen}
        onOpenChange={setDropdownOpen}
        placement="bottomRight"
      >
        <Button type="primary" icon={<PlusOutlined />} style={{ whiteSpace: 'nowrap' }}>
          {t('quickAccess.quickActions')}
        </Button>
      </Dropdown>

      <CreateProjectModal
        visible={createProjectVisible}
        onClose={() => setCreateProjectVisible(false)}
      />
      <CreateSprintModal
        visible={createSprintVisible}
        onClose={() => setCreateSprintVisible(false)}
      />
      <CreateTaskModal
        visible={createTaskVisible}
        onClose={() => setCreateTaskVisible(false)}
      />
    </>
  );
};

export default QuickAccessMenu;
