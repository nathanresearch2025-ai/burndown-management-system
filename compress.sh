#!/bin/bash

# 压缩 backend 和 frontend 目录到 1.zip
# 如果 1.zip 已存在，会先删除再创建新的

set -e

echo "开始压缩 backend 和 frontend 目录..."

# 删除旧的 1.zip（如果存在）
if [ -f "1.zip" ]; then
    echo "删除旧的 1.zip..."
    rm -f 1.zip
fi

# 创建新的压缩文件
echo "创建新的 1.zip..."
zip -r 1.zip backend frontend

echo "压缩完成！"
echo "文件大小："
ls -lh 1.zip
echo ""
echo "=========================================="
echo "启动临时下载服务器..."
echo "请在浏览器中访问以下地址下载文件："
echo "http://159.75.202.106:8000/1.zip"
echo ""
echo "下载完成后按 Ctrl+C 停止服务器"
echo "=========================================="
echo ""
python3 -m http.server 8000
