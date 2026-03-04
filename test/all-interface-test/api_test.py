#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
API接口自动化测试脚本
测试目标: http://159.75.202.106:30080
"""

import requests
import json
import os
from datetime import datetime
from typing import Dict, List, Optional, Any
import sys


class APITester:
    def __init__(self, base_url: str, config: Dict):
        self.base_url = base_url
        self.config = config
        self.token = None
        self.test_results = []
        self.created_resources = {
            'users': [],
            'projects': [],
            'sprints': [],
            'tasks': [],
            'roles': []
        }

    def log_test(self, module: str, endpoint: str, method: str,
                 status: str, response_code: int, message: str = "",
                 response_data: Any = None):
        """记录测试结果"""
        result = {
            'timestamp': datetime.now().isoformat(),
            'module': module,
            'endpoint': endpoint,
            'method': method,
            'status': status,
            'response_code': response_code,
            'message': message,
            'response_data': response_data
        }
        self.test_results.append(result)

        status_icon = "✓" if status == "PASS" else "✗"
        print(f"{status_icon} [{module}] {method} {endpoint} - {status} ({response_code})")
        if message:
            print(f"  └─ {message}")

    def make_request(self, method: str, endpoint: str, data: Optional[Dict] = None,
                     params: Optional[Dict] = None, auth_required: bool = True) -> requests.Response:
        """发送HTTP请求"""
        url = f"{self.base_url}{endpoint}"
        headers = {'Content-Type': 'application/json'}

        if auth_required and self.token:
            headers['Authorization'] = f'Bearer {self.token}'

        try:
            if method == 'GET':
                response = requests.get(url, headers=headers, params=params, timeout=10)
            elif method == 'POST':
                response = requests.post(url, headers=headers, json=data, timeout=10)
            elif method == 'PUT':
                response = requests.put(url, headers=headers, json=data, timeout=10)
            elif method == 'PATCH':
                response = requests.patch(url, headers=headers, json=data, params=params, timeout=10)
            elif method == 'DELETE':
                response = requests.delete(url, headers=headers, timeout=10)
            else:
                raise ValueError(f"不支持的HTTP方法: {method}")

            return response
        except requests.exceptions.RequestException as e:
            print(f"请求失败: {e}")
            raise

    # ==================== 认证模块测试 ====================
    def test_auth_module(self):
        """测试认证模块"""
        print("\n" + "="*60)
        print("测试模块: 认证 (Authentication)")
        print("="*60)

        # 测试注册
        user_data = self.config['testData']['users'][0]
        register_data = {
            'username': user_data['username'],
            'password': user_data['password'],
            'email': user_data['email']
        }

        try:
            response = self.make_request('POST', '/auth/register', register_data, auth_required=False)
            if response.status_code in [200, 201]:
                self.log_test('认证', '/auth/register', 'POST', 'PASS', response.status_code, '用户注册成功')
                self.created_resources['users'].append(user_data['username'])
            elif response.status_code == 409:
                self.log_test('认证', '/auth/register', 'POST', 'PASS', response.status_code, '用户已存在（预期行为）')
            else:
                self.log_test('认证', '/auth/register', 'POST', 'FAIL', response.status_code,
                            f'注册失败: {response.text}')
        except Exception as e:
            self.log_test('认证', '/auth/register', 'POST', 'ERROR', 0, str(e))

        # 测试登录
        login_data = {
            'username': user_data['username'],
            'password': user_data['password']
        }

        try:
            response = self.make_request('POST', '/auth/login', login_data, auth_required=False)
            if response.status_code == 200:
                response_json = response.json()
                self.token = response_json.get('token')
                self.log_test('认证', '/auth/login', 'POST', 'PASS', response.status_code,
                            f'登录成功，获取到token')
            else:
                self.log_test('认证', '/auth/login', 'POST', 'FAIL', response.status_code,
                            f'登录失败: {response.text}')
        except Exception as e:
            self.log_test('认证', '/auth/login', 'POST', 'ERROR', 0, str(e))

    # ==================== 角色模块测试 ====================
    def test_roles_module(self):
        """测试角色模块"""
        print("\n" + "="*60)
        print("测试模块: 角色 (Roles)")
        print("="*60)

        # 测试获取可用角色（无需认证）
        try:
            response = self.make_request('GET', '/roles/available', auth_required=False)
            if response.status_code == 200:
                roles = response.json()
                self.log_test('角色', '/roles/available', 'GET', 'PASS', response.status_code,
                            f'获取到 {len(roles)} 个可用角色', roles)
            else:
                self.log_test('角色', '/roles/available', 'GET', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('角色', '/roles/available', 'GET', 'ERROR', 0, str(e))

        # 测试获取所有角色（需要权限）
        try:
            response = self.make_request('GET', '/roles')
            if response.status_code in [200, 403]:
                status = 'PASS' if response.status_code == 200 else 'PASS'
                msg = '获取角色列表成功' if response.status_code == 200 else '权限不足（预期行为）'
                self.log_test('角色', '/roles', 'GET', status, response.status_code, msg)
            else:
                self.log_test('角色', '/roles', 'GET', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('角色', '/roles', 'GET', 'ERROR', 0, str(e))

    # ==================== 用户模块测试 ====================
    def test_users_module(self):
        """测试用户模块"""
        print("\n" + "="*60)
        print("测试模块: 用户 (Users)")
        print("="*60)

        # 测试获取所有用户
        try:
            response = self.make_request('GET', '/users')
            if response.status_code == 200:
                users = response.json()
                self.log_test('用户', '/users', 'GET', 'PASS', response.status_code,
                            f'获取到 {len(users)} 个用户')
                # 保存第一个用户ID用于后续测试
                if users and len(users) > 0:
                    self.created_resources['users'].append(users[0].get('id'))
            else:
                self.log_test('用户', '/users', 'GET', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('用户', '/users', 'GET', 'ERROR', 0, str(e))

        # 测试获取用户详情
        if self.created_resources['users']:
            user_id = self.created_resources['users'][0]
            try:
                response = self.make_request('GET', f'/users/{user_id}')
                if response.status_code == 200:
                    self.log_test('用户', f'/users/{user_id}', 'GET', 'PASS', response.status_code, '获取用户详情成功')
                else:
                    self.log_test('用户', f'/users/{user_id}', 'GET', 'FAIL', response.status_code, response.text)
            except Exception as e:
                self.log_test('用户', f'/users/{user_id}', 'GET', 'ERROR', 0, str(e))

            # 测试获取用户角色
            try:
                response = self.make_request('GET', f'/users/{user_id}/roles')
                if response.status_code in [200, 403]:
                    status = 'PASS'
                    msg = '获取用户角色成功' if response.status_code == 200 else '权限不足（预期行为）'
                    self.log_test('用户', f'/users/{user_id}/roles', 'GET', status, response.status_code, msg)
                else:
                    self.log_test('用户', f'/users/{user_id}/roles', 'GET', 'FAIL', response.status_code, response.text)
            except Exception as e:
                self.log_test('用户', f'/users/{user_id}/roles', 'GET', 'ERROR', 0, str(e))

            # 测试获取用户权限
            try:
                response = self.make_request('GET', f'/users/{user_id}/permissions')
                if response.status_code in [200, 403]:
                    status = 'PASS'
                    msg = '获取用户权限成功' if response.status_code == 200 else '权限不足（预期行为）'
                    self.log_test('用户', f'/users/{user_id}/permissions', 'GET', status, response.status_code, msg)
                else:
                    self.log_test('用户', f'/users/{user_id}/permissions', 'GET', 'FAIL', response.status_code, response.text)
            except Exception as e:
                self.log_test('用户', f'/users/{user_id}/permissions', 'GET', 'ERROR', 0, str(e))

    # ==================== 项目模块测试 ====================
    def test_projects_module(self):
        """测试项目模块"""
        print("\n" + "="*60)
        print("测试模块: 项目 (Projects)")
        print("="*60)

        # 测试创建项目
        project_data = self.config['testData']['projects'][0].copy()
        # 生成唯一的项目键，避免重复
        import time
        project_data['projectKey'] = f"TEST-{int(time.time() * 1000) % 1000000}"
        try:
            response = self.make_request('POST', '/projects', project_data)
            if response.status_code in [200, 201]:
                project = response.json()
                project_id = project.get('id')
                self.created_resources['projects'].append(project_id)
                self.log_test('项目', '/projects', 'POST', 'PASS', response.status_code,
                            f'创建项目成功，ID: {project_id}')
            else:
                self.log_test('项目', '/projects', 'POST', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('项目', '/projects', 'POST', 'ERROR', 0, str(e))

        # 测试获取所有项目
        try:
            response = self.make_request('GET', '/projects')
            if response.status_code == 200:
                projects = response.json()
                self.log_test('项目', '/projects', 'GET', 'PASS', response.status_code,
                            f'获取到 {len(projects)} 个项目')
                # 如果创建失败，从现有项目中获取ID
                if not self.created_resources['projects'] and projects and len(projects) > 0:
                    self.created_resources['projects'].append(projects[0].get('id'))
            else:
                self.log_test('项目', '/projects', 'GET', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('项目', '/projects', 'GET', 'ERROR', 0, str(e))

        # 测试获取我的项目
        try:
            response = self.make_request('GET', '/projects/my-projects')
            if response.status_code == 200:
                my_projects = response.json()
                self.log_test('项目', '/projects/my-projects', 'GET', 'PASS', response.status_code,
                            f'获取到 {len(my_projects)} 个我的项目')
            else:
                self.log_test('项目', '/projects/my-projects', 'GET', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('项目', '/projects/my-projects', 'GET', 'ERROR', 0, str(e))

        # 测试获取项目详情
        if self.created_resources['projects']:
            project_id = self.created_resources['projects'][0]
            try:
                response = self.make_request('GET', f'/projects/{project_id}')
                if response.status_code == 200:
                    self.log_test('项目', f'/projects/{project_id}', 'GET', 'PASS', response.status_code, '获取项目详情成功')
                else:
                    self.log_test('项目', f'/projects/{project_id}', 'GET', 'FAIL', response.status_code, response.text)
            except Exception as e:
                self.log_test('项目', f'/projects/{project_id}', 'GET', 'ERROR', 0, str(e))

    # ==================== 冲刺模块测试 ====================
    def test_sprints_module(self):
        """测试冲刺模块"""
        print("\n" + "="*60)
        print("测试模块: 冲刺 (Sprints)")
        print("="*60)

        if not self.created_resources['projects']:
            print("跳过冲刺测试：没有可用的项目")
            return

        project_id = self.created_resources['projects'][0]
        sprint_data = self.config['testData']['sprints'][0].copy()
        sprint_data['projectId'] = project_id

        # 测试创建冲刺
        try:
            response = self.make_request('POST', '/sprints', sprint_data)
            if response.status_code in [200, 201]:
                sprint = response.json()
                sprint_id = sprint.get('id')
                self.created_resources['sprints'].append(sprint_id)
                self.log_test('冲刺', '/sprints', 'POST', 'PASS', response.status_code,
                            f'创建冲刺成功，ID: {sprint_id}')
            else:
                self.log_test('冲刺', '/sprints', 'POST', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('冲刺', '/sprints', 'POST', 'ERROR', 0, str(e))

        # 测试获取项目的冲刺
        try:
            response = self.make_request('GET', f'/sprints/project/{project_id}')
            if response.status_code == 200:
                sprints = response.json()
                self.log_test('冲刺', f'/sprints/project/{project_id}', 'GET', 'PASS', response.status_code,
                            f'获取到 {len(sprints)} 个冲刺')
            else:
                self.log_test('冲刺', f'/sprints/project/{project_id}', 'GET', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('冲刺', f'/sprints/project/{project_id}', 'GET', 'ERROR', 0, str(e))

        # 测试获取冲刺详情
        if self.created_resources['sprints']:
            sprint_id = self.created_resources['sprints'][0]
            try:
                response = self.make_request('GET', f'/sprints/{sprint_id}')
                if response.status_code == 200:
                    self.log_test('冲刺', f'/sprints/{sprint_id}', 'GET', 'PASS', response.status_code, '获取冲刺详情成功')
                else:
                    self.log_test('冲刺', f'/sprints/{sprint_id}', 'GET', 'FAIL', response.status_code, response.text)
            except Exception as e:
                self.log_test('冲刺', f'/sprints/{sprint_id}', 'GET', 'ERROR', 0, str(e))

            # 测试启动冲刺
            try:
                response = self.make_request('POST', f'/sprints/{sprint_id}/start')
                if response.status_code in [200, 201]:
                    self.log_test('冲刺', f'/sprints/{sprint_id}/start', 'POST', 'PASS', response.status_code, '启动冲刺成功')
                else:
                    self.log_test('冲刺', f'/sprints/{sprint_id}/start', 'POST', 'FAIL', response.status_code, response.text)
            except Exception as e:
                self.log_test('冲刺', f'/sprints/{sprint_id}/start', 'POST', 'ERROR', 0, str(e))

    # ==================== 任务模块测试 ====================
    def test_tasks_module(self):
        """测试任务模块"""
        print("\n" + "="*60)
        print("测试模块: 任务 (Tasks)")
        print("="*60)

        if not self.created_resources['projects']:
            print("跳过任务测试：没有可用的项目")
            return

        project_id = self.created_resources['projects'][0]
        task_data = self.config['testData']['tasks'][0].copy()
        task_data['projectId'] = project_id

        if self.created_resources['sprints']:
            task_data['sprintId'] = self.created_resources['sprints'][0]

        # 测试创建任务
        try:
            response = self.make_request('POST', '/tasks', task_data)
            if response.status_code in [200, 201]:
                task = response.json()
                task_id = task.get('id')
                self.created_resources['tasks'].append(task_id)
                self.log_test('任务', '/tasks', 'POST', 'PASS', response.status_code,
                            f'创建任务成功，ID: {task_id}')
            else:
                self.log_test('任务', '/tasks', 'POST', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('任务', '/tasks', 'POST', 'ERROR', 0, str(e))

        # 测试获取项目的任务
        try:
            response = self.make_request('GET', f'/tasks/project/{project_id}')
            if response.status_code == 200:
                tasks = response.json()
                self.log_test('任务', f'/tasks/project/{project_id}', 'GET', 'PASS', response.status_code,
                            f'获取到 {len(tasks)} 个任务')
            else:
                self.log_test('任务', f'/tasks/project/{project_id}', 'GET', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('任务', f'/tasks/project/{project_id}', 'GET', 'ERROR', 0, str(e))

        # 测试获取冲刺的任务
        if self.created_resources['sprints']:
            sprint_id = self.created_resources['sprints'][0]
            try:
                response = self.make_request('GET', f'/tasks/sprint/{sprint_id}')
                if response.status_code == 200:
                    tasks = response.json()
                    self.log_test('任务', f'/tasks/sprint/{sprint_id}', 'GET', 'PASS', response.status_code,
                                f'获取到 {len(tasks)} 个任务')
                else:
                    self.log_test('任务', f'/tasks/sprint/{sprint_id}', 'GET', 'FAIL', response.status_code, response.text)
            except Exception as e:
                self.log_test('任务', f'/tasks/sprint/{sprint_id}', 'GET', 'ERROR', 0, str(e))

        # 测试获取任务详情和更新任务
        if self.created_resources['tasks']:
            task_id = self.created_resources['tasks'][0]

            # 获取任务详情
            try:
                response = self.make_request('GET', f'/tasks/{task_id}')
                if response.status_code == 200:
                    self.log_test('任务', f'/tasks/{task_id}', 'GET', 'PASS', response.status_code, '获取任务详情成功')
                else:
                    self.log_test('任务', f'/tasks/{task_id}', 'GET', 'FAIL', response.status_code, response.text)
            except Exception as e:
                self.log_test('任务', f'/tasks/{task_id}', 'GET', 'ERROR', 0, str(e))

            # 更新任务状态
            try:
                response = self.make_request('PATCH', f'/tasks/{task_id}/status', params={'status': 'IN_PROGRESS'})
                if response.status_code == 200:
                    self.log_test('任务', f'/tasks/{task_id}/status', 'PATCH', 'PASS', response.status_code, '更新任务状态成功')
                else:
                    self.log_test('任务', f'/tasks/{task_id}/status', 'PATCH', 'FAIL', response.status_code, response.text)
            except Exception as e:
                self.log_test('任务', f'/tasks/{task_id}/status', 'PATCH', 'ERROR', 0, str(e))

            # 更新任务
            update_data = task_data.copy()
            update_data['title'] = '更新后的任务标题'
            try:
                response = self.make_request('PUT', f'/tasks/{task_id}', update_data)
                if response.status_code == 200:
                    self.log_test('任务', f'/tasks/{task_id}', 'PUT', 'PASS', response.status_code, '更新任务成功')
                else:
                    self.log_test('任务', f'/tasks/{task_id}', 'PUT', 'FAIL', response.status_code, response.text)
            except Exception as e:
                self.log_test('任务', f'/tasks/{task_id}', 'PUT', 'ERROR', 0, str(e))

    # ==================== 工作日志模块测试 ====================
    def test_worklogs_module(self):
        """测试工作日志模块"""
        print("\n" + "="*60)
        print("测试模块: 工作日志 (Worklogs)")
        print("="*60)

        if not self.created_resources['tasks']:
            print("跳过工作日志测试：没有可用的任务")
            return

        task_id = self.created_resources['tasks'][0]

        # 测试记录工作时间
        worklog_data = self.config['testData'].get('worklogs', [{}])[0].copy()
        worklog_data['taskId'] = task_id

        try:
            response = self.make_request('POST', '/worklogs', worklog_data)
            if response.status_code in [200, 201]:
                self.log_test('工作日志', '/worklogs', 'POST', 'PASS', response.status_code, '记录工作时间成功')
            else:
                self.log_test('工作日志', '/worklogs', 'POST', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('工作日志', '/worklogs', 'POST', 'ERROR', 0, str(e))

        # 测试获取任务的工作日志
        try:
            response = self.make_request('GET', f'/worklogs/task/{task_id}')
            if response.status_code == 200:
                worklogs = response.json()
                self.log_test('工作日志', f'/worklogs/task/{task_id}', 'GET', 'PASS', response.status_code,
                            f'获取到 {len(worklogs)} 条工作日志')
            else:
                self.log_test('工作日志', f'/worklogs/task/{task_id}', 'GET', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('工作日志', f'/worklogs/task/{task_id}', 'GET', 'ERROR', 0, str(e))

        # 测试获取我的工作日志
        try:
            response = self.make_request('GET', '/worklogs/my-worklogs')
            if response.status_code == 200:
                my_worklogs = response.json()
                self.log_test('工作日志', '/worklogs/my-worklogs', 'GET', 'PASS', response.status_code,
                            f'获取到 {len(my_worklogs)} 条我的工作日志')
            else:
                self.log_test('工作日志', '/worklogs/my-worklogs', 'GET', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('工作日志', '/worklogs/my-worklogs', 'GET', 'ERROR', 0, str(e))

    # ==================== 燃尽图模块测试 ====================
    def test_burndown_module(self):
        """测试燃尽图模块"""
        print("\n" + "="*60)
        print("测试模块: 燃尽图 (Burndown)")
        print("="*60)

        if not self.created_resources['sprints']:
            print("跳过燃尽图测试：没有可用的冲刺")
            return

        sprint_id = self.created_resources['sprints'][0]

        # 测试计算燃尽图
        try:
            response = self.make_request('POST', f'/burndown/sprints/{sprint_id}/calculate')
            if response.status_code in [200, 201]:
                self.log_test('燃尽图', f'/burndown/sprints/{sprint_id}/calculate', 'POST', 'PASS',
                            response.status_code, '计算燃尽图成功')
            else:
                self.log_test('燃尽图', f'/burndown/sprints/{sprint_id}/calculate', 'POST', 'FAIL',
                            response.status_code, response.text)
        except Exception as e:
            self.log_test('燃尽图', f'/burndown/sprints/{sprint_id}/calculate', 'POST', 'ERROR', 0, str(e))

        # 测试获取燃尽图数据
        try:
            response = self.make_request('GET', f'/burndown/sprints/{sprint_id}')
            if response.status_code == 200:
                burndown_data = response.json()
                self.log_test('燃尽图', f'/burndown/sprints/{sprint_id}', 'GET', 'PASS',
                            response.status_code, '获取燃尽图数据成功')
            else:
                self.log_test('燃尽图', f'/burndown/sprints/{sprint_id}', 'GET', 'FAIL',
                            response.status_code, response.text)
        except Exception as e:
            self.log_test('燃尽图', f'/burndown/sprints/{sprint_id}', 'GET', 'ERROR', 0, str(e))

    # ==================== 权限模块测试 ====================
    def test_permissions_module(self):
        """测试权限模块"""
        print("\n" + "="*60)
        print("测试模块: 权限 (Permissions)")
        print("="*60)

        # 测试获取所有权限
        try:
            response = self.make_request('GET', '/permissions')
            if response.status_code in [200, 403]:
                status = 'PASS'
                msg = '获取权限列表成功' if response.status_code == 200 else '权限不足（预期行为）'
                self.log_test('权限', '/permissions', 'GET', status, response.status_code, msg)
            else:
                self.log_test('权限', '/permissions', 'GET', 'FAIL', response.status_code, response.text)
        except Exception as e:
            self.log_test('权限', '/permissions', 'GET', 'ERROR', 0, str(e))

    def run_all_tests(self):
        """运行所有测试"""
        print("\n" + "="*70)
        print("开始API接口测试")
        print(f"目标服务器: {self.base_url}")
        print(f"测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print("="*70)

        # 按顺序执行测试
        self.test_auth_module()
        self.test_roles_module()
        self.test_users_module()
        self.test_projects_module()
        self.test_sprints_module()
        self.test_tasks_module()
        self.test_worklogs_module()
        self.test_burndown_module()
        self.test_permissions_module()

        # 生成测试报告
        self.generate_report()

    def generate_report(self):
        """生成测试报告"""
        print("\n" + "="*70)
        print("测试报告生成中...")
        print("="*70)

        # 统计测试结果
        total = len(self.test_results)
        passed = len([r for r in self.test_results if r['status'] == 'PASS'])
        failed = len([r for r in self.test_results if r['status'] == 'FAIL'])
        errors = len([r for r in self.test_results if r['status'] == 'ERROR'])

        # 获取当前版本并自动递增
        script_dir = os.path.dirname(os.path.abspath(__file__))
        date_str = datetime.now().strftime('%Y%m%d')
        time_str = datetime.now().strftime('%H%M%S')

        # 查找当天已有的最大版本号
        max_version = 0
        if os.path.exists(script_dir):
            for dirname in os.listdir(script_dir):
                if dirname.startswith(f"{date_str}-") and '-v' in dirname:
                    try:
                        version_part = dirname.split('-v')[-1]
                        version_num = int(version_part)
                        max_version = max(max_version, version_num)
                    except (ValueError, IndexError):
                        pass

        # 新版本号
        new_version = max_version + 1
        version_str = f"v{new_version}"

        # 创建报告目录：日期-时分秒-版本
        report_dir = os.path.join(script_dir, f"{date_str}-{time_str}-{version_str}")
        os.makedirs(report_dir, exist_ok=True)

        # 更新config.json中的版本号
        config_path = os.path.join(script_dir, 'config.json')
        self.config['version'] = version_str
        with open(config_path, 'w', encoding='utf-8') as f:
            json.dump(self.config, f, ensure_ascii=False, indent=2)

        # 生成JSON报告
        json_report_path = os.path.join(report_dir, 'test_report.json')
        report_data = {
            'summary': {
                'total': total,
                'passed': passed,
                'failed': failed,
                'errors': errors,
                'success_rate': f"{(passed/total*100):.2f}%" if total > 0 else "0%",
                'test_time': datetime.now().isoformat(),
                'base_url': self.base_url,
                'version': version_str
            },
            'test_results': self.test_results,
            'created_resources': self.created_resources
        }

        with open(json_report_path, 'w', encoding='utf-8') as f:
            json.dump(report_data, f, ensure_ascii=False, indent=2)

        # 生成HTML报告
        html_report_path = os.path.join(report_dir, 'test_report.html')
        self.generate_html_report(html_report_path, report_data)

        # 生成Markdown报告
        md_report_path = os.path.join(report_dir, 'test_report.md')
        self.generate_markdown_report(md_report_path, report_data)

        # 打印摘要
        print(f"\n测试完成!")
        print(f"总计: {total} | 通过: {passed} | 失败: {failed} | 错误: {errors}")
        print(f"成功率: {(passed/total*100):.2f}%" if total > 0 else "成功率: 0%")
        print(f"\n报告已生成:")
        print(f"  - JSON: {json_report_path}")
        print(f"  - HTML: {html_report_path}")
        print(f"  - Markdown: {md_report_path}")

    def generate_html_report(self, file_path: str, report_data: Dict):
        """生成HTML格式的测试报告"""
        summary = report_data['summary']
        results = report_data['test_results']

        html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>API测试报告 - {summary['test_time']}</title>
    <style>
        body {{
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            margin: 0;
            padding: 20px;
            background-color: #f5f5f5;
        }}
        .container {{
            max-width: 1200px;
            margin: 0 auto;
            background-color: white;
            padding: 30px;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }}
        h1 {{
            color: #333;
            border-bottom: 3px solid #4CAF50;
            padding-bottom: 10px;
        }}
        .summary {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin: 30px 0;
        }}
        .summary-card {{
            padding: 20px;
            border-radius: 8px;
            text-align: center;
        }}
        .summary-card h3 {{
            margin: 0 0 10px 0;
            font-size: 14px;
            text-transform: uppercase;
            letter-spacing: 1px;
        }}
        .summary-card .value {{
            font-size: 32px;
            font-weight: bold;
        }}
        .card-total {{ background-color: #e3f2fd; color: #1976d2; }}
        .card-passed {{ background-color: #e8f5e9; color: #388e3c; }}
        .card-failed {{ background-color: #ffebee; color: #d32f2f; }}
        .card-errors {{ background-color: #fff3e0; color: #f57c00; }}
        .card-rate {{ background-color: #f3e5f5; color: #7b1fa2; }}
        table {{
            width: 100%;
            border-collapse: collapse;
            margin-top: 20px;
        }}
        th, td {{
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #ddd;
        }}
        th {{
            background-color: #4CAF50;
            color: white;
            font-weight: 600;
        }}
        tr:hover {{
            background-color: #f5f5f5;
        }}
        .status-PASS {{
            color: #388e3c;
            font-weight: bold;
        }}
        .status-FAIL {{
            color: #d32f2f;
            font-weight: bold;
        }}
        .status-ERROR {{
            color: #f57c00;
            font-weight: bold;
        }}
        .method {{
            display: inline-block;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 12px;
            font-weight: bold;
        }}
        .method-GET {{ background-color: #4CAF50; color: white; }}
        .method-POST {{ background-color: #2196F3; color: white; }}
        .method-PUT {{ background-color: #FF9800; color: white; }}
        .method-PATCH {{ background-color: #9C27B0; color: white; }}
        .method-DELETE {{ background-color: #F44336; color: white; }}
        .info {{
            background-color: #e3f2fd;
            padding: 15px;
            border-radius: 4px;
            margin: 20px 0;
        }}
    </style>
</head>
<body>
    <div class="container">
        <h1>🧪 API接口测试报告</h1>

        <div class="info">
            <p><strong>测试服务器:</strong> {summary['base_url']}</p>
            <p><strong>测试时间:</strong> {summary['test_time']}</p>
            <p><strong>版本:</strong> {summary['version']}</p>
        </div>

        <div class="summary">
            <div class="summary-card card-total">
                <h3>总测试数</h3>
                <div class="value">{summary['total']}</div>
            </div>
            <div class="summary-card card-passed">
                <h3>通过</h3>
                <div class="value">{summary['passed']}</div>
            </div>
            <div class="summary-card card-failed">
                <h3>失败</h3>
                <div class="value">{summary['failed']}</div>
            </div>
            <div class="summary-card card-errors">
                <h3>错误</h3>
                <div class="value">{summary['errors']}</div>
            </div>
            <div class="summary-card card-rate">
                <h3>成功率</h3>
                <div class="value">{summary['success_rate']}</div>
            </div>
        </div>

        <h2>测试详情</h2>
        <table>
            <thead>
                <tr>
                    <th>时间</th>
                    <th>模块</th>
                    <th>方法</th>
                    <th>接口</th>
                    <th>状态</th>
                    <th>响应码</th>
                    <th>消息</th>
                </tr>
            </thead>
            <tbody>
"""

        for result in results:
            timestamp = result['timestamp'].split('T')[1].split('.')[0]
            html_content += f"""
                <tr>
                    <td>{timestamp}</td>
                    <td>{result['module']}</td>
                    <td><span class="method method-{result['method']}">{result['method']}</span></td>
                    <td><code>{result['endpoint']}</code></td>
                    <td class="status-{result['status']}">{result['status']}</td>
                    <td>{result['response_code']}</td>
                    <td>{result['message']}</td>
                </tr>
"""

        html_content += """
            </tbody>
        </table>
    </div>
</body>
</html>
"""

        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(html_content)

    def generate_markdown_report(self, file_path: str, report_data: Dict):
        """生成Markdown格式的测试报告"""
        summary = report_data['summary']
        results = report_data['test_results']

        md_content = f"""# API接口测试报告

## 测试信息

- **测试服务器**: {summary['base_url']}
- **测试时间**: {summary['test_time']}
- **版本**: {summary['version']}

## 测试摘要

| 指标 | 数值 |
|------|------|
| 总测试数 | {summary['total']} |
| 通过 | {summary['passed']} ✅ |
| 失败 | {summary['failed']} ❌ |
| 错误 | {summary['errors']} ⚠️ |
| 成功率 | {summary['success_rate']} |

## 测试详情

| 时间 | 模块 | 方法 | 接口 | 状态 | 响应码 | 消息 |
|------|------|------|------|------|--------|------|
"""

        for result in results:
            timestamp = result['timestamp'].split('T')[1].split('.')[0]
            status_icon = "✅" if result['status'] == "PASS" else "❌" if result['status'] == "FAIL" else "⚠️"
            md_content += f"| {timestamp} | {result['module']} | {result['method']} | `{result['endpoint']}` | {status_icon} {result['status']} | {result['response_code']} | {result['message']} |\n"

        md_content += f"""
## 按模块统计

"""
        # 按模块统计
        modules = {}
        for result in results:
            module = result['module']
            if module not in modules:
                modules[module] = {'total': 0, 'passed': 0, 'failed': 0, 'errors': 0}
            modules[module]['total'] += 1
            if result['status'] == 'PASS':
                modules[module]['passed'] += 1
            elif result['status'] == 'FAIL':
                modules[module]['failed'] += 1
            else:
                modules[module]['errors'] += 1

        md_content += "| 模块 | 总数 | 通过 | 失败 | 错误 | 成功率 |\n"
        md_content += "|------|------|------|------|------|--------|\n"

        for module, stats in modules.items():
            success_rate = f"{(stats['passed']/stats['total']*100):.2f}%" if stats['total'] > 0 else "0%"
            md_content += f"| {module} | {stats['total']} | {stats['passed']} | {stats['failed']} | {stats['errors']} | {success_rate} |\n"

        md_content += """
---
*报告由API测试脚本自动生成*
"""

        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(md_content)


def main():
    """主函数"""
    # 读取配置文件
    config_path = os.path.join(os.path.dirname(__file__), 'config.json')

    if not os.path.exists(config_path):
        print(f"错误: 配置文件不存在: {config_path}")
        sys.exit(1)

    with open(config_path, 'r', encoding='utf-8') as f:
        config = json.load(f)

    base_url = config['baseUrl']

    # 创建测试器并运行测试
    tester = APITester(base_url, config)
    tester.run_all_tests()


if __name__ == '__main__':
    main()

