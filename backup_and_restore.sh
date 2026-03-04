#!/bin/bash

# 备份和恢复脚本
# 功能：将 backend 和 frontend 移动到备份目录，然后解压新版本

set -e  # 遇到错误立即退出

# 获取当前日期
CURRENT_DATE=$(date +%Y%m%d)

# 设置版本号（可以通过参数传入，默认为 v1）
VERSION=${1:-v1}

# 设置备份目录
BACKUP_DIR="/backup/${CURRENT_DATE}_${VERSION}"

# 当前工作目录
WORK_DIR="/myapp"

echo "=========================================="
echo "开始备份和恢复流程"
echo "=========================================="
echo "当前日期: ${CURRENT_DATE}"
echo "版本号: ${VERSION}"
echo "备份目录: ${BACKUP_DIR}"
echo "=========================================="

# 1. 创建备份目录
echo "[步骤 1/4] 创建备份目录..."
if [ ! -d "${BACKUP_DIR}" ]; then
    mkdir -p "${BACKUP_DIR}"
    echo "✓ 备份目录创建成功: ${BACKUP_DIR}"
else
    echo "✓ 备份目录已存在: ${BACKUP_DIR}"
fi

# 2. 移动 backend 和 frontend 到备份目录
echo "[步骤 2/4] 移动 backend 和 frontend 到备份目录..."

if [ -d "${WORK_DIR}/backend" ]; then
    mv "${WORK_DIR}/backend" "${BACKUP_DIR}/"
    echo "✓ backend 已移动到 ${BACKUP_DIR}/backend"
else
    echo "⚠ backend 目录不存在，跳过"
fi

if [ -d "${WORK_DIR}/frontend" ]; then
    mv "${WORK_DIR}/frontend" "${BACKUP_DIR}/"
    echo "✓ frontend 已移动到 ${BACKUP_DIR}/frontend"
else
    echo "⚠ frontend 目录不存在，跳过"
fi

# 3. 解压 1.zip
echo "[步骤 3/4] 解压 1.zip..."
if [ -f "${WORK_DIR}/1.zip" ]; then
    cd "${WORK_DIR}"
    unzip -q 1.zip
    echo "✓ 1.zip 解压完成"
else
    echo "✗ 错误: 1.zip 文件不存在"
    exit 1
fi

# 4. 显示结果
echo "[步骤 4/4] 显示结果..."
echo ""
echo "备份目录内容:"
ls -lh "${BACKUP_DIR}"
echo ""
echo "当前工作目录内容:"
ls -lh "${WORK_DIR}"
echo ""
echo "=========================================="
echo "✓ 备份和恢复流程完成！"
echo "=========================================="
echo "备份位置: ${BACKUP_DIR}"
echo "使用方法: ./backup_and_restore.sh [版本号]"
echo "示例: ./backup_and_restore.sh v2"
echo "=========================================="
