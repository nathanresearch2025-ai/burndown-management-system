 # Cell 1: 测试安装
  import sys
  print(f"Python 版本: {sys.version}")
  print("✅ Jupyter Notebook 运行正常！")

  # Cell 2: 测试数据处理
  import pandas as pd
  import numpy as np

  # 创建示例数据
  data = {
      'task_type': ['STORY', 'BUG', 'TASK'],
      'story_points': [5, 3, 8],
      'actual_hours': [24, 12, 40]
  }
  df = pd.DataFrame(data)
  print(df)

  # Cell 3: 简单可视化
  import matplotlib.pyplot as plt

  plt.bar(df['task_type'], df['actual_hours'])
  plt.xlabel('任务类型')
  plt.ylabel('实际工时（小时）')
  plt.title('任务工时分布')
  plt.show()