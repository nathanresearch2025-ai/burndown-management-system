#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
燃尽图计算性能测试 - Selenium端到端测试
测试从前端发起燃尽图计算到完成的整体时间
"""

import time
import json
import os
from datetime import datetime
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
from selenium.common.exceptions import TimeoutException, NoSuchElementException

class BurndownCalculateTest:
    def __init__(self, base_url="http://159.75.202.106:30173"):
        self.base_url = base_url
        self.driver = None
        self.results = []

    def setup_driver(self):
        """初始化Chrome驱动"""
        chrome_options = Options()
        chrome_options.add_argument('--headless')  # 无头模式
        chrome_options.add_argument('--no-sandbox')
        chrome_options.add_argument('--disable-dev-shm-usage')
        chrome_options.add_argument('--disable-gpu')
        chrome_options.add_argument('--window-size=1920,1080')

        self.driver = webdriver.Chrome(options=chrome_options)
        self.driver.implicitly_wait(10)

    def login(self, username="admin", password="password123"):
        """登录系统"""
        print(f"正在登录: {username}")

        self.driver.get(f"{self.base_url}/login")
        time.sleep(2)

        # 输入用户名和密码
        username_input = self.driver.find_element(By.ID, "username")
        password_input = self.driver.find_element(By.ID, "password")

        username_input.clear()
        username_input.send_keys(username)

        password_input.clear()
        password_input.send_keys(password)

        # 点击登录按钮
        login_button = self.driver.find_element(By.CSS_SELECTOR, "button[type='submit']")
        login_button.click()

        # 等待登录成功（跳转到首页）
        WebDriverWait(self.driver, 10).until(
            EC.url_contains("/dashboard")
        )

        print("✓ 登录成功")

    def navigate_to_sprint(self, project_id=1, sprint_id=1):
        """导航到冲刺详情页"""
        print(f"导航到冲刺详情页: 项目{project_id}, 冲刺{sprint_id}")

        # 访问冲刺详情页
        sprint_url = f"{self.base_url}/projects/{project_id}/sprints/{sprint_id}"
        self.driver.get(sprint_url)

        # 等待页面加载
        time.sleep(2)

        print("✓ 已到达冲刺详情页")

    def test_burndown_calculate(self, sprint_id=1, test_count=5):
        """测试燃尽图计算时间"""
        print(f"\n{'='*60}")
        print(f"开始测试燃尽图计算性能 (冲刺ID: {sprint_id})")
        print(f"测试次数: {test_count}")
        print(f"{'='*60}\n")

        for i in range(test_count):
            print(f"[测试 {i+1}/{test_count}]")

            try:
                # 记录开始时间
                start_time = time.time()

                # 查找并点击"计算燃尽图"按钮
                # 根据实际前端实现调整选择器
                calculate_button = self.driver.find_element(
                    By.XPATH,
                    "//button[contains(text(), '计算燃尽图') or contains(text(), 'Calculate') or contains(text(), '刷新')]"
                )

                print(f"  点击计算按钮...")
                calculate_button.click()

                # 等待计算完成的标志
                # 方式1: 等待成功提示消息
                try:
                    WebDriverWait(self.driver, 30).until(
                        EC.presence_of_element_located((By.CLASS_NAME, "ant-message-success"))
                    )
                    success = True
                except TimeoutException:
                    # 方式2: 等待图表渲染完成
                    try:
                        WebDriverWait(self.driver, 30).until(
                            EC.presence_of_element_located((By.CSS_SELECTOR, "canvas, svg"))
                        )
                        success = True
                    except TimeoutException:
                        success = False

                # 记录结束时间
                end_time = time.time()
                duration = (end_time - start_time) * 1000  # 转换为毫秒

                result = {
                    'test_number': i + 1,
                    'sprint_id': sprint_id,
                    'success': success,
                    'duration_ms': round(duration, 2),
                    'timestamp': datetime.now().isoformat()
                }

                self.results.append(result)

                if success:
                    print(f"  ✓ 计算完成: {duration:.2f}ms")
                else:
                    print(f"  ✗ 计算超时: {duration:.2f}ms")

                # 等待一下再进行下一次测试
                time.sleep(2)

            except NoSuchElementException as e:
                print(f"  ✗ 找不到计算按钮: {e}")
                self.results.append({
                    'test_number': i + 1,
                    'sprint_id': sprint_id,
                    'success': False,
                    'duration_ms': 0,
                    'error': '找不到计算按钮',
                    'timestamp': datetime.now().isoformat()
                })
            except Exception as e:
                print(f"  ✗ 测试失败: {e}")
                self.results.append({
                    'test_number': i + 1,
                    'sprint_id': sprint_id,
                    'success': False,
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

            # 计算P95
            sorted_durations = sorted(durations)
            p95_index = int(len(sorted_durations) * 0.95)
            p95_duration = sorted_durations[p95_index] if p95_index < len(sorted_durations) else sorted_durations[-1]
        else:
            avg_duration = min_duration = max_duration = p95_duration = 0

        # 生成报告
        report = {
            'test_info': {
                'base_url': self.base_url,
                'test_time': datetime.now().isoformat(),
                'total_tests': total_tests
            },
            'summary': {
                'success_count': success_count,
                'fail_count': fail_count,
                'success_rate': f"{success_rate:.2f}%",
                'avg_duration_ms': round(avg_duration, 2),
                'min_duration_ms': round(min_duration, 2),
                'max_duration_ms': round(max_duration, 2),
                'p95_duration_ms': round(p95_duration, 2)
            },
            'detailed_results': self.results
        }

        # 保存JSON报告
        output_dir = os.path.dirname(os.path.abspath(__file__))
        timestamp = datetime.now().strftime('%Y%m%d-%H%M%S')
        json_file = os.path.join(output_dir, f'burndown_test_{timestamp}.json')

        with open(json_file, 'w', encoding='utf-8') as f:
            json.dump(report, f, ensure_ascii=False, indent=2)

        # 生成Markdown报告
        md_file = os.path.join(output_dir, f'burndown_test_{timestamp}.md')
        with open(md_file, 'w', encoding='utf-8') as f:
            f.write(f"# 燃尽图计算性能测试报告\n\n")
            f.write(f"## 测试信息\n\n")
            f.write(f"- **测试URL**: {self.base_url}\n")
            f.write(f"- **测试时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"- **测试次数**: {total_tests}\n\n")

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
            f.write(f"| P95耗时 | {p95_duration:.2f}ms |\n\n")

            f.write(f"## 详细测试结果\n\n")
            f.write(f"| 测试编号 | 状态 | 耗时(ms) | 时间戳 |\n")
            f.write(f"|---------|------|---------|--------|\n")

            for result in self.results:
                status = "✓ 成功" if result['success'] else "✗ 失败"
                duration = result.get('duration_ms', 0)
                timestamp = result['timestamp'].split('T')[1].split('.')[0]
                f.write(f"| {result['test_number']} | {status} | {duration:.2f} | {timestamp} |\n")

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
        print(f"P95耗时: {p95_duration:.2f}ms\n")
        print(f"报告已保存:")
        print(f"  JSON: {json_file}")
        print(f"  Markdown: {md_file}\n")

    def cleanup(self):
        """清理资源"""
        if self.driver:
            self.driver.quit()

    def run(self, sprint_id=1, test_count=5):
        """运行完整测试流程"""
        try:
            print("初始化Chrome驱动...")
            self.setup_driver()

            print("登录系统...")
            self.login()

            print("导航到冲刺页面...")
            self.navigate_to_sprint(sprint_id=sprint_id)

            print("开始性能测试...")
            self.test_burndown_calculate(sprint_id=sprint_id, test_count=test_count)

            print("生成测试报告...")
            self.generate_report()

        except Exception as e:
            print(f"测试过程中发生错误: {e}")
            import traceback
            traceback.print_exc()
        finally:
            print("清理资源...")
            self.cleanup()


def main():
    """主函数"""
    import argparse

    parser = argparse.ArgumentParser(description='燃尽图计算性能测试')
    parser.add_argument('--url', default='http://159.75.202.106:30173', help='前端URL')
    parser.add_argument('--sprint-id', type=int, default=1, help='冲刺ID')
    parser.add_argument('--count', type=int, default=5, help='测试次数')

    args = parser.parse_args()

    tester = BurndownCalculateTest(base_url=args.url)
    tester.run(sprint_id=args.sprint_id, test_count=args.count)


if __name__ == '__main__':
    main()
