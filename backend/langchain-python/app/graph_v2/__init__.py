# 等价 Java：package com.burndown.graph_v2;（包声明，对外只暴露 build_graph_v2）
from .graph import build_graph_v2   # 等价 Java：import com.burndown.graph_v2.GraphBuilder; （从同包 graph 模块导入图构建函数）

# 等价 Java：public 导出列表，相当于只把 build_graph_v2 声明为 public，其余为 package-private
__all__ = ["build_graph_v2"]   # 等价 Java：// 只有 build_graph_v2 对外可见，其余类为包内访问
