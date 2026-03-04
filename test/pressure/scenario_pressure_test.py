#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
场景化API压力测试脚本
支持 baseline、standard、peak 三种测试场景
生成对比图表和详细报告
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
current_stats = None

# 三种测试场景配置
SCENARIOS = {
    'baseline': {
        'name': '基线测试',
        'users': 5,
        'duration': 30,
        'description': '模拟低负载场景，5个并发用户'
    },
    'standard': {
        'name': '标准测试',
        'users': 20,
        'duration': 60,
        'description': '模拟正常负载场景，20个并发用户'
    },
    'peak': {
        'name': '峰值测试',
        'users': 50,
        'duration': 60,
        'description': '模拟高负载场景，50个并发用户'
    }
}


def init_stats():
    """初始化统计数据"""
    return {
        'login': {'total': 0, 'success': 0, 'fail': 0, 'time': 0, 'times': []},
        'projects': {'total': 0, 'success': 0, 'fail': 0, 'time': 0, 'times': []},
        'tasks': {'total': 0, 'success': 0, 'fail': 0, 'time': 0, 'times': []}
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
        elapsed = (time.time() - start_time) * 1000

        with stats_lock:
            current_stats['login']['total'] += 1
            current_stats['login']['time'] += elapsed
            current_stats['login']['times'].append(elapsed)

            if response.status_code == 200:
                current_stats['login']['success'] += 1
                return response.json().get('token')
            else:
                current_stats['login']['fail'] += 1
                return None
    except Exception:
        elapsed = (time.time() - start_time) * 1000
        with stats_lock:
            current_stats['login']['total'] += 1
            current_stats['login']['fail'] += 1
            current_stats['login']['time'] += elapsed
            current_stats['login']['times'].append(elapsed)
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
            current_stats['projects']['total'] += 1
            current_stats['projects']['time'] += elapsed
            current_stats['projects']['times'].append(elapsed)

            if response.status_code == 200:
                current_stats['projects']['success'] += 1
                projects = response.json()
                if projects and len(projects) > 0:
                    return projects[0].get('id')
            else:
                current_stats['projects']['fail'] += 1
        return None
    except Exception:
        elapsed = (time.time() - start_time) * 1000
        with stats_lock:
            current_stats['projects']['total'] += 1
            current_stats['projects']['fail'] += 1
            current_stats['projects']['time'] += elapsed
            current_stats['projects']['times'].append(elapsed)
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
            current_stats['tasks']['total'] += 1
            current_stats['tasks']['time'] += elapsed
            current_stats['tasks']['times'].append(elapsed)

            if response.status_code == 200:
                current_stats['tasks']['success'] += 1
            else:
                current_stats['tasks']['fail'] += 1
    except Exception:
        elapsed = (time.time() - start_time) * 1000
        with stats_lock:
            current_stats['tasks']['total'] += 1
            current_stats['tasks']['fail'] += 1
            current_stats['tasks']['time'] += elapsed
            current_stats['tasks']['times'].append(elapsed)


def user_worker(base_url, username, password, duration, stop_event):
    """单个用户的工作线程"""
    import random

    end_time = time.time() + duration

    while time.time() < end_time and not stop_event.is_set():
        # 登录
        token = test_login(base_url, username, password)
        if not token:
            time.sleep(1)
            continue

        # 随机选择接口进行测试（权重：登录20%，项目30%，任务50%）
        rand = random.random()

        if rand < 0.3:  # 30% 获取项目列表
            project_id = test_projects(base_url, token)
            if project_id:
                test_tasks(base_url, token, project_id)
        else:  # 70% 获取任务列表
            project_id = test_projects(base_url, token)
            if project_id:
                test_tasks(base_url, token, project_id)

        time.sleep(random.uniform(0.1, 0.5))


def run_scenario(base_url, username, password, scenario_name):
    """运行单个测试场景"""
    global current_stats

    scenario = SCENARIOS[scenario_name]
    current_stats = init_stats()

    print(f"\n{'='*60}")
    print(f"开始 {scenario['name']} ({scenario_name})")
    print(f"{'='*60}")
    print(f"并发用户: {scenario['users']}")
    print(f"测试时长: {scenario['duration']}秒")
    print(f"说明: {scenario['description']}")
    print()

    start_time = time.time()
    stop_event = threading.Event()
    threads = []

    # 启动用户线程
    for i in range(scenario['users']):
        t = threading.Thread(
            target=user_worker,
            args=(base_url, username, password, scenario['duration'], stop_event)
        )
        t.daemon = True
        t.start()
        threads.append(t)
        time.sleep(0.1)  # 逐步启动

    # 等待所有线程完成
    for t in threads:
        t.join()

    elapsed_time = time.time() - start_time

    # 计算统计数据
    result = {
        'scenario': scenario_name,
        'name': scenario['name'],
        'users': scenario['users'],
        'duration': scenario['duration'],
        'actual_duration': elapsed_time,
        'stats': {}
    }

    total_requests = 0
    total_success = 0
    total_fail = 0

    for api_name, api_stats in current_stats.items():
        total = api_stats['total']
        success = api_stats['success']
        fail = api_stats['fail']

        total_requests += total
        total_success += success
        total_fail += fail

        avg_time = api_stats['time'] / total if total > 0 else 0

        # 计算百分位数
        times = sorted(api_stats['times'])
        p50 = times[int(len(times) * 0.5)] if times else 0
        p95 = times[int(len(times) * 0.95)] if times else 0
        p99 = times[int(len(times) * 0.99)] if times else 0

        result['stats'][api_name] = {
            'total': total,
            'success': success,
            'fail': fail,
            'success_rate': (success / total * 100) if total > 0 else 0,
            'avg_time': avg_time,
            'p50': p50,
            'p95': p95,
            'p99': p99,
            'rps': total / elapsed_time if elapsed_time > 0 else 0
        }

    result['total_requests'] = total_requests
    result['total_success'] = total_success
    result['total_fail'] = total_fail
    result['success_rate'] = (total_success / total_requests * 100) if total_requests > 0 else 0
    result['rps'] = total_requests / elapsed_time if elapsed_time > 0 else 0

    print(f"\n{scenario['name']} 完成!")
    print(f"总请求数: {total_requests}")
    print(f"成功: {total_success}, 失败: {total_fail}")
    print(f"成功率: {result['success_rate']:.2f}%")
    print(f"RPS: {result['rps']:.2f}")

    return result


def generate_charts(results, output_dir):
    """生成对比图表并返回base64编码"""
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
        import numpy as np
        import base64
        from io import BytesIO

        # 使用英文避免中文乱码
        plt.rcParams['font.sans-serif'] = ['DejaVu Sans']
        plt.rcParams['axes.unicode_minus'] = False

        charts_base64 = {}

        # 1. RPS对比图
        fig, ax = plt.subplots(figsize=(10, 6))
        scenarios = ['Baseline', 'Standard', 'Peak']
        rps_values = [r['rps'] for r in results]
        colors = ['#4CAF50', '#2196F3', '#FF9800']

        bars = ax.bar(scenarios, rps_values, color=colors, alpha=0.8)
        ax.set_ylabel('RPS (Requests/Second)', fontsize=12)
        ax.set_title('RPS Comparison Across Scenarios', fontsize=14, fontweight='bold')
        ax.grid(axis='y', alpha=0.3)

        for bar in bars:
            height = bar.get_height()
            ax.text(bar.get_x() + bar.get_width()/2., height,
                   f'{height:.2f}', ha='center', va='bottom', fontsize=10)

        plt.tight_layout()

        # 保存为base64
        buffer = BytesIO()
        plt.savefig(buffer, format='png', dpi=150, bbox_inches='tight')
        buffer.seek(0)
        charts_base64['chart1'] = base64.b64encode(buffer.read()).decode()
        plt.close()

        # 2. 响应时间对比图
        fig, ax = plt.subplots(figsize=(12, 6))
        x = np.arange(len(scenarios))
        width = 0.25

        api_labels = ['POST /auth/login', 'GET /projects', 'GET /tasks/project/{id}']

        for i, api_label in enumerate(api_labels):
            api_name = ['login', 'projects', 'tasks'][i]
            avg_times = [r['stats'][api_name]['avg_time'] for r in results]
            ax.bar(x + i*width, avg_times, width, label=api_label, alpha=0.8)

        ax.set_ylabel('Average Response Time (ms)', fontsize=12)
        ax.set_title('Response Time Comparison by API', fontsize=14, fontweight='bold')
        ax.set_xticks(x + width)
        ax.set_xticklabels(scenarios)
        ax.legend()
        ax.grid(axis='y', alpha=0.3)

        plt.tight_layout()
        buffer = BytesIO()
        plt.savefig(buffer, format='png', dpi=150, bbox_inches='tight')
        buffer.seek(0)
        charts_base64['chart2'] = base64.b64encode(buffer.read()).decode()
        plt.close()

        # 3. 成功率对比图
        fig, ax = plt.subplots(figsize=(10, 6))
        success_rates = [r['success_rate'] for r in results]

        bars = ax.bar(scenarios, success_rates, color=colors, alpha=0.8)
        ax.set_ylabel('Success Rate (%)', fontsize=12)
        ax.set_title('Success Rate Comparison', fontsize=14, fontweight='bold')
        ax.set_ylim([0, 105])
        ax.grid(axis='y', alpha=0.3)

        for bar in bars:
            height = bar.get_height()
            ax.text(bar.get_x() + bar.get_width()/2., height,
                   f'{height:.2f}%', ha='center', va='bottom', fontsize=10)

        plt.tight_layout()
        buffer = BytesIO()
        plt.savefig(buffer, format='png', dpi=150, bbox_inches='tight')
        buffer.seek(0)
        charts_base64['chart3'] = base64.b64encode(buffer.read()).decode()
        plt.close()

        # 4. P95响应时间对比
        fig, ax = plt.subplots(figsize=(12, 6))

        for i, api_label in enumerate(api_labels):
            api_name = ['login', 'projects', 'tasks'][i]
            p95_times = [r['stats'][api_name]['p95'] for r in results]
            ax.bar(x + i*width, p95_times, width, label=api_label, alpha=0.8)

        ax.set_ylabel('P95 Response Time (ms)', fontsize=12)
        ax.set_title('P95 Response Time Comparison by API', fontsize=14, fontweight='bold')
        ax.set_xticks(x + width)
        ax.set_xticklabels(scenarios)
        ax.legend()
        ax.grid(axis='y', alpha=0.3)

        plt.tight_layout()
        buffer = BytesIO()
        plt.savefig(buffer, format='png', dpi=150, bbox_inches='tight')
        buffer.seek(0)
        charts_base64['chart4'] = base64.b64encode(buffer.read()).decode()
        plt.close()

        # 5. 总请求数对比
        fig, ax = plt.subplots(figsize=(10, 6))
        total_requests = [r['total_requests'] for r in results]

        bars = ax.bar(scenarios, total_requests, color=colors, alpha=0.8)
        ax.set_ylabel('Total Requests', fontsize=12)
        ax.set_title('Total Requests Comparison', fontsize=14, fontweight='bold')
        ax.grid(axis='y', alpha=0.3)

        for bar in bars:
            height = bar.get_height()
            ax.text(bar.get_x() + bar.get_width()/2., height,
                   f'{int(height)}', ha='center', va='bottom', fontsize=10)

        plt.tight_layout()
        buffer = BytesIO()
        plt.savefig(buffer, format='png', dpi=150, bbox_inches='tight')
        buffer.seek(0)
        charts_base64['chart5'] = base64.b64encode(buffer.read()).decode()
        plt.close()

        print(f"\n✓ 图表已生成（嵌入HTML）")

        return charts_base64

    except ImportError:
        print("\n⚠ matplotlib未安装，跳过图表生成")
        print("  安装命令: pip3 install matplotlib")
        return None
    except Exception as e:
        print(f"\n⚠ 图表生成失败: {e}")
        return None


def generate_report(results, output_dir):
    """生成Markdown报告"""
    report_path = f"{output_dir}/scenario_comparison_report.md"

    with open(report_path, 'w', encoding='utf-8') as f:
        f.write("# 压力测试场景对比报告\n\n")
        f.write(f"**测试时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        f.write("---\n\n")

        # 场景概览
        f.write("## 场景概览\n\n")
        f.write("| 场景 | 并发用户 | 测试时长 | 总请求数 | 成功率 | RPS |\n")
        f.write("|------|---------|---------|---------|--------|-----|\n")

        for r in results:
            f.write(f"| {r['name']} | {r['users']} | {r['duration']}s | "
                   f"{r['total_requests']} | {r['success_rate']:.2f}% | {r['rps']:.2f} |\n")

        f.write("\n---\n\n")

        # 各场景详细数据
        for r in results:
            f.write(f"## {r['name']} 详细数据\n\n")
            f.write(f"**场景说明**: {SCENARIOS[r['scenario']]['description']}\n\n")

            f.write("### 整体指标\n\n")
            f.write(f"- 并发用户数: {r['users']}\n")
            f.write(f"- 测试时长: {r['duration']}秒\n")
            f.write(f"- 实际运行时长: {r['actual_duration']:.2f}秒\n")
            f.write(f"- 总请求数: {r['total_requests']}\n")
            f.write(f"- 成功请求: {r['total_success']}\n")
            f.write(f"- 失败请求: {r['total_fail']}\n")
            f.write(f"- 成功率: {r['success_rate']:.2f}%\n")
            f.write(f"- RPS: {r['rps']:.2f}\n\n")

            f.write("### 各接口性能\n\n")
            f.write("| 接口 | 请求数 | 成功率 | 平均响应(ms) | P50(ms) | P95(ms) | P99(ms) | RPS |\n")
            f.write("|------|--------|--------|-------------|---------|---------|---------|-----|\n")

            api_labels = {
        'login': 'POST /api/v1/auth/login',
        'projects': 'GET /api/v1/projects',
        'tasks': 'GET /api/v1/tasks/project/{id}'
    }
            for api_name, api_label in api_labels.items():
                s = r['stats'][api_name]
                f.write(f"| {api_label} | {s['total']} | {s['success_rate']:.2f}% | "
                       f"{s['avg_time']:.2f} | {s['p50']:.2f} | {s['p95']:.2f} | "
                       f"{s['p99']:.2f} | {s['rps']:.2f} |\n")

            f.write("\n---\n\n")

        # 对比分析
        f.write("## 对比分析\n\n")

        f.write("### RPS 对比\n\n")
        f.write("![RPS对比](1.png)\n\n")

        f.write("### 平均响应时间对比\n\n")
        f.write("![响应时间对比](2.png)\n\n")

        f.write("### 成功率对比\n\n")
        f.write("![成功率对比](3.png)\n\n")

        f.write("### P95 响应时间对比\n\n")
        f.write("![P95对比](4.png)\n\n")

        f.write("### 总请求数对比\n\n")
        f.write("![请求数对比](5.png)\n\n")

        # 性能评估
        f.write("## 性能评估\n\n")

        baseline = results[0]
        peak = results[2]

        f.write(f"### 负载能力\n\n")
        f.write(f"- 从基线({baseline['users']}用户)到峰值({peak['users']}用户)，"
               f"RPS提升了 {((peak['rps']/baseline['rps']-1)*100):.1f}%\n")
        f.write(f"- 峰值场景下成功率: {peak['success_rate']:.2f}%\n\n")

        f.write(f"### 响应时间\n\n")
        for api_name, api_label in api_labels.items():
            baseline_time = baseline['stats'][api_name]['avg_time']
            peak_time = peak['stats'][api_name]['avg_time']
            increase = ((peak_time/baseline_time-1)*100) if baseline_time > 0 else 0
            f.write(f"- {api_label}: 基线 {baseline_time:.2f}ms → 峰值 {peak_time:.2f}ms "
                   f"(增加 {increase:.1f}%)\n")

        f.write("\n---\n\n")
        f.write("*报告生成时间: {}*\n".format(datetime.now().strftime('%Y-%m-%d %H:%M:%S')))

    print(f"\n✓ 报告已生成: {report_path}")
    return report_path


def generate_analysis(results):
    """生成性能分析内容"""
    baseline = results[0]
    standard = results[1]
    peak = results[2]

    analysis = []

    # 1. 前后值对比 - RPS递增性分析
    baseline_rps = baseline['rps']
    standard_rps = standard['rps']
    peak_rps = peak['rps']

    rps_increase_1 = ((standard_rps / baseline_rps - 1) * 100) if baseline_rps > 0 else 0
    rps_increase_2 = ((peak_rps / standard_rps - 1) * 100) if standard_rps > 0 else 0
    rps_increase_total = ((peak_rps / baseline_rps - 1) * 100) if baseline_rps > 0 else 0

    analysis.append(f"<strong>📊 RPS递增性分析</strong>:<br>"
                   f"• 基线→标准: {baseline_rps:.2f} → {standard_rps:.2f} (增长 {rps_increase_1:.1f}%)<br>"
                   f"• 标准→峰值: {standard_rps:.2f} → {peak_rps:.2f} (增长 {rps_increase_2:.1f}%)<br>"
                   f"• 基线→峰值: {baseline_rps:.2f} → {peak_rps:.2f} (总增长 {rps_increase_total:.1f}%)")

    if rps_increase_total > 50:
        analysis.append(f"✅ <strong>吞吐量表现优秀</strong>: 系统具有良好的扩展性，负载增加10倍时RPS提升{rps_increase_total:.1f}%。")
    elif rps_increase_total > 20:
        analysis.append(f"⚠️ <strong>吞吐量表现一般</strong>: RPS提升{rps_increase_total:.1f}%，可能存在性能瓶颈。")
    else:
        analysis.append(f"❌ <strong>吞吐量表现较差</strong>: RPS提升不明显({rps_increase_total:.1f}%)，系统扩展性受限。")

    # 2. 前后值对比 - 响应时间递增性分析
    api_names = ['login', 'projects', 'tasks']
    api_labels_short = ['登录', '项目列表', '任务列表']

    analysis.append("<br><strong>⏱️ 响应时间递增性分析</strong>:")
    for api_name, api_label in zip(api_names, api_labels_short):
        baseline_time = baseline['stats'][api_name]['avg_time']
        standard_time = standard['stats'][api_name]['avg_time']
        peak_time = peak['stats'][api_name]['avg_time']

        increase_1 = ((standard_time / baseline_time - 1) * 100) if baseline_time > 0 else 0
        increase_2 = ((peak_time / standard_time - 1) * 100) if standard_time > 0 else 0
        increase_total = ((peak_time / baseline_time - 1) * 100) if baseline_time > 0 else 0

        analysis.append(f"• <strong>{api_label}</strong>: {baseline_time:.1f}ms → {standard_time:.1f}ms → {peak_time:.1f}ms "
                       f"(总增长 {increase_total:.1f}%)")

    # 3. 成功率稳定性分析
    baseline_success = baseline['success_rate']
    standard_success = standard['success_rate']
    peak_success = peak['success_rate']

    analysis.append(f"<br><strong>✓ 成功率稳定性</strong>:<br>"
                   f"• 基线: {baseline_success:.2f}% | 标准: {standard_success:.2f}% | 峰值: {peak_success:.2f}%")

    if peak_success >= 99.9:
        analysis.append(f"✅ <strong>稳定性优秀</strong>: 峰值场景下成功率达到{peak_success:.2f}%，系统稳定性极佳。")
    elif peak_success >= 99:
        analysis.append(f"✅ <strong>稳定性良好</strong>: 峰值场景下成功率为{peak_success:.2f}%，系统运行稳定。")
    else:
        analysis.append(f"⚠️ <strong>稳定性需改进</strong>: 峰值场景下成功率为{peak_success:.2f}%，建议优化错误处理。")

    # 4. P95响应时间对比分析
    analysis.append("<br><strong>📈 P95响应时间对比</strong>:")
    worst_p95_time = 0
    worst_p95_api = None

    for api_name, api_label in zip(api_names, api_labels_short):
        baseline_p95 = baseline['stats'][api_name]['p95']
        standard_p95 = standard['stats'][api_name]['p95']
        peak_p95 = peak['stats'][api_name]['p95']

        analysis.append(f"• <strong>{api_label}</strong>: {baseline_p95:.1f}ms → {standard_p95:.1f}ms → {peak_p95:.1f}ms")

        if peak_p95 > worst_p95_time:
            worst_p95_time = peak_p95
            worst_p95_api = api_label

    if worst_p95_time < 100:
        analysis.append(f"✅ <strong>P95响应时间优秀</strong>: 所有接口P95响应时间均低于100ms，用户体验极佳。")
    elif worst_p95_time < 500:
        analysis.append(f"✅ <strong>P95响应时间良好</strong>: 最慢接口({worst_p95_api})P95为{worst_p95_time:.0f}ms，用户体验良好。")
    else:
        analysis.append(f"⚠️ <strong>P95响应时间需优化</strong>: 最慢接口({worst_p95_api})P95达到{worst_p95_time:.0f}ms，建议优化慢查询。")

    # 5. 综合优化建议
    recommendations = []

    # 基于RPS增长率的建议
    if rps_increase_total < 30:
        recommendations.append("检查数据库连接池配置和缓存策略，提升系统吞吐量")

    # 基于响应时间增长的建议
    for api_name, api_label in zip(api_names, api_labels_short):
        baseline_time = baseline['stats'][api_name]['avg_time']
        peak_time = peak['stats'][api_name]['avg_time']
        increase = ((peak_time / baseline_time - 1) * 100) if baseline_time > 0 else 0

        if increase > 200:
            recommendations.append(f"优化{api_label}接口，响应时间增长过大({increase:.0f}%)")

    # 基于成功率的建议
    if peak_success < 99:
        recommendations.append("增加错误重试机制和熔断保护，提升系统稳定性")

    # 基于P95的建议
    if worst_p95_time > 500:
        recommendations.append(f"优化{worst_p95_api}接口慢查询，添加数据库索引")

    # 基于递增性的建议
    if rps_increase_2 < 5:
        recommendations.append("标准到峰值场景RPS增长停滞，可能已达到系统瓶颈，建议进行性能调优或扩容")

    if recommendations:
        analysis.append(f"<br>💡 <strong>优化建议</strong>:<br>• " + "<br>• ".join(recommendations))
    else:
        analysis.append("<br>🎉 <strong>系统表现优秀</strong>: 当前性能指标良好，无需特别优化。")

    return "<br><br>".join(analysis)


def generate_html_report(results, output_dir, charts_base64):
    """生成HTML报告"""
    html_path = f"{output_dir}/scenario_comparison_report.html"

    # 生成性能分析
    analysis_content = generate_analysis(results)

    # 生成场景卡片
    scenario_cards = ""
    card_classes = ['baseline', 'standard', 'peak']
    for i, r in enumerate(results):
        scenario_cards += f"""
            <div class="scenario-card {card_classes[i]}">
                <h3>{r['name']}</h3>
                <div class="scenario-stat">并发用户: <strong>{r['users']}</strong></div>
                <div class="scenario-stat">总请求数: <strong>{r['total_requests']}</strong></div>
                <div class="scenario-stat">成功率: <strong>{r['success_rate']:.2f}%</strong></div>
                <div class="scenario-stat">RPS: <strong>{r['rps']:.2f}</strong></div>
            </div>
        """

    # 生成概览表格行
    overview_rows = ""
    for r in results:
        overview_rows += f"""
                <tr>
                    <td><strong>{r['name']}</strong></td>
                    <td>{r['users']}</td>
                    <td>{r['duration']}秒</td>
                    <td>{r['total_requests']}</td>
                    <td><span class="badge badge-success">{r['success_rate']:.2f}%</span></td>
                    <td>{r['rps']:.2f}</td>
                </tr>
        """

    # 生成详细数据部分
    detailed_sections = ""
    api_labels = {
        'login': 'POST /api/v1/auth/login',
        'projects': 'GET /api/v1/projects',
        'tasks': 'GET /api/v1/tasks/project/{id}'
    }

    for r in results:
        detailed_sections += f"""
        <h2>📋 {r['name']} 详细数据</h2>
        <p><em>{SCENARIOS[r['scenario']]['description']}</em></p>

        <h3>整体指标</h3>
        <div class="performance-grid">
            <div class="perf-card">
                <div class="label">并发用户数</div>
                <div class="value">{r['users']}</div>
            </div>
            <div class="perf-card">
                <div class="label">测试时长</div>
                <div class="value">{r['duration']}秒</div>
            </div>
            <div class="perf-card">
                <div class="label">总请求数</div>
                <div class="value">{r['total_requests']}</div>
            </div>
            <div class="perf-card">
                <div class="label">成功率</div>
                <div class="value">{r['success_rate']:.2f}%</div>
            </div>
            <div class="perf-card">
                <div class="label">RPS</div>
                <div class="value">{r['rps']:.2f}</div>
            </div>
        </div>

        <h3>各接口性能</h3>
        <table>
            <thead>
                <tr>
                    <th>接口</th>
                    <th>请求数</th>
                    <th>成功率</th>
                    <th>平均响应(ms)</th>
                    <th>P50(ms)</th>
                    <th>P95(ms)</th>
                    <th>P99(ms)</th>
                    <th>RPS</th>
                </tr>
            </thead>
            <tbody>
        """

        for api_name, api_label in api_labels.items():
            s = r['stats'][api_name]
            detailed_sections += f"""
                <tr>
                    <td><strong>{api_label}</strong></td>
                    <td>{s['total']}</td>
                    <td><span class="badge badge-success">{s['success_rate']:.2f}%</span></td>
                    <td>{s['avg_time']:.2f}</td>
                    <td>{s['p50']:.2f}</td>
                    <td>{s['p95']:.2f}</td>
                    <td>{s['p99']:.2f}</td>
                    <td>{s['rps']:.2f}</td>
                </tr>
            """

        detailed_sections += """
            </tbody>
        </table>
        """

    # 生成性能评估
    baseline = results[0]
    peak = results[2]

    performance_evaluation = f"""
        <div class="metric">
            <div class="metric-label">负载能力提升</div>
            <div class="metric-value">从 {baseline['users']} 用户到 {peak['users']} 用户，RPS 提升了 {((peak['rps']/baseline['rps']-1)*100):.1f}%</div>
        </div>

        <div class="metric">
            <div class="metric-label">峰值场景成功率</div>
            <div class="metric-value" style="color: #27ae60;">{peak['success_rate']:.2f}%</div>
        </div>

        <h3>响应时间变化</h3>
        <table>
            <thead>
                <tr>
                    <th>接口</th>
                    <th>基线响应时间</th>
                    <th>峰值响应时间</th>
                    <th>增长率</th>
                </tr>
            </thead>
            <tbody>
    """

    for api_name, api_label in api_labels.items():
        baseline_time = baseline['stats'][api_name]['avg_time']
        peak_time = peak['stats'][api_name]['avg_time']
        increase = ((peak_time/baseline_time-1)*100) if baseline_time > 0 else 0
        badge_class = 'badge-success' if increase < 100 else 'badge-warning'

        performance_evaluation += f"""
                <tr>
                    <td><strong>{api_label}</strong></td>
                    <td>{baseline_time:.2f} ms</td>
                    <td>{peak_time:.2f} ms</td>
                    <td><span class="badge {badge_class}">+{increase:.1f}%</span></td>
                </tr>
        """

    performance_evaluation += """
            </tbody>
        </table>
    """

    # 生成图表HTML（使用base64嵌入或显示提示）
    charts_html = ""
    if charts_base64:
        charts_html = f"""
        <h3>RPS 对比</h3>
        <div class="chart-container">
            <img src="data:image/png;base64,{charts_base64['chart1']}" alt="RPS对比">
        </div>

        <h3>平均响应时间对比</h3>
        <div class="chart-container">
            <img src="data:image/png;base64,{charts_base64['chart2']}" alt="响应时间对比">
        </div>

        <h3>成功率对比</h3>
        <div class="chart-container">
            <img src="data:image/png;base64,{charts_base64['chart3']}" alt="成功率对比">
        </div>

        <h3>P95 响应时间对比</h3>
        <div class="chart-container">
            <img src="data:image/png;base64,{charts_base64['chart4']}" alt="P95对比">
        </div>

        <h3>总请求数对比</h3>
        <div class="chart-container">
            <img src="data:image/png;base64,{charts_base64['chart5']}" alt="请求数对比">
        </div>
        """
    else:
        charts_html = """
        <div class="metric" style="background: #fff3cd; border-left: 4px solid #ffc107;">
            <div class="metric-label" style="color: #856404;">图表生成失败</div>
            <div class="metric-value" style="font-size: 16px; color: #856404;">
                请安装 matplotlib: pip3 install matplotlib
            </div>
        </div>
        """

    test_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    generation_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>压力测试场景对比报告</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif;
               background: #f5f7fa; padding: 20px; line-height: 1.6; }}
        .container {{ max-width: 1400px; margin: 0 auto; background: white;
                     border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); padding: 40px; }}
        h1 {{ color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 15px; margin-bottom: 30px; }}
        h2 {{ color: #34495e; margin-top: 40px; margin-bottom: 20px; padding-left: 10px;
             border-left: 4px solid #3498db; }}
        h3 {{ color: #555; margin-top: 25px; margin-bottom: 15px; }}
        .meta {{ color: #7f8c8d; font-size: 14px; margin-bottom: 30px; }}
        table {{ width: 100%; border-collapse: collapse; margin: 20px 0;
                box-shadow: 0 1px 3px rgba(0,0,0,0.1); }}
        th, td {{ padding: 12px 15px; text-align: left; border-bottom: 1px solid #e0e0e0; }}
        th {{ background: #3498db; color: white; font-weight: 600; }}
        tr:hover {{ background: #f8f9fa; }}
        .success {{ color: #27ae60; font-weight: bold; }}
        .metric {{ background: #ecf0f1; padding: 15px; border-radius: 5px; margin: 10px 0; }}
        .metric-label {{ color: #7f8c8d; font-size: 13px; }}
        .metric-value {{ color: #2c3e50; font-size: 24px; font-weight: bold; margin-top: 5px; }}
        .scenario-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                         gap: 20px; margin: 20px 0; }}
        .scenario-card {{ background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                         color: white; padding: 20px; border-radius: 8px; }}
        .scenario-card.baseline {{ background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%); }}
        .scenario-card.standard {{ background: linear-gradient(135deg, #2196F3 0%, #1976D2 100%); }}
        .scenario-card.peak {{ background: linear-gradient(135deg, #FF9800 0%, #F57C00 100%); }}
        .scenario-card h3 {{ color: white; margin-top: 0; }}
        .scenario-stat {{ margin: 10px 0; font-size: 14px; }}
        .scenario-stat strong {{ font-size: 20px; display: block; margin-top: 5px; }}
        .chart-container {{ margin: 30px 0; text-align: center; }}
        .chart-container img {{ max-width: 100%; height: auto; border-radius: 8px;
                               box-shadow: 0 2px 8px rgba(0,0,0,0.1); }}
        .performance-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                           gap: 15px; margin: 20px 0; }}
        .perf-card {{ background: #f8f9fa; padding: 15px; border-radius: 5px;
                     border-left: 4px solid #3498db; }}
        .perf-card .label {{ color: #7f8c8d; font-size: 13px; }}
        .perf-card .value {{ color: #2c3e50; font-size: 20px; font-weight: bold; margin-top: 5px; }}
        .footer {{ margin-top: 50px; padding-top: 20px; border-top: 1px solid #e0e0e0;
                  text-align: center; color: #7f8c8d; font-size: 13px; }}
        .badge {{ display: inline-block; padding: 4px 8px; border-radius: 3px;
                 font-size: 12px; font-weight: bold; }}
        .badge-success {{ background: #d4edda; color: #155724; }}
        .badge-warning {{ background: #fff3cd; color: #856404; }}
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 压力测试场景对比报告</h1>
        <div class="meta">测试时间: {test_time}</div>

        <h2>📊 场景概览</h2>
        <div class="scenario-grid">
            {scenario_cards}
        </div>

        <h2>📝 性能分析</h2>
        <div class="analysis-box">
            {analysis_content}
        </div>

        <h2>📈 核心指标对比</h2>
        <table>
            <thead>
                <tr>
                    <th>场景</th>
                    <th>并发用户</th>
                    <th>测试时长</th>
                    <th>总请求数</th>
                    <th>成功率</th>
                    <th>RPS</th>
                </tr>
            </thead>
            <tbody>
                {overview_rows}
            </tbody>
        </table>

        {detailed_sections}

        <h2>📉 可视化对比分析</h2>

        {charts_html}

        <h2>🎯 性能评估</h2>
        {performance_evaluation}

        <div class="footer">
            报告生成时间: {generation_time}
        </div>
    </div>
</body>
</html>
"""

    with open(html_path, 'w', encoding='utf-8') as f:
        f.write(html_content)

    print(f"✓ HTML报告已生成: {html_path}")
    return html_path

    # 生成场景卡片
    scenario_cards = ""
    card_classes = ['baseline', 'standard', 'peak']
    for i, r in enumerate(results):
        scenario_cards += f"""
            <div class="scenario-card {card_classes[i]}">
                <h3>{r['name']}</h3>
                <div class="scenario-stat">并发用户: <strong>{r['users']}</strong></div>
                <div class="scenario-stat">总请求数: <strong>{r['total_requests']}</strong></div>
                <div class="scenario-stat">成功率: <strong>{r['success_rate']:.2f}%</strong></div>
                <div class="scenario-stat">RPS: <strong>{r['rps']:.2f}</strong></div>
            </div>
        """

    # 生成概览表格行
    overview_rows = ""
    for r in results:
        overview_rows += f"""
                <tr>
                    <td><strong>{r['name']}</strong></td>
                    <td>{r['users']}</td>
                    <td>{r['duration']}秒</td>
                    <td>{r['total_requests']}</td>
                    <td><span class="badge badge-success">{r['success_rate']:.2f}%</span></td>
                    <td>{r['rps']:.2f}</td>
                </tr>
        """

    # 生成详细数据部分
    detailed_sections = ""
    api_labels = {
        'login': 'POST /api/v1/auth/login',
        'projects': 'GET /api/v1/projects',
        'tasks': 'GET /api/v1/tasks/project/{id}'
    }

    for r in results:
        detailed_sections += f"""
        <h2>📋 {r['name']} 详细数据</h2>
        <p><em>{SCENARIOS[r['scenario']]['description']}</em></p>

        <h3>整体指标</h3>
        <div class="performance-grid">
            <div class="perf-card">
                <div class="label">并发用户数</div>
                <div class="value">{r['users']}</div>
            </div>
            <div class="perf-card">
                <div class="label">测试时长</div>
                <div class="value">{r['duration']}秒</div>
            </div>
            <div class="perf-card">
                <div class="label">总请求数</div>
                <div class="value">{r['total_requests']}</div>
            </div>
            <div class="perf-card">
                <div class="label">成功率</div>
                <div class="value">{r['success_rate']:.2f}%</div>
            </div>
            <div class="perf-card">
                <div class="label">RPS</div>
                <div class="value">{r['rps']:.2f}</div>
            </div>
        </div>

        <h3>各接口性能</h3>
        <table>
            <thead>
                <tr>
                    <th>接口</th>
                    <th>请求数</th>
                    <th>成功率</th>
                    <th>平均响应(ms)</th>
                    <th>P50(ms)</th>
                    <th>P95(ms)</th>
                    <th>P99(ms)</th>
                    <th>RPS</th>
                </tr>
            </thead>
            <tbody>
        """

        for api_name, api_label in api_labels.items():
            s = r['stats'][api_name]
            detailed_sections += f"""
                <tr>
                    <td><strong>{api_label}</strong></td>
                    <td>{s['total']}</td>
                    <td><span class="badge badge-success">{s['success_rate']:.2f}%</span></td>
                    <td>{s['avg_time']:.2f}</td>
                    <td>{s['p50']:.2f}</td>
                    <td>{s['p95']:.2f}</td>
                    <td>{s['p99']:.2f}</td>
                    <td>{s['rps']:.2f}</td>
                </tr>
            """

        detailed_sections += """
            </tbody>
        </table>
        """

    # 生成性能评估
    baseline = results[0]
    peak = results[2]

    performance_evaluation = f"""
        <div class="metric">
            <div class="metric-label">负载能力提升</div>
            <div class="metric-value">从 {baseline['users']} 用户到 {peak['users']} 用户，RPS 提升了 {((peak['rps']/baseline['rps']-1)*100):.1f}%</div>
        </div>

        <div class="metric">
            <div class="metric-label">峰值场景成功率</div>
            <div class="metric-value" style="color: #27ae60;">{peak['success_rate']:.2f}%</div>
        </div>

        <h3>响应时间变化</h3>
        <table>
            <thead>
                <tr>
                    <th>接口</th>
                    <th>基线响应时间</th>
                    <th>峰值响应时间</th>
                    <th>增长率</th>
                </tr>
            </thead>
            <tbody>
    """

    for api_name, api_label in api_labels.items():
        baseline_time = baseline['stats'][api_name]['avg_time']
        peak_time = peak['stats'][api_name]['avg_time']
        increase = ((peak_time/baseline_time-1)*100) if baseline_time > 0 else 0
        badge_class = 'badge-success' if increase < 100 else 'badge-warning'

        performance_evaluation += f"""
                <tr>
                    <td><strong>{api_label}</strong></td>
                    <td>{baseline_time:.2f} ms</td>
                    <td>{peak_time:.2f} ms</td>
                    <td><span class="badge {badge_class}">+{increase:.1f}%</span></td>
                </tr>
        """

    performance_evaluation += """
            </tbody>
        </table>
    """

    # 生成性能分析
    analysis_content = generate_analysis(results)

    test_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    generation_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>压力测试场景对比报告</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif;
               background: #f5f7fa; padding: 20px; line-height: 1.6; }}
        .container {{ max-width: 1400px; margin: 0 auto; background: white;
                     border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); padding: 40px; }}
        h1 {{ color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 15px; margin-bottom: 30px; }}
        h2 {{ color: #34495e; margin-top: 40px; margin-bottom: 20px; padding-left: 10px;
             border-left: 4px solid #3498db; }}
        h3 {{ color: #555; margin-top: 25px; margin-bottom: 15px; }}
        .meta {{ color: #7f8c8d; font-size: 14px; margin-bottom: 30px; }}
        .analysis-box {{ background: #e8f5e9; border-left: 4px solid #4caf50; padding: 20px;
                        margin: 20px 0; border-radius: 5px; line-height: 1.8; }}
        table {{ width: 100%; border-collapse: collapse; margin: 20px 0;
                box-shadow: 0 1px 3px rgba(0,0,0,0.1); }}
        th, td {{ padding: 12px 15px; text-align: left; border-bottom: 1px solid #e0e0e0; }}
        th {{ background: #3498db; color: white; font-weight: 600; }}
        tr:hover {{ background: #f8f9fa; }}
        .success {{ color: #27ae60; font-weight: bold; }}
        .metric {{ background: #ecf0f1; padding: 15px; border-radius: 5px; margin: 10px 0; }}
        .metric-label {{ color: #7f8c8d; font-size: 13px; }}
        .metric-value {{ color: #2c3e50; font-size: 24px; font-weight: bold; margin-top: 5px; }}
        .scenario-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                         gap: 20px; margin: 20px 0; }}
        .scenario-card {{ background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                         color: white; padding: 20px; border-radius: 8px; }}
        .scenario-card.baseline {{ background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%); }}
        .scenario-card.standard {{ background: linear-gradient(135deg, #2196F3 0%, #1976D2 100%); }}
        .scenario-card.peak {{ background: linear-gradient(135deg, #FF9800 0%, #F57C00 100%); }}
        .scenario-card h3 {{ color: white; margin-top: 0; }}
        .scenario-stat {{ margin: 10px 0; font-size: 14px; }}
        .scenario-stat strong {{ font-size: 20px; display: block; margin-top: 5px; }}
        .chart-container {{ margin: 30px 0; text-align: center; }}
        .chart-container img {{ max-width: 100%; height: auto; border-radius: 8px;
                               box-shadow: 0 2px 8px rgba(0,0,0,0.1); }}
        .performance-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                           gap: 15px; margin: 20px 0; }}
        .perf-card {{ background: #f8f9fa; padding: 15px; border-radius: 5px;
                     border-left: 4px solid #3498db; }}
        .perf-card .label {{ color: #7f8c8d; font-size: 13px; }}
        .perf-card .value {{ color: #2c3e50; font-size: 20px; font-weight: bold; margin-top: 5px; }}
        .footer {{ margin-top: 50px; padding-top: 20px; border-top: 1px solid #e0e0e0;
                  text-align: center; color: #7f8c8d; font-size: 13px; }}
        .badge {{ display: inline-block; padding: 4px 8px; border-radius: 3px;
                 font-size: 12px; font-weight: bold; }}
        .badge-success {{ background: #d4edda; color: #155724; }}
        .badge-warning {{ background: #fff3cd; color: #856404; }}
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 压力测试场景对比报告</h1>
        <div class="meta">测试时间: {test_time}</div>

        <h2>📊 场景概览</h2>
        <div class="scenario-grid">
            {scenario_cards}
        </div>

        <h2>📈 核心指标对比</h2>
        <table>
            <thead>
                <tr>
                    <th>场景</th>
                    <th>并发用户</th>
                    <th>测试时长</th>
                    <th>总请求数</th>
                    <th>成功率</th>
                    <th>RPS</th>
                </tr>
            </thead>
            <tbody>
                {overview_rows}
            </tbody>
        </table>

        {detailed_sections}

        <h2>📉 可视化对比分析</h2>

        <h3>RPS 对比</h3>
        <div class="chart-container">
            <img src="1.png" alt="RPS对比">
        </div>

        <h3>平均响应时间对比</h3>
        <div class="chart-container">
            <img src="2.png" alt="响应时间对比">
        </div>

        <h3>成功率对比</h3>
        <div class="chart-container">
            <img src="3.png" alt="成功率对比">
        </div>

        <h3>P95 响应时间对比</h3>
        <div class="chart-container">
            <img src="4.png" alt="P95对比">
        </div>

        <h3>总请求数对比</h3>
        <div class="chart-container">
            <img src="5.png" alt="请求数对比">
        </div>

        <h2>🎯 性能评估</h2>
        {performance_evaluation}

        <div class="footer">
            报告生成时间: {generation_time}
        </div>
    </div>
</body>
</html>
"""

    with open(html_path, 'w', encoding='utf-8') as f:
        f.write(html_content)

    print(f"✓ HTML报告已生成: {html_path}")
    return html_path

    with open(html_path, 'w', encoding='utf-8') as f:
        f.write(html_content)

    print(f"✓ HTML报告已生成: {html_path}")
    return html_path


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='场景化API压力测试')
    parser.add_argument('--scenarios', nargs='+',
                       choices=['baseline', 'standard', 'peak', 'all'],
                       default=['all'],
                       help='要运行的测试场景')
    args = parser.parse_args()

    # 读取配置
    script_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(os.path.dirname(script_dir), 'all-interface-test', 'config.json')

    try:
        with open(config_path, 'r', encoding='utf-8') as f:
            config = json.load(f)
    except Exception as e:
        print(f"错误: 无法读取配置文件 {config_path}")
        print(f"详情: {e}")
        sys.exit(1)

    base_url = config.get('baseUrl', 'http://159.75.202.106:30080/api/v1')
    test_user = config.get('testData', {}).get('users', [{}])[0]
    username = test_user.get('username', 'testuser1')
    password = test_user.get('password', 'Test123456!')

    # 创建输出目录 - 使用日期-时分秒-版本号格式
    now = datetime.now()
    date_str = now.strftime('%Y%m%d')
    time_str = now.strftime('%H%M%S')

    # 查找当天已有的最大版本号（只按日期匹配）
    max_version = 0
    if os.path.exists(script_dir):
        for dirname in os.listdir(script_dir):
            if dirname.startswith(f"{date_str}-") and '-v' in dirname:
                try:
                    version_num = int(dirname.split('-v')[1])
                    max_version = max(max_version, version_num)
                except (ValueError, IndexError):
                    pass

    # 新版本号
    new_version = max_version + 1
    version_str = f"v{new_version}"

    output_dir = os.path.join(script_dir, f"{date_str}-{time_str}-{version_str}")
    os.makedirs(output_dir, exist_ok=True)

    print("="*60)
    print("场景化API压力测试")
    print("="*60)
    print(f"目标服务器: {base_url}")
    print(f"测试用户: {username}")
    print(f"输出目录: {output_dir}")
    print(f"版本号: {version_str}")

    # 确定要运行的场景
    if 'all' in args.scenarios:
        scenarios_to_run = ['baseline', 'standard', 'peak']
    else:
        scenarios_to_run = args.scenarios

    # 运行测试场景
    results = []
    for scenario_name in scenarios_to_run:
        result = run_scenario(base_url, username, password, scenario_name)
        results.append(result)
        time.sleep(2)  # 场景间休息

    # 生成图表和报告
    print(f"\n{'='*60}")
    print("生成对比报告和图表")
    print("="*60)

    charts_base64 = generate_charts(results, output_dir)
    generate_report(results, output_dir)
    generate_html_report(results, output_dir, charts_base64)

    # 保存JSON数据
    json_path = f"{output_dir}/results.json"
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"✓ JSON数据已保存: {json_path}")

    print(f"\n{'='*60}")
    print("测试完成！")
    print("="*60)
    print(f"\n所有文件已保存到: {output_dir}/")
    print(f"\n快速查看报告:")
    print(f"  HTML: {output_dir}/scenario_comparison_report.html")
    print(f"  Markdown: {output_dir}/scenario_comparison_report.md")

    # 生成汇总报告
    print(f"\n生成汇总报告...")
    try:
        import subprocess
        subprocess.run([sys.executable, os.path.join(script_dir, 'generate_summary.py')],
                      check=True, capture_output=True)
        print(f"✓ 汇总报告已更新: {script_dir}/summary_report.html")
    except Exception as e:
        print(f"⚠ 汇总报告生成失败: {e}")


if __name__ == '__main__':
    main()


