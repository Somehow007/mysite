#!/bin/bash

# MySite 停止脚本

set -e

echo "========================================="
echo "  MySite 开发环境停止脚本"
echo "========================================="

cd "$(dirname "$0")"

echo "正在停止 Docker 服务..."
docker-compose down

echo ""
echo "服务已停止"
echo ""
echo "如需删除数据卷，请运行: docker-compose down -v"
echo ""
