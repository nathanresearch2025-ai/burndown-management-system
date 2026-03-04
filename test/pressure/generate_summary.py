#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
压力测试汇总报告生成器
"""

import json
import os
from datetime import datetime


def load_all_test_results(pressure_dir):
    """加载所有测试结果"""
    all_results = []

    for dirname in sorted(os.listdir(pressure_dir)):
        if dirname.startswith('202') and '-v' in dirname:
            result_file = os.path.join(pressure_dir, dirname, 'results.json')
            if os.path.exists(result_file):
                try:
                    with open(result_file, 'r', encoding='utf-8') as f:
                        data = json.load(f)
                        all_results.append({
                            'version': dirname,
                            'data': data
                        })
                except Exception as e:
                    print(f"警告: 无法读取 {result_file}: {e}")

    return all_results


def generate_summary_html(pressure_dir):
    """生成汇总HTML报告"""
    all_results = load_all_test_results(pressure_dir)

    if not all_results:
        print("没有找到测试结果")
        return

    # 生成表格行
    table_rows = ""
    for result in all_results:
        version = result['version']
        data = result['data']

        # 提取关键指标
        baseline = data[0]
        standard = data[1]
        peak = data[2]

        # 计算汇总指标
        total_requests = baseline['total_requests'] + standard['total_requests'] + peak['total_requests']
        avg_success_rate = (baseline['success_rate'] + standard['success_rate'] + peak['success_rate']) / 3

        # 判断状态
        status_class = 'status-good' if avg_success_rate >= 99.9 else 'status-warning' if avg_success_rate >= 99 else 'status-bad'
        status_text = '优秀' if avg_success_rate >= 99.9 else '良好' if avg_success_rate >= 99 else '需优化'

        table_rows += f"""
            <tr>
                <td><strong>{version}</strong></td>
                <td>{baseline['users']}</td>
                <td>{standard['users']}</td>
                <td>{peak['users']}</td>
                <td>{total_requests}</td>
                <td>{baseline['rps']:.2f}</td>
                <td>{standard['rps']:.2f}</td>
                <td>{peak['rps']:.2f}</td>
                <td>{avg_success_rate:.2f}%</td>
                <td><span class="{status_class}">{status_text}</span></td>
                <td><a href="{version}/scenario_comparison_report.html" target="_blank">查看详情</a></td>
            </tr>
        """

    html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>压力测试汇总报告</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif;
               background: #f5f7fa; padding: 20px; line-height: 1.6; }}
        .container {{ max-width: 1600px; margin: 0 auto; background: white;
                     border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); padding: 40px; }}
        h1 {{ color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 15px; margin-bottom: 30px; }}
        .meta {{ color: #7f8c8d; font-size: 14px; margin-bottom: 30px; }}
        .summary-stats {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                         gap: 20px; margin: 30px 0; }}
        .stat-card {{ background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                     color: white; padding: 20px; border-radius: 8px; text-align: center; }}
        .stat-card.green {{ background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%); }}
        .stat-card.blue {{ background: linear-gradient(135deg, #2196F3 0%, #1976D2 100%); }}
        .stat-card.orange {{ background: linear-gradient(135deg, #FF9800 0%, #F57C00 100%); }}
        .stat-label {{ font-size: 14px; opacity: 0.9; }}
        .stat-value {{ font-size: 32px; font-weight: bold; margin-top: 10px; }}
        table {{ width: 100%; border-collapse: collapse; margin: 20px 0;
                box-shadow: 0 1px 3px rgba(0,0,0,0.1); font-size: 13px; }}
        th, td {{ padding: 12px 10px; text-align: center; border-bottom: 1px solid #e0e0e0; }}
        th {{ background: #3498db; color: white; font-weight: 600; position: sticky; top: 0; }}
        tr:hover {{ background: #f8f9fa; }}
        .status-good {{ color: #27ae60; font-weight: bold; }}
        .status-warning {{ color: #f39c12; font-weight: bold; }}
        .status-bad {{ color: #e74c3c; font-weight: bold; }}
        a {{ color: #3498db; text-decoration: none; }}
        a:hover {{ text-decoration: underline; }}
        .footer {{ margin-top: 50px; padding-top: 20px; border-top: 1px solid #e0e0e0;
                  text-align: center; color: #7f8c8d; font-size: 13px; }}
    </style>
</head>
<body>
    <div class="container">
        <h1>📊 压力测试汇总报告</h1>
        <div class="meta">最后更新时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</div>

        <div class="summary-stats">
            <div class="stat-card green">
                <div class="stat-label">总测试次数</div>
                <div class="stat-value">{len(all_results)}</div>
            </div>
            <div class="stat-card blue">
                <div class="stat-label">最新版本</div>
                <div class="stat-value">{all_results[-1]['version'].split('-')[-1] if all_results else 'N/A'}</div>
            </div>
            <div class="stat-card orange">
                <div class="stat-label">最高RPS</div>
                <div class="stat-value">{max([r['data'][2]['rps'] for r in all_results]):.1f}</div>
            </div>
        </div>

        <h2 style="color: #34495e; margin-top: 40px; margin-bottom: 20px;">历史测试记录</h2>
        <table>
            <thead>
                <tr>
                    <th>版本</th>
                    <th>Baseline<br>用户数</th>
                    <th>Standard<br>用户数</th>
                    <th>Peak<br>用户数</th>
                    <th>总请求数</th>
                    <th>Baseline<br>RPS</th>
                    <th>Standard<br>RPS</th>
                    <th>Peak<br>RPS</th>
                    <th>平均成功率</th>
                    <th>状态</th>
                    <th>操作</th>
                </tr>
            </thead>
            <tbody>
                {table_rows}
            </tbody>
        </table>

        <div class="footer">
            <p>压力测试汇总报告 - 自动生成</p>
            <p>报告路径: /myapp/test/pressure/summary_report.html</p>
        </div>
    </div>
</body>
</html>
"""

    output_path = os.path.join(pressure_dir, 'summary_report.html')
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(html_content)

    print(f"✓ 汇总报告已生成: {output_path}")
    return output_path


if __name__ == '__main__':
    pressure_dir = '/myapp/test/pressure'
    generate_summary_html(pressure_dir)
