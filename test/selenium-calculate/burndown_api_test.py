#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
燃尽图计算性能测试 - API直接测试
测试燃尽图计算接口的响应时间
"""

import time
import json
import os
import requests
from datetime import datetime
from typing import Dict, List


class BurndownAPITest:
    def __init__(self, base_url="http://159.75.202.106:30080/api/v1"):
        self.base_url = base_url
        self.token = None
        self.results = []

    def login(self, username="admin", password="password123"):
        """登录获取token"""
        print(f"正在登录: {username}")

        url = f"{self.base_url}/auth/login"
        data = {
            "username": username,
            "password": password
        }

        response = requests.post(url, json=data, timeout=10)

        if response.status_code == 200:
            result = response.json()
            self.token = result.get('token')
            print(f"✓ 登录成功，获取到token")
            return True
        else:
            print(f"✗ 登录失败: {response.status_code} - {response.text}")
            return False

    def test_burndown_calculate(self, sprint_id=1, test_count=10):
        """测试燃尽图计算性能"""
        print(f"\n{'='*60}")
        print(f"开始测试燃尽图计算性能")
        print(f"冲刺ID: {sprint_id}")
        print(f"测试次数: {test_count}")
        print(f"{'='*60}\n")

        headers = {
            'Authorization': f'Bearer {self.token}',
            'Content-Type': 'application/json'
        }

        for i in range(test_count):
            print(f"[测试 {i+1}/{test_count}]", end=" ")

            try:
                # 记录开始时间
                start_time = time.time()

                # 调用燃尽图计算接口
                url = f"{self.base_url}/burndown/sprints/{sprint_id}/calculate"
                response = requests.post(url, headers=headers, timeout=30)

                # 记录结束时间
                end_time = time.time()
                duration = (end_time - start_time) * 1000  # 转换为毫秒

                success = response.status_code in [200, 201]

                result = {
                    'test_number': i + 1,
                    'sprint_id': sprint_id,
                    'success': success,
                    'status_code': response.status_code,
                    'duration_ms': round(duration, 2),
                    'timestamp': datetime.now().isoformat()
                }

                if success:
                    print(f"✓ {duration:.2f}ms (HTTP {response.status_code})")
                else:
                    print(f"✗ {duration:.2f}ms (HTTP {response.status_code})")
                    result['error'] = response.text[:200]

                self.results.append(result)

                # 短暂等待
                time.sleep(0.5)

            except requests.exceptions.Timeout:
                print(f"✗ 超时 (>30s)")
                self.results.append({
                    'test_number': i + 1,
                    'sprint_id': sprint_id,
                    'success': False,
                    'status_code': 0,
                    'duration_ms': 30000,
                    'error': 'Timeout',
                    'timestamp': datetime.now().isoformat()
                })
            except Exception as e:
                print(f"✗ 错误: {str(e)}")
                self.results.append({
                    'test_number': i + 1,
                    'sprint_id': sprint_id,
                    'success': False,
                    'status_code': 0,
                    'duration_ms': 0,
                    'error': str(e),
                    'timestamp': datetime.now().isoformat()
                })

    def generate_report(self):
        """生成测试报告"""
        if not self.results:
            print("没有测试结果")
            return

        # 计算统计数据
        successful_tests = [r for r in self.results if r['success']]
        failed_tests = [r for r in self.results if not r['success']]

        total_tests = len(self.results)
        success_count = len(successful_tests)
        fail_count = len(failed_tests)
        success_rate = (success_count / total_tests * 100) if total_tests > 0 else 0

        if successful_tests:
            durations = [r['duration_ms'] for r in successful_tests]
            avg_duration = sum(durations) / len(durations)
            min_duration = min(durations)
            max_duration = max(durations)

            # 计算P50, P95, P99
            sorted_durations = sorted(durations)
            p50_index = int(len(sorted_durations) * 0.50)
            p95_index = int(len(sorted_durations) * 0.95)
            p99_index = int(len(sorted_durations) * 0.99)

            p50_duration = sorted_durations[p50_index] if p50_index < len(sorted_durations) else sorted_durations[-1]
            p95_duration = sorted_durations[p95_index] if p95_index < len(sorted_durations) else sorted_durations[-1]
            p99_duration = sorted_durations[p99_index] if p99_index < len(sorted_durations) else sorted_durations[-1]
        else:
            avg_duration = min_duration = max_duration = 0
            p50_duration = p95_duration = p99_duration = 0

        # 生成报告
        report = {
            'test_info': {
                'base_url': self.base_url,
                'test_time': datetime.now().isoformat(),
                'total_tests': total_tests,
                'test_type': 'API直接测试（同步模式）'
            },
            'summary': {
                'success_count': success_count,
                'fail_count': fail_count,
                'success_rate': f"{success_rate:.2f}%",
                'avg_duration_ms': round(avg_duration, 2),
                'min_duration_ms': round(min_duration, 2),
                'max_duration_ms': round(max_duration, 2),
                'p50_duration_ms': round(p50_duration, 2),
                'p95_duration_ms': round(p95_duration, 2),
                'p99_duration_ms': round(p99_duration, 2)
            },
            'detailed_results': self.results
        }

        # 保存JSON报告
        output_dir = os.path.dirname(os.path.abspath(__file__))
        timestamp = datetime.now().strftime('%Y%m%d-%H%M%S')
        json_file = os.path.join(output_dir, f'burndown_api_test_{timestamp}.json')

        with open(json_file, 'w', encoding='utf-8') as f:
            json.dump(report, f, ensure_ascii=False, indent=2)

        # 生成Markdown报告
        md_file = os.path.join(output_dir, f'burndown_api_test_{timestamp}.md')
        with open(md_file, 'w', encoding='utf-8') as f:
            f.write(f"# 燃尽图计算性能测试报告 - API直接测试\n\n")
            f.write(f"## 测试信息\n\n")
            f.write(f"- **测试URL**: {self.base_url}\n")
            f.write(f"- **测试时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"- **测试次数**: {total_tests}\n")
            f.write(f"- **测试类型**: API直接测试（同步模式）\n\n")

            f.write(f"## 测试结果摘要\n\n")
            f.write(f"| 指标 | 数值 |\n")
            f.write(f"|------|------|\n")
            f.write(f"| 总测试数 | {total_tests} |\n")
            f.write(f"| 成功次数 | {success_count} |\n")
            f.write(f"| 失败次数 | {fail_count} |\n")
            f.write(f"| 成功率 | {success_rate:.2f}% |\n")
            f.write(f"| 平均耗时 | {avg_duration:.2f}ms |\n")
            f.write(f"| 最小耗时 | {min_duration:.2f}ms |\n")
            f.write(f"| 最大耗时 | {max_duration:.2f}ms |\n")
            f.write(f"| P50耗时 | {p50_duration:.2f}ms |\n")
            f.write(f"| P95耗时 | {p95_duration:.2f}ms |\n")
            f.write(f"| P99耗时 | {p99_duration:.2f}ms |\n\n")

            f.write(f"## 详细测试结果\n\n")
            f.write(f"| 测试编号 | 状态 | HTTP状态码 | 耗时(ms) | 时间戳 |\n")
            f.write(f"|---------|------|-----------|---------|--------|\n")

            for result in self.results:
                status = "✓ 成功" if result['success'] else "✗ 失败"
                duration = result.get('duration_ms', 0)
                status_code = result.get('status_code', 0)
                timestamp = result['timestamp'].split('T')[1].split('.')[0]
                f.write(f"| {result['test_number']} | {status} | {status_code} | {duration:.2f} | {timestamp} |\n")

        # 打印控制台报告
        print(f"\n{'='*60}")
        print(f"测试报告")
        print(f"{'='*60}\n")
        print(f"总测试数: {total_tests}")
        print(f"成功: {success_count} | 失败: {fail_count}")
        print(f"成功率: {success_rate:.2f}%\n")
        print(f"平均耗时: {avg_duration:.2f}ms")
        print(f"最小耗时: {min_duration:.2f}ms")
        print(f"最大耗时: {max_duration:.2f}ms")
        print(f"P50耗时: {p50_duration:.2f}ms")
        print(f"P95耗时: {p95_duration:.2f}ms")
        print(f"P99耗时: {p99_duration:.2f}ms\n")
        print(f"报告已保存:")
        print(f"  JSON: {json_file}")
        print(f"  Markdown: {md_file}\n")

    def run(self, sprint_id=1, test_count=10):
        """运行完整测试流程"""
        try:
            if not self.login():
                print("登录失败，无法继续测试")
                return

            self.test_burndown_calculate(sprint_id=sprint_id, test_count=test_count)
            self.generate_report()

        except Exception as e:
            print(f"测试过程中发生错误: {e}")
            import traceback
            traceback.print_exc()


def main():
    """主函数"""
    import argparse

    parser = argparse.ArgumentParser(description='燃尽图计算性能测试 - API直接测试')
    parser.add_argument('--url', default='http://159.75.202.106:30080/api/v1', help='后端API URL')
    parser.add_argument('--sprint-id', type=int, default=1, help='冲刺ID')
    parser.add_argument('--count', type=int, default=10, help='测试次数')

    args = parser.parse_args()

    tester = BurndownAPITest(base_url=args.url)
    tester.run(sprint_id=args.sprint_id, test_count=args.count)


if __name__ == '__main__':
    main()
