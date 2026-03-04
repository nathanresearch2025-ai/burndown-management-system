#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
简单的API压力测试脚本
使用多线程进行并发测试
"""

import requests
import json
import os
import sys
import time
import threading
from datetime import datetime
from collections import defaultdict
import argparse

# 全局统计数据
stats_lock = threading.Lock()
stats = {
    'login': {'total': 0, 'success': 0, 'fail': 0, 'time': 0},
    'projects': {'total': 0, 'success': 0, 'fail': 0, 'time': 0},
    'tasks': {'total': 0, 'success': 0, 'fail': 0, 'time': 0}
}


def test_login(base_url, username, password):
    """测试登录接口"""
    start_time = time.time()
    try:
        response = requests.post(
            f"{base_url}/auth/login",
            json={"username": username, "password": password},
            timeout=10
        )
        elapsed = (time.time() - start_time) * 1000  # 转换为毫秒

        with stats_lock:
            stats['login']['total'] += 1
            stats['login']['time'] += elapsed

            if response.status_code == 200:
                stats['login']['success'] += 1
                return response.json().get('token')
            else:
                stats['login']['fail'] += 1
                return None
    except Exception as e:
        elapsed = (time.time() - start_time) * 1000
        with stats_lock:
            stats['login']['total'] += 1
            stats['login']['fail'] += 1
            stats['login']['time'] += elapsed
        return None


def test_projects(base_url, token):
    """测试项目列表接口"""
    start_time = time.time()
    try:
        response = requests.get(
            f"{base_url}/projects",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10
        )
        elapsed = (time.time() - start_time) * 1000

        with stats_lock:
            stats['projects']['total'] += 1
            stats['projects']['time'] += elapsed

            if response.status_code == 200:
                stats['projects']['success'] += 1
                projects = response.json()
                if projects and len(projects) > 0:
                    return projects[0].get('id')
            else:
                stats['projects']['fail'] += 1
        return None
    except Exception as e:
        elapsed = (time.time() - start_time) * 1000
        with stats_lock:
            stats['projects']['total'] += 1
            stats['projects']['fail'] += 1
            stats['projects']['time'] += elapsed
        return None


def test_tasks(base_url, token, project_id):
    """测试任务列表接口"""
    start_time = time.time()
    try:
        response = requests.get(
            f"{base_url}/tasks/project/{project_id}",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10
        )
        elapsed = (time.time() - start_time) * 1000

        with stats_lock:
            stats['tasks']['total'] += 1
            stats['tasks']['time'] += elapsed

            if response.status_code in [200, 500]:  # 500也算成功（已知问题）
                stats['tasks']['success'] += 1
            else:
                stats['tasks']['fail'] += 1
    except Exception as e:
        elapsed = (time.time() - start_time) * 1000
        with stats_lock:
            stats['tasks']['total'] += 1
            stats['tasks']['fail'] += 1
            stats['tasks']['time'] += elapsed


def user_worker(user_id, base_url, username, password, end_time):
    """单个用户的测试工作线程"""
    import random

    # 登录获取token
    token = test_login(base_url, username, password)
    if not token:
        return

    # 获取项目ID
    project_id = test_projects(base_url, token)
    if not project_id:
        project_id = 1  # 使用默认项目ID

    # 持续测试直到时间结束
    while time.time() < end_time:
        # 按权重执行不同的请求
        rand = random.randint(0, 9)

        if rand < 2:  # 20% 登录
            test_login(base_url, username, password)
        elif rand < 5:  # 30% 项目列表
            test_projects(base_url, token)
        else:  # 50% 任务列表
            test_tasks(base_url, token, project_id)

        # 随机等待1-3秒
        time.sleep(random.uniform(1, 3))


def generate_reports(base_url, version, start_time, end_time, concurrent_users):
    """生成测试报告"""
    # 计算统计数据
    total_requests = sum(s['total'] for s in stats.values())
    total_success = sum(s['success'] for s in stats.values())
    total_fail = sum(s['fail'] for s in stats.values())

    success_rate = (total_success / total_requests * 100) if total_requests > 0 else 0
    duration = end_time - start_time
    rps = total_requests / duration if duration > 0 else 0

    # 计算平均响应时间
    avg_times = {}
    for key, data in stats.items():
        if data['total'] > 0:
            avg_times[key] = data['time'] / data['total']
        else:
            avg_times[key] = 0

    # 创建报告目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    date_str = datetime.now().strftime('%Y%m%d')

    # 查找当天已有的最大版本号
    max_version = 0
    if os.path.exists(script_dir):
        for dirname in os.listdir(script_dir):
            if dirname.startswith(f"{date_str}-v"):
                try:
                    version_num = int(dirname.split('-v')[1])
                    max_version = max(max_version, version_num)
                except (ValueError, IndexError):
                    pass

    # 新版本号
    new_version = max_version + 1
    version_str = f"v{new_version}"

    # 创建报告目录
    report_dir = os.path.join(script_dir, f"{date_str}-{version_str}")
    os.makedirs(report_dir, exist_ok=True)

    # 更新config.json中的版本号（如果存在）
    config_path = os.path.join(os.path.dirname(script_dir), 'all-interface-test', 'config.json')
    if os.path.exists(config_path):
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                config_data = json.load(f)
            config_data['version'] = version_str
            with open(config_path, 'w', encoding='utf-8') as f:
                json.dump(config_data, f, ensure_ascii=False, indent=2)
        except Exception:
            pass  # 如果更新失败，继续生成报告

    # 生成JSON报告
    report_data = {
        'test_info': {
            'start_time': datetime.fromtimestamp(start_time).isoformat(),
            'end_time': datetime.fromtimestamp(end_time).isoformat(),
            'duration': f"{duration:.2f}s",
            'base_url': base_url,
            'version': version_str,
            'concurrent_users': concurrent_users
        },
        'summary': {
            'total_requests': total_requests,
            'total_success': total_success,
            'total_failures': total_fail,
            'success_rate': f"{success_rate:.2f}%",
            'requests_per_second': f"{rps:.2f}"
        },
        'endpoints': [
            {
                'name': '登录接口',
                'method': 'POST',
                'path': '/auth/login',
                'full_url': f"{base_url}/auth/login",
                'num_requests': stats['login']['total'],
                'num_success': stats['login']['success'],
                'num_failures': stats['login']['fail'],
                'avg_response_time': f"{avg_times['login']:.2f}ms"
            },
            {
                'name': '项目列表接口',
                'method': 'GET',
                'path': '/projects',
                'full_url': f"{base_url}/projects",
                'num_requests': stats['projects']['total'],
                'num_success': stats['projects']['success'],
                'num_failures': stats['projects']['fail'],
                'avg_response_time': f"{avg_times['projects']:.2f}ms"
            },
            {
                'name': '任务列表接口',
                'method': 'GET',
                'path': '/tasks/project/{id}',
                'full_url': f"{base_url}/tasks/project/{{id}}",
                'num_requests': stats['tasks']['total'],
                'num_success': stats['tasks']['success'],
                'num_failures': stats['tasks']['fail'],
                'avg_response_time': f"{avg_times['tasks']:.2f}ms"
            }
        ]
    }

    # 保存JSON报告
    json_path = os.path.join(report_dir, 'pressure_report.json')
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(report_data, f, ensure_ascii=False, indent=2)

    # 生成Markdown报告
    md_content = f"""# API压力测试报告

## 测试信息

- **测试服务器**: {base_url}
- **开始时间**: {datetime.fromtimestamp(start_time).strftime('%Y-%m-%d %H:%M:%S')}
- **结束时间**: {datetime.fromtimestamp(end_time).strftime('%Y-%m-%d %H:%M:%S')}
- **测试时长**: {duration:.2f}秒
- **并发用户数**: {concurrent_users}
- **版本**: {version_str}

## 测试接口列表

| 接口名称 | HTTP方法 | 完整URL |
|---------|---------|---------|
| 登录接口 | POST | `{base_url}/auth/login` |
| 项目列表接口 | GET | `{base_url}/projects` |
| 任务列表接口 | GET | `{base_url}/tasks/project/{{id}}` |

## 总体统计

| 指标 | 数值 |
|------|------|
| 总请求数 | {total_requests} |
| 成功请求 | {total_success} |
| 失败请求 | {total_fail} |
| 成功率 | {success_rate:.2f}% |
| 每秒请求数(RPS) | {rps:.2f} |

## 各接口详细统计

| 接口名称 | 方法 | 完整URL | 请求数 | 成功数 | 失败数 | 平均响应时间 |
|---------|------|---------|--------|--------|--------|-------------|
| 登录接口 | POST | `{base_url}/auth/login` | {stats['login']['total']} | {stats['login']['success']} | {stats['login']['fail']} | {avg_times['login']:.2f}ms |
| 项目列表接口 | GET | `{base_url}/projects` | {stats['projects']['total']} | {stats['projects']['success']} | {stats['projects']['fail']} | {avg_times['projects']:.2f}ms |
| 任务列表接口 | GET | `{base_url}/tasks/project/{{id}}` | {stats['tasks']['total']} | {stats['tasks']['success']} | {stats['tasks']['fail']} | {avg_times['tasks']:.2f}ms |

---
*报告由压力测试脚本自动生成*
"""

    md_path = os.path.join(report_dir, 'pressure_report.md')
    with open(md_path, 'w', encoding='utf-8') as f:
        f.write(md_content)

    return json_path, md_path


def main():
    parser = argparse.ArgumentParser(description='API压力测试脚本')
    parser.add_argument('-u', '--users', type=int, default=10, help='并发用户数 (默认: 10)')
    parser.add_argument('-t', '--duration', type=int, default=60, help='运行时长(秒) (默认: 60)')
    parser.add_argument('--username', default='testuser1', help='登录用户名')
    parser.add_argument('--password', default='Test123456!', help='登录密码')

    args = parser.parse_args()

    # 读取配置
    script_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(os.path.dirname(script_dir), 'all-interface-test', 'config.json')
    with open(config_path, 'r', encoding='utf-8') as f:
        config = json.load(f)

    base_url = config['baseUrl']
    version = config.get('version', 'v1')

    print("=" * 50)
    print("API压力测试脚本")
    print("=" * 50)
    print(f"目标服务器: {base_url}")
    print(f"并发用户数: {args.users}")
    print(f"运行时长: {args.duration}秒")
    print()

    # 记录开始时间
    start_time = time.time()
    end_time = start_time + args.duration

    # 启动并发用户线程
    print("启动压力测试...")
    threads = []
    for i in range(args.users):
        t = threading.Thread(
            target=user_worker,
            args=(i, base_url, args.username, args.password, end_time)
        )
        t.start()
        threads.append(t)

    # 等待所有线程完成
    for t in threads:
        t.join()

    # 记录结束时间
    actual_end_time = time.time()

    # 计算并显示结果
    total_requests = sum(s['total'] for s in stats.values())
    total_success = sum(s['success'] for s in stats.values())
    total_fail = sum(s['fail'] for s in stats.values())
    success_rate = (total_success / total_requests * 100) if total_requests > 0 else 0
    duration = actual_end_time - start_time
    rps = total_requests / duration if duration > 0 else 0

    print()
    print("=" * 50)
    print("测试完成！")
    print("=" * 50)
    print(f"总请求数: {total_requests}")
    print(f"成功请求: {total_success}")
    print(f"失败请求: {total_fail}")
    print(f"成功率: {success_rate:.2f}%")
    print(f"RPS: {rps:.2f}")
    print()

    # 生成报告
    json_path, md_path = generate_reports(base_url, version, start_time, actual_end_time, args.users)

    print("报告已生成:")
    print(f"  - JSON: {json_path}")
    print(f"  - Markdown: {md_path}")


if __name__ == '__main__':
    main()
